package com.guidinglight.nexusquant.app.marketdata;

import com.guidinglight.nexusquant.adapter.okx.model.OkxVenueRuleFact;
import com.guidinglight.nexusquant.adapter.okx.model.OkxVenueRuleFactsSnapshot;
import com.guidinglight.nexusquant.adapter.okx.service.OkxVenueRuleFactsProvider;
import com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogService;
import com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogUpsertStats;
import com.guidinglight.nexusquant.marketdata.domain.instrument.InstrumentCatalogItem;
import com.guidinglight.nexusquant.marketdata.domain.instrument.OkxVenueRuleContract;
import com.guidinglight.nexusquant.marketdata.domain.instrument.VenueRuleChecksumCalculator;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OkxVenueRuleFactsSyncService 编排 GateW-3 operator-triggered public metadata sync。
 *
 * <p>线程安全：依赖均为无共享可变状态或线程安全组件；每次调用只处理 server-side allowlist 中的 1..3
 * 个 OKX Spot symbols。副作用仅为 bounded UPSERT `instrument_catalog` 和脱敏日志；无 Controller、
 * scheduler、startup/background polling、credential、private endpoint、LIVE 或交易动作。</p>
 */
public final class OkxVenueRuleFactsSyncService {

    private static final Logger log = LoggerFactory.getLogger(OkxVenueRuleFactsSyncService.class);
    private static final Pattern OKX_SPOT_SYMBOL = Pattern.compile("[A-Z0-9]+-[A-Z0-9]+");

    private final InstrumentCatalogService instrumentCatalogService;
    private final OkxVenueRuleFactsProvider venueRuleFactsProvider;
    private final VenueRuleChecksumCalculator checksumCalculator;
    private final Clock clock;
    private final Set<String> serverAllowlist;

