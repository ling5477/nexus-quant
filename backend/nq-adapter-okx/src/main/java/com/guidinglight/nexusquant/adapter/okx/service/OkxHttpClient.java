package com.guidinglight.nexusquant.adapter.okx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.okx.model.OkxApiCredentials;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * OkxHttpClient 负责统一构建 OKX REST 请求、超时与错误解析。
 * <p>
 * Why:
 * GateC-1 要避免在 adapter 的每个 endpoint 方法里重复拼 header、重复处理 HTTP 错误、重复吞掉定位信息。
 * 把这些横切逻辑集中在这里，后续新增 endpoint 时才能复用同一排障口径。
 */
public class OkxHttpClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final Duration timeout;
    private final OkxRequestSigner signer;
    private final OkxTimestampProvider timestampProvider;
    private final OkxApiCredentials credentials;
    private final boolean authenticated;
    private final Map<String, String> staticHeaders;

    /**
     * 构造不持有 credential、signer 或 private header 能力的 public-only client。
     *
     * @param httpClient   Java HTTP client
     * @param objectMapper JSON 解析器
     * @param baseUrl      OKX public API origin
     * @param timeout      单次请求超时
     */
    public OkxHttpClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String baseUrl,
            Duration timeout
    ) {
        this(httpClient, objectMapper, baseUrl, timeout, null, null, null, false, Map.of());
    }

    /**
     * @param httpClient        Java HTTP client
     * @param objectMapper      JSON 解析器
     * @param baseUrl           OKX base URL
     * @param timeout           单次请求超时
     * @param signer            OKX signer
     * @param timestampProvider 时间戳提供器
     * @param credentials       OKX 凭证；public client 可传空占位
     * @param authenticated     true 表示自动附加 OKX 签名头
     */
    public OkxHttpClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String baseUrl,
            Duration timeout,
            OkxRequestSigner signer,
            OkxTimestampProvider timestampProvider,
            OkxApiCredentials credentials,
            boolean authenticated
    ) {
        this(httpClient, objectMapper, baseUrl, timeout, signer, timestampProvider, credentials, authenticated, Map.of());
    }

    /**
     * @param staticHeaders 固定附加头；用于模拟盘等运行时横切约束
     */
    public OkxHttpClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String baseUrl,
            Duration timeout,
            OkxRequestSigner signer,
            OkxTimestampProvider timestampProvider,
            OkxApiCredentials credentials,
            boolean authenticated,
            Map<String, String> staticHeaders
    ) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        if (authenticated) {
            this.signer = Objects.requireNonNull(signer, "signer must not be null for authenticated client");
            this.timestampProvider = Objects.requireNonNull(
                    timestampProvider,
                    "timestampProvider must not be null for authenticated client"
            );
        } else {
            this.signer = null;
            this.timestampProvider = null;
        }
        this.credentials = credentials;
        this.authenticated = authenticated;
        this.staticHeaders = staticHeaders == null ? Map.of() : Collections.unmodifiableMap(staticHeaders);
    }

    /**
     * 发起 GET 请求。
     */
    public JsonNode get(String requestPathWithQuery, String traceId) {
        return send("GET", requestPathWithQuery, "", traceId);
    }

    /**
     * 发起 POST 请求。
     */
    public JsonNode post(String requestPath, String requestBodyJson, String traceId) {
        return send("POST", requestPath, requestBodyJson == null ? "" : requestBodyJson, traceId);
    }

    /**
     * 发起请求并返回原始 JSON。
     *
     * @throws OkxApiException HTTP/解析/业务错误时抛出，包含 endpoint 与 traceId 等定位信息
     */
    public JsonNode send(String method, String requestPathWithQuery, String requestBodyJson, String traceId) {
        try {
            HttpRequest request = buildRequest(method, requestPathWithQuery, requestBodyJson, traceId);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode payload = parseBody(response.body(), response.statusCode(), requestPathWithQuery, traceId);
            if (response.statusCode() >= 400) {
                throw new OkxApiException(
                        buildErrorMessage(response.statusCode(), requestPathWithQuery, traceId, payload.path("code").asText(null)),
                        response.statusCode(),
                        requestPathWithQuery,
                        payload.path("code").asText(null),
                        traceId
                );
            }
            return payload;
        } catch (HttpTimeoutException ex) {
            throw new OkxApiException(
                    "OKX request timed out, endpoint=" + requestPathWithQuery + ", trace_id=" + traceId,
                    0,
                    requestPathWithQuery,
                    "HTTP_TIMEOUT",
                    traceId,
                    ex
            );
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new OkxApiException(
                    "OKX request failed, endpoint=" + requestPathWithQuery + ", trace_id=" + traceId + ", reason=" + ex.getMessage(),
                    0,
                    requestPathWithQuery,
                    "HTTP_CLIENT_ERROR",
                    traceId,
                    ex
            );
        }
    }

    private HttpRequest buildRequest(String method, String requestPathWithQuery, String requestBodyJson, String traceId) {
        HttpRequest.BodyPublisher bodyPublisher = "POST".equals(method)
                ? HttpRequest.BodyPublishers.ofString(requestBodyJson)
                : HttpRequest.BodyPublishers.noBody();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + requestPathWithQuery))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("X-NQ-TRACE-ID", traceId == null ? "" : traceId)
                .method(method, bodyPublisher);
        for (Map.Entry<String, String> header : staticHeaders.entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }
        if (authenticated) {
            if (credentials == null || !credentials.isConfigured()) {
                throw new OkxApiException(
                        "OKX credentials missing, endpoint=" + requestPathWithQuery + ", trace_id=" + traceId,
                        0,
                        requestPathWithQuery,
                        "OKX_CREDENTIALS_MISSING",
                        traceId
                );
            }
            String timestamp = timestampProvider.now();
            for (Map.Entry<String, String> header : signer.signHeaders(
                    credentials,
                    method,
                    requestPathWithQuery,
                    requestBodyJson,
                    timestamp
            ).entrySet()) {
                builder.header(header.getKey(), header.getValue());
            }
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
            throw new OkxApiException(
                    "failed to parse OKX response, status=" + statusCode + ", endpoint=" + endpoint + ", trace_id=" + traceId,
                    statusCode,
                    endpoint,
                    "INVALID_JSON",
                    traceId,
                    ex
            );
        }
    }

    private String buildErrorMessage(int statusCode, String endpoint, String traceId, String errorCode) {
        return "OKX request failed, status=" + statusCode
                + ", error_code=" + errorCode
                + ", endpoint=" + endpoint
                + ", trace_id=" + traceId;
    }
}
