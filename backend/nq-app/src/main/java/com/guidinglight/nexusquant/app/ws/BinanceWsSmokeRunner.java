package com.guidinglight.nexusquant.app.ws;

import com.guidinglight.nexusquant.adapter.binance.ws.BinanceWsClient;
import com.guidinglight.nexusquant.adapter.binance.ws.BinanceWsMetricsSnapshot;

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
 * BinanceWsSmokeRunner 负责本地 Binance 私有 WS smoke 启动器。
 * <p>
 * Why:
 * PR-BW1 要求提供“只做连接 + listenKey + 指标输出”的最小联调入口，但默认必须关闭避免生产误连私有流。
 * 因此该 runner 仅在 local 且显式开关打开时启用，并且不消费业务消息。
 */
@Component
@Profile("local")
@ConditionalOnProperty(name = "nq.binance.ws.enabled", havingValue = "true")
public class BinanceWsSmokeRunner implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(BinanceWsSmokeRunner.class);

    private final BinanceWsClient binanceWsClient;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running;
    private final long forceReconnectAfterMs;

    /**
     * 构造本地 smoke runner。
     * <p>
     * Why:
     * `.env` 中 `NQ_BINANCE_WS_SMOKE_FORCE_RECONNECT_MS` 允许留空表示“不启用强制重连”。
     * 如果直接注入 `long`，Spring 会把空串当成数字解析并在启动阶段失败。
     * 因此这里先按字符串接收，再在本地 runner 内做最小解析，把“空值 = 0”固定下来。
     */
    public BinanceWsSmokeRunner(
            BinanceWsClient binanceWsClient,
            @Value("${nq.binance.ws.smoke.force-reconnect-ms:}") String forceReconnectAfterMs
    ) {
        this.binanceWsClient = binanceWsClient;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "binance-ws-smoke-metrics"));
        this.running = new AtomicBoolean(false);
        this.forceReconnectAfterMs = parseForceReconnectAfterMs(forceReconnectAfterMs);
    }

    /**
     * 启动 WS 客户端并周期打印指标快照。
     */
    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        binanceWsClient.start();
        scheduler.scheduleAtFixedRate(this::logMetrics, 5L, 15L, TimeUnit.SECONDS);
        if (forceReconnectAfterMs > 0) {
            scheduler.schedule(
                    () -> binanceWsClient.triggerReconnectForSmoke("smoke_forced_reconnect"),
                    forceReconnectAfterMs,
                    TimeUnit.MILLISECONDS
            );
        }
        log.info("binance_ws_smoke_runner_started");
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
        binanceWsClient.stop();
        log.info("binance_ws_smoke_runner_stopped");
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
        BinanceWsMetricsSnapshot snapshot = binanceWsClient.metricsSnapshot();
        log.info(
                "binance_ws_metrics ws_connected={} reconnect_count={} listenkey_refresh_success_count={} "
                        + "listenkey_refresh_fail_count={} last_msg_age_ms={} last_reconnect_ts={}",
                snapshot.wsConnected(),
                snapshot.reconnectCount(),
                snapshot.listenKeyRefreshSuccessCount(),
                snapshot.listenKeyRefreshFailCount(),
                snapshot.lastMsgAgeMs(),
                snapshot.lastReconnectEpochMs()
        );
    }

    private long parseForceReconnectAfterMs(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return 0L;
        }
        return Long.parseLong(rawValue.trim());
    }
}
