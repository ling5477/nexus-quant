package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/** 独立交易所事实：撤单请求受理与撮合端撤单生效分开，不连接 NQ 数据库。 */
public final class B2SyntheticVenueMain {
    private final ObjectMapper mapper = new ObjectMapper();
    private final com.fasterxml.jackson.databind.node.ArrayNode fills = mapper.createArrayNode();
    private final com.fasterxml.jackson.databind.node.ArrayNode events = mapper.createArrayNode();
    private ObjectNode order;
    private boolean pendingCancel;
    private boolean duplicateReports;
    private int places;
    private int cancels;
    private boolean holdPlace;
    private boolean holdCancel;
    private boolean holdAcceptance;
    private int placeRequests;

    public static void main(String[] args) throws Exception {
        B0Fixture.require(args.length == 0);
        var venue = new B2SyntheticVenueMain();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 16);
        server.createContext("/", venue::handle);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
        System.out.println("B0_READY " + server.getAddress().getPort());
        System.out.flush();
    }

    private synchronized void handle(HttpExchange exchange) throws java.io.IOException {
        byte[] body = exchange.getRequestBody().readNBytes(8193);
        if (body.length > 8192 || exchange.getRequestHeaders().keySet().stream()
                .anyMatch(key -> key.toUpperCase(java.util.Locale.ROOT).startsWith("OK-ACCESS"))) {
            respond(exchange, 400, mapper.createObjectNode().put("error", "UNSAFE_REQUEST"));
            return;
        }
        String path = exchange.getRequestURI().getPath();
        var envelope = mapper.createObjectNode().put("code", "0").put("msg", "");
        var data = envelope.putArray("data");
        if ("/facts".equals(path)) {
            envelope.put("pid", ProcessHandle.current().pid()).put("places", places).put("cancels", cancels)
                    .put("pendingCancel", pendingCancel).put("placeRequests", placeRequests);
            envelope.set("order", order);
            envelope.set("fills", fills);
            envelope.set("events", events);
        } else if ("/control".equals(path)) {
            String command = new String(body, StandardCharsets.UTF_8);
            if ("HOLD_ACCEPTANCE".equals(command)) {
                holdAcceptance = true;
                event(command);
            } else if ("RELEASE_ACCEPTANCE".equals(command)) {
                holdAcceptance = false;
                event(command);
                notifyAll();
            } else if ("KILL_DURABLE_OBSERVED".equals(command)) {
                event(command);
            } else if ("HOLD_PLACE".equals(command) || "HOLD_CANCEL".equals(command)) {
                if ("HOLD_PLACE".equals(command)) holdPlace = true;
                else holdCancel = true;
                event(command);
            } else if ("RELEASE_PLACE".equals(command) || "RELEASE_CANCEL".equals(command)) {
                if ("RELEASE_PLACE".equals(command)) holdPlace = false;
                else holdCancel = false;
                event(command);
                notifyAll();
            } else if ("DUPLICATE_REPORTS".equals(command)) {
                duplicateReports = true;
                event("DUPLICATE_REPORTS_ENABLED");
            } else if (command.startsWith("FILL ") && order != null && !"canceled".equals(order.path("state").asText())) {
                String[] values = command.split(" ");
                if (values.length != 3) { respond(exchange, 400, envelope); return; }
                BigDecimal qty = new BigDecimal(values[1]);
                BigDecimal fee = new BigDecimal(values[2]);
                BigDecimal executed = new BigDecimal(order.path("accFillSz").asText()).add(qty);
                if (qty.signum() <= 0 || fee.signum() < 0 || executed.compareTo(new BigDecimal(order.path("sz").asText())) > 0) {
                    respond(exchange, 400, envelope); return;
                }
                String id = "b2-fill-" + (fills.size() + 1);
                fills.addObject().put("tradeId", id).put("ordId", order.path("ordId").asText())
                        .put("instId", "BTC-USDT").put("side", "buy").put("fillPx", "100")
                        .put("fillSz", qty.toPlainString()).put("fee", fee.negate().toPlainString())
                        .put("feeCcy", "USDT").put("ts", Long.toString(System.currentTimeMillis()));
                order.put("accFillSz", executed.toPlainString()).put("avgPx", "100")
                        .put("state", executed.compareTo(new BigDecimal(order.path("sz").asText())) == 0
                                ? "filled" : "partially_filled");
                event("FILL").put("tradeId", id).put("qty", qty.toPlainString()).put("fee", fee.toPlainString());
            } else if ("CANCEL_EFFECT".equals(command) && pendingCancel) {
                // 已全成时撤单无法生效；请求 ACK 只说明受理，不能撤销已经发生的成交。
                if (!"filled".equals(order.path("state").asText())) order.put("state", "canceled");
                pendingCancel = false;
                event("CANCEL_EFFECT").put("state", order.path("state").asText());
            } else { respond(exchange, 400, envelope); return; }
        } else if ("/api/v5/public/instruments".equals(path)) {
            data.addObject().put("instId", "BTC-USDT").put("tickSz", "0.01")
                    .put("lotSz", "0.001").put("minSz", "0.001").put("state", "live");
        } else if ("/api/v5/trade/order".equals(path) && "POST".equals(exchange.getRequestMethod())) {
            placeRequests++;
            event("PLACE_REQUEST_RECEIVED");
            long deadline = System.nanoTime() + java.time.Duration.ofSeconds(20).toNanos();
            while (holdAcceptance) {
                if (System.nanoTime() >= deadline) throw new java.io.IOException("B3_ACCEPTANCE_BARRIER_TIMEOUT");
                try { wait(100); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new java.io.IOException(ex); }
            }
            JsonNode request = mapper.readTree(body);
            if (order != null || request.path("clOrdId").asText().isBlank()) { respond(exchange, 400, envelope); return; }
            places++;
            order = mapper.createObjectNode().put("clOrdId", request.path("clOrdId").asText())
                    .put("ordId", "b2-venue-1").put("instId", "BTC-USDT").put("state", "live")
                    .put("px", request.path("px").asText()).put("sz", request.path("sz").asText())
                    .put("accFillSz", "0").put("avgPx", "0").put("uTime", Long.toString(System.currentTimeMillis()));
            event("PLACE_ACCEPTED");
            data.addObject().put("ordId", "b2-venue-1").put("clOrdId", order.path("clOrdId").asText()).put("sCode", "0");
            awaitRelease(true);
            event("PLACE_ACK_GENERATED");
        } else if ("/api/v5/trade/cancel-order".equals(path) && "POST".equals(exchange.getRequestMethod())) {
            JsonNode request = mapper.readTree(body);
            if (order == null || !(order.path("ordId").asText().equals(request.path("ordId").asText())
                    || order.path("clOrdId").asText().equals(request.path("clOrdId").asText()))) {
                respond(exchange, 400, envelope); return;
            }
            cancels++;
            pendingCancel = true;
            event("CANCEL_REQUEST_RECEIVED").put("state", order.path("state").asText());
            data.addObject().put("ordId", "b2-venue-1").put("clOrdId", order.path("clOrdId").asText()).put("sCode", "0");
            awaitRelease(false);
            event("CANCEL_ACK_GENERATED");
        } else if ("/api/v5/trade/order".equals(path) || "/api/v5/trade/fills".equals(path)) {
            var query = new java.util.HashMap<String, String>();
            String raw = exchange.getRequestURI().getRawQuery();
            if (raw != null) for (String part : raw.split("&")) {
                String[] pair = part.split("=", 2);
                if (pair.length == 2) query.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
            }
            if (order != null && (order.path("ordId").asText().equals(query.get("ordId"))
                    || order.path("clOrdId").asText().equals(query.get("clOrdId")))) {
                if (path.endsWith("/fills")) {
                    data.addAll(fills);
                    if (duplicateReports) data.addAll(fills);
                    event("QUERY_FILLS").put("reportCount", data.size());
                }
                else { data.add(order); event("QUERY_ORDER").put("state", order.path("state").asText()); }
            }
        } else { respond(exchange, 404, envelope); return; }
        respond(exchange, 200, envelope);
        if ("POST".equals(exchange.getRequestMethod())) {
            if ("/api/v5/trade/order".equals(path)) event("PLACE_RESPONSE_DELIVERED");
            if ("/api/v5/trade/cancel-order".equals(path)) event("CANCEL_RESPONSE_DELIVERED");
        }
    }

    /** 等待释放时让出 monitor，使撮合、查询与 ACK 屏障具备实际独立时序。 */
    private void awaitRelease(boolean place) throws java.io.IOException {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(20).toNanos();
        while (place ? holdPlace : holdCancel) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new java.io.IOException("B2_ACK_BARRIER_TIMEOUT");
            try { wait(Math.max(1, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(remaining))); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new java.io.IOException(ex); }
        }
    }

    private ObjectNode event(String type) {
        return events.addObject().put("sequence", events.size() + 1).put("type", type).put("nanoTime", System.nanoTime())
                .put("epochMillis", System.currentTimeMillis());
    }

    private void respond(HttpExchange exchange, int code, JsonNode value) throws java.io.IOException {
        byte[] body = mapper.writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, body.length);
        try (var stream = exchange.getResponseBody()) { stream.write(body); }
        finally { exchange.close(); }
    }
}
