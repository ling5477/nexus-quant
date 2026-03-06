package com.guidinglight.nexusquant.adapter.binance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceApiCredentials;

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
 * BinanceHttpClient 负责统一构建 Binance REST 请求、签名与错误解析。
 *
 * Why:
 * GateC-2 早期阶段只做基础设施，最容易出错的是 query 拼接、signature 附加和 code/msg 解析。
 * 这些横切约束集中在一个 client 中，后续 TradingAdapter 才能直接复用而不重复造轮子。
 */
public class BinanceHttpClient {

    private static final long DEFAULT_RECV_WINDOW_MS = 5_000L;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final Duration timeout;
    private final BinanceRequestSigner signer;
    private final BinanceTimestampProvider timestampProvider;
    private final BinanceApiCredentials credentials;

    public BinanceHttpClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String baseUrl,
            Duration timeout,
            BinanceRequestSigner signer,
            BinanceTimestampProvider timestampProvider,
            BinanceApiCredentials credentials
    ) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        this.signer = Objects.requireNonNull(signer, "signer must not be null");
        this.timestampProvider = Objects.requireNonNull(timestampProvider, "timestampProvider must not be null");
        this.credentials = credentials;
    }

    public JsonNode get(String requestPath, Map<String, ?> queryParams, boolean signed, String traceId) {
        return send("GET", requestPath, queryParams, signed, traceId);
    }

    public JsonNode post(String requestPath, Map<String, ?> queryParams, boolean signed, String traceId) {
        return send("POST", requestPath, queryParams, signed, traceId);
    }

    public JsonNode delete(String requestPath, Map<String, ?> queryParams, boolean signed, String traceId) {
        return send("DELETE", requestPath, queryParams, signed, traceId);
    }

    /**
     * 统一执行请求。
     *
     * Why:
     * 当前 PR 明确禁止盲重试，因此这里对 timeout / IO / 业务错误只做结构化抛错，不做任何补偿重试。
     */
    public JsonNode send(String method, String requestPath, Map<String, ?> queryParams, boolean signed, String traceId) {
        String endpoint = requestPath;
        try {
            HttpRequest request = buildRequest(method, requestPath, queryParams, signed, traceId);
            endpoint = request.uri().getPath();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode payload = parseBody(response.body(), response.statusCode(), endpoint, traceId);
            if (response.statusCode() >= 400) {
                throw buildApiException(response.statusCode(), endpoint, traceId, payload);
            }
            return payload;
        } catch (HttpTimeoutException ex) {
            throw new BinanceApiException(
                    "Binance request timed out, endpoint=" + endpoint + ", trace_id=" + traceId,
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
                    "Binance request failed, endpoint=" + endpoint + ", trace_id=" + traceId + ", reason=" + ex.getMessage(),
                    0,
                    endpoint,
                    "HTTP_CLIENT_ERROR",
                    ex.getMessage(),
                    traceId,
                    ex
            );
        }
    }

    private HttpRequest buildRequest(
            String method,
            String requestPath,
            Map<String, ?> queryParams,
            boolean signed,
            String traceId
    ) {
        String normalizedMethod = normalizeMethod(method);
        LinkedHashMap<String, String> params = normalizeQuery(queryParams);
        if (signed) {
            ensureCredentials(requestPath, traceId);
            params.putIfAbsent("timestamp", String.valueOf(timestampProvider.nowMillis()));
            params.putIfAbsent("recvWindow", String.valueOf(DEFAULT_RECV_WINDOW_MS));
        }
        String encodedQuery = toEncodedQuery(params);
        String finalQuery = encodedQuery;
        if (signed) {
            String signature = signer.sign(encodedQuery, credentials);
            finalQuery = encodedQuery.isBlank() ? "signature=" + signature : encodedQuery + "&signature=" + signature;
        }
        String requestUri = baseUrl + requestPath + (finalQuery.isBlank() ? "" : "?" + finalQuery);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(requestUri))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("X-NQ-TRACE-ID", traceId == null ? "" : traceId)
                .method(normalizedMethod, HttpRequest.BodyPublishers.noBody());
        if (signed) {
            builder.header("X-MBX-APIKEY", credentials.apiKey());
        }
        return builder.build();
    }

    private JsonNode parseBody(String body, int statusCode, String endpoint, String traceId) {
        try {
            if (body == null || body.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(body);
        } catch (IOException ex) {
            throw new BinanceApiException(
                    "failed to parse Binance response, status=" + statusCode + ", endpoint=" + endpoint + ", trace_id=" + traceId,
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
                "Binance request failed, status=" + httpStatus
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

    private LinkedHashMap<String, String> normalizeQuery(Map<String, ?> queryParams) {
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        if (queryParams == null) {
            return normalized;
        }
        for (Map.Entry<String, ?> entry : queryParams.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            normalized.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return normalized;
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

    private void ensureCredentials(String endpoint, String traceId) {
        if (credentials == null || !credentials.isConfigured()) {
            throw new BinanceApiException(
                    "Binance credentials missing, endpoint=" + endpoint + ", trace_id=" + traceId,
                    0,
                    endpoint,
                    "BINANCE_CREDENTIALS_MISSING",
                    "credentials missing",
                    traceId
            );
        }
    }

    private String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("method must not be blank");
        }
        return method.trim().toUpperCase();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
