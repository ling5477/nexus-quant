package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.RejectedExecutionException;

/** 独立 venue-owned 内存事实；不链接 NQ DB，不实现任何本地 Order/Trade/Ledger 状态机。 */
public final class B0SyntheticVenueMain {
    static boolean boundedWorkload;
    private ThreadPoolExecutor boundedExecutor;
    private final AtomicInteger rejectedTasks = new AtomicInteger();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, ObjectNode> orders = new LinkedHashMap<>();
    private int placeCalls;
    private int fillQueries;
    private int delivered;
    private int dropped;
    private int delayed;
    private String mode = "DELIVER";
    private final ArrayNode events = mapper.createArrayNode();
    private final List<HttpExchange> withheld = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        B0Fixture.require(args.length == 0);
        var venue = new B0SyntheticVenueMain();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 16);
        server.createContext("/", venue::handle);
        if (boundedWorkload) {
            venue.boundedExecutor = new ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(16), (task, executor) -> {
                        venue.rejectedTasks.incrementAndGet();
                        throw new RejectedExecutionException("bounded venue queue exhausted");
                    });
            server.setExecutor(venue.boundedExecutor);
        } else {
            server.setExecutor(Executors.newFixedThreadPool(4));
        }
        server.start();
        System.out.println("B0_READY " + server.getAddress().getPort());
        System.out.flush();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
    }

    private synchronized void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        byte[] body = exchange.getRequestBody().readNBytes(8193);
        if (body.length > 8192 || exchange.getRequestHeaders().keySet().stream()
                .anyMatch(key -> key.toUpperCase(Locale.ROOT).startsWith("OK-ACCESS"))) {
            respond(exchange, 400, mapper.createObjectNode().put("error", "UNSAFE_REQUEST"));
            return;
        }
        if ("/control".equals(path)) {
            String requested = new String(body, StandardCharsets.UTF_8);
            if ("FILL_NEW".equals(requested) && boundedWorkload && "L5_OPEN".equals(mode)) {
                orders.values().stream().skip(60).filter(order -> !"filled".equals(order.path("state").asText())).forEach(order -> {
                    order.put("uTime", Long.toString(System.currentTimeMillis()));
                    order.put("state", "filled").put("accFillSz", order.path("sz").asText())
                            .put("avgPx", "100.00000000").put("fee", "-0.01000000");
                    event("FILL", order.path("clOrdId").asText()).put("tradeId", "b0-fill-" + order.path("ordId").asText());
                });
                respond(exchange, 200, mapper.createObjectNode().put("filledNew", true));
                return;
            }
            if ("FILL".equals(requested) && (mode.startsWith("B1_") || "L5_OPEN".equals(mode))) {
                orders.values().forEach(order -> {
                    // L5 成交时间取实际状态转换时间；已生成的成交事实不得在后续 FILL 中改写。
                    if (boundedWorkload && "filled".equals(order.path("state").asText())) return;
                    if (boundedWorkload) order.put("uTime", Long.toString(System.currentTimeMillis()));
                    order.put("state", "filled").put("accFillSz", order.path("sz").asText())
                            .put("avgPx", "100.00000000").put("fee", "-0.01000000");
                    event("FILL", order.path("clOrdId").asText()).put("tradeId", "b0-fill-" + order.path("ordId").asText());
                });
                respond(exchange, 200, mapper.createObjectNode().put("filled", orders.size()));
                return;
            }
            if ("RELEASE_LOST".equals(requested) && "B1_LOST_ACK".equals(mode)) {
                withheld.forEach(HttpExchange::close);
                withheld.clear();
                event("ACK_DROPPED", "");
                respond(exchange, 200, mapper.createObjectNode().put("released", true));
                return;
            }
            if (!Set.of("DELIVER", "DROP", "CLOSE", "DELAY", "B1_ACCEPTED_TIMEOUT", "B1_LOST_ACK", "L5_OPEN").contains(requested)) {
                respond(exchange, 400, mapper.createObjectNode().put("error", "UNKNOWN_MODE"));
                return;
            }
            mode = requested;
            respond(exchange, 200, mapper.createObjectNode().put("mode", mode));
            return;
        }
        if ("/l5-metrics".equals(path) && boundedExecutor != null) {
            respond(exchange, 200, executorMetrics()); return;
        }
        if ("/facts".equals(path)) {
            ObjectNode facts = mapper.createObjectNode().put("pid", ProcessHandle.current().pid())
                    .put("places", placeCalls).put("orders", orders.size()).put("fillQueries", fillQueries)
                    .put("delivered", delivered).put("dropped", dropped).put("delayed", delayed);
            var data = facts.putArray("data");
            orders.values().forEach(data::add);
            facts.set("events", events.deepCopy());
            if (boundedExecutor != null) {
                facts.put("executorActive", boundedExecutor.getActiveCount()).put("executorQueue", boundedExecutor.getQueue().size())
                        .put("executorCompleted", boundedExecutor.getCompletedTaskCount()).put("executorRejected", rejectedTasks.get())
                        .put("executorQueueCapacity", 16);
            }
            respond(exchange, 200, facts);
            return;
        }
        ObjectNode envelope = mapper.createObjectNode().put("code", "0").put("msg", "");
        var data = envelope.putArray("data");
        if ("/api/v5/public/instruments".equals(path)) {
            data.addObject().put("instId", "BTC-USDT").put("tickSz", "0.01")
                    .put("lotSz", "0.001").put("minSz", "0.001").put("state", "live");
        } else if ("/api/v5/trade/order".equals(path) && "POST".equals(exchange.getRequestMethod())) {
            JsonNode request = mapper.readTree(body);
            String client = request.path("clOrdId").asText();
            if (client.isBlank()) { respond(exchange, 400, envelope); return; }
            if (boundedWorkload && !orders.containsKey(client) && orders.size() >= 300) {
                respond(exchange, 429, mapper.createObjectNode().put("error", "L5_ORDER_BUDGET")); return;
            }
            placeCalls++;
            event("REQUEST_RECEIVED", client);
            ObjectNode order = orders.computeIfAbsent(client, key -> mapper.createObjectNode()
                    .put("clOrdId", key).put("ordId", "b0-venue-" + (orders.size() + 1))
                    .put("instId", request.path("instId").asText()).put("state", "filled")
                    .put("px", request.path("px").asText()).put("sz", request.path("sz").asText())
                    .put("accFillSz", request.path("sz").asText()).put("avgPx", "123.45000000")
                    .put("uTime", Long.toString(System.currentTimeMillis())));
            event("VENUE_ACCEPTED", client).put("ordId", order.path("ordId").asText());
            if (mode.startsWith("B1_") || "L5_OPEN".equals(mode)) {
                order.put("state", "live").put("accFillSz", "0").put("avgPx", "0");
            }
            data.addObject().put("ordId", order.path("ordId").asText()).put("clOrdId", client).put("sCode", "0");
            // AT 不生成 ACK；LA 先序列化 ACK，但不发送任何响应字节，等待调用方死亡。
            if (mode.startsWith("B1_")) {
                if ("B1_LOST_ACK".equals(mode)) {
                    byte[] ack = mapper.writeValueAsBytes(envelope);
                    try {
                        event("ACK_GENERATED", client).put("bytes", ack.length)
                                .put("body", new String(ack, StandardCharsets.UTF_8)).put("sha256",
                                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(ack)));
                    } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
                }
                withheld.add(exchange);
                return;
            }
            // 先存 venue 事实，再丢弃响应或关闭连接；不伪造 client-side 异常。
            if ("DROP".equals(mode) || "CLOSE".equals(mode)) {
                dropped++;
                exchange.close();
                return;
            }
            if ("DELAY".equals(mode)) {
                delayed++;
                try { Thread.sleep(700); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            }
            delivered++;
        } else if ("/api/v5/trade/order".equals(path) || "/api/v5/trade/fills".equals(path)) {
            Map<String, String> query = new LinkedHashMap<>();
            String raw = exchange.getRequestURI().getRawQuery();
            if (raw != null) for (String part : raw.split("&")) {
                String[] pair = part.split("=", 2);
                if (pair.length == 2) query.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
            }
            ObjectNode order = orders.values().stream().filter(value ->
                    value.path("ordId").asText().equals(query.get("ordId"))
                            || value.path("clOrdId").asText().equals(query.get("clOrdId"))).findFirst().orElse(null);
            event(path.endsWith("/order") ? "QUERY_ORDER" : "QUERY_FILLS", query.getOrDefault("clOrdId", ""))
                    .put("ordId", query.getOrDefault("ordId", "")).put("found", order != null);
            // V51准备已提交但尚未PLACE时，真实不存在必须复用B2的NOT_FOUND协议，不能返回成功空数据。
            if (boundedWorkload && order == null && path.endsWith("/order")) {
                envelope.put("code", "51603").put("msg", "Order does not exist");
                event("QUERY_ORDER_NOT_FOUND", query.getOrDefault("clOrdId", ""));
            }
            if (order != null && path.endsWith("/order")) data.add(order);
            if (order != null && path.endsWith("/fills") && "filled".equals(order.path("state").asText())) {
                fillQueries++;
                data.addObject().put("tradeId", "b0-fill-" + order.path("ordId").asText())
                        .put("ordId", order.path("ordId").asText()).put("instId", "BTC-USDT")
                        .put("side", "buy").put("fillPx", order.path("avgPx").asText()).put("fillSz", order.path("sz").asText())
                        .put("fee", order.path("fee").asText("0.00000000")).put("feeCcy", "USDT").put("ts", order.path("uTime").asText());
            }
        } else {
            respond(exchange, 404, mapper.createObjectNode().put("error", "UNSUPPORTED_PROTOCOL"));
            return;
        }
        respond(exchange, 200, envelope);
    }

    private ObjectNode event(String type, String client) {
        return events.addObject().put("sequence", events.size() + 1).put("type", type)
                .put("nanoTime", System.nanoTime()).put("client", client);
    }

    private ObjectNode executorMetrics() {
        return mapper.createObjectNode().put("active", boundedExecutor.getActiveCount())
                .put("queue", boundedExecutor.getQueue().size()).put("completed", boundedExecutor.getCompletedTaskCount())
                .put("rejected", rejectedTasks.get()).put("capacity", 16);
    }

    private void respond(HttpExchange exchange, int code, JsonNode value) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
        finally { exchange.close(); }
    }
}
