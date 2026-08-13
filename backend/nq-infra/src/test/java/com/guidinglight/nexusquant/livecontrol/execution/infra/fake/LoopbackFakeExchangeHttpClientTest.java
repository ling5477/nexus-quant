package com.guidinglight.nexusquant.livecontrol.execution.infra.fake;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoopbackFakeExchangeHttpClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void permitsOnlyExplicitIpv4LoopbackWithoutUrlSmuggling() {
        Duration timeout = Duration.ofSeconds(1);
        assertThrows(IllegalArgumentException.class, () -> client("https://127.0.0.1:18080/", timeout));
        assertThrows(IllegalArgumentException.class, () -> client("http://localhost:18080/", timeout));
        assertThrows(IllegalArgumentException.class, () -> client("http://127.0.0.2:18080/", timeout));
        assertThrows(IllegalArgumentException.class, () -> client("http://user@127.0.0.1:18080/", timeout));
        assertThrows(IllegalArgumentException.class, () -> client("http://127.0.0.1:18080/?next=evil", timeout));
        assertThrows(IllegalArgumentException.class, () -> client("http://127.0.0.1:18080/#fragment", timeout));
        assertThrows(IllegalArgumentException.class,
                () -> new LoopbackFakeExchangeHttpClient(
                        URI.create("http://127.0.0.1:18080/"), timeout, Duration.ofSeconds(31)));
    }

    @Test
    void performsBoundedQueryOnlyCallAgainstLoopback() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/fake/query", exchange -> {
            byte[] request = exchange.getRequestBody().readAllBytes();
            assertEquals("clientOrderId=stable-client-order", new String(request, StandardCharsets.UTF_8));
            byte[] response = ("status=CONFIRMED\n"
                    + "exchangeRequestId=fake-query-1\n"
                    + "exchangeOrderId=fake-order-1\n"
                    + "errorCategory=FAKE_RECONCILIATION\n"
                    + "errorCode=-\n").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        var result = client("http://127.0.0.1:" + server.getAddress().getPort() + "/",
                Duration.ofSeconds(2)).queryByClientOrderId("stable-client-order");

        assertEquals("CONFIRMED", result.status().name());
        assertEquals("fake-order-1", result.exchangeOrderId());
    }

    private static LoopbackFakeExchangeHttpClient client(String endpoint, Duration timeout) {
        return new LoopbackFakeExchangeHttpClient(URI.create(endpoint), timeout, timeout);
    }
}
