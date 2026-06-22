package com.guidinglight.nexusquant.adapter.binance.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.adapter.binance.service.BinanceApiException;
import com.guidinglight.nexusquant.adapter.binance.service.BinanceRequestSigner;
import com.guidinglight.nexusquant.adapter.binance.service.BinanceRuntimeConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayDeque;
import java.util.Deque;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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
 * BinanceWsClient 负责 Binance 私有用户数据流的连接治理。
 * <p>
 * Why:
 * Binance 当前官方用户数据流已经迁移到 `ws-api` 订阅模型，但 GateC 的 BW2/BW3 仍依赖这个 client
 * 继续提供“连接治理 + 原始消息分发 + 断线回调”的稳定边界。
 * 因此该类在 adapter-binance 内兼容两种会话模式：
 * 1) 旧 listenKey 路径，仅作为历史兼容；
 * 2) 官方 `userDataStream.subscribe.signature` 路径，作为当前主链路。
 * 上层仍然只感知“私有 WS 已连接并开始收消息”，不把协议切换扩散到业务层。
 */
public class BinanceWsClient {

    private static final Logger log = LoggerFactory.getLogger(BinanceWsClient.class);
    private final ObjectMapper objectMapper;
    private final BinanceRuntimeConfig runtimeConfig;
    private final BinanceListenKeyClient listenKeyClient;
    private final BinanceRequestSigner requestSigner;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService callbackExecutor;
    private final WebSocketConnector webSocketConnector;
    private final AtomicBoolean running;
    private final AtomicBoolean reconnectScheduled;
    private final AtomicInteger wsConnected;
    private final AtomicInteger reconnectAttempt;
    private final AtomicLong reconnectCount;
    private final AtomicLong listenKeyRefreshSuccessCount;
    private final AtomicLong listenKeyRefreshFailCount;
    private final AtomicLong lastMessageEpochMs;
    private final AtomicLong lastReconnectEpochMs;
    private final AtomicLong wsRequestIdSequence;
    private final List<BinanceWsConnectionListener> connectionListeners;
    private final List<BinanceWsRawMessageListener> rawMessageListeners;
    private final Deque<String> recentInboundFrames;
    private final Deque<String> recentLifecycleEvents;
    private volatile WebSocket webSocket;
    private volatile String currentListenKey;
    private volatile Integer currentSubscriptionId;
    private volatile String pendingSubscriptionRequestId;
    private volatile String pendingSessionSubscriptionsRequestId;
    private volatile SubscriptionState subscriptionState;
    private volatile long subscriptionPendingStartedAtEpochMs;
    private volatile String lastLocalLifecycleAction;
    private volatile String lastSubscribeRequestSummary;
    private volatile String lastSubscribeSignaturePayload;
    private volatile String lastCloseSummary;
    private volatile boolean serverPingObserved;
    private volatile boolean serverPongObserved;
    private volatile boolean clientPingSent;
    private volatile boolean clientPongSent;
    private volatile boolean localCloseSent;
    private volatile SessionMode sessionMode;

    public BinanceWsClient() {
        this(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                BinanceRuntimeConfig.fromSystemEnv(),
                Clock.systemUTC()
        );
    }

