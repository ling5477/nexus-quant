package com.guidinglight.nexusquant.app.marketdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.model.PublicMarketDataEndpointCategory;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.model.PublicMarketDataOutboundRequest;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.port.PublicMarketDataOutboundClient;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.port.PublicMarketDataOutboundClient.SnapshotResponse;
import com.guidinglight.nexusquant.adapter.okx.marketdata.OkxHistoricalKlineAdapter;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.PublicMarketSnapshot;
import com.guidinglight.nexusquant.marketdata.domain.PublicMarketSnapshot.Freshness;
import com.guidinglight.nexusquant.marketdata.domain.PublicMarketSnapshot.SourceStatus;
import com.guidinglight.nexusquant.marketdata.domain.instrument.InstrumentCatalogItem;
import com.guidinglight.nexusquant.marketdata.domain.instrument.port.InstrumentCatalogReadPort;
import com.guidinglight.nexusquant.marketdata.domain.port.PublicMarketSnapshotReadPort;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Service
final class PublicMarketSnapshotService implements PublicMarketSnapshotReadPort {
    private static final Pattern SPOT_SYMBOL = Pattern.compile("[A-Z0-9]{1,24}-[A-Z0-9]{1,24}");
    private static final Set<String> ACTIVE = Set.of("TRADING", "LIVE", "ACTIVE");
    private static final int MAX_IN_FLIGHT = 4;
    private final InstrumentCatalogReadPort catalog;
    private final Supplier<PublicMarketDataOutboundClient> client;
    private final BooleanSupplier outboundAllowed;
    private final Clock clock;
    private final OkxPublicSnapshotParser parser;
    private final ExecutorService tickerExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<Selection, CompletableFuture<PublicMarketSnapshot>> inFlight = new HashMap<>();
    private final AtomicReference<Instant> retryAt = new AtomicReference<>(Instant.EPOCH);

    @Autowired
    PublicMarketSnapshotService(InstrumentCatalogReadPort catalog,
                                ObjectProvider<PublicMarketDataOutboundClient> client,
                                ObjectMapper mapper, Environment environment) {
        this(catalog, client::getIfAvailable, mapper,
                () -> ManualPublicMarketRead.allowed(environment), Clock.systemUTC());
    }

    PublicMarketSnapshotService(InstrumentCatalogReadPort catalog,
                               Supplier<PublicMarketDataOutboundClient> client,
                               ObjectMapper mapper, BooleanSupplier outboundAllowed, Clock clock) {
        this.catalog = catalog;
        this.client = client;
        this.outboundAllowed = outboundAllowed;
        this.clock = clock;
        this.parser = new OkxPublicSnapshotParser(mapper);
    }

    @Override
    public PublicMarketSnapshot read(String exchangeCode, String marketType, String symbol, String interval) {
        if (!"OKX".equals(exchangeCode) || !"SPOT".equals(marketType)
                || symbol == null || !SPOT_SYMBOL.matcher(symbol).matches()) throw unsupported();
        BarInterval barInterval;
        try {
            barInterval = BarInterval.fromWireValue(interval);
        } catch (IllegalArgumentException ex) {
            throw unsupported();
        }
        if (!barInterval.wireValue().equals(interval)) throw unsupported();
        List<InstrumentCatalogItem> instruments = catalog.findByExchangeAndSymbols("OKX", List.of(symbol));
        boolean visible = instruments.stream().anyMatch(item ->
                "OKX".equals(item.exchangeCode()) && "SPOT".equals(item.instrumentType())
                        && symbol.equals(item.internalSymbol()) && symbol.equals(item.exchangeSymbol())
                        && item.status() != null && ACTIVE.contains(item.status()));
        if (!visible) throw unsupported();

        var selection = new Selection(symbol, barInterval);
        if (!outboundAllowed.getAsBoolean()) return unavailable(selection, SourceStatus.DISABLED, null);
        PublicMarketDataOutboundClient outbound = client.get();
        if (outbound == null) return unavailable(selection, SourceStatus.DISABLED, null);
        Instant cooldown = retryAt.get();
        if (clock.instant().isBefore(cooldown)) return unavailable(selection, SourceStatus.RATE_LIMITED, cooldown);

        CompletableFuture<PublicMarketSnapshot> existing;
        var result = new CompletableFuture<PublicMarketSnapshot>();
        synchronized (inFlight) {
            existing = inFlight.get(selection);
            if (existing == null) {
                if (inFlight.size() >= MAX_IN_FLIGHT) return unavailable(selection, SourceStatus.BUSY, null);
                inFlight.put(selection, result);
            }
        }
        if (existing != null) return existing.join();
        try {
            var snapshot = fetch(selection, outbound);
            result.complete(snapshot);
            return snapshot;
        } catch (RuntimeException | Error ex) {
            result.completeExceptionally(ex);
            throw ex;
        } finally {
            synchronized (inFlight) {
                inFlight.remove(selection);
            }
        }
    }

    private PublicMarketSnapshot fetch(Selection selection, PublicMarketDataOutboundClient outbound) {
        String symbol = selection.symbol();
        String candlePath = "/api/v5/market/candles?instId=" + symbol
                + "&bar=" + OkxHistoricalKlineAdapter.venueInterval(selection.interval().wireValue()) + "&limit="
                + OkxPublicSnapshotParser.MAX_BARS;
        // 每个快照最多两个读取，共用四个选中市场的并发上限；各端点有独立成功事实。
        var ticker = CompletableFuture.supplyAsync(() -> readPart(outbound,
                PublicMarketDataEndpointCategory.TICKER, "/api/v5/market/ticker?instId=" + symbol,
                response -> parser.ticker(response.body(), symbol, response.observedAt())), tickerExecutor);
        var bars = readPart(outbound, PublicMarketDataEndpointCategory.OHLCV, candlePath,
                response -> parser.candles(response.body(), selection.interval(), response.observedAt()));
        return compose(selection, ticker.join(), bars);
    }

