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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

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
    private final TransactionTemplate batches;
    private final Semaphore capacity = new Semaphore(2);
    private static final int BATCH_BARS = 300;
    private static final int MAX_BATCHES = 10;
    private static final Duration RUN_LEASE = Duration.ofMinutes(5);

    @Autowired
    public MarketdataIngestionService(
            MarketdataIngestionJobRepository jobs, MarketdataBarRepository bars,
            HistoricalKlineProvider provider, PlatformTransactionManager manager) {
        this(jobs, bars, provider, Clock.systemUTC(), manager);
    }

    public MarketdataIngestionService(
            MarketdataIngestionJobRepository jobs, MarketdataBarRepository bars,
            HistoricalKlineProvider provider) {
        this(jobs, bars, provider, Clock.systemUTC(), null);
    }

    MarketdataIngestionService(
            MarketdataIngestionJobRepository jobs, MarketdataBarRepository bars,
            HistoricalKlineProvider provider, Clock clock) {
        this(jobs, bars, provider, clock, null);
    }

    MarketdataIngestionService(
            MarketdataIngestionJobRepository jobs, MarketdataBarRepository bars,
            HistoricalKlineProvider provider, Clock clock, PlatformTransactionManager manager) {
        this.ingestionJobRepository = Objects.requireNonNull(jobs);
        this.marketdataBarRepository = Objects.requireNonNull(bars);
        this.historicalKlineProvider = Objects.requireNonNull(provider);
        this.clock = Objects.requireNonNull(clock);
        this.batches = manager == null ? null : new TransactionTemplate(manager);
        if (batches != null) {
            batches.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            batches.setTimeout(10);
        }
    }

    private <T> T transaction(Supplier<T> action) {
        return batches == null ? action.get() : batches.execute(status -> action.get());
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

    /** 网络读取不持有数据库事务；每批行情和连续前缀进度在同一短事务中确认。 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MarketdataIngestionRun runOnce(UUID jobId) {
        return runOnce(jobId, UUID.randomUUID());
    }

    /** 同一运行编号重读原状态；中断后使用新的显式运行编号从已确认前缀继续。 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MarketdataIngestionRun runOnce(UUID jobId, UUID runId) {
        Objects.requireNonNull(jobId);
        Objects.requireNonNull(runId);
        if (!capacity.tryAcquire()) throw new IllegalStateException("INGESTION_CAPACITY_BUSY");
        try {
            Claim claim = transaction(() -> claim(jobId, runId));
            if (!claim.execute()) return claim.run();
            MarketdataIngestionJob job = claim.job();
            MarketdataIngestionRun progress = claim.run();
            try {
                for (int page = 0; page < MAX_BATCHES; page++) {
                    Instant from = progress.actualEndTime() == null
                            ? progress.requestedStartTime() : progress.actualEndTime().plusMillis(1);
                    if (from.plus(job.interval().duration()).minusMillis(1).isAfter(job.endTime())) {
                        MarketdataIngestionRun complete = progress;
                        boolean covered = !from.isBefore(job.endTime());
                        return transaction(() -> finishOwned(job, complete,
                                covered ? MarketdataIngestionStatus.SUCCEEDED : MarketdataIngestionStatus.PARTIAL,
                                covered ? null : "INGESTION_WINDOW_HAS_INCOMPLETE_INTERVAL"));
                    }
                    Instant until = from.plus(job.interval().duration().multipliedBy(BATCH_BARS))
                            .minusMillis(1);
                    if (until.isAfter(job.endTime())) until = job.endTime();
                    List<HistoricalBar> fetched = historicalKlineProvider.fetchBars(job, from, until);
                    Instant observedAt = now();
                    Batch checked = validateBatch(job, from, until, fetched, observedAt);
                    MarketdataIngestionRun previous = progress;
                    progress = transaction(() -> checkpoint(job, previous, checked, observedAt));
                    if (checked.failure() != null) {
                        MarketdataIngestionRun partial = progress;
                        return transaction(() -> finishOwned(job, partial,
                                MarketdataIngestionStatus.PARTIAL, checked.failure()));
                    }
                }
                MarketdataIngestionRun partial = progress;
                return transaction(() -> finishOwned(job, partial,
                        MarketdataIngestionStatus.PARTIAL, "INGESTION_BATCH_LIMIT_CONTINUE"));
            } catch (RuntimeException failure) {
                MarketdataIngestionRun previous = progress;
                return transaction(() -> finishOwned(job, previous,
                        MarketdataIngestionStatus.FAILED, safeErrorMessage(failure)));
            }
        } finally {
            capacity.release();
        }
    }

    private Claim claim(UUID jobId, UUID runId) {
        MarketdataIngestionJob job = ingestionJobRepository.lockJob(jobId)
                .orElseThrow(() -> new IllegalArgumentException("marketdata ingestion job not found"));
        Optional<MarketdataIngestionRun> existing = ingestionJobRepository.findRun(runId);
        if (existing.isPresent()) {
            if (!existing.get().jobId().equals(jobId))
                throw new IllegalStateException("ingestion run identity already belongs to another request");
            return new Claim(job, existing.get(), false);
        }
        Instant at = now();
        for (MarketdataIngestionRun active : ingestionJobRepository.listRuns(jobId)) {
            if (active.status() != MarketdataIngestionStatus.RUNNING) continue;
            if (job.updatedAt().plus(RUN_LEASE).isAfter(at))
                throw new IllegalStateException("INGESTION_RUN_IN_PROGRESS");
            // 失联工作者只有租约过期后才被接替，旧工作者的后续批次必须在写入前重新核对归属。
            ingestionJobRepository.finishRun(copy(active, MarketdataIngestionStatus.FAILED, at,
                    active.actualStartTime(), active.actualEndTime(), active.fetchedBars(),
                    active.insertedBars(), active.updatedBars(), active.skippedBars(),
                    "INGESTION_INTERRUPTED_RESUME_REQUIRED"));
        }
        Instant from = resolveResumeStart(job);
        var run = ingestionJobRepository.createRun(new MarketdataIngestionRun(runId, jobId,
                MarketdataIngestionStatus.RUNNING, at, null, from, job.endTime(), null, null,
                0, 0, 0, 0, null, "{}", at));
        if (!run.jobId().equals(jobId))
            throw new IllegalStateException("ingestion run identity already belongs to another request");
        ingestionJobRepository.updateJobStatus(jobId, MarketdataIngestionStatus.RUNNING, at);
        return new Claim(job, run, true);
    }

    private MarketdataIngestionRun checkpoint(MarketdataIngestionJob job,
            MarketdataIngestionRun previous, Batch batch, Instant observedAt) {
        requireOwned(job, previous);
        MarketdataBarUpsertStats stats = batch.bars().isEmpty()
                ? new MarketdataBarUpsertStats(0, 0)
                : marketdataBarRepository.upsertBars(batch.bars(), job.source(), observedAt);
        if (stats.skippedCount() != 0)
            throw new IllegalStateException("INGESTION_BATCH_PERSISTENCE_INCOMPLETE");
        var saved = ingestionJobRepository.finishRun(copy(previous,
                MarketdataIngestionStatus.RUNNING, null,
                previous.actualStartTime() != null ? previous.actualStartTime()
                        : batch.bars().isEmpty() ? null : batch.bars().getFirst().openTime(),
                batch.bars().isEmpty() ? previous.actualEndTime() : batch.bars().getLast().closeTime(),
                previous.fetchedBars() + batch.fetched(),
                previous.insertedBars() + stats.insertedCount(),
                previous.updatedBars() + stats.updatedCount(),
                previous.skippedBars() + batch.fetched() - batch.bars().size(), batch.failure()));
        ingestionJobRepository.updateJobStatus(job.jobId(), MarketdataIngestionStatus.RUNNING, now());
        return saved;
    }

    private MarketdataIngestionRun finishOwned(MarketdataIngestionJob job,
            MarketdataIngestionRun previous, MarketdataIngestionStatus status, String reason) {
        requireOwned(job, previous);
        Instant at = now();
        var finished = ingestionJobRepository.finishRun(copy(previous, status, at,
                previous.actualStartTime(), previous.actualEndTime(), previous.fetchedBars(),
                previous.insertedBars(), previous.updatedBars(), previous.skippedBars(), reason));
        ingestionJobRepository.updateJobStatus(job.jobId(), status, at);
        return finished;
    }

    private void requireOwned(MarketdataIngestionJob job, MarketdataIngestionRun run) {
        ingestionJobRepository.lockJob(job.jobId()).orElseThrow();
        MarketdataIngestionRun stored = ingestionJobRepository.findRun(run.runId()).orElseThrow();
        if (stored.status() != MarketdataIngestionStatus.RUNNING
                || !stored.jobId().equals(job.jobId()))
            throw new IllegalStateException("INGESTION_RUN_OWNERSHIP_LOST");
    }

    private Batch validateBatch(MarketdataIngestionJob job, Instant from, Instant until,
            List<HistoricalBar> fetched, Instant observedAt) {
        if (fetched == null || fetched.size() > BATCH_BARS)
            throw new IllegalStateException("INGESTION_PAGE_BOUND_EXCEEDED");
        if (fetched.isEmpty()) return new Batch(List.of(), 0, "INGESTION_EMPTY_PAGE");
        var ordered = fetched.stream().sorted(Comparator.comparing(HistoricalBar::openTime)).toList();
        var accepted = new java.util.ArrayList<HistoricalBar>();
        Instant expected = from;
        Set<Instant> seen = new java.util.HashSet<>();
        for (HistoricalBar bar : ordered) {
            if (!seen.add(bar.openTime()))
                return new Batch(List.copyOf(accepted), fetched.size(), "INGESTION_DUPLICATE_PAGE");
            if (!bar.openTime().equals(expected) || bar.closeTime().isAfter(until)
                    || !isValidBar(job, bar, observedAt))
                return new Batch(List.copyOf(accepted), fetched.size(), "INGESTION_GAP_OR_INVALID_BAR");
            accepted.add(bar);
            expected = expected.plus(job.interval().duration());
        }
        // 短页只确认连续前缀，下一次显式续拉仍从紧邻开盘时间开始，绝不跨过缺口。
        String failure = expected.plus(job.interval().duration()).minusMillis(1).isAfter(until)
                ? null : "INGESTION_INCOMPLETE_PAGE";
        return new Batch(List.copyOf(accepted), fetched.size(), failure);
    }

    private MarketdataIngestionRun copy(MarketdataIngestionRun run, MarketdataIngestionStatus status,
            Instant finished, Instant first, Instant last, int fetched, int inserted, int updated,
            int skipped, String error) {
        return new MarketdataIngestionRun(run.runId(), run.jobId(), status, run.startedAt(), finished,
                run.requestedStartTime(), run.requestedEndTime(), first, last, fetched, inserted,
                updated, skipped, error, buildSummaryJson(fetched, inserted, updated, skipped), run.createdAt());
    }

    private Instant resolveResumeStart(MarketdataIngestionJob job) {
        Optional<Instant> latestEnd = ingestionJobRepository.findLatestSuccessfulActualEnd(job.jobId());
        return latestEnd.map(value -> value.plusMillis(1)).orElse(job.startTime());
    }

    private boolean isValidBar(MarketdataIngestionJob job, HistoricalBar bar, Instant observedAt) {
        return job.exchangeCode().equals(bar.exchangeCode())
                && job.marketType().equals(bar.marketType()) && job.symbol().equals(bar.symbol())
                && job.interval() == bar.interval()
                && !bar.openTime().isBefore(job.startTime()) && !bar.closeTime().isAfter(job.endTime())
                && bar.openTime().toEpochMilli() % job.interval().duration().toMillis() == 0
                && bar.closeTime().equals(bar.openTime().plus(job.interval().duration()).minusMillis(1))
                && bar.closeTime().isBefore(observedAt)
                && isPositive(bar.openPrice()) && isPositive(bar.highPrice())
                && isPositive(bar.lowPrice()) && isPositive(bar.closePrice())
                && bar.highPrice().compareTo(bar.openPrice().max(bar.closePrice()).max(bar.lowPrice())) >= 0
                && bar.lowPrice().compareTo(bar.openPrice().min(bar.closePrice()).min(bar.highPrice())) <= 0
                && bar.volume() != null && bar.volume().signum() >= 0;
    }

    private boolean isPositive(BigDecimal value) { return value != null && value.signum() > 0; }
    private Instant now() { return Instant.now(clock).truncatedTo(ChronoUnit.MICROS); }
    private record Claim(MarketdataIngestionJob job, MarketdataIngestionRun run, boolean execute) {}
    private record Batch(List<HistoricalBar> bars, int fetched, String failure) {}

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

}
