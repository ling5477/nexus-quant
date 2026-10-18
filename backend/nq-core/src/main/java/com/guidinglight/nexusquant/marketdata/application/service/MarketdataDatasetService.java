package com.guidinglight.nexusquant.marketdata.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.marketdata.application.command.CreateMarketdataDatasetCommand;
import com.guidinglight.nexusquant.marketdata.application.result.MarketdataDatasetRefresh;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataDataset;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataDatasetCoverage;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataDatasetStatus;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityStatus;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataDatasetRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * MarketdataDatasetService 提供历史行情接入数据集创建、查询和质量刷新能力。
 *
 * <p>Why: 行情数据集的 dataset 是 `marketdata_bars` 到回测配置之间的稳定契约；服务层负责固定行情数据集第一版范围、 生成可审计 request
 * 快照，并把覆盖统计结果写回 dataset 主表。它不启动回测，也不改变策略或交易逻辑。
 */
@Service
public class MarketdataDatasetService {

    private static final Set<String> SUPPORTED_EXCHANGES = Set.of("OKX", "BINANCE");
    // BNB只扩展历史行情输入，供优惠费用换算使用；不扩大任何下单标的或交易权限。
    private static final Set<String> SUPPORTED_SYMBOLS =
            Set.of("BTC-USDT", "ETH-USDT", "SOL-USDT", "BNB-USDT");
    private static final Set<String> SUPPORTED_INTERVALS =
            Set.of("1m", "5m", "15m", "1h", "4h", "1d");
    private static final String SOURCE = "marketdata_bars";

    private final MarketdataDatasetRepository marketdataDatasetRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public MarketdataDatasetService(
            MarketdataDatasetRepository marketdataDatasetRepository, ObjectMapper objectMapper) {
        this(marketdataDatasetRepository, objectMapper, Clock.systemUTC());
    }

