package com.guidinglight.nexusquant.scheduler.paper;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.guidinglight.nexusquant.common.tenant.TenantWorkAccess;
import com.guidinglight.nexusquant.common.tenant.TenantContext;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.port.ClosedBarMarketFeed;
import com.guidinglight.nexusquant.research.application.paper.service.PaperTradingRunService;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRunStatus;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** 单线程轮询受限 OKX 公开小时 bar；数据库会话锁保护同一 run 的跨实例推进。 */
@Service
@Profile("public-marketdata-manual")
@ConditionalOnProperty(prefix = "nq.continuous-sim", name = "enabled", havingValue = "true")
public class ContinuousSimRunService {
    @Autowired private TenantWorkAccess tenantWork;
    private static final Duration HOUR = Duration.ofHours(1);
    private final ContinuousSimRepository progress;
    private final StrategySimRunService sim;
    private final PaperTradingRunService paperRuns;
    private final ClosedBarMarketFeed feed;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public ContinuousSimRunService(
            ContinuousSimRepository progress,
            StrategySimRunService sim,
            PaperTradingRunService paperRuns,
            ClosedBarMarketFeed feed,
            JdbcTemplate jdbc) {
        this(progress, sim, paperRuns, feed, jdbc, Clock.systemUTC());
    }

    public ContinuousSimRunService(
            ContinuousSimRepository progress,
            StrategySimRunService sim,
            PaperTradingRunService paperRuns,
            ClosedBarMarketFeed feed,
            JdbcTemplate jdbc,
            Clock clock) {
        this.progress = Objects.requireNonNull(progress);
        this.sim = Objects.requireNonNull(sim);
        this.paperRuns = Objects.requireNonNull(paperRuns);
        this.feed = Objects.requireNonNull(feed);
        this.jdbc = Objects.requireNonNull(jdbc);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(timeout = 30)
    public Status start(String publishId, BigDecimal budget) {
        return start(publishId, budget, "system");
    }

    /** 正式命令沿用认证主体记录创建归属；调度和既有Java调用保留原两参数入口。 */
    @Transactional(timeout = 30)
    public Status start(String publishId, BigDecimal budget, String actor) {
        return start(publishId, budget, actor, null);
    }

    @Transactional(timeout = 30)
    public Status start(String publishId, BigDecimal budget, String actor, Instant endAt) {
        Instant start = clock.instant();
        if (endAt != null && (!endAt.equals(endAt.truncatedTo(ChronoUnit.HOURS))
                || !endAt.isAfter(start) || endAt.isAfter(start.plus(Duration.ofDays(90))))) {
            throw new IllegalArgumentException("INVALID_CONTINUOUS_SIM_DEADLINE");
        }
        progress.requireAdmissionCapacity();
        StrategySimRunService.RunView created = sim.create(publishId, budget, actor);
        StrategySimRunService.ContinuousSeed seed = sim.continuousSeed(created.paperRunId());
        paperRuns.start(created.paperRunId());
        progress.create(
                created.paperRunId(),
                seed.strategyVersionId(),
                seed.strategyChecksum(),
                seed.costSha256(),
                seed.bars(),
                start, endAt);
        return status(created.paperRunId());
    }

    @Transactional(timeout = 30)
    public Status stop(String runId) {
        // 必须在改变游标前检查来源所有者，不能等返回状态时才发现无权读取。
        paperRuns.getById(runId);
        return withRunLock(
                runId,
                false,
                () -> {
                    if (progress.get(runId).bounded() != null) throw new IllegalStateException("BOUNDED_RUN_REQUIRES_END");
                    progress.stop(runId, clock.instant());
                    return status(runId);
                });
    }

    @Transactional(timeout = 30)
    public Status resume(String runId) {
        paperRuns.getById(runId);
        return withRunLock(
                runId,
                false,
                () -> {
                    ContinuousSimRepository.Cursor cursor = progress.get(runId);
                    if (cursor.bounded() != null) throw new IllegalStateException("BOUNDED_RUN_CANNOT_RESUME");
                    checkBinding(cursor);
                    boolean manualResume = Boolean.TRUE.equals(jdbc.queryForObject(
                            "SELECT requires_manual_resume FROM tenant_execution_controls WHERE tenant_id=nq_current_tenant_id()", Boolean.class));
                    if (!"STOPPED".equals(cursor.status()) && !manualResume) {
                        throw new IllegalStateException("CONTINUOUS_SIM_NOT_STOPPED");
                    }
                    if ("DATA_REVISION_DETECTED".equals(cursor.blockReason())) {
                        throw new IllegalStateException("DATA_REVISION_DETECTED");
                    }
                    jdbc.queryForObject("SELECT nq_resume_tenant_execution(?)", Object.class, runId);
                    if ("STOPPED".equals(cursor.status())) {
                        progress.requireAdmissionCapacity();
                        progress.resume(runId, clock.instant());
                    } else if (manualResume) {
                        // 禁用与在途轮询竞争可能留下 STALLED；显式恢复须返回 RUNNING 才能生成原回执。
                        progress.resumeAfterSuspension(runId, clock.instant());
                    }
                    return status(runId);
                });
    }

    @Transactional(timeout = 10)
    public Status end(String runId) {
        paperRuns.getById(runId);
        if (!progress.requestEnd(runId, clock.instant())) throw new IllegalStateException("CONTINUOUS_SIM_NOT_BOUNDED");
        return status(runId);
    }

    public Status status(String runId) {
        paperRuns.getById(runId);
        ContinuousSimRepository.Cursor cursor = progress.get(runId);
        var recent = sim.decisions(runId);
        var latest = recent.isEmpty() ? null : recent.getFirst();
        return describe(cursor, latest);
    }

    /** 执行结果与独立只读入口共用原游标投影，不维护另一套状态或进度计算。 */
    static Status describe(
            ContinuousSimRepository.Cursor cursor,
            StrategySimDecisionRepository.DecisionView latest) {
        return new Status(
                cursor.runId(),
                cursor.status(),
                cursor.startedAt(),
                cursor.lastPollAt(),
                cursor.lastSuccessfulPollAt(),
                cursor.lastProcessedOpenTime(),
                cursor.lastProcessedOpenTime().plus(HOUR),
                cursor.lastObservedOpenTime(),
                cursor.gapStartOpenTime(),
                latest == null ? null : latest.decisionId(),
                latest == null ? null : latest.status(),
                cursor.blockReason(),
                cursor.consecutivePollFailures(), cursor.bounded());
    }

    public boolean has(String runId) {
        paperRuns.getById(runId);
        return progress.exists(runId);
    }

    /** 单次定向推进也供隔离测试调用；调度器不在未启用配置下创建。 */
    public Status pollOnce(String runId) {
        if (tenantWork != null) return tenantWork.execute(
                TenantWorkAccess.Kind.PAPER, runId, () -> pollScoped(runId));
        return pollScoped(runId);
    }

    private Status pollScoped(String runId) {
        paperRuns.getById(runId);
        return withRunLock(runId, true, () -> pollLocked(runId));
    }

    private Status pollLocked(String runId) {
        ContinuousSimRepository.Cursor cursor = progress.get(runId);
        if ("STOPPED".equals(cursor.status())) return status(runId);
        Instant now = clock.instant();
        if (cursor.bounded() != null && progress.closeAdmissionIfDue(runId)) return settle(runId);
        Instant latestAvailable = cursor.lastObservedOpenTime();
        Instant gapStart = null;
        boolean resolvingOverlap = false;
        try {
            checkBinding(cursor);
            List<HistoricalBar> before = progress.recentBars(runId);
            if (before.isEmpty()) throw new IllegalStateException("STRATEGY_INPUT_REJECTED");
            Instant latestOpen = before.getLast().openTime();
            Instant firstOpen = latestOpen.minus(HOUR);
            boolean bounded = cursor.bounded() != null;
            resolvingOverlap = bounded;
            if (bounded && !progress.beginOverlapCheck(runId))
                return progress.closeAdmissionIfDue(runId) ? settle(runId) : status(runId);
            ClosedBarMarketFeed.Observation observation;
            try {
                observation = feed.observe(firstOpen, 24);
            } catch (IllegalStateException failure) {
                // 适配器明确未交付响应时可结束本次核验；中断留下恢复标记。
                if (bounded && !Thread.currentThread().isInterrupted())
                    progress.finishOverlapCheck(runId, firstOpen, latestOpen, List.of(), clock);
                throw failure;
            }
            if (bounded) {
                progress.finishOverlapCheck(runId, firstOpen, latestOpen, observation.bars(), clock);
                if (progress.closeAdmissionIfDue(runId)) return settle(runId);
            }
            resolvingOverlap = false;
            for (HistoricalBar bar : observation.bars()) {
                if (cursor.bounded() != null && !bar.openTime().isBefore(cursor.bounded().endAt())) break;
                if (bar.openTime().isAfter(latestAvailable)) latestAvailable = bar.openTime();
            }
            if (observation.serverTime() == null
                    || observation.rule() == null
                    || observation.rule().observedAt() == null
                    || Duration.between(observation.rule().observedAt(), observation.serverTime())
                                    .abs()
                                    .compareTo(Duration.ofMinutes(5))
                            > 0) {
                throw new IllegalStateException("VENUE_RULE_STALE");
            }
            if (!"LIVE".equals(observation.rule().state())) {
                throw new IllegalStateException("VENUE_RULE_UNAVAILABLE");
            }
            if (observation.quote() == null
                    || observation.quote().observedAt() == null
                    || observation.quote().price() == null
                    || observation.quote().price().signum() <= 0
                    || observation
                            .quote()
                            .observedAt()
                            .isAfter(observation.serverTime().plusSeconds(5))
                    || observation
                            .quote()
                            .observedAt()
                            .isBefore(observation.serverTime().minus(Duration.ofMinutes(2)))) {
                throw new IllegalStateException("PUBLIC_MARKETDATA_UNAVAILABLE");
            }
            Instant expected = firstOpen;
            for (HistoricalBar bar : observation.bars()) {
                if (cursor.bounded() != null && !bar.openTime().isBefore(cursor.bounded().endAt().minus(HOUR))) break;
                if (!"OKX".equals(bar.exchangeCode())
                        || !"SPOT".equals(bar.marketType())
                        || !"BTC-USDT".equals(bar.symbol())
                        || !"1h".equals(bar.interval().wireValue())
                        || bar.closeTime().isAfter(observation.serverTime())
                        || bar.availableAt().isBefore(bar.closeTime())) {
                    throw new IllegalStateException("EXPECTED_BAR_MISSING");
                }
                if (!bar.openTime().equals(expected)) {
                    gapStart = expected;
                    throw new IllegalStateException("EXPECTED_BAR_MISSING");
                }
                progress.insertBar(runId, bar, observation.observedAt());
                expected = expected.plus(HOUR);
            }
            Instant closedBoundary = observation.serverTime().truncatedTo(ChronoUnit.HOURS);
            if (expected.isBefore(closedBoundary) && observation.bars().size() < 24) {
                gapStart = expected;
                throw new IllegalStateException("EXPECTED_BAR_MISSING");
            }
            List<HistoricalBar> bars = progress.recentBars(runId);
            Instant lastProcessed = cursor.lastProcessedOpenTime();
            if (bars.getFirst().openTime().isAfter(lastProcessed.plus(HOUR))) {
                throw new IllegalStateException("BACKFILL_LIMIT_EXCEEDED");
            }
            for (int count = 0; count < 24; count++) {
                if (!lastProcessed.isBefore(bars.getLast().openTime())) {
                    // 无新窗口时仍收敛旧执行；未终态只等待正式撮合，不能立即重试创建下一 run。
                    try {
                        sim.advanceContinuous(
                                runId,
                                bars,
                                lastProcessed,
                                observation.rule(),
                                observation.quote());
                    } catch (IllegalStateException pending) {
                        if (!"SIM_PREVIOUS_EXECUTION_PENDING".equals(pending.getMessage()))
                            throw pending;
                    }
                    break;
                }
                var decision =
                        sim.advanceContinuous(
                                runId,
                                bars,
                                lastProcessed,
                                observation.rule(),
                                observation.quote());
                if (decision == null) break;
                if (!decision.signalOpenTime().equals(lastProcessed.plus(HOUR))) {
                    throw new IllegalStateException("CONTINUOUS_SIM_CURSOR_CONFLICT");
                }
                HistoricalBar signalBar =
                        bars.stream()
                                .filter(bar -> bar.openTime().equals(decision.signalOpenTime()))
                                .findFirst()
                                .orElseThrow();
                progress.advanceCursor(runId, lastProcessed, signalBar, clock.instant());
                lastProcessed = signalBar.openTime();
            }
            progress.pollResult(
                    runId,
                    now,
                    latestAvailable,
                    null,
                    "RUNNING",
                    expected.isBefore(closedBoundary)
                            ? "CATCHING_UP"
                            : lastProcessed.isBefore(bars.getLast().openTime())
                                    ? "WAITING_FOR_EXECUTION_QUOTE"
                                    : "WAITING_FOR_BAR_CLOSE",
                    true);
            return status(runId);
        } catch (RuntimeException ex) {
            if ((cursor.bounded() != null && ex instanceof TransientDataAccessException)
                    || (resolvingOverlap && ex instanceof DataAccessException)) throw ex;
            String reason = classify(ex);
            String next = "DATA_REVISION_DETECTED".equals(reason) ? "STOPPED" : "STALLED";
            // 修订直到响应校验完成才可知，不能把请求开始时刻当作历史评价已知修订的时间。
            Instant failedAt = "DATA_REVISION_DETECTED".equals(reason) ? clock.instant() : now;
            progress.pollResult(runId, failedAt, latestAvailable, gapStart, next, reason, false);
            return status(runId);
        }
    }

    /** 到期通道只接收末槽证据、回收原执行；任何异常保留来源与未决状态。 */
    private Status settle(String runId) {
        try {
            return settleLocked(runId);
        } catch (TransientDataAccessException conflict) {
            // 数据库事务冲突必须让该轮调度失败，下一轮按原身份重读，不把失败写成业务终态。
            throw conflict;
        } catch (IllegalStateException failure) {
            String reason = switch (Objects.toString(failure.getMessage(), "")) {
                case "SIM_EXECUTION_BINDING_UNKNOWN", "SIM_PREPARED_EXECUTION_UNRESOLVED",
                     "SIM_CANCEL_FINALITY_UNAVAILABLE", "SIM_CANONICAL_RUN_NOT_TERMINAL",
                     "CONTINUOUS_SIM_CURSOR_CONFLICT", "SIM_DECISION_RECOVERY_INVALID" -> failure.getMessage();
                default -> "BOUNDED_SETTLEMENT_UNPROVEN";
            };
            var latest = progress.get(runId);
            progress.recordSettlement(runId, "BLOCKED", latest.bounded().finalValuationStatus(),
                    null, reason, false);
            return status(runId);
        }
    }

    private Status settleLocked(String runId) {
        var cursor = progress.get(runId);
        Instant end = cursor.bounded().endAt();
        String priceIssue = collectFinalPrice(runId, cursor, end);
        String valuation = priceIssue == null ? "VERIFIED" : "UNAVAILABLE";
        StrategySimRunService.Settlement economics = sim.settleBounded(runId);
        cursor = progress.get(runId);
        String reason = economics.blockingReason();
        if (reason != null || economics.pendingOrders() != 0) {
            boolean waiting = "SIM_PREVIOUS_EXECUTION_PENDING".equals(reason)
                    || "SIM_DECISION_RECOVERY_PENDING".equals(reason) || "SIM_CANONICAL_RUN_NOT_TERMINAL".equals(reason);
            progress.recordSettlement(runId, waiting ? "SETTLING" : "BLOCKED",
                    valuation, economics.pendingOrders(), reason, false);
            return status(runId);
        }
        if (cursor.manualEndAt() != null) {
            progress.recordSettlement(runId, "MANUALLY_STOPPED", valuation,0, "MANUAL_END", true);
            return status(runId);
        }
        if (priceIssue != null) {
            boolean exhausted = cursor.overlapCheckPending() || cursor.revisionDetectedAt() != null
                    || clock.instant().isAfter(end.plusSeconds(ClosedBarValuation.MAXIMUM_LATENESS_SECONDS));
            progress.recordSettlement(runId, exhausted ? "BLOCKED" : "ADMISSION_CLOSED",
                    valuation,0, priceIssue, exhausted);
            return status(runId);
        }

        boolean covered = closeFinalSlot(runId, cursor, end);
        progress.recordSettlement(runId, covered ? "COMPLETED" : "BLOCKED", valuation,0,
                covered ? "DEADLINE_SETTLED" : "FINAL_SLOT_COVERAGE_INCOMPLETE", true);
        return status(runId);
    }

    private String collectFinalPrice(String runId, ContinuousSimRepository.Cursor cursor, Instant end) {
        if (cursor.revisionDetectedAt() != null) return "DATA_REVISION_DETECTED";
        if (cursor.overlapCheckPending()) return "OVERLAP_CHECK_INCOMPLETE";
        if (cursor.manualEndAt() != null && cursor.manualEndAt().isBefore(end)) return "MANUAL_END_BEFORE_DEADLINE";
        String priceIssue = progress.closingPriceFailure(runId, end, clock.instant());
        if (priceIssue != null && cursor.manualEndAt() == null
                && !clock.instant().isAfter(end.plusSeconds(ClosedBarValuation.MAXIMUM_LATENESS_SECONDS))) {
            try {
                var observation = progress.observeMissingFinalBar(runId, end, clock,
                        () -> feed.observeFinalBar(end.minus(HOUR)));
                if (observation != null) progress.saveFinalBar(runId, end, observation);
                priceIssue = progress.closingPriceFailure(runId, end, clock.instant());
            } catch (IllegalStateException failure) {
                priceIssue = classify(failure);
            }
        }
        return priceIssue;
    }

    private boolean closeFinalSlot(String runId, ContinuousSimRepository.Cursor cursor, Instant end) {
        // 最后一小时仍需真实策略判断才能证明覆盖；缺少先前槽位时不可跳过游标伪造完整运行。
        Instant lastOpen = end.minus(HOUR);
        if (cursor.lastProcessedOpenTime().equals(lastOpen.minus(HOUR))) {
            var bars = progress.recentBars(runId);
            var last = bars.stream().filter(bar -> bar.openTime().equals(lastOpen)).findFirst().orElseThrow();
            var decision = sim.advanceContinuous(runId, bars, cursor.lastProcessedOpenTime(), null, null);
            if (decision != null && lastOpen.equals(decision.signalOpenTime())
                    && decision.orderId() == null && decision.strategyRunId() == null) {
                progress.advanceCursor(runId, cursor.lastProcessedOpenTime(), last, clock.instant());
            }
        }
        return progress.get(runId).lastProcessedOpenTime().equals(lastOpen);
    }

    private void checkBinding(ContinuousSimRepository.Cursor cursor) {
        if (paperRuns.getById(cursor.runId()).status() != PaperTradingRunStatus.RUNNING) {
            throw new IllegalStateException("SIM_EXECUTION_BLOCKED");
        }
        StrategySimRunService.ContinuousSeed seed = sim.continuousSeed(cursor.runId());
        if (!seed.strategyVersionId().equals(cursor.strategyVersionId())
                || !seed.strategyChecksum().equals(cursor.strategyChecksum())
                || !seed.costSha256().equals(cursor.costSha256())
                || !seed.bars().getLast().openTime().equals(cursor.seedLastOpenTime())) {
            throw new IllegalStateException("STRATEGY_INPUT_REJECTED");
        }
    }

    private String classify(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null) return "SIM_EXECUTION_BLOCKED";
        for (String reason :
                List.of(
                        "DATA_REVISION_DETECTED",
                        "BACKFILL_LIMIT_EXCEEDED",
                        "EXPECTED_BAR_MISSING",
                        "VENUE_RULE_STALE",
                        "VENUE_RULE_UNAVAILABLE",
                        "STRATEGY_INPUT_REJECTED",
                        "SIM_EXECUTION_BLOCKED")) {
            if (message.contains(reason)) return reason;
        }
        if (message.startsWith("PUBLIC_MARKET")) return "PUBLIC_MARKETDATA_UNAVAILABLE";
        return "SIM_EXECUTION_BLOCKED";
    }