    public OkxVenueRuleFactsSyncService(
            InstrumentCatalogService instrumentCatalogService,
            OkxVenueRuleFactsProvider venueRuleFactsProvider,
            Clock clock,
            Set<String> serverAllowlist
    ) {
        this.instrumentCatalogService = Objects.requireNonNull(
                instrumentCatalogService,
                "instrumentCatalogService must not be null"
        );
        this.venueRuleFactsProvider = Objects.requireNonNull(
                venueRuleFactsProvider,
                "venueRuleFactsProvider must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.serverAllowlist = normalizeSymbols(serverAllowlist, "serverAllowlist");
        this.checksumCalculator = new VenueRuleChecksumCalculator();
    }

    /**
     * 同步 1..3 个 allowlisted symbols。
     *
     * <p>整批 public fetch/parse/checksum/read 任一步失败均不会调用 UPSERT，旧 snapshot 和 observedAt 保持
     * 不变并自然 stale。相同 checksum 只刷新 observedAt/syncedAt；不同 checksum 覆盖 current facts 并记录
     * 不含 raw payload 的变化摘要。</p>
     *
     * @param requestedSymbols 本次 operator 选择的 1..3 个 server-allowlisted OKX Spot symbols
     * @param traceId 脱敏追踪标识
     * @return bounded sync 统计
     */
    public OkxVenueRuleFactsSyncResult sync(Set<String> requestedSymbols, String traceId) {
        Set<String> normalizedRequested = normalizeSymbols(requestedSymbols, "requestedSymbols");
        if (!serverAllowlist.containsAll(normalizedRequested)) {
            throw new IllegalArgumentException("requestedSymbols must be a subset of the server allowlist");
        }
        OkxVenueRuleFactsSnapshot snapshot = venueRuleFactsProvider.fetch(normalizedRequested, traceId);
        List<InstrumentCatalogItem> items = snapshot.facts().stream()
                .map(fact -> toCatalogItem(fact, snapshot.observedAt()))
                .toList();
        List<String> symbols = items.stream().map(InstrumentCatalogItem::exchangeSymbol).sorted().toList();
        Map<String, String> previousChecksums = previousChecksums(symbols);
        Instant syncedAt = Instant.now(clock);
        InstrumentCatalogUpsertStats stats = instrumentCatalogService.upsertVenueRuleFacts(items, syncedAt);
        String safeTraceId = sanitizeTraceId(traceId);
        for (InstrumentCatalogItem item : items) {
            String previousChecksum = previousChecksums.get(item.exchangeSymbol());
            String result = previousChecksum == null
                    ? "INSERTED"
                    : previousChecksum.equals(item.ruleChecksum()) ? "REFRESHED" : "RULE_CHANGED";
            log.info(
                    "okx_venue_rule_sync trace_id={} exchange=OKX symbol={} old_checksum={} new_checksum={} "
                            + "source_schema_version={} observed_at={} result={}",
                    safeTraceId,
                    item.exchangeSymbol(),
                    previousChecksum,
                    item.ruleChecksum(),
                    item.sourceSchemaVersion(),
                    item.observedAt(),
                    result
            );
        }
        return new OkxVenueRuleFactsSyncResult(
                symbols,
                stats.insertedCount(),
                stats.updatedCount(),
                snapshot.observedAt(),
                syncedAt
        );
    }

    private InstrumentCatalogItem toCatalogItem(OkxVenueRuleFact fact, Instant observedAt) {
        InstrumentCatalogItem withoutChecksum = new InstrumentCatalogItem(
                null,
                "OKX",
                fact.instType(),
                fact.instId(),
                fact.instId(),
                fact.baseCurrency(),
                fact.quoteCurrency(),
                fact.state(),
                fact.tickSize(),
                fact.lotSize(),
                fact.minimumSize(),
                fact.maximumLimitSize(),
                fact.maximumMarketSize(),
                fact.maximumMarketSizeUnit(),
                fact.maximumLimitAmountUsd(),
                fact.maximumMarketAmountUsd(),
                OkxVenueRuleContract.SOURCE,
                OkxVenueRuleContract.SOURCE_SCHEMA_VERSION,
                observedAt,
                null,
                fact.nextRuleEffectiveAt(),
                null,
                null,
                null
        );
        String checksum = checksumCalculator.calculate(withoutChecksum);
        return new InstrumentCatalogItem(
                withoutChecksum.instrumentId(),
                withoutChecksum.exchangeCode(),
                withoutChecksum.instrumentType(),
                withoutChecksum.exchangeSymbol(),
                withoutChecksum.internalSymbol(),
                withoutChecksum.baseAsset(),
                withoutChecksum.quoteAsset(),
                withoutChecksum.status(),
                withoutChecksum.tickSize(),
                withoutChecksum.stepSize(),
                withoutChecksum.minQuantity(),
                withoutChecksum.maxLimitQuantity(),
                withoutChecksum.maxMarketSize(),
                withoutChecksum.maxMarketSizeUnit(),
                withoutChecksum.maxLimitNotionalUsd(),
                withoutChecksum.maxMarketNotionalUsd(),
                withoutChecksum.source(),
                withoutChecksum.sourceSchemaVersion(),
                withoutChecksum.observedAt(),
                withoutChecksum.syncedAt(),
                withoutChecksum.nextRuleEffectiveAt(),
                checksum,
                withoutChecksum.createdAt(),
                withoutChecksum.updatedAt()
        );
    }

    private Map<String, String> previousChecksums(List<String> symbols) {
        Map<String, String> checksums = new HashMap<>();
        for (InstrumentCatalogItem item : instrumentCatalogService.findByExchangeAndSymbols("OKX", symbols)) {
            checksums.put(item.exchangeSymbol(), item.ruleChecksum());
        }
        return checksums;
    }

    private static Set<String> normalizeSymbols(Set<String> symbols, String fieldName) {
        Objects.requireNonNull(symbols, fieldName + " must not be null");
        if (symbols.isEmpty() || symbols.size() > 3) {
            throw new IllegalArgumentException(fieldName + " must contain 1..3 symbols");
        }
        Set<String> normalized = new HashSet<>();
        List<String> invalid = new ArrayList<>();
        for (String symbol : symbols) {
            String value = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
            if (!OKX_SPOT_SYMBOL.matcher(value).matches()) {
                invalid.add(value);
            } else {
                normalized.add(value);
            }
        }
        if (!invalid.isEmpty() || normalized.size() != symbols.size()) {
            throw new IllegalArgumentException(fieldName + " contains invalid or duplicate OKX Spot symbols");
        }
        return Set.copyOf(normalized);
    }

    private static String sanitizeTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return "UNKNOWN";
        }
        String sanitized = traceId.replaceAll("[^A-Za-z0-9._:-]", "_");
        return sanitized.substring(0, Math.min(sanitized.length(), 128));
    }
}
