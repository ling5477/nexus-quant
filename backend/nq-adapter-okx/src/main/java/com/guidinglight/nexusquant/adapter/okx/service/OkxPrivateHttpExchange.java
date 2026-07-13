package com.guidinglight.nexusquant.adapter.okx.service;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

/** package-private HTTP primitive；生产调用面仍由 typed transport 封闭。 */
@FunctionalInterface
interface OkxPrivateHttpExchange {

    Response get(URI uri, Map<String, String> headers, Duration timeout) throws IOException, InterruptedException;

    record Response(int statusCode, byte[] body) {
    }
}
