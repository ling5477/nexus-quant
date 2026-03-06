package com.guidinglight.nexusquant.adapter.okx.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * OkxWsClient 负责 GateC-1.1 的私有 WS 连接治理。
 * <p>
 * Why:
 * WS 在 GateC-1.1 只能作为“加速层”，不能写业务表、不能推进状态机。
 * 该类只处理连接/login/订阅/心跳/重连/可观测性，并把业务消息分发给外部监听器。
 */
public class OkxWsClient {

    private static final Logger log = LoggerFactory.getLogger(OkxWsClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final OkxRuntimeConfig runtimeConfig;
    private final OkxRequestSigner signer;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService callbackExecutor;
    private final Set<OkxWsSubscription> subscriptions;
    private final List<OkxWsBusinessMessageListener> businessMessageListeners;
    private final List<OkxWsConnectionListener> connectionListeners;
    private final AtomicBoolean running;
    private final AtomicBoolean loginConfirmed;
    private final AtomicInteger wsConnected;
    private final AtomicLong reconnectCount;
    private final AtomicLong subscribeSuccessCount;
    private final AtomicLong subscribeFailCount;
    private final AtomicLong lastMessageEpochMs;
    private final AtomicInteger reconnectAttempt;
    private volatile WebSocket webSocket;

    /**
     * 默认构造器从环境变量创建可运行配置。
     */
    public OkxWsClient() {
        this(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                OkxRuntimeConfig.fromSystemEnv(),
                new OkxRequestSigner(),
                Clock.systemUTC()
        );
    }

    /**
     * 可测试构造器。
     */
    public OkxWsClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            OkxRuntimeConfig runtimeConfig,
            OkxRequestSigner signer,
            Clock clock
    ) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "runtimeConfig must not be null");
        this.signer = Objects.requireNonNull(signer, "signer must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "okx-ws-scheduler"));
        this.callbackExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "okx-ws-callback"));
        this.subscriptions = ConcurrentHashMap.newKeySet();
        this.businessMessageListeners = new CopyOnWriteArrayList<>();
        this.connectionListeners = new CopyOnWriteArrayList<>();
        this.running = new AtomicBoolean(false);
        this.loginConfirmed = new AtomicBoolean(false);
        this.wsConnected = new AtomicInteger(0);
        this.reconnectCount = new AtomicLong(0L);
        this.subscribeSuccessCount = new AtomicLong(0L);
        this.subscribeFailCount = new AtomicLong(0L);
        this.lastMessageEpochMs = new AtomicLong(0L);
        this.reconnectAttempt = new AtomicInteger(0);
    }

    /**
     * 注册业务消息监听器。
     * <p>
     * Why:
     * W2 需要把 WS 消息映射与入链放在独立组件，避免连接层直接依赖 event_store。
     */
    public void addBusinessMessageListener(OkxWsBusinessMessageListener listener) {
        businessMessageListeners.add(Objects.requireNonNull(listener, "listener must not be null"));
    }

    /**
     * 注册连接状态监听器。
     * <p>
     * Why:
     * PR-W3 需要在连接异常窗口触发 REST reconcile 降级，
     * 连接层只暴露状态回调，不直接依赖任何业务服务。
     */
    public void addConnectionListener(OkxWsConnectionListener listener) {
        connectionListeners.add(Objects.requireNonNull(listener, "listener must not be null"));
    }

    /**
     * 启动连接并注册默认私有通道订阅。
     */
    public synchronized void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        // Why:
        // PR-W1/W2 先覆盖 orders/account；balance_and_position 用于外部仓位快照证据。
        subscriptions.add(new OkxWsSubscription("orders", "SPOT"));
        subscriptions.add(new OkxWsSubscription("account", null));
        subscriptions.add(new OkxWsSubscription("balance_and_position", null));
        log.info("okx_ws_start fingerprint={} ws_url={}", runtimeConfig.fingerprint(), runtimeConfig.wsPrivateUrl());
        connectWithDelayMs(0L);
        scheduler.scheduleAtFixedRate(this::sendPingIfConnected, runtimeConfig.wsHeartbeat().toMillis(), runtimeConfig.wsHeartbeat().toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * 优雅关闭连接与后台线程。
     */
    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        WebSocket local = this.webSocket;
        this.webSocket = null;
        wsConnected.set(0);
        loginConfirmed.set(false);
        if (local != null) {
            try {
                local.sendClose(WebSocket.NORMAL_CLOSURE, "stop").join();
            } catch (Exception ex) {
                log.warn("okx_ws_close_failed reason={}", ex.getMessage());
            }
        }
        scheduler.shutdownNow();
        callbackExecutor.shutdownNow();
        log.info("okx_ws_stopped reconnect_count={} subscribe_ok={} subscribe_fail={}", reconnectCount.get(), subscribeSuccessCount.get(), subscribeFailCount.get());
    }

    /**
     * 动态添加订阅。
     */
    public void subscribe(OkxWsSubscription subscription) {
        Objects.requireNonNull(subscription, "subscription must not be null");
        subscriptions.add(subscription);
        if (wsConnected.get() == 1 && isAuthReady()) {
            sendSubscribe(List.of(subscription));
        }
    }

    /**
     * 动态移除订阅。
     */
    public void unsubscribe(OkxWsSubscription subscription) {
        Objects.requireNonNull(subscription, "subscription must not be null");
        subscriptions.remove(subscription);
        if (wsConnected.get() == 1 && isAuthReady()) {
            sendUnsubscribe(List.of(subscription));
        }
    }

    /**
     * 导出指标快照。
     */
    public OkxWsMetricsSnapshot metricsSnapshot() {
        long age = lastMessageEpochMs.get() <= 0 ? Long.MAX_VALUE : Math.max(0L, Instant.now(clock).toEpochMilli() - lastMessageEpochMs.get());
        return new OkxWsMetricsSnapshot(
                wsConnected.get(),
                reconnectCount.get(),
                subscribeSuccessCount.get(),
                subscribeFailCount.get(),
                age
        );
    }

    /**
     * 手动触发一次重连（仅用于本地 smoke 验证）。
     * <p>
     * Why:
     * 本地环境无法稳定复现“断网”，需要一个显式触发点验证重连与重订阅路径是否可用。
     *
     * @param reason 触发原因
     */
    public void triggerReconnectForSmoke(String reason) {
        if (!running.get()) {
            return;
        }
        WebSocket local = this.webSocket;
        if (local != null) {
            try {
                local.sendClose(WebSocket.NORMAL_CLOSURE, "smoke-reconnect").join();
            } catch (Exception ex) {
                log.warn("okx_ws_smoke_trigger_close_failed reason={}", ex.getMessage());
            }
        }
        scheduleReconnect(reason == null || reason.isBlank() ? "smoke_trigger" : reason);
    }

    private void connectWithDelayMs(long delayMs) {
        if (!running.get()) {
            return;
        }
        scheduler.schedule(this::connect, delayMs, TimeUnit.MILLISECONDS);
    }

    private void connect() {
        if (!running.get()) {
            return;
        }
        URI wsUri = URI.create(runtimeConfig.wsPrivateUrl());
        CompletableFuture<WebSocket> future = httpClient.newWebSocketBuilder()
                .buildAsync(wsUri, new ListenerImpl());
        future.whenCompleteAsync((ws, throwable) -> {
            if (throwable != null) {
                log.warn("okx_ws_connect_failed attempt={} reason={}", reconnectAttempt.get(), throwable.getMessage());
                scheduleReconnect("connect_failed");
                return;
            }
            this.webSocket = ws;
            reconnectAttempt.set(0);
            wsConnected.set(1);
            lastMessageEpochMs.set(Instant.now(clock).toEpochMilli());
            log.info("okx_ws_connected ws_url={}", runtimeConfig.wsPrivateUrl());
            String traceId = "trc-okx-ws-" + UUID.randomUUID();
            notifyConnected(traceId);
            if (runtimeConfig.credentials().isConfigured()) {
                sendLogin();
            } else {
                loginConfirmed.set(true);
                sendSubscribe(new ArrayList<>(subscriptions));
            }
        }, callbackExecutor);
    }

    private void sendLogin() {
        try {
            String loginMessage = OkxWsProtocol.buildLoginMessage(objectMapper, runtimeConfig.credentials(), signer, clock);
            sendText(loginMessage);
        } catch (Exception ex) {
            log.warn("okx_ws_login_build_failed reason={}", ex.getMessage());
            scheduleReconnect("login_build_failed");
        }
    }

    private void sendSubscribe(List<OkxWsSubscription> targetSubscriptions) {
        if (targetSubscriptions.isEmpty()) {
            return;
        }
        try {
            String payload = OkxWsProtocol.buildSubscribeMessage(objectMapper, targetSubscriptions);
            sendText(payload);
        } catch (Exception ex) {
            subscribeFailCount.incrementAndGet();
            log.warn("okx_ws_subscribe_send_failed reason={}", ex.getMessage());
        }
    }

    private void sendUnsubscribe(List<OkxWsSubscription> targetSubscriptions) {
        if (targetSubscriptions.isEmpty()) {
            return;
        }
        try {
            String payload = OkxWsProtocol.buildUnsubscribeMessage(objectMapper, targetSubscriptions);
            sendText(payload);
        } catch (Exception ex) {
            subscribeFailCount.incrementAndGet();
            log.warn("okx_ws_unsubscribe_send_failed reason={}", ex.getMessage());
        }
    }

    private void sendText(String text) {
        WebSocket local = this.webSocket;
        if (local == null) {
            throw new IllegalStateException("ws is not connected");
        }
        local.sendText(text, true);
    }

    private void sendPingIfConnected() {
        if (!running.get() || wsConnected.get() != 1) {
            return;
        }
        try {
            sendText("ping");
        } catch (Exception ex) {
            log.warn("okx_ws_ping_failed reason={}", ex.getMessage());
            scheduleReconnect("ping_failed");
        }
    }

    private void scheduleReconnect(String reason) {
        if (!running.get()) {
            return;
        }
        wsConnected.set(0);
        loginConfirmed.set(false);
        int attempt = reconnectAttempt.incrementAndGet();
        reconnectCount.incrementAndGet();
        long delayMs = OkxWsProtocol.reconnectDelayMs(
                attempt,
                runtimeConfig.wsReconnectBase().toMillis(),
                runtimeConfig.wsReconnectMax().toMillis()
        );
        log.warn("okx_ws_reconnect_scheduled reason={} attempt={} delay_ms={}", reason, attempt, delayMs);
        String traceId = "trc-okx-ws-" + UUID.randomUUID();
        notifyReconnectScheduled(reason, attempt, delayMs, traceId);
        connectWithDelayMs(delayMs);
    }

    private boolean isAuthReady() {
        return !runtimeConfig.credentials().isConfigured() || loginConfirmed.get();
    }

    private void dispatchBusinessMessages(String rawMessage) {
        if (businessMessageListeners.isEmpty()) {
            return;
        }
        List<OkxWsBusinessMessage> messages = OkxWsProtocol.extractBusinessMessages(objectMapper, rawMessage);
        if (messages.isEmpty()) {
            return;
        }
        String traceId = "trc-okx-ws-" + UUID.randomUUID();
        MDC.put("trace_id", traceId);
        try {
            for (OkxWsBusinessMessage message : messages) {
                for (OkxWsBusinessMessageListener listener : businessMessageListeners) {
                    try {
                        listener.onMessage(message, traceId);
                    } catch (Exception listenerEx) {
                        log.warn(
                                "okx_ws_business_listener_failed trace_id={} channel={} reason={}",
                                traceId,
                                message.channel(),
                                listenerEx.getMessage()
                        );
                    }
                }
            }
        } finally {
            MDC.remove("trace_id");
        }
    }

    private void notifyConnected(String traceId) {
        if (connectionListeners.isEmpty()) {
            return;
        }
        MDC.put("trace_id", traceId);
        try {
            for (OkxWsConnectionListener listener : connectionListeners) {
                try {
                    listener.onConnected(traceId);
                } catch (Exception listenerEx) {
                    log.warn("okx_ws_connection_listener_failed event=connected trace_id={} reason={}", traceId, listenerEx.getMessage());
                }
            }
        } finally {
            MDC.remove("trace_id");
        }
    }

    private void notifyReconnectScheduled(String reason, int attempt, long delayMs, String traceId) {
        if (connectionListeners.isEmpty()) {
            return;
        }
        MDC.put("trace_id", traceId);
        try {
            for (OkxWsConnectionListener listener : connectionListeners) {
                try {
                    listener.onReconnectScheduled(reason, attempt, delayMs, traceId);
                } catch (Exception listenerEx) {
                    log.warn("okx_ws_connection_listener_failed event=reconnect trace_id={} reason={}", traceId, listenerEx.getMessage());
                }
            }
        } finally {
            MDC.remove("trace_id");
        }
    }

    private void notifySubscribeFailed(String channel, String code, String message, String traceId) {
        if (connectionListeners.isEmpty()) {
            return;
        }
        MDC.put("trace_id", traceId);
        try {
            for (OkxWsConnectionListener listener : connectionListeners) {
                try {
                    listener.onSubscribeFailed(channel, code, message, traceId);
                } catch (Exception listenerEx) {
                    log.warn("okx_ws_connection_listener_failed event=subscribe_failed trace_id={} reason={}", traceId, listenerEx.getMessage());
                }
            }
        } finally {
            MDC.remove("trace_id");
        }
    }

    private final class ListenerImpl implements WebSocket.Listener {

        @Override
        public void onOpen(WebSocket webSocket) {
            WebSocket.Listener.super.onOpen(webSocket);
            webSocket.request(1);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            String raw = data.toString();
            lastMessageEpochMs.set(Instant.now(clock).toEpochMilli());
            OkxWsProtocol.ParsedMessage parsed = OkxWsProtocol.parseInboundMessage(objectMapper, raw);
            switch (parsed.kind()) {
                case LOGIN_SUCCESS -> {
                    loginConfirmed.set(true);
                    log.info("okx_ws_login_success");
                    sendSubscribe(new ArrayList<>(subscriptions));
                }
                case LOGIN_FAILED -> {
                    log.warn("okx_ws_login_failed code={} msg={}", parsed.code(), parsed.msg());
                    scheduleReconnect("login_failed");
                }
                case SUBSCRIBE_SUCCESS -> {
                    subscribeSuccessCount.incrementAndGet();
                    log.info("okx_ws_subscribe_success channel={} code={} msg={}", parsed.channel(), parsed.code(), parsed.msg());
                }
                case SUBSCRIBE_FAILED -> {
                    subscribeFailCount.incrementAndGet();
                    log.warn("okx_ws_subscribe_failed channel={} code={} msg={}", parsed.channel(), parsed.code(), parsed.msg());
                    String traceId = "trc-okx-ws-" + UUID.randomUUID();
                    notifySubscribeFailed(parsed.channel(), parsed.code(), parsed.msg(), traceId);
                }
                case BUSINESS_MESSAGE -> dispatchBusinessMessages(raw);
                case PONG -> {
                    // keepalive success; only refresh timestamp
                }
                default -> {
                    if (!parsed.event().isBlank() || !parsed.channel().isBlank()) {
                        log.debug("okx_ws_message event={} channel={} code={} msg={}", parsed.event(), parsed.channel(), parsed.code(), parsed.msg());
                    }
                }
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("okx_ws_error reason={}", error.getMessage());
            scheduleReconnect("listener_error");
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.warn("okx_ws_closed status_code={} reason={}", statusCode, reason);
            scheduleReconnect("listener_close");
            return CompletableFuture.completedFuture(null);
        }
    }
}
