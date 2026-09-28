package com.guidinglight.nexusquant.scheduler.paper;

import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.port.ClosedBarMarketFeed;
import com.guidinglight.nexusquant.research.application.paper.service.PaperTradingRunService;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRunStatus;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 单线程轮询受限 OKX 公开小时 bar；数据库会话锁保护同一 run 的跨实例推进。 */
@Service
@Profile("public-marketdata-manual")
@ConditionalOnProperty(prefix = "nq.continuous-sim", name = "enabled", havingValue = "true")
public class ContinuousSimRunService implements DisposableBean {
    private static final Duration HOUR = Duration.ofHours(1);
    private final ContinuousSimRepository progress;
    private final StrategySimRunService sim;
    private final PaperTradingRunService paperRuns;
    private final ClosedBarMarketFeed feed;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;

    @Autowired
    public ContinuousSimRunService(ContinuousSimRepository progress, StrategySimRunService sim,
            PaperTradingRunService paperRuns, ClosedBarMarketFeed feed, JdbcTemplate jdbc) {
        this(progress, sim, paperRuns, feed, jdbc, Clock.systemUTC(), true);
    }

    public ContinuousSimRunService(ContinuousSimRepository progress, StrategySimRunService sim,
            PaperTradingRunService paperRuns, ClosedBarMarketFeed feed, JdbcTemplate jdbc,
            Clock clock, boolean startScheduler) {
        this.progress = Objects.requireNonNull(progress);
        this.sim = Objects.requireNonNull(sim);
        this.paperRuns = Objects.requireNonNull(paperRuns);
        this.feed = Objects.requireNonNull(feed);
        this.jdbc = Objects.requireNonNull(jdbc);
        this.clock = Objects.requireNonNull(clock);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "nq-continuous-sim-poll");
            thread.setDaemon(true);
            return thread;
        });
        if (startScheduler) scheduler.scheduleWithFixedDelay(this::scheduledTick,
                30, 300, TimeUnit.SECONDS);
    }

    @Transactional(timeout = 30)
    public Status start(String publishId, BigDecimal budget) {
        progress.requireAdmissionCapacity();
        StrategySimRunService.RunView created = sim.create(publishId, budget, "system");
        StrategySimRunService.ContinuousSeed seed = sim.continuousSeed(created.paperRunId());
        paperRuns.start(created.paperRunId());
        progress.create(created.paperRunId(), seed.strategyVersionId(), seed.strategyChecksum(),
                seed.costSha256(), seed.bars(), clock.instant());
        return status(created.paperRunId());
    }

    public Status stop(String runId) {
        return withRunLock(runId, false, () -> {
            progress.stop(runId, clock.instant());
            return status(runId);
        });
    }

    @Transactional(timeout = 30)
    public Status resume(String runId) {
        return withRunLock(runId, false, () -> {
            ContinuousSimRepository.Cursor cursor = progress.get(runId);
            checkBinding(cursor);
            if (!"STOPPED".equals(cursor.status())) {
                throw new IllegalStateException("CONTINUOUS_SIM_NOT_STOPPED");
            }
            if ("DATA_REVISION_DETECTED".equals(cursor.blockReason())) {
                throw new IllegalStateException("DATA_REVISION_DETECTED");
            }
            progress.requireAdmissionCapacity();
            progress.resume(runId, clock.instant());
            return status(runId);
        });
    }

    public Status status(String runId) {
        ContinuousSimRepository.Cursor cursor = progress.get(runId);
        var recent = sim.decisions(runId);
        var latest = recent.isEmpty() ? null : recent.getFirst();
        return new Status(cursor.runId(), cursor.status(), cursor.startedAt(), cursor.lastPollAt(),
                cursor.lastSuccessfulPollAt(), cursor.lastProcessedOpenTime(),
                cursor.lastProcessedOpenTime().plus(HOUR), cursor.lastObservedOpenTime(),
                cursor.gapStartOpenTime(),
                latest == null ? null : latest.decisionId(),
                latest == null ? null : latest.status(), cursor.blockReason(),
                cursor.consecutivePollFailures());
    }

    public boolean has(String runId) {
        return progress.exists(runId);
    }

    /** 单次定向推进也供隔离测试调用；调度器不在未启用配置下创建。 */
    public Status pollOnce(String runId) {
        return withRunLock(runId, true, () -> pollLocked(runId));
    }

    private Status pollLocked(String runId) {
        ContinuousSimRepository.Cursor cursor = progress.get(runId);
        if ("STOPPED".equals(cursor.status())) return status(runId);
        Instant now = clock.instant();
        Instant latestAvailable = cursor.lastObservedOpenTime();
        Instant gapStart = null;
        try {
            checkBinding(cursor);
            List<HistoricalBar> before = progress.recentBars(runId);
            if (before.isEmpty()) throw new IllegalStateException("STRATEGY_INPUT_REJECTED");
            Instant latestOpen = before.getLast().openTime();
            Instant firstOpen = latestOpen.minus(HOUR);
            ClosedBarMarketFeed.Observation observation = feed.observe(firstOpen, 24);
            for (HistoricalBar bar : observation.bars()) {
                if (bar.openTime().isAfter(latestAvailable)) latestAvailable = bar.openTime();
            }
            if (observation.serverTime() == null || observation.rule() == null
                    || observation.rule().observedAt() == null
                    || Duration.between(observation.rule().observedAt(), observation.serverTime()).abs()
                            .compareTo(Duration.ofMinutes(5)) > 0) {
                throw new IllegalStateException("VENUE_RULE_STALE");
            }
            if (!"LIVE".equals(observation.rule().state())) {
                throw new IllegalStateException("VENUE_RULE_UNAVAILABLE");
            }
            if (observation.quote() == null || observation.quote().observedAt() == null
                    || observation.quote().price() == null || observation.quote().price().signum() <= 0
                    || observation.quote().observedAt().isAfter(observation.serverTime().plusSeconds(5))
                    || observation.quote().observedAt().isBefore(observation.serverTime().minus(Duration.ofMinutes(2)))) {
                throw new IllegalStateException("PUBLIC_MARKETDATA_UNAVAILABLE");
            }
            Instant expected = firstOpen;
            for (HistoricalBar bar : observation.bars()) {
                if (!"OKX".equals(bar.exchangeCode()) || !"SPOT".equals(bar.marketType())
                        || !"BTC-USDT".equals(bar.symbol()) || !"1h".equals(bar.interval().wireValue())
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
            Instant closedBoundary = observation.serverTime().truncatedTo(java.time.temporal.ChronoUnit.HOURS);
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
                var decision = sim.advanceContinuous(runId, bars, lastProcessed,
                        observation.rule(), observation.quote());
                if (decision == null) break;
                if (!decision.signalOpenTime().equals(lastProcessed.plus(HOUR))) {
                    throw new IllegalStateException("CONTINUOUS_SIM_CURSOR_CONFLICT");
                }
                HistoricalBar signalBar = bars.stream()
                        .filter(bar -> bar.openTime().equals(decision.signalOpenTime()))
                        .findFirst().orElseThrow();
                progress.advanceCursor(runId, lastProcessed, signalBar, clock.instant());
                lastProcessed = signalBar.openTime();
            }
            progress.pollResult(runId, now, latestAvailable, null, "RUNNING",
                    expected.isBefore(closedBoundary) ? "CATCHING_UP"
                    : lastProcessed.isBefore(bars.getLast().openTime())
                            ? "WAITING_FOR_EXECUTION_QUOTE" : "WAITING_FOR_BAR_CLOSE", true);
            return status(runId);
        } catch (RuntimeException ex) {
            String reason = classify(ex);
            String next = "DATA_REVISION_DETECTED".equals(reason) ? "STOPPED" : "STALLED";
            progress.pollResult(runId, now, latestAvailable, gapStart, next, reason, false);
            return status(runId);
        }
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
        for (String reason : List.of("DATA_REVISION_DETECTED", "BACKFILL_LIMIT_EXCEEDED",
                "EXPECTED_BAR_MISSING", "VENUE_RULE_STALE", "VENUE_RULE_UNAVAILABLE",
                "STRATEGY_INPUT_REJECTED", "SIM_EXECUTION_BLOCKED")) {
            if (message.contains(reason)) return reason;
        }
        if (message.startsWith("PUBLIC_MARKET")) return "PUBLIC_MARKETDATA_UNAVAILABLE";
        return "SIM_EXECUTION_BLOCKED";
    }

    private void scheduledTick() {
        try {
            for (String runId : progress.activeIds()) pollOnce(runId);
        } catch (RuntimeException ignored) {
            // 单轮异常由 run 状态记录；下轮仍可处理其他 run。
        }
    }

    private Status withRunLock(String runId, boolean skipWhenBusy, Supplier<Status> action) {
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            try (PreparedStatement lock = connection.prepareStatement(
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
            try { return action.get(); }
            finally {
                try (PreparedStatement unlock = connection.prepareStatement(
                        "SELECT pg_advisory_unlock(hashtextextended(?, 9252))")) {
                    unlock.setString(1, "continuous-sim-run:" + runId);
                    unlock.execute();
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("CONTINUOUS_SIM_LOCK_UNAVAILABLE", ex);
        }
    }

    @Override
    public void destroy() {
        scheduler.shutdownNow();
    }

    public record Status(String paperRunId, String status, Instant startedAt, Instant lastPollAt,
                         Instant lastSuccessfulPollAt, Instant lastProcessedBar,
                         Instant nextExpectedBar, Instant lastObservedBar, Instant gapStartBar,
                         String lastDecisionId, String lastDecisionStatus, String blockReason,
                         int consecutivePollFailures) { }
}
