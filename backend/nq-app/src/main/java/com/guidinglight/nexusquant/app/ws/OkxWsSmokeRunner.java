package com.guidinglight.nexusquant.app.ws;

import com.guidinglight.nexusquant.adapter.okx.service.OkxWsClient;
import com.guidinglight.nexusquant.adapter.okx.service.OkxWsMetricsSnapshot;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * OkxWsSmokeRunner 负责本地 WS smoke 启动器。
 * <p>
 * Why:
 * PR-W1 要求“可连接+可订阅+可观测”的最小联调入口，但默认必须关闭避免生产误暴露。
 * 因此该 runner 仅在 local 且显式开关打开时启用。
 */
@Component
@Profile("local")
@ConditionalOnProperty(name = "nq.okx.ws.enabled", havingValue = "true")
public class OkxWsSmokeRunner implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(OkxWsSmokeRunner.class);

    private final OkxWsClient okxWsClient;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running;
    private final long forceReconnectAfterMs;

    /**
     * @param okxWsClient WS 治理客户端
     */
    public OkxWsSmokeRunner(
            OkxWsClient okxWsClient,
            @Value("${nq.okx.ws.smoke.force-reconnect-ms:0}") long forceReconnectAfterMs
    ) {
        this.okxWsClient = okxWsClient;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "okx-ws-smoke-metrics"));
        this.running = new AtomicBoolean(false);
        this.forceReconnectAfterMs = forceReconnectAfterMs;
    }

    /**
     * 启动 WS 客户端并周期打印指标快照。
     */
    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        okxWsClient.start();
        scheduler.scheduleAtFixedRate(this::logMetrics, 5L, 15L, TimeUnit.SECONDS);
        if (forceReconnectAfterMs > 0) {
            scheduler.schedule(() -> okxWsClient.triggerReconnectForSmoke("smoke_forced_reconnect"), forceReconnectAfterMs, TimeUnit.MILLISECONDS);
        }
        log.info("okx_ws_smoke_runner_started");
    }

    /**
     * 停止 WS 客户端并释放后台线程。
     */
    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        scheduler.shutdownNow();
        okxWsClient.stop();
        log.info("okx_ws_smoke_runner_stopped");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    private void logMetrics() {
        OkxWsMetricsSnapshot snapshot = okxWsClient.metricsSnapshot();
        log.info(
                "okx_ws_metrics ws_connected={} reconnect_count={} subscribe_success_count={} subscribe_fail_count={} last_msg_age_ms={}",
                snapshot.wsConnected(),
                snapshot.reconnectCount(),
                snapshot.subscribeSuccessCount(),
                snapshot.subscribeFailCount(),
                snapshot.lastMsgAgeMs()
        );
    }
}
