package com.guidinglight.nexusquant.marketdata.application.service;

import com.guidinglight.nexusquant.marketdata.application.command.CreateMarketdataIngestionJobCommand;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataBarUpsertStats;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataIngestionJob;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataIngestionRun;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataIngestionStatus;
import com.guidinglight.nexusquant.marketdata.domain.port.HistoricalKlineProvider;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataBarRepository;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataIngestionJobRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * MarketdataIngestionService 编排历史 K 线接入历史 K 线接入最小闭环。
 * <p>
 * Why:
 * 创建任务、断点续拉、交易所拉取、质量校验、幂等写入和运行统计必须在 application 层形成一个可测试流程；
 * controller 不能直接调用 adapter，adapter 也不能直接写数据库。
 */
@Service
public class MarketdataIngestionService {

    private static final Set<String> ALLOWED_EXCHANGES = Set.of("OKX", "BINANCE");
    // 优惠币换算沿用正式历史行情，不因此授予BNB下单或真实资金操作权限。
    private static final Set<String> ALLOWED_SYMBOLS =
            Set.of("BTC-USDT", "ETH-USDT", "SOL-USDT", "BNB-USDT");
    private static final String MARKET_TYPE_SPOT = "SPOT";
    private static final String SOURCE_EXCHANGE_HISTORICAL = "EXCHANGE_HISTORICAL";

    private final MarketdataIngestionJobRepository ingestionJobRepository;
    private final MarketdataBarRepository marketdataBarRepository;
    private final HistoricalKlineProvider historicalKlineProvider;
    private final Clock clock;

    @Autowired
    public MarketdataIngestionService(
            MarketdataIngestionJobRepository ingestionJobRepository,
            MarketdataBarRepository marketdataBarRepository,
            HistoricalKlineProvider historicalKlineProvider) {
        this(
                ingestionJobRepository,
                marketdataBarRepository,
                historicalKlineProvider,
                Clock.systemUTC());
    }

    MarketdataIngestionService(
            MarketdataIngestionJobRepository ingestionJobRepository,
            MarketdataBarRepository marketdataBarRepository,
            HistoricalKlineProvider historicalKlineProvider,
            Clock clock) {
        this.ingestionJobRepository =
                Objects.requireNonNull(
                        ingestionJobRepository, "ingestionJobRepository must not be null");
        this.marketdataBarRepository =
                Objects.requireNonNull(
                        marketdataBarRepository, "marketdataBarRepository must not be null");
        this.historicalKlineProvider =
                Objects.requireNonNull(
                        historicalKlineProvider, "historicalKlineProvider must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 创建历史 K 线接入历史 K 线接入任务。
     *
     * @param command HTTP 层映射后的任务请求，不允许为空
     * @return 已持久化的任务事实
     */
    @Transactional
    public MarketdataIngestionJob createJob(CreateMarketdataIngestionJobCommand command) {
        return createJob(UUID.randomUUID(), command);
    }

    /** 以调用方冻结的编号创建；相同创建者和输入重放只返回原任务，冲突不修改原事实。 */
    @Transactional
    public MarketdataIngestionJob createJob(
            UUID jobId, CreateMarketdataIngestionJobCommand command) {
        Objects.requireNonNull(jobId, "jobId must not be null");
        Objects.requireNonNull(command, "command must not be null");
        String exchangeCode = normalizeExchange(command.exchangeCode());
        String marketType = normalizeMarketType(command.marketType());
        String symbol = normalizeSymbol(command.symbol());
        BarInterval interval =
                BarInterval.fromWireValue(requireText(command.interval(), "interval"));
        Instant startTime =
                Objects.requireNonNull(command.startTime(), "startTime must not be null")
                        .truncatedTo(ChronoUnit.MICROS);
        Instant endTime =
                Objects.requireNonNull(command.endTime(), "endTime must not be null")
                        .truncatedTo(ChronoUnit.MICROS);
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        String requestJson =
                """
                {"exchangeCode":"%s","marketType":"%s","symbol":"%s","interval":"%s","startTime":"%s","endTime":"%s"}
                """
                        .formatted(
                                exchangeCode,
                                marketType,
                                symbol,
                                interval.wireValue(),
                                startTime,
                                endTime)
                        .trim();
        var requested =
                new MarketdataIngestionJob(
                        jobId,
                        exchangeCode,
                        marketType,
                        symbol,
                        interval,
                        startTime,
                        endTime,
                        MarketdataIngestionStatus.CREATED,
                        SOURCE_EXCHANGE_HISTORICAL,
                        defaultUser(command.createdBy()),
                        now,
                        now,
                        requestJson);
        var stored = ingestionJobRepository.createJob(requested);
        // 时间统一至PostgreSQL微秒精度后比较；状态、更新时间和续拉进度不属于创建输入。
        if (!stored.createdBy().equals(requested.createdBy())
                || !stored.exchangeCode().equals(requested.exchangeCode())
                || !stored.marketType().equals(requested.marketType())
                || !stored.symbol().equals(requested.symbol())
                || stored.interval() != requested.interval()
                || !stored.startTime().equals(requested.startTime())
                || !stored.endTime().equals(requested.endTime())
                || !stored.source().equals(requested.source())) {
            throw new IllegalStateException(
                    "ingestion job identity already belongs to another request");
        }
        return stored;
    }

    public List<MarketdataIngestionJob> listJobs() {
        return ingestionJobRepository.listJobs();
    }

    public MarketdataIngestionJob getJob(UUID jobId) {
        return ingestionJobRepository
                .findJob(Objects.requireNonNull(jobId, "jobId must not be null"))
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "marketdata ingestion job not found: " + jobId));
    }

