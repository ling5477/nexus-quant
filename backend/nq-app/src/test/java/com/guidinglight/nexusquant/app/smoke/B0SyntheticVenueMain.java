package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/** 独立 venue-owned 内存事实；不链接 NQ DB，不实现任何本地 Order/Trade/Ledger 状态机。 */
public final class B0SyntheticVenueMain {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, ObjectNode> orders = new LinkedHashMap<>();
    private int placeCalls;
    private int fillQueries;
    private int delivered;
    private int dropped;
    private int delayed;
    private String mode = "DELIVER";
    private final com.fasterxml.jackson.databind.node.ArrayNode events = mapper.createArrayNode();
    private final java.util.List<HttpExchange> withheld = new java.util.ArrayList<>();

    public static void main(String[] args) throws Exception {
        B0Fixture.require(args.length == 0);
        var venue = new B0SyntheticVenueMain();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 16);
        server.createContext("/", venue::handle);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("B0_READY " + server.getAddress().getPort());
        System.out.flush();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
    }

    private synchronized void handle(HttpExchange exchange) throws java.io.IOException {
        String path = exchange.getRequestURI().getPath();
        byte[] body = exchange.getRequestBody().readNBytes(8193);
        if (body.length > 8192 || exchange.getRequestHeaders().keySet().stream()
                .anyMatch(key -> key.toUpperCase(java.util.Locale.ROOT).startsWith("OK-ACCESS"))) {
            respond(exchange, 400, mapper.createObjectNode().put("error", "UNSAFE_REQUEST"));
            return;
        }
        if ("/control".equals(path)) {
            String requested = new String(body, StandardCharsets.UTF_8);
            if ("FILL".equals(requested) && mode.startsWith("B1_")) {
                orders.values().forEach(order -> {
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
            if (!java.util.Set.of("DELIVER", "DROP", "CLOSE", "DELAY", "B1_ACCEPTED_TIMEOUT", "B1_LOST_ACK").contains(requested)) {
                respond(exchange, 400, mapper.createObjectNode().put("error", "UNKNOWN_MODE"));
                return;
            }
            mode = requested;
            respond(exchange, 200, mapper.createObjectNode().put("mode", mode));
            return;
        }
        if ("/facts".equals(path)) {
            ObjectNode facts = mapper.createObjectNode().put("pid", ProcessHandle.current().pid())
                    .put("places", placeCalls).put("orders", orders.size()).put("fillQueries", fillQueries)
                    .put("delivered", delivered).put("dropped", dropped).put("delayed", delayed);
            var data = facts.putArray("data");
            orders.values().forEach(data::add);
            facts.set("events", events.deepCopy());
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
            placeCalls++;
            event("REQUEST_RECEIVED", client);
            ObjectNode order = orders.computeIfAbsent(client, key -> mapper.createObjectNode()
                    .put("clOrdId", key).put("ordId", "b0-venue-" + (orders.size() + 1))
                    .put("instId", request.path("instId").asText()).put("state", "filled")
                    .put("px", request.path("px").asText()).put("sz", request.path("sz").asText())
                    .put("accFillSz", request.path("sz").asText()).put("avgPx", "123.45000000")
                    .put("uTime", Long.toString(System.currentTimeMillis())));
            event("VENUE_ACCEPTED", client).put("ordId", order.path("ordId").asText());
            if (mode.startsWith("B1_")) {
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
                                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(ack)));
                    } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
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

    private void respond(HttpExchange exchange, int code, JsonNode value) throws java.io.IOException {
        byte[] bytes = mapper.writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
        finally { exchange.close(); }
    }
}