    /** Dispatcher 每轮最多处理仓储返回的八条活跃 run；单条失败不阻断其余 run。 */
    public void pollActiveOnce() {
        RuntimeException firstFailure = null;
        var candidates = tenantWork != null && TenantContext.currentTenantId().isEmpty()
                ? tenantWork.next(TenantWorkAccess.Kind.PAPER, 8) : progress.activeIds();
        for (String runId : candidates) {
            try {
                pollOnce(runId);
            } catch (RuntimeException failure) {
                if (firstFailure == null) firstFailure = failure;
            }
        }
        if (firstFailure != null) throw firstFailure;
    }

    private Status withRunLock(String runId, boolean skipWhenBusy, Supplier<Status> action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            // 命令结果与业务事务共同提交，锁必须保持到提交/回滚，不能在回执尚未提交时交给轮询器。
            Boolean acquired =
                    jdbc.queryForObject(
                            "SELECT pg_try_advisory_xact_lock(hashtextextended(?,9252))",
                            Boolean.class,
                            "continuous-sim-run:" + runId);
            if (!Boolean.TRUE.equals(acquired)) {
                if (skipWhenBusy) return status(runId);
                throw new IllegalStateException("CONTINUOUS_SIM_BUSY");
            }
            return action.get();
        }
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            try (PreparedStatement lock =
                    connection.prepareStatement(
                            "SELECT pg_try_advisory_lock(hashtextextended(?, 9252))")) {
                lock.setString(1, "continuous-sim-run:" + runId);
                try (ResultSet result = lock.executeQuery()) {
                    result.next();
                    if (!result.getBoolean(1)) {
                        if (skipWhenBusy) return status(runId);
                        throw new IllegalStateException("CONTINUOUS_SIM_BUSY");
                    }
                }
            }
            try {
                return action.get();
            } finally {
                try (PreparedStatement unlock =
                        connection.prepareStatement(
                                "SELECT pg_advisory_unlock(hashtextextended(?, 9252))")) {
                    unlock.setString(1, "continuous-sim-run:" + runId);
                    unlock.execute();
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("CONTINUOUS_SIM_LOCK_UNAVAILABLE", ex);
        }
    }

