package com.guidinglight.nexusquant.adapter.binance.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceApiCredentials;
import com.guidinglight.nexusquant.adapter.binance.service.BinanceApiException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * BinanceListenKeyClient 负责 Binance 用户数据流 listenKey 的创建、续期与关闭。
 * <p>
 * Why:
 * listenKey 生命周期是 Binance 私有 WS 的前置条件，但它既不是 TradingAdapter，也不是业务事件映射。
 * 把它单独抽成 client，可以确保 PR-BW1 只处理连接治理，不把 listenKey 细节泄漏到上层。
 */
public class BinanceListenKeyClient {

    private static final String USER_DATA_STREAM_ENDPOINT = "/api/v3/userDataStream";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final Duration timeout;
    private final BinanceApiCredentials credentials;

    public BinanceListenKeyClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String baseUrl,
            Duration timeout,
            BinanceApiCredentials credentials
    ) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        this.credentials = Objects.requireNonNull(credentials, "credentials must not be null");
    }

    /**
     * 创建新的 listenKey。
     * <p>
     * Why:
     * Binance 用户数据流的 WS URL 依赖 listenKey；若这里失败，连接层必须停止推进，等待后续重连退避重新申请。
     *
     * @param traceId 本次创建调用的追踪 ID
     * @return 可用于用户数据流连接的 listenKey
     */
    public String createListenKey(String traceId) {
        JsonNode payload = send("POST", Map.of(), traceId);
        String listenKey = payload.path("listenKey").asText("");
        if (listenKey.isBlank()) {
            throw new BinanceApiException(
                    "Binance listenKey create response missing listenKey, trace_id=" + traceId,
                    200,
                    USER_DATA_STREAM_ENDPOINT,
                    "BINANCE_LISTENKEY_MISSING",
                    "listenKey missing",
                    traceId
            );
        }
        return listenKey;
    }

    /**
     * 续期现有 listenKey。
     * <p>
     * Why:
     * Binance 用户数据流会在固定时间窗口后失效；PR-BW1 要求连接治理层自行续期，不能等业务层发现消息中断再补救。
     *
     * @param listenKey 当前有效的 listenKey
     * @param traceId   本次续期调用的追踪 ID
     */
    public void refreshListenKey(String listenKey, String traceId) {
        validateListenKey(listenKey);
        send("PUT", Map.of("listenKey", listenKey), traceId);
    }

    /**
     * 关闭当前 listenKey。
     * <p>
     * Why:
     * PR-BW1 要求 stop/close 优雅，不留下长期悬挂的用户数据流会话；关闭 listenKey 是资源释放的一部分。
     *
     * @param listenKey 当前有效的 listenKey
     * @param traceId   本次关闭调用的追踪 ID
     */
    public void closeListenKey(String listenKey, String traceId) {
        validateListenKey(listenKey);
        send("DELETE", Map.of("listenKey", listenKey), traceId);
    }

    private JsonNode send(String method, Map<String, String> queryParams, String traceId) {
        ensureApiKeyConfigured(traceId);
        String endpoint = USER_DATA_STREAM_ENDPOINT;
        try {
            HttpRequest request = buildRequest(method, queryParams, traceId);
            endpoint = request.uri().getPath();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode payload = parseBody(response.body(), response.statusCode(), endpoint, traceId);
            if (response.statusCode() >= 400) {
                throw buildApiException(response.statusCode(), endpoint, traceId, payload);
            }
            return payload;
        } catch (HttpTimeoutException ex) {
            throw new BinanceApiException(
                    "Binance listenKey request timed out, endpoint=" + endpoint + ", trace_id=" + traceId,
                    0,
                    endpoint,
                    "HTTP_TIMEOUT",
                    "request timed out",
                    traceId,
                    ex
            );
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BinanceApiException(
                    "Binance listenKey request failed, endpoint=" + endpoint + ", trace_id=" + traceId + ", reason=" + ex.getMessage(),
                    0,
                    endpoint,
                    "HTTP_CLIENT_ERROR",
                    ex.getMessage(),
                    traceId,
                    ex
            );
        }
    }

    private HttpRequest buildRequest(String method, Map<String, String> queryParams, String traceId) {
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        normalized.putAll(queryParams);
        String encodedQuery = toEncodedQuery(normalized);
        String requestUri = baseUrl + USER_DATA_STREAM_ENDPOINT + (encodedQuery.isBlank() ? "" : "?" + encodedQuery);
        return HttpRequest.newBuilder()
                .uri(URI.create(requestUri))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("X-NQ-TRACE-ID", traceId == null ? "" : traceId)
                .header("X-MBX-APIKEY", credentials.apiKey())
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
    }

    private JsonNode parseBody(String body, int statusCode, String endpoint, String traceId) {
        try {
            if (body == null || body.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(body);
        } catch (IOException ex) {
            throw new BinanceApiException(
                    "failed to parse Binance listenKey response, status=" + statusCode + ", endpoint=" + endpoint + ", trace_id=" + traceId,
                    statusCode,
                    endpoint,
                    "INVALID_JSON",
                    "invalid json response",
                    traceId,
                    ex
            );
        }
    }

    private BinanceApiException buildApiException(int httpStatus, String endpoint, String traceId, JsonNode payload) {
        String code = payload.path("code").asText("UNKNOWN");
        String message = payload.path("msg").asText("unknown Binance error");
        return new BinanceApiException(
                "Binance listenKey request failed, status=" + httpStatus
                        + ", error_code=" + code
                        + ", endpoint=" + endpoint
                        + ", trace_id=" + traceId,
                httpStatus,
                endpoint,
                code,
                message,
                traceId
        );
    }

    private void ensureApiKeyConfigured(String traceId) {
        if (credentials.apiKey() == null || credentials.apiKey().isBlank()) {
            throw new BinanceApiException(
                    "Binance apiKey missing for listenKey call, trace_id=" + traceId,
                    0,
                    USER_DATA_STREAM_ENDPOINT,
                    "BINANCE_CREDENTIALS_MISSING",
                    "apiKey missing",
                    traceId
            );
        }
    }

    private void validateListenKey(String listenKey) {
        if (listenKey == null || listenKey.isBlank()) {
            throw new IllegalArgumentException("listenKey must not be blank");
        }
    }

    private String toEncodedQuery(LinkedHashMap<String, String> params) {
        if (params.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!builder.isEmpty()) {
                builder.append('&');
            }
            builder.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return builder.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