    private <T> Part<T> readPart(PublicMarketDataOutboundClient outbound,
                                 PublicMarketDataEndpointCategory category, String path,
                                 Function<SnapshotResponse, T> parse) {
        SnapshotResponse response = outbound.fetchSnapshot(PublicMarketDataOutboundRequest.publicGet("OKX", category, path));
        if (response.retryAt() != null) rememberCooldown(response.retryAt());
        return switch (response.error()) {
            case NONE -> {
                try {
                    yield new Part<>(parse.apply(response), response.observedAt(), SourceStatus.AVAILABLE, null);
                } catch (OkxPublicSnapshotParser.SourceFailure ex) {
                    Instant cooldown = ex.status() == SourceStatus.RATE_LIMITED
                            ? rememberCooldown(clock.instant().plusSeconds(30)) : null;
                    yield Part.failed(ex.status(), cooldown);
                }
            }
            case DISABLED -> Part.failed(SourceStatus.DISABLED, null);
            case DENIED -> Part.failed(SourceStatus.DENIED, null);
            case RATE_LIMITED -> Part.failed(SourceStatus.RATE_LIMITED, response.retryAt());
            case TIMEOUT -> Part.failed(SourceStatus.TIMEOUT, null);
            case INVALID_RESPONSE -> Part.failed(SourceStatus.INVALID_RESPONSE, null);
            default -> Part.failed(SourceStatus.SOURCE_UNAVAILABLE, null);
        };
    }

    private Instant rememberCooldown(Instant next) {
        return retryAt.accumulateAndGet(next, PublicMarketSnapshotService::latest);
    }

    private PublicMarketSnapshot compose(Selection selection, Part<OkxPublicSnapshotParser.Ticker> ticker,
                                          Part<List<PublicMarketSnapshot.Bar>> bars) {
        Instant now = clock.instant();
        Freshness tickerFreshness = ticker.data() == null ? Freshness.UNAVAILABLE
                : freshness(now, ticker.data().venueTime().plusSeconds(OkxPublicSnapshotParser.FRESH_SECONDS));
        Freshness barsFreshness = Freshness.UNAVAILABLE;
        List<PublicMarketSnapshot.Bar> candles = bars.data() == null ? List.of() : bars.data();
        if (!candles.isEmpty()) {
            var tail = candles.getLast();
            Instant expiresAt = tail.status() == PublicMarketSnapshot.BarStatus.FORMING
                    ? tail.closeTime()
                    : tail.closeTime().plus(selection.interval().duration())
                            .plusSeconds(OkxPublicSnapshotParser.FRESH_SECONDS);
            barsFreshness = freshness(now, expiresAt);
        }
        if (tickerFreshness == Freshness.FRESH && observedStale(ticker, now)) tickerFreshness = Freshness.STALE;
        if (barsFreshness == Freshness.FRESH && observedStale(bars, now)) barsFreshness = Freshness.STALE;
        Freshness snapshotFreshness = tickerFreshness == barsFreshness ? tickerFreshness
                : tickerFreshness == Freshness.UNAVAILABLE || barsFreshness == Freshness.UNAVAILABLE
                    ? Freshness.PARTIAL : Freshness.STALE;
        return new PublicMarketSnapshot("OKX", "SPOT", selection.symbol(), selection.interval().wireValue(),
                ticker.data() == null ? null : ticker.data().price(),
                ticker.data() == null ? null : ticker.data().venueTime(), ticker.observedAt(),
                "OKX_PUBLIC_TICKER", candles, bars.observedAt(), "OKX_PUBLIC_CANDLES",
                latest(ticker.observedAt(), bars.observedAt()), snapshotFreshness, tickerFreshness, barsFreshness,
                ticker.status(), bars.status(), latest(ticker.retryAt(), bars.retryAt()));
    }

    private static boolean observedStale(Part<?> part, Instant now) {
        return freshness(now, part.observedAt().plusSeconds(OkxPublicSnapshotParser.FRESH_SECONDS))
                == Freshness.STALE;
    }

    private static Freshness freshness(Instant now, Instant expiresAt) {
        return now.isAfter(expiresAt) ? Freshness.STALE : Freshness.FRESH;
    }

    private PublicMarketSnapshot unavailable(Selection selection, SourceStatus status, Instant cooldown) {
        return compose(selection, Part.failed(status, cooldown), Part.failed(status, cooldown));
    }

    private static Instant latest(Instant a, Instant b) {
        return a == null || (b != null && b.isAfter(a)) ? b : a;
    }

    private static UnsupportedSelection unsupported() {
        return new UnsupportedSelection();
    }

    @PreDestroy
    void stop() {
        tickerExecutor.shutdownNow();
    }

    private record Selection(String symbol, BarInterval interval) {}

    private record Part<T>(T data, Instant observedAt, SourceStatus status, Instant retryAt) {
        private static <T> Part<T> failed(SourceStatus status, Instant retryAt) {
            return new Part<>(null, null, status, retryAt);
        }
    }
}
