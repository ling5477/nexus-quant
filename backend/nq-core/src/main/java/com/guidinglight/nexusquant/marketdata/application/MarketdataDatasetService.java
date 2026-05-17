package com.guidinglight.nexusquant.marketdata.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.marketdata.application.command.CreateMarketdataDatasetCommand;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataDataset;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataDatasetCoverage;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataDatasetStatus;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityStatus;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataDatasetRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * MarketdataDatasetService 提供 GateH-3 数据集创建、查询和质量刷新能力。
 * <p>
 * Why:
 * GateH-3 的 dataset 是 `marketdata_bars` 到回测配置之间的稳定契约；服务层负责固定 GateH-3 第一版范围、
 * 生成可审计 request 快照，并把覆盖统计结果写回 dataset 主表。它不启动回测，也不改变策略或交易逻辑。
 */
@Service
public class MarketdataDatasetService {

    private static final Set<String> SUPPORTED_EXCHANGES = Set.of("OKX", "BINANCE");
    private static final Set<String> SUPPORTED_SYMBOLS = Set.of("BTC-USDT", "ETH-USDT", "SOL-USDT");
    private static final Set<String> SUPPORTED_INTERVALS = Set.of("1m", "5m", "15m", "1h", "4h", "1d");
    private static final String SOURCE = "marketdata_bars";

    private final MarketdataDatasetRepository marketdataDatasetRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public MarketdataDatasetService(
            MarketdataDatasetRepository marketdataDatasetRepository,
            ObjectMapper objectMapper
    ) {
        this(marketdataDatasetRepository, objectMapper, Clock.systemUTC());
    }