    public BinanceWsClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            BinanceRuntimeConfig runtimeConfig,
            Clock clock
    ) {
        this(
                objectMapper,
                runtimeConfig,
                new BinanceListenKeyClient(
                        httpClient,
                        objectMapper,
                        runtimeConfig.baseUrl(),
                        runtimeConfig.timeout(),
                        runtimeConfig.credentials()
                ),
                new BinanceRequestSigner(),
                clock,
                new DefaultWebSocketConnector(httpClient)
        );
    }

    BinanceWsClient(
            ObjectMapper objectMapper,
            BinanceRuntimeConfig runtimeConfig,
            BinanceListenKeyClient listenKeyClient,
            BinanceRequestSigner requestSigner,
            Clock clock,
            WebSocketConnector webSocketConnector
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "runtimeConfig must not be null");
        this.listenKeyClient = Objects.requireNonNull(listenKeyClient, "listenKeyClient must not be null");
        this.requestSigner = Objects.requireNonNull(requestSigner, "requestSigner must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.webSocketConnector = Objects.requireNonNull(webSocketConnector, "webSocketConnector must not be null");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "binance-ws-scheduler"));
        this.callbackExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "binance-ws-callback"));
        this.running = new AtomicBoolean(false);
        this.reconnectScheduled = new AtomicBoolean(false);
        this.wsConnected = new AtomicInteger(0);
        this.reconnectAttempt = new AtomicInteger(0);
        this.reconnectCount = new AtomicLong(0L);
        this.listenKeyRefreshSuccessCount = new AtomicLong(0L);
        this.listenKeyRefreshFailCount = new AtomicLong(0L);
        this.lastMessageEpochMs = new AtomicLong(0L);
        this.lastReconnectEpochMs = new AtomicLong(0L);
        this.wsRequestIdSequence = new AtomicLong(0L);
        this.connectionListeners = new CopyOnWriteArrayList<>();
        this.rawMessageListeners = new CopyOnWriteArrayList<>();
        this.recentInboundFrames = new ArrayDeque<>();
        this.recentLifecycleEvents = new ArrayDeque<>();
        this.currentListenKey = "";
        this.currentSubscriptionId = null;
        this.pendingSubscriptionRequestId = null;
        this.pendingSessionSubscriptionsRequestId = null;
        this.subscriptionState = SubscriptionState.IDLE;
        this.subscriptionPendingStartedAtEpochMs = 0L;
        this.lastLocalLifecycleAction = "";
        this.lastSubscribeRequestSummary = "";
        this.lastSubscribeSignaturePayload = "";
        this.lastCloseSummary = "";
        this.serverPingObserved = false;
        this.serverPongObserved = false;
        this.clientPingSent = false;
        this.clientPongSent = false;
        this.localCloseSent = false;
        this.sessionMode = SessionMode.WS_API_SIGNATURE;
    }

    public void addConnectionListener(BinanceWsConnectionListener listener) {
        connectionListeners.add(Objects.requireNonNull(listener, "listener must not be null"));
    }

    public void addRawMessageListener(BinanceWsRawMessageListener listener) {
        rawMessageListeners.add(Objects.requireNonNull(listener, "listener must not be null"));
    }

    public synchronized void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        log.info("binance_ws_start fingerprint={}", runtimeConfig.fingerprint());
        rebuildSession("start");
        scheduler.scheduleAtFixedRate(
                this::refreshListenKeySafely,
                runtimeConfig.listenKeyRefreshInterval().toMillis(),
                runtimeConfig.listenKeyRefreshInterval().toMillis(),
                TimeUnit.MILLISECONDS
        );
        scheduler.scheduleAtFixedRate(
                this::heartbeatCheckSafely,
                runtimeConfig.wsHeartbeat().toMillis(),
                runtimeConfig.wsHeartbeat().toMillis(),
                TimeUnit.MILLISECONDS
        );
    }

    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        reconnectScheduled.set(false);
        closeSocketQuietly("stop");
        closeListenKeyQuietly(currentListenKey, "stop");
        currentListenKey = "";
        currentSubscriptionId = null;
        pendingSubscriptionRequestId = null;
        scheduler.shutdownNow();
        callbackExecutor.shutdownNow();
        wsConnected.set(0);
        log.info(
                "binance_ws_stopped reconnect_count={} listenkey_refresh_success_count={} listenkey_refresh_fail_count={}",
                reconnectCount.get(),
                listenKeyRefreshSuccessCount.get(),
                listenKeyRefreshFailCount.get()
        );
    }

    public BinanceWsMetricsSnapshot metricsSnapshot() {
        long lastMessage = lastMessageEpochMs.get();
        long age = lastMessage <= 0 ? Long.MAX_VALUE : Math.max(0L, Instant.now(clock).toEpochMilli() - lastMessage);
        return new BinanceWsMetricsSnapshot(
                wsConnected.get(),
                reconnectCount.get(),
                listenKeyRefreshSuccessCount.get(),
                listenKeyRefreshFailCount.get(),
                age,
                lastReconnectEpochMs.get()
        );
    }

    public void triggerReconnectForSmoke(String reason) {
        if (!running.get()) {
            return;
        }
        if (subscriptionState == SubscriptionState.PENDING) {
            log.debug("binance_ws_smoke_reconnect_skipped state=pending reason={}", reason);
            return;
        }
        rememberLifecycleEvent("smoke_trigger_reconnect reason=" + reason);
        closeSocketQuietly("smoke_trigger");
        scheduleReconnect(reason == null || reason.isBlank() ? "smoke_trigger" : reason);
    }

    private void rebuildSession(String reason) {
        if (!running.get()) {
            return;
        }
        String traceId = newTraceId();
        MDC.put("trace_id", traceId);
        try {
            rememberLifecycleEvent("rebuild_session reason=" + reason);
            sessionMode = SessionMode.WS_API_SIGNATURE;
            currentListenKey = "";
            connectWsApi(reason, traceId);
        } finally {
            MDC.remove("trace_id");
        }
    }

    private void connectLegacy(String listenKey, String reason, String traceId) {
        if (!running.get()) {
            return;
        }
        String wsUrl = BinanceWsProtocol.buildUserDataStreamUrl(runtimeConfig.wsUrl(), listenKey);
        webSocketConnector.connect(URI.create(wsUrl), new ListenerImpl(SessionMode.LEGACY_LISTEN_KEY, reason, traceId))
                .whenCompleteAsync((ws, throwable) -> {
                    if (throwable != null) {
                        log.warn(
                                "binance_ws_connect_failed trace_id={} reason={} attempt={}",
                                traceId,
                                throwable.getMessage(),
                                reconnectAttempt.get()
                        );
                        scheduleReconnect("connect_failed", traceId);
                        return;
                    }
                    this.webSocket = ws;
                    this.wsConnected.set(1);
                    this.lastMessageEpochMs.set(Instant.now(clock).toEpochMilli());
                    if (lastReconnectEpochMs.get() > 0L || reconnectAttempt.get() > 0) {
                        lastReconnectEpochMs.set(Instant.now(clock).toEpochMilli());
                    }
                    reconnectAttempt.set(0);
                    reconnectScheduled.set(false);
                    log.info(
                            "binance_ws_connected trace_id={} reason={} ws_url={} listen_key_suffix={}",
                            traceId,
                            reason,
                            runtimeConfig.wsUrl(),
                            maskListenKey(listenKey)
                    );
                    notifyConnected(reason, traceId);
                    if (!"start".equals(reason)) {
                        log.info("binance_ws_session_rebuilt trace_id={} reason={} listen_key_suffix={}", traceId, reason, maskListenKey(listenKey));
                    }
                }, callbackExecutor);
    }

    /**
     * 通过官方 `ws-api` 建立用户数据流订阅。
     * <p>
     * Why:
     * Binance 现行文档要求通过 WebSocket API 的 `userDataStream.subscribe.signature` 建立用户流。
     * 这里只修复连通性和原始消息前提，不触碰 BW2/BW3 的业务边界。
     */
    private void connectWsApi(String reason, String traceId) {
        if (!running.get()) {
            return;
        }
        String wsApiUrl = BinanceWsProtocol.resolveUserDataWsApiUrl(runtimeConfig.wsUrl());
        this.currentListenKey = "";
        this.currentSubscriptionId = null;
        this.pendingSubscriptionRequestId = null;
        this.pendingSessionSubscriptionsRequestId = null;
        this.subscriptionState = SubscriptionState.IDLE;
        this.subscriptionPendingStartedAtEpochMs = 0L;
        webSocketConnector.connect(URI.create(wsApiUrl), new ListenerImpl(SessionMode.WS_API_SIGNATURE, reason, traceId))
                .whenCompleteAsync((ws, throwable) -> {
                    if (throwable != null) {
                        log.warn(
                                "binance_ws_connect_failed trace_id={} reason={} attempt={}",
                                traceId,
                                throwable.getMessage(),
                                reconnectAttempt.get()
                        );
                        scheduleReconnect("connect_failed", traceId);
                        return;
                    }
                    this.webSocket = ws;
                }, callbackExecutor);
    }

    private void refreshListenKeySafely() {
        if (!running.get()) {
            return;
        }
        if (sessionMode == SessionMode.WS_API_SIGNATURE) {
            return;
        }
        String listenKey = currentListenKey;
        if (listenKey == null || listenKey.isBlank()) {
            return;
        }
        String traceId = newTraceId();
        MDC.put("trace_id", traceId);
        try {
            listenKeyClient.refreshListenKey(listenKey, traceId);
            listenKeyRefreshSuccessCount.incrementAndGet();
            log.debug("binance_ws_listenkey_refresh_success trace_id={} listen_key_suffix={}", traceId, maskListenKey(listenKey));
        } catch (BinanceApiException ex) {
            listenKeyRefreshFailCount.incrementAndGet();
            notifyListenKeyExpired(ex.errorCode(), ex.errorMessage(), traceId);
            log.warn(
                    "binance_ws_listenkey_refresh_failed trace_id={} error_code={} reason={} listen_key_suffix={}",
                    traceId,
                    ex.errorCode(),
                    ex.errorMessage(),
                    maskListenKey(listenKey)
            );
            scheduleReconnect("listenkey_refresh_failed", traceId);
        } finally {
            MDC.remove("trace_id");
        }
    }

    private void heartbeatCheckSafely() {
        if (!running.get() || wsConnected.get() != 1) {
            return;
        }
        long lastMessage = lastMessageEpochMs.get();
        long now = Instant.now(clock).toEpochMilli();
        long ageMs = lastMessage <= 0 ? Long.MAX_VALUE : Math.max(0L, now - lastMessage);
        long staleThresholdMs = Math.max(
                runtimeConfig.wsHeartbeat().toMillis() * 3,
                runtimeConfig.wsHeartbeat().toMillis() + 5_000L
        );
        if (ageMs > staleThresholdMs) {
            log.warn("binance_ws_heartbeat_stale last_msg_age_ms={} threshold_ms={}", ageMs, staleThresholdMs);
            scheduleReconnect("heartbeat_stale");
            return;
        }
        WebSocket local = this.webSocket;
        if (local == null) {
            scheduleReconnect("heartbeat_socket_missing");
            return;
        }
        try {
            local.sendPing(ByteBuffer.wrap(new byte[]{0x1}));
            clientPingSent = true;
            rememberLifecycleEvent("client_ping_sent payload_hex=01");
        } catch (Exception ex) {
            log.warn("binance_ws_ping_failed reason={}", ex.getMessage());
            scheduleReconnect("ping_failed");
        }
    }

    private void scheduleReconnect(String reason) {
        scheduleReconnect(reason, newTraceId());
    }

    private void scheduleReconnect(String reason, String traceId) {
        if (!running.get() || !reconnectScheduled.compareAndSet(false, true)) {
            return;
        }
        wsConnected.set(0);
        rememberLifecycleEvent("reconnect_scheduled reason=" + reason + " trace_id=" + traceId);
        closeSocketQuietly("reconnect");
        int attempt = reconnectAttempt.incrementAndGet();
        long delayMs = BinanceWsProtocol.reconnectDelayMs(
                attempt,
                runtimeConfig.wsReconnectBase().toMillis(),
                runtimeConfig.wsReconnectMax().toMillis()
        );
        reconnectCount.incrementAndGet();
        lastReconnectEpochMs.set(Instant.now(clock).toEpochMilli());
        log.warn(
                "binance_ws_reconnect_scheduled trace_id={} reason={} attempt={} delay_ms={} reconnect_count={}",
                traceId,
                reason,
                attempt,
                delayMs,
                reconnectCount.get()
        );
        notifyDisconnected(reason, attempt, delayMs, traceId);
        scheduler.schedule(() -> {
            reconnectScheduled.set(false);
            rebuildSession("reconnect_" + reason);
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private void closeSocketQuietly(String reason) {
        WebSocket local = this.webSocket;
        this.webSocket = null;
        if (local == null) {
            return;
        }
        this.lastLocalLifecycleAction = reason;
        this.localCloseSent = true;
        rememberLifecycleEvent("local_close_requested reason=" + reason);
        try {
            local.sendClose(WebSocket.NORMAL_CLOSURE, reason).join();
        } catch (Exception ex) {
            log.warn("binance_ws_close_failed reason={} error={}", reason, ex.getMessage());
        }
    }

    private void closeListenKeyQuietly(String listenKey, String reason) {
        if (sessionMode == SessionMode.WS_API_SIGNATURE || listenKey == null || listenKey.isBlank()) {
            return;
        }
        String traceId = newTraceId();
        MDC.put("trace_id", traceId);
        try {
            listenKeyClient.closeListenKey(listenKey, traceId);
            log.debug(
                    "binance_ws_listenkey_closed trace_id={} reason={} listen_key_suffix={}",
                    traceId,
                    reason,
                    maskListenKey(listenKey)
            );
        } catch (Exception ex) {
            log.warn(
                    "binance_ws_listenkey_close_failed trace_id={} reason={} listen_key_suffix={} error={}",
                    traceId,
                    reason,
                    maskListenKey(listenKey),
                    ex.getMessage()
            );
        } finally {
            MDC.remove("trace_id");
        }
    }

    private void sendWsApiSubscription(WebSocket webSocket, String reason, String traceId) {
        try {
            LinkedHashMap<String, String> signedParams = new LinkedHashMap<>();
            signedParams.put("apiKey", runtimeConfig.credentials().apiKey());
            signedParams.put("timestamp", String.valueOf(Instant.now(clock).toEpochMilli()));
            String signaturePayload = buildWsApiSignaturePayload(signedParams);
            String signature = requestSigner.sign(signaturePayload, runtimeConfig.credentials());
            long requestId = nextWsApiRequestId();
            ObjectNode params = objectMapper.createObjectNode();
            params.put("apiKey", signedParams.get("apiKey"));
            params.put("timestamp", Long.parseLong(signedParams.get("timestamp")));
            params.put("signature", signature);
            ObjectNode request = objectMapper.createObjectNode();
            request.put("id", requestId);
            request.put("method", "userDataStream.subscribe.signature");
            request.set("params", params);
            pendingSubscriptionRequestId = String.valueOf(requestId);
            subscriptionState = SubscriptionState.PENDING;
            subscriptionPendingStartedAtEpochMs = Instant.now(clock).toEpochMilli();
            lastSubscribeRequestSummary = buildDiagnosticSubscribeSummary(request);
            lastSubscribeSignaturePayload = signaturePayload;
            if (runtimeConfig.wsDiagnosticEnabled()) {
                log.debug(
                        "binance_ws_subscribe_request_summary trace_id={} request={} signature_payload={} recv_window={}",
                        traceId,
                        lastSubscribeRequestSummary,
                        maskSignaturePayload(signaturePayload),
                        params.has("recvWindow") ? params.path("recvWindow").asText() : "absent"
                );
            }
            // Why:
            // 独立探针使用“先 request(1)，再同步发送订阅帧”的顺序可以稳定收到首个 200 控制响应。
            // 这里显式等待 sendText 完成，避免 onOpen 返回过快导致订阅帧仍在排队时服务端已经给出 close。
            webSocket.sendText(objectMapper.writeValueAsString(request), true).join();
            log.debug("binance_ws_subscribe_sent trace_id={} reason={} request_id={}", traceId, reason, requestId);
            scheduler.schedule(
                    () -> timeoutPendingSubscription(String.valueOf(requestId), traceId),
                    runtimeConfig.timeout().toMillis(),
                    TimeUnit.MILLISECONDS
            );
        } catch (Exception ex) {
            log.warn("binance_ws_subscribe_prepare_failed trace_id={} reason={}", traceId, ex.getMessage());
            scheduleReconnect("subscribe_prepare_failed", traceId);
        }
    }

    private boolean handleWsApiControlFrame(String raw, String reason, String traceId) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (root.has("event")) {
                return false;
            }
            if (log.isDebugEnabled()) {
                log.debug("binance_ws_control_frame trace_id={} raw={}", traceId, raw);
            }
            if (root.hasNonNull("status") && root.hasNonNull("id")) {
                String responseId = root.path("id").asText("");
                int status = root.path("status").asInt(0);
                if (pendingSubscriptionRequestId != null && pendingSubscriptionRequestId.equals(responseId)) {
                    if (status == 200 && root.path("result").has("subscriptionId")) {
                        currentSubscriptionId = root.path("result").path("subscriptionId").asInt();
                        pendingSubscriptionRequestId = null;
                        subscriptionState = SubscriptionState.CONFIRMED;
                        subscriptionPendingStartedAtEpochMs = 0L;
                        wsConnected.set(1);
                        lastMessageEpochMs.set(Instant.now(clock).toEpochMilli());
                        if (lastReconnectEpochMs.get() > 0L || reconnectAttempt.get() > 0) {
                            lastReconnectEpochMs.set(Instant.now(clock).toEpochMilli());
                        }
                        reconnectAttempt.set(0);
                        reconnectScheduled.set(false);
                        log.info(
                                "binance_ws_connected trace_id={} reason={} ws_url={} subscription_id={}",
                                traceId,
                                reason,
                                BinanceWsProtocol.resolveUserDataWsApiUrl(runtimeConfig.wsUrl()),
                                currentSubscriptionId
                        );
                        notifyConnected(reason, traceId);
                        sendSessionSubscriptionsQuery(webSocket, traceId);
                        if (!"start".equals(reason)) {
                            log.info(
                                    "binance_ws_session_rebuilt trace_id={} reason={} subscription_id={}",
                                    traceId,
                                    reason,
                                    currentSubscriptionId
                            );
                        }
                    } else {
                        String errorCode = root.path("error").path("code").asText(String.valueOf(status));
                        String errorMessage = root.path("error").path("msg").asText("subscription failed");
                        log.warn(
                                "binance_ws_subscribe_failed trace_id={} status={} error_code={} reason={}",
                                traceId,
                                status,
                                errorCode,
                                errorMessage
                        );
                        scheduleReconnect("subscribe_failed", traceId);
                    }
                    return true;
                }
                if (pendingSessionSubscriptionsRequestId != null && pendingSessionSubscriptionsRequestId.equals(responseId)) {
                    pendingSessionSubscriptionsRequestId = null;
                    boolean confirmed = false;
                    for (JsonNode item : root.path("result")) {
                        if (item.path("subscriptionId").asInt(Integer.MIN_VALUE) == currentSubscriptionId) {
                            confirmed = true;
                            break;
                        }
                    }
                    log.debug(
                            "binance_ws_session_subscriptions_checked trace_id={} subscription_id={} confirmed={}",
                            traceId,
                            currentSubscriptionId,
                            confirmed
                    );
                    return true;
                }
            }
        } catch (Exception ignored) {
            // Why:
            // ws-api 下的业务事件与控制响应都走文本帧；控制帧解析失败时必须把机会留给原始消息桥接，不能提前吞掉业务事件。
        }
        return false;
    }

    private void sendSessionSubscriptionsQuery(WebSocket webSocket, String traceId) {
        try {
            long requestId = nextWsApiRequestId();
            ObjectNode request = objectMapper.createObjectNode();
            request.put("id", requestId);
            request.put("method", "session.subscriptions");
            request.set("params", objectMapper.createObjectNode());
            pendingSessionSubscriptionsRequestId = String.valueOf(requestId);
            webSocket.sendText(objectMapper.writeValueAsString(request), true).join();
            log.debug(
                    "binance_ws_session_subscriptions_sent trace_id={} request_id={} subscription_id={}",
                    traceId,
                    requestId,
                    currentSubscriptionId
            );
        } catch (Exception ex) {
            log.warn("binance_ws_session_subscriptions_send_failed trace_id={} reason={}", traceId, ex.getMessage());
        }
    }

    private void timeoutPendingSubscription(String requestId, String traceId) {
        if (!running.get()) {
            return;
        }
        if (subscriptionState == SubscriptionState.PENDING
                && pendingSubscriptionRequestId != null
                && pendingSubscriptionRequestId.equals(requestId)) {
            log.warn(
                    "binance_ws_subscribe_timeout trace_id={} pending_ms={}",
                    traceId,
                    Math.max(0L, Instant.now(clock).toEpochMilli() - subscriptionPendingStartedAtEpochMs)
            );
            scheduleReconnect("subscribe_timeout", traceId);
        }
    }

    private String newTraceId() {
        return "trc-binance-ws-" + UUID.randomUUID();
    }

    /**
     * 生成 Binance ws-api 控制请求 id。
     * <p>
     * Why:
     * Binance ws-api 明确限制 `id` 只能是整数、`null` 或长度不超过 36 的短字符串。
     * 本轮修复目标就是收敛到最稳妥的整数 id，避免再次触发 `-1135 Invalid 'id' in JSON request`。
     */
    private long nextWsApiRequestId() {
        return wsRequestIdSequence.incrementAndGet();
    }

    private String maskListenKey(String listenKey) {
        if (listenKey == null || listenKey.isBlank()) {
            return "missing";
        }
        if (listenKey.length() <= 8) {
            return listenKey.charAt(0) + "***" + listenKey.charAt(listenKey.length() - 1);
        }
        return listenKey.substring(0, 4) + "..." + listenKey.substring(listenKey.length() - 4);
    }

    private void dispatchRawMessage(String raw) {
        if (rawMessageListeners.isEmpty()) {
            logInboundMessage(raw, null);
            return;
        }
        Instant receivedAt = Instant.now(clock);
        JsonNode payload = null;
        String eventType = "UNKNOWN";
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode candidatePayload = root.path("event");
            payload = candidatePayload.isMissingNode() || candidatePayload.isNull() ? root : candidatePayload;
            String parsedEventType = payload.path("e").asText("");
            if (parsedEventType != null && !parsedEventType.isBlank()) {
                eventType = parsedEventType;
            }
        } catch (Exception ignored) {
            // Why:
            // BW2 要求解析失败也要进入审计路径；这里故意不吞掉 raw 文本，而是把 payload 置空交给 bridge 记证据。
        }
        logInboundMessage(raw, eventType);
        BinanceWsRawMessage message = new BinanceWsRawMessage(eventType, payload, raw, receivedAt);
        for (BinanceWsRawMessageListener listener : rawMessageListeners) {
            try {
                listener.onMessage(message);
            } catch (Exception ex) {
                log.warn("binance_ws_listener_failed event_type={} reason={}", eventType, ex.getMessage());
            }
        }
    }

    private void logInboundMessage(String raw, String eventType) {
        if (eventType != null && !"UNKNOWN".equals(eventType)) {
            log.debug("binance_ws_message event_type={}", eventType);
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode candidatePayload = root.path("event");
            JsonNode payload = candidatePayload.isMissingNode() || candidatePayload.isNull() ? root : candidatePayload;
            String parsedEventType = payload.path("e").asText("");
            if (!parsedEventType.isBlank()) {
                log.debug("binance_ws_message event_type={}", parsedEventType);
            } else {
                log.debug("binance_ws_message_unparsed");
            }
        } catch (Exception ignored) {
            log.debug("binance_ws_message_unparsed");
        }
    }

    private void notifyConnected(String reason, String traceId) {
        for (BinanceWsConnectionListener listener : connectionListeners) {
            try {
                listener.onConnected(traceId);
                if (!"start".equals(reason)) {
                    listener.onReconnected(reason, traceId);
                }
            } catch (Exception ex) {
                log.warn("binance_ws_connection_listener_failed event=connected reason={}", ex.getMessage());
            }
        }
    }

    private void notifyDisconnected(String reason, int attempt, long delayMs, String traceId) {
        if (subscriptionState == SubscriptionState.PENDING) {
            log.debug(
                    "binance_ws_disconnect_suppressed trace_id={} reason={} state={} pending_ms={}",
                    traceId,
                    reason,
                    subscriptionState,
                    Math.max(0L, Instant.now(clock).toEpochMilli() - subscriptionPendingStartedAtEpochMs)
            );
            return;
        }
        for (BinanceWsConnectionListener listener : connectionListeners) {
            try {
                listener.onDisconnected(reason, attempt, delayMs, traceId);
            } catch (Exception ex) {
                log.warn("binance_ws_connection_listener_failed event=disconnected reason={}", ex.getMessage());
            }
        }
    }

    private void notifyListenKeyExpired(String errorCode, String reason, String traceId) {
        for (BinanceWsConnectionListener listener : connectionListeners) {
            try {
                listener.onListenKeyExpired(errorCode, reason, traceId);
            } catch (Exception ex) {
                log.warn("binance_ws_connection_listener_failed event=listenkey_expired reason={}", ex.getMessage());
            }
        }
    }

    /**
     * 构建 ws-api 签名原文。
     * <p>
     * Why:
     * Binance WebSocket API 的签名规则要求：
     * 1) 参数按名称字母序排序；
     * 2) 按 `key=value&...` 原文拼接；
     * 3) 使用 UTF-8 字节签名，而不是 REST 风格的 URL 编码 query。
     * 如果这里继续复用 REST 的 percent-encoding 逻辑，会在服务端侧被当成无效签名断开连接。
     */
    private String buildWsApiSignaturePayload(LinkedHashMap<String, String> params) {
        StringBuilder builder = new StringBuilder();
        params.entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> {
                    if (!builder.isEmpty()) {
                        builder.append('&');
                    }
                    builder.append(entry.getKey()).append('=').append(entry.getValue());
                });
        return builder.toString();
    }

    private void rememberInboundFrame(String raw) {
        synchronized (recentInboundFrames) {
            if (recentInboundFrames.size() == 3) {
                recentInboundFrames.removeFirst();
            }
            recentInboundFrames.addLast("TEXT " + maskSensitiveJson(raw));
        }
    }

    private String recentInboundFramesSnapshot() {
        synchronized (recentInboundFrames) {
            return recentInboundFrames.toString();
        }
    }

    private void rememberControlFrame(String frameType, ByteBuffer payload) {
        ByteBuffer copy = payload.slice();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        String description = frameType
                + " len=" + bytes.length
                + " hex=" + HexFormat.of().formatHex(bytes)
                + " utf8=" + sanitizeDiagnosticText(new String(bytes, StandardCharsets.UTF_8));
        synchronized (recentInboundFrames) {
            if (recentInboundFrames.size() == 3) {
                recentInboundFrames.removeFirst();
            }
            recentInboundFrames.addLast(description);
        }
    }

    private void rememberLifecycleEvent(String event) {
        synchronized (recentLifecycleEvents) {
            if (recentLifecycleEvents.size() == 6) {
                recentLifecycleEvents.removeFirst();
            }
            recentLifecycleEvents.addLast(event);
        }
    }

    private String recentLifecycleEventsSnapshot() {
        synchronized (recentLifecycleEvents) {
            return recentLifecycleEvents.toString();
        }
    }

    private String maskSensitiveJson(String raw) {
        return raw
                .replaceAll("(\"apiKey\"\\s*:\\s*\")[^\"]+(\"\\s*)", "$1***$2")
                .replaceAll("(\"signature\"\\s*:\\s*\")[^\"]+(\"\\s*)", "$1***$2");
    }

    private String buildDiagnosticSubscribeSummary(ObjectNode request) {
        ObjectNode masked = request.deepCopy();
        ObjectNode params = (ObjectNode) masked.path("params");
        if (params.has("apiKey")) {
            params.put("apiKey", maskApiKey(params.path("apiKey").asText("")));
        }
        if (params.has("signature")) {
            params.put("signature", maskSignature(params.path("signature").asText("")));
        }
        return masked.toString();
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "missing";
        }
        String trimmed = apiKey.trim();
        if (trimmed.length() <= 8) {
            return trimmed.charAt(0) + "***" + trimmed.charAt(trimmed.length() - 1);
        }
        return trimmed.substring(0, 4) + "..." + trimmed.substring(trimmed.length() - 4);
    }

    private String maskSignature(String signature) {
        if (signature == null || signature.isBlank()) {
            return "missing";
        }
        String trimmed = signature.trim();
        String prefix = trimmed.substring(0, Math.min(6, trimmed.length()));
        String suffix = trimmed.substring(Math.max(0, trimmed.length() - 6));
        return prefix + "..." + suffix + "#sha256=" + sha256Hex(trimmed);
    }

    private String maskSignaturePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return "missing";
        }
        String apiKey = runtimeConfig.credentials().apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return payload;
        }
        return payload.replace(apiKey, maskApiKey(apiKey));
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return "sha256-unavailable";
        }
    }

    private String sanitizeDiagnosticText(String value) {
        return value == null ? "" : value.replace("\r", "\\r").replace("\n", "\\n");
    }

    BinanceWsDiagnosticSnapshot diagnosticSnapshot() {
        return new BinanceWsDiagnosticSnapshot(
                lastSubscribeRequestSummary,
                maskSignaturePayload(lastSubscribeSignaturePayload),
                recentInboundFramesSnapshot(),
                recentLifecycleEventsSnapshot(),
                lastCloseSummary,
                serverPingObserved,
                serverPongObserved,
                clientPingSent,
                clientPongSent,
                localCloseSent
        );
    }

    private final class ListenerImpl implements WebSocket.Listener {

        private final SessionMode mode;
        private final String connectReason;
        private final String connectTraceId;

        private ListenerImpl(SessionMode mode, String connectReason, String connectTraceId) {
            this.mode = mode;
            this.connectReason = connectReason;
            this.connectTraceId = connectTraceId;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            WebSocket.Listener.super.onOpen(webSocket);
            // Why:
            // JDK WebSocket.Listener 只有在 request(n) 后才会继续投递后续帧。
            // 这里先声明对首帧响应的消费能力，再同步发送 subscribe.signature，避免服务端极快返回 200/错误帧时客户端尚未准备接收。
            webSocket.request(1);
            rememberLifecycleEvent("transport_open mode=" + mode + " reason=" + connectReason);
            if (mode == SessionMode.WS_API_SIGNATURE) {
                BinanceWsClient.this.webSocket = webSocket;
                log.debug(
                        "binance_ws_transport_connected trace_id={} reason={} ws_url={}",
                        connectTraceId,
                        connectReason,
                        BinanceWsProtocol.resolveUserDataWsApiUrl(runtimeConfig.wsUrl())
                );
                // Why:
                // 独立 Java 探针验证了“onOpen 后立即发送 subscribe.signature”可以稳定拿到 200。
                // 这里与探针保持相同顺序，避免 buildAsync/whenComplete 的额外线程切换引入会话建立时序问题。
                sendWsApiSubscription(webSocket, connectReason, connectTraceId);
            }
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            String raw = data.toString();
            lastMessageEpochMs.set(Instant.now(clock).toEpochMilli());
            rememberInboundFrame(raw);
            if (mode == SessionMode.WS_API_SIGNATURE && handleWsApiControlFrame(raw, connectReason, connectTraceId)) {
                webSocket.request(1);
                return CompletableFuture.completedFuture(null);
            }
            dispatchRawMessage(raw);
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
            lastMessageEpochMs.set(Instant.now(clock).toEpochMilli());
            serverPingObserved = true;
            clientPongSent = true;
            rememberControlFrame("PING", message);
            ByteBuffer copy = ByteBuffer.allocate(message.remaining());
            copy.put(message.slice());
            copy.flip();
            webSocket.sendPong(copy);
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
            lastMessageEpochMs.set(Instant.now(clock).toEpochMilli());
            serverPongObserved = true;
            rememberControlFrame("PONG", message);
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("binance_ws_error reason={}", error.getMessage());
            scheduleReconnect("listener_error");
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            lastCloseSummary = "code=" + statusCode
                    + ", reason=" + sanitizeDiagnosticText(reason)
                    + ", payload_hex=" + HexFormat.of().formatHex(reason.getBytes(StandardCharsets.UTF_8));
            rememberLifecycleEvent("remote_close " + lastCloseSummary);
            boolean localLifecycleClose = localCloseSent
                    && ("stop".equals(lastLocalLifecycleAction) || "reconnect".equals(lastLocalLifecycleAction));
            if (localLifecycleClose) {
                log.info(
                        "binance_ws_closed_local status_code={} reason={} local_action={} payload_hex={}",
                        statusCode,
                        reason,
                        lastLocalLifecycleAction,
                        HexFormat.of().formatHex(reason.getBytes(StandardCharsets.UTF_8))
                );
            } else {
                log.warn(
                        "binance_ws_closed status_code={} reason={} payload_hex={} local_action={} local_close_sent={} "
                                + "recent_frames={} recent_lifecycle={}",
                        statusCode,
                        reason,
                        HexFormat.of().formatHex(reason.getBytes(StandardCharsets.UTF_8)),
                        lastLocalLifecycleAction,
                        localCloseSent,
                        runtimeConfig.wsDiagnosticEnabled() ? recentInboundFramesSnapshot() : "[diagnostic-disabled]",
                        runtimeConfig.wsDiagnosticEnabled() ? recentLifecycleEventsSnapshot() : "[diagnostic-disabled]"
                );
            }
            scheduleReconnect("listener_close");
            return CompletableFuture.completedFuture(null);
        }
    }

    @FunctionalInterface
    interface WebSocketConnector {
        CompletableFuture<WebSocket> connect(URI uri, WebSocket.Listener listener);
    }

    private static final class DefaultWebSocketConnector implements WebSocketConnector {

        private final HttpClient httpClient;

        private DefaultWebSocketConnector(HttpClient httpClient) {
            this.httpClient = httpClient;
        }

        @Override
        public CompletableFuture<WebSocket> connect(URI uri, WebSocket.Listener listener) {
            return httpClient.newWebSocketBuilder().buildAsync(uri, listener);
        }
    }

    private enum SessionMode {
        LEGACY_LISTEN_KEY,
        WS_API_SIGNATURE
    }

    private enum SubscriptionState {
        IDLE,
        PENDING,
        CONFIRMED
    }

    record BinanceWsDiagnosticSnapshot(
            String subscribeRequestSummary,
            String signaturePayload,
            String recentFrames,
            String recentLifecycleEvents,
            String lastCloseSummary,
            boolean serverPingObserved,
            boolean serverPongObserved,
            boolean clientPingSent,
            boolean clientPongSent,
            boolean localCloseSent
    ) {
    }
}
