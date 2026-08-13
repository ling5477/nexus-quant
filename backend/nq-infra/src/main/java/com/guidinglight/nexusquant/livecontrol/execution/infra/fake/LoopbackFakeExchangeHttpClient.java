package com.guidinglight.nexusquant.livecontrol.execution.infra.fake;

import com.guidinglight.nexusquant.livecontrol.execution.application.port.FakeExchangeMutationPort;
import com.guidinglight.nexusquant.livecontrol.execution.application.port.FakeExchangeQueryResult;
import com.guidinglight.nexusquant.livecontrol.execution.application.port.FakeExchangeResult;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntent;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Disposable fake venue transport。只允许明文 loopback HTTP，禁止 DNS、redirect 与 fallback。
 */
public final class LoopbackFakeExchangeHttpClient implements FakeExchangeMutationPort {

    private static final int MAX_RESPONSE_BYTES = 16 * 1024;
    private final URI endpoint;
    private final Duration requestTimeout;
    private final HttpClient client;

    public LoopbackFakeExchangeHttpClient(URI endpoint, Duration connectTimeout, Duration requestTimeout) {
        this.endpoint = requireLoopback(endpoint);
        this.requestTimeout = requireBounded(requestTimeout, "requestTimeout");
        this.client = HttpClient.newBuilder()
                .connectTimeout(requireBounded(connectTimeout, "connectTimeout"))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public FakeExchangeResult place(ExecutionIntent intent) {
        return mutate("/v1/fake/place", intent);
    }

    @Override
    public FakeExchangeResult cancel(ExecutionIntent intent) {
        return mutate("/v1/fake/cancel", intent);
    }

    @Override
    public FakeExchangeQueryResult queryByClientOrderId(String clientOrderId) {
        Map<String, String> response = send("/v1/fake/query", Map.of("clientOrderId", requireText(clientOrderId)));
        return new FakeExchangeQueryResult(
                FakeExchangeQueryResult.Status.valueOf(required(response, "status")),
                nullable(response, "exchangeRequestId"), nullable(response, "exchangeOrderId"),
                nullable(response, "errorCategory"), nullable(response, "errorCode"));
    }

    private FakeExchangeResult mutate(String path, ExecutionIntent intent) {
        Objects.requireNonNull(intent, "intent must not be null");
        Map<String, String> request = new LinkedHashMap<>();
        request.put("clientOrderId", intent.clientOrderId());
        request.put("intentId", intent.intentId().toString());
        request.put("action", intent.action().name());
        request.put("symbol", intent.symbol());
        request.put("localOrderId", intent.localOrderId());
        Map<String, String> response = send(path, request);
        return new FakeExchangeResult(
                FakeExchangeResult.Outcome.valueOf(required(response, "outcome")),
                nullable(response, "exchangeRequestId"), nullable(response, "exchangeOrderId"),
                nullable(response, "errorCategory"), nullable(response, "errorCode"));
    }

    private Map<String, String> send(String path, Map<String, String> values) {
        String body = values.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder(endpoint.resolve(path))
                .timeout(requestTimeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200 || response.body().length > MAX_RESPONSE_BYTES) {
                throw new IllegalStateException("fake venue returned invalid bounded response");
            }
            return parse(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("fake venue call interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("fake venue loopback call failed", exception);
        }
    }

    private static Map<String, String> parse(byte[] body) {
        Map<String, String> values = new LinkedHashMap<>();
        String text = new String(body, StandardCharsets.UTF_8);
        for (String line : text.split("\\R")) {
            if (line.isBlank()) continue;
            int separator = line.indexOf('=');
            if (separator < 1 || values.putIfAbsent(line.substring(0, separator),
                    line.substring(separator + 1)) != null) {
                throw new IllegalStateException("fake venue response is malformed");
            }
        }
        return values;
    }

    private static URI requireLoopback(URI value) {
        Objects.requireNonNull(value, "endpoint must not be null");
        if (!"http".equals(value.getScheme()) || !"127.0.0.1".equals(value.getHost())
                || value.getPort() < 1024 || value.getPort() > 65535
                || value.getUserInfo() != null || value.getQuery() != null || value.getFragment() != null) {
            throw new IllegalArgumentException("fake venue endpoint must be explicit loopback HTTP");
        }
        return URI.create("http://127.0.0.1:" + value.getPort() + "/");
    }

    private static Duration requireBounded(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative() || value.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException(name + " must be within (0,30s]");
        }
        return value;
    }

    private static String encode(String value) {
        return URLEncoder.encode(requireText(value), StandardCharsets.UTF_8);
    }

    private static String required(Map<String, String> values, String key) {
        return requireText(values.get(key));
    }

    private static String nullable(Map<String, String> values, String key) {
        String value = values.get(key);
        return value == null || value.equals("-") ? null : requireText(value);
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank() || value.length() > 512) {
            throw new IllegalArgumentException("bounded non-blank text is required");
        }
        return value.trim();
    }
}
