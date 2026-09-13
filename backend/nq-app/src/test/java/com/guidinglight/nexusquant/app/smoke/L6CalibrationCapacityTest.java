package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class L6CalibrationCapacityTest {
    @TempDir Path directory;
    @Test void invalidContractCannotReachInfrastructureOrTimer() {
        var created = new AtomicInteger();
        var timer = new AtomicInteger();
        for (var budget : new QualificationCapacity[] {
                new QualificationCapacity(QualificationCapacity.Mode.L5, 301, 301, 301),
                new QualificationCapacity(QualificationCapacity.Mode.L6_CALIBRATION, 600, 600, 599),
                new QualificationCapacity(null, 600, 600, 600)}) {
            var failure = assertThrows(IllegalStateException.class, () -> budget.start(() -> {
                created.incrementAndGet(); timer.incrementAndGet();
            }));
            assertTrue(failure.getMessage().contains("QUALIFICATION_BUDGET_CAPACITY_CONTRACT_INVALID"));
        }
        assertEquals(0, created.get()); assertEquals(0, timer.get());
    }
    @Test void equalCapacityAndWholeRunAdmissionBoundary() throws Exception {
        var budget = L6CalibrationBudget.frozen();
        assertEquals(600, budget.runOrderBudget());
        assertEquals(budget.runOrderBudget(), budget.venueLogicalOrderCapacity());
        budget.start(() -> { });
        budget.reserve(596, 4);
        assertThrows(IllegalStateException.class, () -> budget.reserve(597, 4));
        assertThrows(IllegalStateException.class, () -> budget.reserve(600, 4));
        assertEquals(300, B0SyntheticVenueMain.DEFAULT_ORDER_CAPACITY);
    }
    @Test void modeAuthorityAndCapacityNeverGrantProducerPermission() {
        assertThrows(IllegalStateException.class, () -> QualificationCapacity.resolve("UNKNOWN"));
        var over = new QualificationCapacity(QualificationCapacity.Mode.L6_FORMAL, 3001, 3001, 3001);
        assertTrue(assertThrows(IllegalStateException.class, over::validate).getMessage().contains("EXCEEDS_FROZEN"));
        var spare = new QualificationCapacity(QualificationCapacity.Mode.L6_CALIBRATION, 100, 100, 600);
        spare.validate(); spare.reserve(96, 4);
        assertThrows(IllegalStateException.class, () -> spare.reserve(100, 1));
        assertEquals(300, QualificationCapacity.l5().globalStageSafetyCap());
        assertEquals(3000, L6CalibrationBudget.frozen().globalStageSafetyCap());
        assertEquals(600, L6CalibrationBudget.frozen().maximumPossibleDistinctOrdersForRun());
    }
    @Test void realVenueDefaultBoundaryAndCalibrationOverridePreserveProtocol() throws Exception {
        var mapper = new ObjectMapper();
        for (var main : new Class<?>[]{L5VenueProcessMain.class, L6CalibrationVenueProcessMain.class}) {
            boolean calibration = main == L6CalibrationVenueProcessMain.class;
            try (var child = new B0Processes.Child(main, directory, calibration ? "cal-venue" : "l5-venue", B0Processes.cleanEnvironment());
                 var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
                String endpoint = "http://127.0.0.1:" + child.ready();
                L5BoundedWorkloadTest.http(endpoint, "L5_OPEN");
                int accepted = calibration ? 301 : 300;
                for (int i = 1; i <= 301; i++) {
                    var response = place(client, endpoint, "capacity-" + i);
                    assertEquals(i <= accepted ? 200 : 429, response.statusCode());
                    if (response.statusCode() == 429) assertTrue(response.body().contains("L5_ORDER_BUDGET"));
                }
                // 原Venue协议保留重复请求计数，但不新增逻辑订单；重复副作用由共享oracle拒绝。
                var replay = place(client, endpoint, "capacity-1");
                assertEquals(200, replay.statusCode());
                assertEquals("b0-venue-1", mapper.readTree(replay.body()).path("data").get(0).path("ordId").asText());
                L5BoundedWorkloadTest.http(endpoint, "FILL");
                var facts = L5BoundedWorkloadTest.http(endpoint, null);
                assertEquals(accepted, facts.path("orders").asInt());
                assertEquals(accepted + 1, facts.path("places").asInt());
                assertEquals(accepted + 1, facts.path("delivered").asInt());
                assertEquals(16, facts.path("executorQueueCapacity").asInt());
                assertEquals(0, facts.path("executorRejected").asInt());
                assertEquals(0, facts.path("executorQueue").asInt());
                for (var order : facts.path("data")) assertEquals("filled", order.path("state").asText());
                var fill = client.send(HttpRequest.newBuilder(URI.create(endpoint + "/api/v5/trade/fills?ordId=b0-venue-1"))
                        .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals("b0-fill-b0-venue-1", mapper.readTree(fill.body()).path("data").get(0).path("tradeId").asText());
                assertEquals(1, L5BoundedWorkloadTest.http(endpoint, null).path("fillQueries").asInt());
            }
        }
    }
    private static HttpResponse<String> place(HttpClient client, String endpoint, String identity) throws Exception {
        var body = new ObjectMapper().createObjectNode().put("clOrdId", identity).put("instId", "BTC-USDT")
                .put("px", "100").put("sz", "0.1");
        return client.send(HttpRequest.newBuilder(URI.create(endpoint + "/api/v5/trade/order"))
                .timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
