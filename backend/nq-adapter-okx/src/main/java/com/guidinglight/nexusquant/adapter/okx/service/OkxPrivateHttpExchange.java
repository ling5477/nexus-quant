package com.guidinglight.nexusquant.adapter.okx.service;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * package-private HTTP primitive；生产调用面仍由 typed transport 封闭。
 */
@FunctionalInterface
interface OkxPrivateHttpExchange {

    Response get(URI uri, Map<String, String> headers, Duration timeout) throws IOException, InterruptedException;

    default Response get(
            URI uri,
            Map<String, String> headers,
            Duration timeout,
            int maximumResponseBytes
    ) throws IOException, InterruptedException {
        return enforceLimit(get(uri, headers, timeout), maximumResponseBytes);
    }

    default Response post(
            URI uri,
            Map<String, String> headers,
            byte[] body,
            Duration timeout,
            int maximumResponseBytes
    ) throws IOException, InterruptedException {
        throw new IOException("POST is not supported by this exchange");
    }

    private static Response enforceLimit(Response response, int maximumResponseBytes) throws IOException {
        if (response == null || response.body() == null || response.body().length > maximumResponseBytes) {
            throw new ResponseLimitExceededIOException();
        }
        return response;
    }

    record Response(int statusCode, byte[] body) {
    }

    final class ResponseLimitExceededIOException extends IOException {
        ResponseLimitExceededIOException() {
            super("response exceeded configured byte limit");
        }
    }
}