    public MarketdataDatasetService(
            MarketdataDatasetRepository marketdataDatasetRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.marketdataDatasetRepository = Objects.requireNonNull(
                marketdataDatasetRepository,
                "marketdataDatasetRepository must not be null"
        );
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 创建数据集并立即计算一次质量覆盖。
     *
     * @param command 创建命令
     * @return 已持久化且带最新质量状态的数据集
     */
    public MarketdataDataset createDataset(CreateMarketdataDatasetCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String datasetName = requireText(command.datasetName(), "datasetName");
        String exchangeCode = normalizeExchange(command.exchangeCode());
        String marketType = normalizeMarketType(command.marketType());
        String symbol = normalizeSymbol(command.symbol());
        BarInterval interval = normalizeInterval(command.interval());
        Instant startTime = Objects.requireNonNull(command.startTime(), "startTime must not be null");
        Instant endTime = Objects.requireNonNull(command.endTime(), "endTime must not be null");
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
        Instant now = Instant.now(clock);
        MarketdataDataset dataset = new MarketdataDataset(
                UUID.randomUUID(),
                datasetName,
                exchangeCode,
                marketType,
                symbol,
                interval,
                startTime,
                endTime,
                MarketdataDatasetStatus.CREATED,
                MarketdataQualityStatus.INCOMPLETE,
                0,
                0,
                SOURCE,
                normalizeCreatedBy(command.createdBy()),
                now,
                now,
                buildRequestJson(datasetName, exchangeCode, marketType, symbol, interval, startTime, endTime)
        );
        marketdataDatasetRepository.insert(dataset);
        return refreshQuality(dataset.datasetId());
    }

    public List<MarketdataDataset> listDatasets(String exchangeCode, String marketType, String symbol, String interval) {
        return marketdataDatasetRepository.list(
                normalizeOptionalUpper(exchangeCode),
                normalizeOptionalUpper(marketType),
                normalizeOptionalSymbol(symbol),
                normalizeOptionalInterval(interval)
        );
    }

    public MarketdataDataset getDataset(UUID datasetId) {
        return marketdataDatasetRepository.findByDatasetId(Objects.requireNonNull(datasetId, "datasetId must not be null"))
                .orElseThrow(() -> new IllegalArgumentException("marketdata dataset not found: " + datasetId));
    }

    /**
     * 重新计算数据集覆盖率和质量状态。
     *
     * @param datasetId 数据集 ID
     * @return 更新后的数据集
     */
    public MarketdataDataset refreshQuality(UUID datasetId) {
        MarketdataDataset dataset = getDataset(datasetId);
        Instant now = Instant.now(clock);
        MarketdataDatasetCoverage coverage = marketdataDatasetRepository.calculateCoverage(dataset, now);
        marketdataDatasetRepository.insertCoverage(coverage);
        MarketdataDatasetStatus nextStatus = coverage.qualityStatus() == MarketdataQualityStatus.OK
                ? MarketdataDatasetStatus.READY
                : MarketdataDatasetStatus.INVALID;
        marketdataDatasetRepository.updateQuality(
                dataset.datasetId(),
                nextStatus,
                coverage.qualityStatus(),
                coverage.actualBars(),
                coverage.missingBars(),
                now
        );
        return getDataset(dataset.datasetId());
    }

    /**
     * 生成回测配置和 run 使用的数据集快照。
     *
     * @param datasetId 数据集 ID
     * @return 数据集 JSON 快照
     */
    public String buildDatasetSnapshot(UUID datasetId) {
        MarketdataDataset dataset = getDataset(datasetId);
        try {
            ObjectNode snapshot = objectMapper.createObjectNode();
            snapshot.put("datasetId", dataset.datasetId().toString());
            snapshot.put("datasetName", dataset.datasetName());
            snapshot.put("provider", "db");
            snapshot.put("resourcePath", SOURCE);
            snapshot.put("exchangeCode", dataset.exchangeCode());
            snapshot.put("marketType", dataset.marketType());
            snapshot.put("symbol", dataset.symbol());
            snapshot.put("interval", dataset.interval().wireValue());
            snapshot.put("startTime", dataset.startTime().toString());
            snapshot.put("endTime", dataset.endTime().toString());
            snapshot.put("status", dataset.status().name());
            snapshot.put("qualityStatus", dataset.qualityStatus().name());
            snapshot.put("barCount", dataset.barCount());
            snapshot.put("gapCount", dataset.gapCount());
            snapshot.put("source", dataset.source());
            snapshot.put("snapshotAt", Instant.now(clock).toString());
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to build dataset snapshot", ex);
        }
    }

    private String buildRequestJson(
            String datasetName,
            String exchangeCode,
            String marketType,
            String symbol,
            BarInterval interval,
            Instant startTime,
            Instant endTime
    ) {
        try {
            ObjectNode request = objectMapper.createObjectNode();
            request.put("datasetName", datasetName);
            request.put("exchangeCode", exchangeCode);
            request.put("marketType", marketType);
            request.put("symbol", symbol);
            request.put("interval", interval.wireValue());
            request.put("startTime", startTime.toString());
            request.put("endTime", endTime.toString());
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to build dataset request snapshot", ex);
        }
    }

    private String normalizeExchange(String value) {
        String normalized = requireText(value, "exchangeCode").toUpperCase();
        if (!SUPPORTED_EXCHANGES.contains(normalized)) {
            throw new IllegalArgumentException("unsupported exchangeCode for GateH-3 dataset: " + value);
        }
        return normalized;
    }

    private String normalizeMarketType(String value) {
        String normalized = requireText(value, "marketType").toUpperCase();
        if (!"SPOT".equals(normalized)) {
            throw new IllegalArgumentException("GateH-3 dataset only supports SPOT");
        }
        return normalized;
    }

    private String normalizeSymbol(String value) {
        String normalized = requireText(value, "symbol").toUpperCase();
        if (!SUPPORTED_SYMBOLS.contains(normalized)) {
            throw new IllegalArgumentException("unsupported symbol for GateH-3 dataset: " + value);
        }
        return normalized;
    }

    private BarInterval normalizeInterval(String value) {
        String normalized = requireText(value, "interval");
        if (!SUPPORTED_INTERVALS.contains(normalized)) {
            throw new IllegalArgumentException("unsupported interval for GateH-3 dataset: " + value);
        }
        return BarInterval.fromWireValue(normalized);
    }

    private String normalizeOptionalUpper(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private String normalizeOptionalSymbol(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private String normalizeOptionalInterval(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeCreatedBy(String value) {
        String normalized = value == null || value.isBlank() ? "local" : value.trim();
        return normalized.length() > 512 ? normalized.substring(0, 512) : normalized;
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
