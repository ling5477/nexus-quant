package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** 独立只读端点使资源采样不等待stdin业务命令；无缓存、无后台第二时钟。 */
final class L6MetricsEndpoint implements AutoCloseable {
    private final HttpServer server;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.AbortPolicy());
    private final AtomicLong observations = new AtomicLong();
    private final boolean l6B = L6BContract.inRunDirectory();

    L6MetricsEndpoint(Supplier<ObjectNode> resources) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 1);
        server.setExecutor(executor);
        server.createContext("/metrics", exchange -> {
            try (exchange) {
                String token = exchange.getRequestHeaders().getFirst("X-L6-Sample");
                if (!exchange.getRequestMethod().equals("GET") || !exchange.getRequestURI().getPath().equals("/metrics")
                        || token == null || !token.matches(l6B
                                ? "[a-f0-9-]{36}:[0-9]{1,4}" : "[a-f0-9-]{36}:[0-9]{1,3}")) {
                    exchange.sendResponseHeaders(400, -1); return;
                }
                try {
                    ObjectNode result = resources.get().deepCopy();
                    result.put("sampleToken", token).put("observationSequence", observations.incrementAndGet());
                    result.set("metricsExecutor", L6Measurements.queue(executor));
                    byte[] bytes = new ObjectMapper().writeValueAsBytes(result);
                    exchange.getResponseHeaders().add("Cache-Control", "no-store");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } catch (RuntimeException error) {
                    byte[] bytes = error.toString().getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(503, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            }
        });
        try { server.start(); }
        catch (RuntimeException error) { server.stop(0); executor.shutdownNow(); throw error; }
    }

    String endpoint() { return "http://127.0.0.1:" + server.getAddress().getPort(); }

    void publish(Path path) throws Exception {
        Files.writeString(path, endpoint(), java.nio.file.StandardOpenOption.CREATE_NEW);
    }

    @Override public void close() throws Exception {
        server.stop(0);
        executor.shutdown();
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            executor.shutdownNow();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) throw new IllegalStateException("L6_METRICS_ENDPOINT_SURVIVOR");
        }
    }
}