    /** 原创建结果查询保留缺失，不把未提交事务或未知编号伪装成另一项已存在任务。 */
    public Optional<MarketdataIngestionJob> findJob(UUID jobId) {
        return ingestionJobRepository.findJob(
                Objects.requireNonNull(jobId, "jobId must not be null"));
    }

    public List<MarketdataIngestionRun> listRuns(UUID jobId) {
        return ingestionJobRepository.listRuns(
                Objects.requireNonNull(jobId, "jobId must not be null"));
    }

    /** 共享行情任务沿用原只读范围；路径中的任务和运行身份必须同时匹配。 */
    public Optional<MarketdataIngestionRun> findRun(UUID jobId, UUID runId) {
        Objects.requireNonNull(jobId, "jobId must not be null");
        return ingestionJobRepository
                .findRun(Objects.requireNonNull(runId, "runId must not be null"))
                .filter(run -> run.jobId().equals(jobId));
    }

    /**
     * 执行一次历史 K 线接入。
     * <p>
     * Why:
     * 每次 run-once 都先写 RUNNING 记录，再调用交易所；即使外部网络失败，也能留下可查询的失败状态。
     */
    @Transactional
    public MarketdataIngestionRun runOnce(UUID jobId) {
        return runOnce(jobId, UUID.randomUUID());
    }

