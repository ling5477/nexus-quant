package com.guidinglight.nexusquant.marketdata.application.service;

import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.BarProvenance;
import com.guidinglight.nexusquant.marketdata.domain.DiscountAssetPriceSnapshot;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalDatasetSpec;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalMarketDataQuery;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataDataset;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataDatasetStatus;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityStatus;
import com.guidinglight.nexusquant.marketdata.domain.port.BarProvenanceReadPort;
import com.guidinglight.nexusquant.marketdata.domain.port.DiscountAssetPriceSnapshotRepository;
import com.guidinglight.nexusquant.marketdata.domain.port.HistoricalMarketDataPort;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataDatasetRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 从正式数据集及真实可见时间冻结价格；调用方仅选数据集，不能提交价格或完整来源文本。 */
@Service
public class DiscountAssetPriceService {
    private final MarketdataDatasetRepository datasets;
    private final HistoricalMarketDataPort history;
    private final DiscountAssetPriceSnapshotRepository snapshots;
    private final BarProvenanceReadPort provenance;

    public DiscountAssetPriceService(
            MarketdataDatasetRepository datasets,
            HistoricalMarketDataPort history,
            DiscountAssetPriceSnapshotRepository snapshots,
            BarProvenanceReadPort provenance) {
        this.datasets = datasets;
        this.history = history;
        this.snapshots = snapshots;
        this.provenance = provenance;
    }

    /** 一致读同时绑定数据集和行情版本；分页至多四十页，拒绝超限、缺口及不支持来源，不执行联网补数。 */
    @Transactional(isolation = Isolation.REPEATABLE_READ, timeout = 30)
    public DiscountAssetPriceSnapshot freeze(
            long owner,
            UUID datasetId,
            String exchange,
            String asset,
            String quote,
            Instant from,
            Instant to,
            Instant frozenAt) {
        if (owner <= 0
                || datasetId == null
                || !("BINANCE".equals(exchange) || "OKX".equals(exchange))
                || !"BNB".equals(asset)
                || !"USDT".equals(quote)
                || from == null
                || to == null
                || frozenAt == null
                || !from.equals(from.truncatedTo(ChronoUnit.MICROS))
                || !to.equals(to.truncatedTo(ChronoUnit.MICROS))
                || !frozenAt.equals(frozenAt.truncatedTo(ChronoUnit.MICROS))
                || !to.isAfter(from)
                || to.isAfter(frozenAt)
                || Duration.between(from.minusSeconds(300), to).toMinutes()
                        >= DiscountAssetPriceSnapshot.MAX_POINTS) throw invalid();
        MarketdataDataset dataset =
                datasets.findByDatasetId(datasetId).orElseThrow(DiscountAssetPriceService::invalid);
        if (!exchange.equals(dataset.exchangeCode())
                || !"SPOT".equals(dataset.marketType())
                || !(asset + "-" + quote).equals(dataset.symbol())
                || dataset.interval() != BarInterval.ONE_MINUTE
                || dataset.status() != MarketdataDatasetStatus.READY
                || dataset.qualityStatus() != MarketdataQualityStatus.OK
                || dataset.gapCount() != 0
                || !"marketdata_bars".equals(dataset.source())
                || dataset.startTime()
                        .isAfter(from.minusSeconds(300).truncatedTo(ChronoUnit.MINUTES))
                || dataset.endTime().isBefore(to)) throw invalid();
        HistoricalDatasetSpec spec =
                new HistoricalDatasetSpec(
                        "db",
                        datasetId.toString(),
                        exchange,
                        dataset.symbol(),
                        BarInterval.ONE_MINUTE,
                        "marketdata_bars");
        List<HistoricalBar> bars = new ArrayList<>();
        Map<Instant, BarProvenance> versions = new HashMap<>();
        for (int page = 0; page <= DiscountAssetPriceSnapshot.MAX_POINTS / 500; page++) {
            List<HistoricalBar> slice =
                    history.loadBars(
                            new HistoricalMarketDataQuery(
                                    spec,
                                    exchange,
                                    "SPOT",
                                    dataset.symbol(),
                                    BarInterval.ONE_MINUTE,
                                    from.minusSeconds(300).truncatedTo(ChronoUnit.MINUTES),
                                    to,
                                    page,
                                    500));
            if (slice == null
                    || slice.size() > 500
                    || bars.size() + slice.size() > DiscountAssetPriceSnapshot.MAX_POINTS)
                throw invalid();
            bars.addAll(slice);
            // 这里只保留实际入库版本和来源，不采用展示用四价发生时点作为收费或可见性依据。
            versions.putAll(
                    provenance.read(
                            exchange,
                            "SPOT",
                            dataset.symbol(),
                            "1m",
                            slice.stream().map(HistoricalBar::openTime).toList()));
            if (slice.size() < 500) break;
        }
        DiscountAssetPriceSnapshot result =
                DiscountAssetPriceSnapshot.capture(
                        owner, dataset, exchange, asset, quote, from, to, frozenAt, bars, versions);
        snapshots.insert(result);
        return result;
    }

    /** 重启仅重读不可变原来源并核验所有者与摘要，不查询或替换最新行情。 */
    @Transactional(readOnly = true, timeout = 5)
    public DiscountAssetPriceSnapshot read(long owner, UUID snapshotId, String expectedSha) {
        if (owner <= 0
                || snapshotId == null
                || expectedSha == null
                || !expectedSha.matches("[a-f0-9]{64}")) throw invalid();
        DiscountAssetPriceSnapshot result =
                snapshots
                        .findByIdForOwner(owner, snapshotId)
                        .orElseThrow(DiscountAssetPriceService::invalid);
        if (result.ownerUserId() != owner
                || !result.snapshotId().equals(snapshotId)
                || !result.sourceSha256().equals(expectedSha)) throw invalid();
        return DiscountAssetPriceSnapshot.restore(result.sourceJson(), expectedSha);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("FEE_DISCOUNT_PRICE_SOURCE_INVALID");
    }
}