    public MarketdataDatasetService(
            MarketdataDatasetRepository marketdataDatasetRepository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.marketdataDatasetRepository =
                Objects.requireNonNull(
                        marketdataDatasetRepository,
                        "marketdataDatasetRepository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 创建数据集并立即计算一次质量覆盖。
     *
     * @param command 创建命令
     * @return 已持久化且带最新质量状态的数据集
     */
    @Transactional(timeout = 30)
    public MarketdataDataset createDataset(CreateMarketdataDatasetCommand command) {
        return createDataset(UUID.randomUUID(), command).dataset();
    }

    /** 稳定创建身份由上层原请求提供；数据集、首次覆盖及主状态共同提交。 */
    @Transactional(timeout = 30)
    public MarketdataDatasetRefresh createDataset(
            UUID datasetId, CreateMarketdataDatasetCommand raw) {
        if (datasetId == null || datasetId.equals(new UUID(0, 0))) {
            throw new IllegalArgumentException("DATASET_ID_INVALID");
        }
        CreateMarketdataDatasetCommand command = normalizeCreateCommand(raw);
        BarInterval interval = BarInterval.fromWireValue(command.interval());
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        MarketdataDataset dataset =
                new MarketdataDataset(
                        datasetId,
                        command.datasetName(),
                        command.exchangeCode(),
                        command.marketType(),
                        command.symbol(),
                        interval,
                        command.startTime(),
                        command.endTime(),
                        MarketdataDatasetStatus.CREATED,
                        MarketdataQualityStatus.INCOMPLETE,
                        0,
                        0,
                        SOURCE,
                        command.createdBy(),
                        now,
                        now,
                        buildRequestJson(
                                command.datasetName(),
                                command.exchangeCode(),
                                command.marketType(),
                                command.symbol(),
                                interval,
                                command.startTime(),
                                command.endTime()));
        marketdataDatasetRepository.insert(dataset);
        return refreshQualityWithCoverage(datasetId, datasetId);
    }

    /** 规范化在登记原请求前执行，确保重放比较与正式写入使用同一业务校验及精度。 */
    public CreateMarketdataDatasetCommand normalizeCreateCommand(
            CreateMarketdataDatasetCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String name = requireText(command.datasetName(), "datasetName");
        if (name.length() > 255 || name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    "datasetName must be at most 255 characters without controls");
        }
        String exchange = normalizeExchange(command.exchangeCode());
        String market = normalizeMarketType(command.marketType());
        String symbol = normalizeSymbol(command.symbol());
        BarInterval interval = normalizeInterval(command.interval());
        Instant start = Objects.requireNonNull(command.startTime(), "startTime must not be null");
        Instant end = Objects.requireNonNull(command.endTime(), "endTime must not be null");
        // PostgreSQL正式时间列保留微秒；拒绝无法往返的输入，不能静默舍入原请求窗口。
        if (!end.isAfter(start) || start.getNano() % 1000 != 0 || end.getNano() % 1000 != 0) {
            throw new IllegalArgumentException(
                    "dataset window must be ordered with at most microsecond precision");
        }
        return new CreateMarketdataDatasetCommand(
                name,
                exchange,
                market,
                symbol,
                interval.wireValue(),
                start,
                end,
                normalizeCreatedBy(command.createdBy()));
    }

    public List<MarketdataDataset> listDatasets(
            String exchangeCode, String marketType, String symbol, String interval) {
        return marketdataDatasetRepository.list(
                normalizeOptionalUpper(exchangeCode),
                normalizeOptionalUpper(marketType),
                normalizeOptionalSymbol(symbol),
                normalizeOptionalInterval(interval));
    }

    public MarketdataDataset getDataset(UUID datasetId) {
        return marketdataDatasetRepository
                .findByDatasetId(Objects.requireNonNull(datasetId, "datasetId must not be null"))
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "marketdata dataset not found: " + datasetId));
    }

    /**
     * 重新计算数据集覆盖率和质量状态。
     *
     * @param datasetId 数据集 ID
     * @return 更新后的数据集
     */
    @Transactional(timeout = 30)
    public MarketdataDataset refreshQuality(UUID datasetId) {
        return refreshQualityWithCoverage(datasetId).dataset();
    }

    /** 质量历史和主状态只形成一份原子结果，串行读取保证后一次刷新不写回先前计算的旧状态。 */
    @Transactional(timeout = 30)
    public MarketdataDatasetRefresh refreshQualityWithCoverage(UUID datasetId) {
        return refreshQualityWithCoverage(datasetId, UUID.randomUUID());
    }

    /** 原次操作编号同时标识本次覆盖记录，数据库可以直接核对，而非绑定任意已有历史。 */
    @Transactional(timeout = 30)
    public MarketdataDatasetRefresh refreshQualityWithCoverage(UUID datasetId, UUID coverageId) {
        if (coverageId == null || coverageId.equals(new UUID(0, 0))) {
            throw new IllegalArgumentException("DATASET_COVERAGE_ID_INVALID");
        }
        return marketdataDatasetRepository.withQualityMutationLock(
                datasetId,
                writer -> {
                    MarketdataDataset dataset = getDataset(datasetId);
                    if ("OKX_PUBLIC_CAPTURE".equals(dataset.source())) {
                        throw new IllegalStateException("PUBLIC_CAPTURE_DATASET_IMMUTABLE");
                    }
                    Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
                    MarketdataDatasetCoverage calculated =
                            marketdataDatasetRepository.calculateCoverage(dataset, now);
                    MarketdataDatasetCoverage coverage =
                            new MarketdataDatasetCoverage(
                                    coverageId,
                                    datasetId,
                                    calculated.rangeStartTime(),
                                    calculated.rangeEndTime(),
                                    calculated.expectedBars(),
                                    calculated.actualBars(),
                                    calculated.missingBars(),
                                    calculated.duplicateBars(),
                                    calculated.invalidBars(),
                                    calculated.qualityStatus(),
                                    calculated.summaryJson(),
                                    now);
                    marketdataDatasetRepository.insertCoverage(coverage);
                    MarketdataDatasetStatus next =
                            coverage.qualityStatus() == MarketdataQualityStatus.OK
                                    ? MarketdataDatasetStatus.READY
                                    : MarketdataDatasetStatus.INVALID;
                    if (!writer.update(
                            next,
                            coverage.qualityStatus(),
                            coverage.actualBars(),
                            coverage.missingBars(),
                            now)) {
                        throw new IllegalStateException("DATASET_QUALITY_WRITE_CONFLICT");
                    }
                    return new MarketdataDatasetRefresh(getDataset(datasetId), coverage);
                });
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
            boolean publicCapture = "OKX_PUBLIC_CAPTURE".equals(dataset.source());
            snapshot.put("provider", publicCapture ? "public-capture" : "db");
            snapshot.put("resourcePath", publicCapture ? dataset.datasetId().toString() : SOURCE);
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
            if (publicCapture) {
                snapshot.set("capture", objectMapper.readTree(dataset.requestJson()));
            } else {
                snapshot.put("snapshotAt", Instant.now(clock).toString());
            }
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
            Instant endTime) {
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
        String normalized = requireText(value, "exchangeCode").toUpperCase(Locale.ROOT);
        if (!SUPPORTED_EXCHANGES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "unsupported exchangeCode for marketdata dataset: " + value);
        }
        return normalized;
    }

    private String normalizeMarketType(String value) {
        String normalized = requireText(value, "marketType").toUpperCase(Locale.ROOT);
        if (!"SPOT".equals(normalized)) {
            throw new IllegalArgumentException("marketdata dataset only supports SPOT");
        }
        return normalized;
    }

    private String normalizeSymbol(String value) {
        String normalized = requireText(value, "symbol").toUpperCase(Locale.ROOT);
        if (!SUPPORTED_SYMBOLS.contains(normalized)) {
            throw new IllegalArgumentException(
                    "unsupported symbol for marketdata dataset: " + value);
        }
        return normalized;
    }

    private BarInterval normalizeInterval(String value) {
        String normalized = requireText(value, "interval");
        if (!SUPPORTED_INTERVALS.contains(normalized)) {
            throw new IllegalArgumentException(
                    "unsupported interval for marketdata dataset: " + value);
        }
        return BarInterval.fromWireValue(normalized);
    }

    private String normalizeOptionalUpper(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeOptionalSymbol(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
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