    /** 同一执行编号包括失败结果也只执行一次；另一次显式执行必须使用新编号。 */
    @Transactional
    public MarketdataIngestionRun runOnce(UUID jobId, UUID runId) {
        Objects.requireNonNull(runId, "runId must not be null");
        MarketdataIngestionJob job =
                ingestionJobRepository
                        .lockJob(Objects.requireNonNull(jobId, "jobId must not be null"))
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "marketdata ingestion job not found"));
        var existing = ingestionJobRepository.findRun(runId);
        if (existing.isPresent()) {
            if (!existing.get().jobId().equals(jobId)) {
                throw new IllegalStateException(
                        "ingestion run identity already belongs to another request");
            }
            return existing.get();
        }
        Instant startedAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        Instant requestedStart = resolveResumeStart(job);
        Instant requestedEnd = job.endTime();
        MarketdataIngestionRun running =
                ingestionJobRepository.createRun(
                        new MarketdataIngestionRun(
                                runId,
                                job.jobId(),
                                MarketdataIngestionStatus.RUNNING,
                                startedAt,
                                null,
                                requestedStart,
                                requestedEnd,
                                null,
                                null,
                                0,
                                0,
                                0,
                                0,
                                null,
                                "{}",
                                startedAt));
        // 不同任务竞争同一个运行主键时，唯一约束重读不能把另一任务的结果当作本次RUNNING。
        if (!running.jobId().equals(jobId)) {
            throw new IllegalStateException(
                    "ingestion run identity already belongs to another request");
        }
        ingestionJobRepository.updateJobStatus(
                job.jobId(), MarketdataIngestionStatus.RUNNING, startedAt);
        try {
            List<HistoricalBar> fetchedBars =
                    historicalKlineProvider.fetchBars(job, requestedStart, requestedEnd);
            // 公开行情只有响应收到后才进入本次采集的观察范围，不能使用请求开始时间提前发布可见性。
            Instant observedAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
            List<HistoricalBar> validBars =
                    fetchedBars.stream()
                            .filter(bar -> isValidBar(job, bar))
                            .sorted(Comparator.comparing(HistoricalBar::openTime))
                            .toList();
            int skippedBars = fetchedBars.size() - validBars.size();
            MarketdataBarUpsertStats stats =
                    validBars.isEmpty()
                            ? new MarketdataBarUpsertStats(0, 0, skippedBars)
                            : marketdataBarRepository.upsertBars(
                                    validBars, job.source(), observedAt);
            Instant finishedAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
            MarketdataIngestionStatus status =
                    skippedBars > 0
                            ? MarketdataIngestionStatus.PARTIAL
                            : MarketdataIngestionStatus.SUCCEEDED;
            MarketdataIngestionRun finished =
                    new MarketdataIngestionRun(
                            running.runId(),
                            running.jobId(),
                            status,
                            running.startedAt(),
                            finishedAt,
                            requestedStart,
                            requestedEnd,
                            validBars.stream()
                                    .map(HistoricalBar::openTime)
                                    .min(Comparator.naturalOrder())
                                    .orElse(null),
                            validBars.stream()
                                    .map(HistoricalBar::closeTime)
                                    .max(Comparator.naturalOrder())
                                    .orElse(null),
                            fetchedBars.size(),
                            stats.insertedCount(),
                            stats.updatedCount(),
                            skippedBars + stats.skippedCount(),
                            skippedBars > 0
                                    ? "some bars failed historical kline quality validation"
                                    : null,
                            buildSummaryJson(
                                    fetchedBars.size(),
                                    stats.insertedCount(),
                                    stats.updatedCount(),
                                    skippedBars + stats.skippedCount()),
                            running.createdAt());
            ingestionJobRepository.updateJobStatus(job.jobId(), status, finishedAt);
            return ingestionJobRepository.finishRun(finished);
        } catch (RuntimeException ex) {
            Instant finishedAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
            MarketdataIngestionRun failed =
                    new MarketdataIngestionRun(
                            running.runId(),
                            running.jobId(),
                            MarketdataIngestionStatus.FAILED,
                            running.startedAt(),
                            finishedAt,
                            requestedStart,
                            requestedEnd,
                            null,
                            null,
                            0,
                            0,
                            0,
                            0,
                            safeErrorMessage(ex),
                            buildErrorSummaryJson(ex),
                            running.createdAt());
            ingestionJobRepository.updateJobStatus(
                    job.jobId(), MarketdataIngestionStatus.FAILED, finishedAt);
            return ingestionJobRepository.finishRun(failed);
        }
    }

    private Instant resolveResumeStart(MarketdataIngestionJob job) {
        Optional<Instant> latestEnd =
                ingestionJobRepository.findLatestSuccessfulActualEnd(job.jobId());
        if (latestEnd.isEmpty()) {
            return job.startTime();
        }
        // 适配器的closeTime是包含最后一毫秒的闭区间；再加一个周期会跳过紧邻的完整K线。
        Instant next = latestEnd.get().plusMillis(1);
        return next.isAfter(job.endTime()) ? job.endTime() : next;
    }

    private boolean isValidBar(MarketdataIngestionJob job, HistoricalBar bar) {
        return job.exchangeCode().equals(bar.exchangeCode())
                && job.marketType().equals(bar.marketType())
                && job.symbol().equals(bar.symbol())
                && job.interval() == bar.interval()
                && !bar.openTime().isBefore(job.startTime())
                && !bar.closeTime().isAfter(job.endTime())
                && isPositive(bar.openPrice())
                && isPositive(bar.highPrice())
                && isPositive(bar.lowPrice())
                && isPositive(bar.closePrice())
                && bar.volume() != null
                && bar.volume().signum() >= 0;
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private String normalizeExchange(String exchangeCode) {
        String normalized = requireText(exchangeCode, "exchangeCode").toUpperCase(Locale.ROOT);
        if (!ALLOWED_EXCHANGES.contains(normalized)) {
            throw new IllegalArgumentException("unsupported exchangeCode: " + exchangeCode);
        }
        return normalized;
    }

    private String normalizeMarketType(String marketType) {
        String normalized = requireText(marketType, "marketType").toUpperCase(Locale.ROOT);
        if (!MARKET_TYPE_SPOT.equals(normalized)) {
            throw new IllegalArgumentException(
                    "historical kline ingestion only supports SPOT marketType");
        }
        return normalized;
    }

    private String normalizeSymbol(String symbol) {
        String normalized = requireText(symbol, "symbol").toUpperCase(Locale.ROOT);
        if (!ALLOWED_SYMBOLS.contains(normalized)) {
            throw new IllegalArgumentException("unsupported symbol: " + symbol);
        }
        return normalized;
    }

    private String defaultUser(String createdBy) {
        String normalized = createdBy == null ? "" : createdBy.trim();
        if (normalized.isBlank()) {
            return "local";
        }
        // Why: Spring Security 的 Principal 在不同认证链下可能返回长格式主体字符串；
        // DB 的 created_by 只保存审计用短标识，超长值截断可避免把认证对象细节写入业务表。
        return normalized.length() > 128 ? normalized.substring(0, 128) : normalized;
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private String safeErrorMessage(RuntimeException ex) {
        String message =
                ex.getMessage() == null || ex.getMessage().isBlank()
                        ? ex.getClass().getSimpleName()
                        : ex.getMessage();
        return message.length() > 1_000 ? message.substring(0, 1_000) : message;
    }

    private String buildSummaryJson(
            int fetchedBars, int insertedBars, int updatedBars, int skippedBars) {
        return """
                {"fetchedBars":%d,"insertedBars":%d,"updatedBars":%d,"skippedBars":%d}
                """
                .formatted(fetchedBars, insertedBars, updatedBars, skippedBars)
                .trim();
    }

    private String buildErrorSummaryJson(RuntimeException ex) {
        return """
                {"errorType":"%s"}
                """
                .formatted(ex.getClass().getSimpleName())
                .trim();
    }
}