    public record Status(
            String paperRunId,
            String status,
            Instant startedAt,
            Instant lastPollAt,
            Instant lastSuccessfulPollAt,
            Instant lastProcessedBar,
            Instant nextExpectedBar,
            Instant lastObservedBar,
            Instant gapStartBar,
            String lastDecisionId,
            String lastDecisionStatus,
            String blockReason,
            int consecutivePollFailures,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            Bounded bounded) {
        public Status(String paperRunId, String status, Instant startedAt, Instant lastPollAt, Instant lastSuccessfulPollAt,
                      Instant lastProcessedBar, Instant nextExpectedBar, Instant lastObservedBar, Instant gapStartBar,
                      String lastDecisionId, String lastDecisionStatus, String blockReason, int consecutivePollFailures) {
            this(paperRunId, status, startedAt, lastPollAt, lastSuccessfulPollAt, lastProcessedBar, nextExpectedBar,
                    lastObservedBar, gapStartBar, lastDecisionId, lastDecisionStatus, blockReason, consecutivePollFailures, null);
        }
    }

    public record Bounded(Instant endAt, String lifecycleStatus, String finalValuationStatus, Instant lastValuationBoundary,
                          Integer pendingOrders, String reason, Instant updatedAt, String publishId, String strategyVersionId,
                          @JsonInclude(JsonInclude.Include.NON_NULL) Instant manualEndAt,
                          @JsonInclude(JsonInclude.Include.NON_NULL) Instant terminatedAt) {}
}
