package com.guidinglight.nexusquant.marketdata.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.BarProvenance;
import com.guidinglight.nexusquant.marketdata.domain.DiscountAssetPriceSnapshot;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataDataset;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataDatasetCoverage;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataDatasetStatus;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityStatus;
import com.guidinglight.nexusquant.marketdata.domain.port.DiscountAssetPriceSnapshotRepository;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataDatasetRepository;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 用确定性历史来源验证as-of、所有者、覆盖和内容身份；不访问供应方或账务。 */
class DiscountAssetPriceServiceTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant FROM = START.plusSeconds(300),
            TO = START.plusSeconds(600),
            FROZEN = TO.plusSeconds(60);

    @Test
    void shouldUseLatestVisibleObservationAndRestoreExactContent() {
        Fixture fixture = new Fixture(bars(10, 1), 10);
        DiscountAssetPriceSnapshot frozen = fixture.freeze();
        assertEquals(new BigDecimal("304"), frozen.priceAt(FROM).quotePerAsset());
        assertEquals(new BigDecimal("305"), frozen.priceAt(FROM.plusSeconds(1)).quotePerAsset());
        assertEquals(FROM, frozen.priceAt(FROM.plusSeconds(1)).observedAt());
        assertEquals(FROM.plusSeconds(1), frozen.priceAt(FROM.plusSeconds(1)).visibleAt());
        var point = frozen.priceAt(FROM);
        assertEquals(point.sourceSha256(), DiscountAssetPriceSnapshot.sha256(point.sourceJson()));
        assertEquals(frozen.sourceSha256(), DiscountAssetPriceSnapshot.sha256(frozen.sourceJson()));
        DiscountAssetPriceSnapshot restored =
                fixture.service.read(7, frozen.snapshotId(), frozen.sourceSha256());
        assertEquals(frozen.sourceJson(), restored.sourceJson());
        assertEquals(frozen.points(), restored.points());
        assertThrows(UnsupportedOperationException.class, () -> frozen.points().clear());
    }

    @Test
    void shouldCarryActualReserveBalanceWithoutManufacturingRebateCredit() {
        DiscountAssetPriceSnapshot frozen = new Fixture(bars(10, 1), 10).freeze();
        var funding = frozen.funding(FROM, new BigDecimal("0.125"));
        assertEquals("BNB", funding.asset());
        assertEquals("USDT", funding.quoteCurrency());
        assertEquals(new BigDecimal("0.125"), funding.availableAfterReserves());
        assertEquals(new BigDecimal("304"), funding.quotePerAsset());
        assertThrows(
                IllegalArgumentException.class,
                () -> frozen.funding(FROM, new BigDecimal("-0.01")));
    }

    @Test
    void shouldRejectWrongOwnerAndExpectedDigest() {
        Fixture fixture = new Fixture(bars(10, 1), 10);
        DiscountAssetPriceSnapshot frozen = fixture.freeze();
        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service.read(8, frozen.snapshotId(), frozen.sourceSha256()));
        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service.read(7, frozen.snapshotId(), "a".repeat(64)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        DiscountAssetPriceSnapshot.restore(
                                frozen.sourceJson().replace("304", "404"), frozen.sourceSha256()));
    }

    @Test
    void shouldRejectUnavailableAndOutsideFrozenWindow() {
        List<HistoricalBar> late =
                bars(10, 1).stream()
                        .map(bar -> replace(bar, bar.closePrice(), TO.plusSeconds(30)))
                        .toList();
        DiscountAssetPriceSnapshot frozen = new Fixture(late, 10).freeze();
        assertEquals(
                "FEE_DISCOUNT_PRICE_UNAVAILABLE",
                assertThrows(IllegalArgumentException.class, () -> frozen.priceAt(FROM))
                        .getMessage());
        assertEquals(
                "FEE_DISCOUNT_PRICE_UNAVAILABLE",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> frozen.priceAt(TO.plusSeconds(1)))
                        .getMessage());
    }

    @Test
    void shouldRejectGapFutureSourceNonMicrosecondAndNonpositivePrice() {
        List<HistoricalBar> gap = new ArrayList<>(bars(10, 1));
        gap.remove(4);
        assertThrows(IllegalArgumentException.class, () -> new Fixture(gap, 10).freeze());
        List<HistoricalBar> future = new ArrayList<>(bars(10, 1));
        future.set(4, replace(future.get(4), new BigDecimal("305"), FROZEN.plusSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new Fixture(future, 10).freeze());
        List<HistoricalBar> zero = new ArrayList<>(bars(10, 1));
        zero.set(4, replace(zero.get(4), BigDecimal.ZERO, FROM.plusSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new Fixture(zero, 10).freeze());
        Fixture fixture = new Fixture(bars(10, 1), 10);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        fixture.service.freeze(
                                7,
                                fixture.dataset.datasetId(),
                                "BINANCE",
                                "BNB",
                                "USDT",
                                FROM.plusNanos(1),
                                TO,
                                FROZEN));
    }

    @Test
    void shouldPreserveOldSnapshotAfterSourceVersionChanges() {
        Fixture fixture = new Fixture(bars(10, 1), 10);
        DiscountAssetPriceSnapshot first = fixture.freeze();
        fixture.input.set(
                3, replace(fixture.input.get(3), new BigDecimal("777"), START.plusSeconds(241)));
        assertEquals(
                new BigDecimal("304"),
                fixture.service
                        .read(7, first.snapshotId(), first.sourceSha256())
                        .priceAt(FROM)
                        .quotePerAsset());
        // 高低价也属于版本内容；数据被改坏时不能以仍为READY的旧元数据绕过准入。
        assertThrows(IllegalArgumentException.class, fixture::freeze);
    }

    @Test
    void shouldRejectNoncanonicalDatasetAndIncompleteCoverage() {
        Fixture fixture = new Fixture(bars(10, 1), 10);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        fixture.service.freeze(
                                7,
                                fixture.dataset.datasetId(),
                                "OKX",
                                "BNB",
                                "USDT",
                                FROM,
                                TO,
                                FROZEN));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        fixture.service.freeze(
                                7,
                                fixture.dataset.datasetId(),
                                "BINANCE",
                                "BTC",
                                "USDT",
                                FROM,
                                TO,
                                FROZEN));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        fixture.service.freeze(
                                7,
                                fixture.dataset.datasetId(),
                                "BINANCE",
                                "BNB",
                                "USDT",
                                FROM,
                                TO.plusSeconds(1),
                                FROZEN));
    }

    @Test
    void shouldReadBoundedPagesWithoutTruncatingSeries() {
        Fixture fixture = new Fixture(bars(600, 0), 600);
        Instant to = START.plusSeconds(600 * 60L);
        DiscountAssetPriceSnapshot frozen =
                fixture.service.freeze(
                        7,
                        fixture.dataset.datasetId(),
                        "BINANCE",
                        "BNB",
                        "USDT",
                        FROM,
                        to,
                        to.plusSeconds(1));
        assertEquals(600, frozen.points().size());
        assertEquals(2, fixture.pages.size());
        assertTrue(frozen.sourceJson().contains("rawPayloadSha256"));
    }

    @Test
    void shouldAcceptExactlyFiveMinuteOldPointAndRejectNextMicrosecond() {
        List<HistoricalBar> delayed =
                bars(10, 1).stream()
                        .map(bar -> replace(bar, bar.closePrice(), TO.plusSeconds(30)))
                        .collect(Collectors.toCollection(ArrayList::new));
        delayed.set(0, bars(10, 1).getFirst());
        DiscountAssetPriceSnapshot frozen = new Fixture(delayed, 10).freeze();
        Instant boundary = START.plusSeconds(360);
        assertEquals(new BigDecimal("301"), frozen.priceAt(boundary).quotePerAsset());
        assertEquals(
                "FEE_DISCOUNT_PRICE_UNAVAILABLE",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> frozen.priceAt(boundary.plusNanos(1000)))
                        .getMessage());
    }

    @Test
    void shouldKeepSecondPrecisionWithoutDroppingTheLeadingSourceBar() {
        Fixture fixture = new Fixture(bars(10, 1), 10);
        DiscountAssetPriceSnapshot frozen =
                fixture.service.freeze(
                        7,
                        fixture.dataset.datasetId(),
                        "BINANCE",
                        "BNB",
                        "USDT",
                        FROM.plusSeconds(30),
                        TO,
                        FROZEN);
        assertEquals(new BigDecimal("305"), frozen.priceAt(FROM.plusSeconds(30)).quotePerAsset());
        assertEquals(10, frozen.points().size());
    }

    private static List<HistoricalBar> bars(int count, int delay) {
        List<HistoricalBar> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            Instant open = START.plusSeconds(index * 60L), close = open.plusSeconds(60);
            BigDecimal price = BigDecimal.valueOf(301L + index);
            result.add(
                    new HistoricalBar(
                            "BINANCE",
                            "SPOT",
                            "BNB-USDT",
                            BarInterval.ONE_MINUTE,
                            open,
                            close,
                            price,
                            price,
                            price,
                            price,
                            BigDecimal.ONE,
                            BigDecimal.TEN,
                            1L,
                            "OK",
                            "{\"n\":" + index + "}",
                            close.plusSeconds(delay)));
        }
        return result;
    }

    private static HistoricalBar replace(HistoricalBar bar, BigDecimal price, Instant visible) {
        return new HistoricalBar(
                bar.exchangeCode(),
                bar.marketType(),
                bar.symbol(),
                bar.interval(),
                bar.openTime(),
                bar.closeTime(),
                bar.openPrice(),
                bar.highPrice(),
                bar.lowPrice(),
                price,
                bar.volume(),
                bar.quoteVolume(),
                bar.tradeCount(),
                bar.qualityStatus(),
                bar.rawPayloadJson(),
                visible);
    }

    private static final class Fixture {
        final List<HistoricalBar> input;
        final List<Integer> pages = new ArrayList<>();
        final MarketdataDataset dataset;
        final DiscountAssetPriceService service;

        Fixture(List<HistoricalBar> bars, int count) {
            input = new ArrayList<>(bars);
            dataset =
                    new MarketdataDataset(
                            UUID.randomUUID(),
                            "Synthetic BNB history",
                            "BINANCE",
                            "SPOT",
                            "BNB-USDT",
                            BarInterval.ONE_MINUTE,
                            START,
                            START.plusSeconds(count * 60L),
                            MarketdataDatasetStatus.READY,
                            MarketdataQualityStatus.OK,
                            count,
                            0,
                            "marketdata_bars",
                            "synthetic",
                            FROZEN,
                            FROZEN,
                            "{}");
            Map<UUID, DiscountAssetPriceSnapshot> stored = new HashMap<>();
            DiscountAssetPriceSnapshotRepository snapshots =
                    new DiscountAssetPriceSnapshotRepository() {
                        public void insert(DiscountAssetPriceSnapshot value) {
                            stored.put(value.snapshotId(), value);
                        }

                        public Optional<DiscountAssetPriceSnapshot> findByIdForOwner(
                                long owner, UUID id) {
                            return Optional.ofNullable(stored.get(id))
                                    .filter(row -> row.ownerUserId() == owner);
                        }
                    };
            service =
                    new DiscountAssetPriceService(
                            new DatasetRepository(dataset),
                            query -> {
                                pages.add(query.page());
                                return input.stream()
                                        .filter(
                                                bar ->
                                                        !bar.openTime().isBefore(query.startTime())
                                                                && !bar.closeTime()
                                                                        .isAfter(query.endTime()))
                                        .skip((long) query.page() * query.size())
                                        .limit(query.size())
                                        .toList();
                            },
                            snapshots,
                            (exchange, market, symbol, interval, opens) -> {
                                Map<Instant, BarProvenance> result = new HashMap<>();
                                for (Instant open : opens) {
                                    HistoricalBar bar =
                                            input.stream()
                                                    .filter(row -> row.openTime().equals(open))
                                                    .findFirst()
                                                    .orElseThrow();
                                    result.put(
                                            open,
                                            new BarProvenance(
                                                    open,
                                                    "FIXTURE_SYNC",
                                                    bar.availableAt(),
                                                    (long) input.indexOf(bar) + 1,
                                                    DiscountAssetPriceSnapshot.sha256(
                                                            bar.rawPayloadJson()),
                                                    null));
                                }
                                return result;
                            });
        }

        DiscountAssetPriceSnapshot freeze() {
            return service.freeze(
                    7, dataset.datasetId(), "BINANCE", "BNB", "USDT", FROM, TO, FROZEN);
        }
    }

    private record DatasetRepository(MarketdataDataset dataset)
            implements MarketdataDatasetRepository {
        public Optional<MarketdataDataset> findByDatasetId(UUID id) {
            return dataset.datasetId().equals(id) ? Optional.of(dataset) : Optional.empty();
        }

        public void insert(MarketdataDataset row) {
            throw new UnsupportedOperationException();
        }

        public List<MarketdataDataset> list(String e, String m, String s, String i) {
            throw new UnsupportedOperationException();
        }

        public MarketdataDatasetCoverage calculateCoverage(MarketdataDataset row, Instant at) {
            throw new UnsupportedOperationException();
        }

        public <T> T withQualityMutationLock(UUID id, Function<QualityWriter, T> mutation) {
            throw new UnsupportedOperationException("只读换算测试不能写入数据集质量");
        }

        public void insertCoverage(MarketdataDatasetCoverage row) {
            throw new UnsupportedOperationException();
        }

        public boolean updateQuality(
                UUID id,
                MarketdataDatasetStatus s,
                MarketdataQualityStatus q,
                long count,
                long gap,
                Instant at) {
            throw new UnsupportedOperationException();
        }
    }
}
