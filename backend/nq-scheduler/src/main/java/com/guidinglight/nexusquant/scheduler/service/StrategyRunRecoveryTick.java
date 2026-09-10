package com.guidinglight.nexusquant.scheduler.service;

import com.guidinglight.nexusquant.strategy.application.StrategyRunRecoveryService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 独立启动/tick 检查已接纳工作；单线程不积压批次，数据库扫描游标负责跨重启公平性。 */
@Component
@ConditionalOnProperty(name = "nq.runtime.trading-components.enabled", havingValue = "true")
public class StrategyRunRecoveryTick implements DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(StrategyRunRecoveryTick.class);
    private final StrategyRunRecoveryService recovery;
    private final long delayMillis;
    private final boolean schedulingEnabled;
    private ScheduledExecutorService executor;

    public StrategyRunRecoveryTick(StrategyRunRecoveryService recovery,
            @Value("${nq.strategy.recovery.fixed-delay-ms:5000}") long delayMillis,
            @Value("${spring.task.scheduling.enabled:true}") boolean schedulingEnabled) {
        if (delayMillis < 1000 || delayMillis > 60000) throw new IllegalArgumentException("strategy recovery delay must be 1000..60000 ms");
        this.recovery = recovery;
        this.delayMillis = delayMillis;
        this.schedulingEnabled = schedulingEnabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (!schedulingEnabled || executor != null) return;
        // 不全局启用其它定时器；当前入口只拥有自身的一批恢复任务。
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "strategy-run-recovery");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::tick, 0, delayMillis, TimeUnit.MILLISECONDS);
    }

    public void tick() {
        try {
            recovery.recoverAll();
        } catch (RuntimeException ex) {
            log.warn("STRATEGY_RECOVERY_TICK_FAILED failureType={}", ex.getClass().getSimpleName());
        }
    }

    @Override
    public synchronized void destroy() {
        if (executor != null) executor.shutdownNow();
    }
}
