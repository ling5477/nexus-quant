package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.Connection;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** 永久回归原 lowercase 旁路；只读 oracle 验证真实 ordinary Spring 链写出的决定与外部请求。 */
@EnabledIfSystemProperty(named = "nq.b5", matches = "true")
class B5VenueIdentityProcessTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    @Test void caseMatrixAndInvalidInputHaveOneCanonicalBoundary() throws Exception {
        Path root = B0Processes.root().resolve("backend/nq-app/target/b5-venue/" + UUID.randomUUID());
        Files.createDirectories(root);
        System.out.println("B5_VENUE_ROOT " + root);
        try (var pg = B0Processes.Pg.start()) {
            int run = 0;
            for (String input : List.of("OKX", "okx", "Okx", "oKx", " \tokx\r\n")) {
                Path dir = root.resolve("CASE" + ++run);
                Files.createDirectories(dir);
                var proof = mapper.createObjectNode().put("externalVenueInput", input)
                        .put("controllerPid", ProcessHandle.current().pid());
                try (var fixture = B0Fixture.create(pg);
                     var venue = new B0Processes.Child(B2SyntheticVenueMain.class, dir, "venue", B0Processes.cleanEnvironment())) {
                    String endpoint = "http://127.0.0.1:" + venue.ready();
                    var env = B0Processes.cleanEnvironment();
                    env.put("NQ_B0_DB", fixture.url()); env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test");
                    fixture.initialize(true, endpoint, env);
                    try (var reader = fixture.checker(); var nq = new B0Processes.Child(B0NqProcessMain.class, dir, "nq", env)) {
                        nq.ready();
                        proof.put("database", fixture.name()).put("nqPid", nq.process.pid()).put("venuePid", venue.process.pid());
                        proof.put("postgres", value(reader, "SHOW server_version"));
                        assertTrue(proof.path("postgres").asText().startsWith("16."));
                        assertEquals("50", value(reader, "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"));
                        for (String invalid : List.of("unsupported", "foo", "okx-test", "", " ", "\t\n", "OKX_SPOT", "SIM")) {
                            setVenue(nq, invalid);
                            assertEquals("INVALID_VENUE_REJECTED", nq.send("TRY_PLACE_B5"));
                            assertEquals("0", value(reader, "SELECT count(*) FROM orders"));
                            assertEquals("0", value(reader, "SELECT count(*) FROM ordinary_place_authorities"));
                            assertEquals("0", value(reader, "SELECT count(*) FROM event_store"));
                            assertEquals(0, facts(endpoint).path("placeRequests").asInt());
                            assertEquals(0, facts(endpoint).path("apiRequests").asInt(-1));
                            assertEquals(0, facts(endpoint).path("events").size());
                        }
                        proof.put("invalidInputsRejected", 8);
                        setVenue(nq, input);
                        assertTrue(nq.send("PLACE_B2").endsWith("ACCEPTED"));
                        assertEquals("OKX", value(reader, "SELECT venue FROM orders"));
                        assertEquals("OKX", value(reader, "SELECT exchange_code FROM orders"));
                        assertEquals("1", value(reader, "SELECT count(*) FROM ordinary_place_authorities"));
                        assertEquals("MAY_HAVE_ESCAPED", value(reader, "SELECT state FROM ordinary_place_authorities"));
                        assertEquals("1", value(reader, "SELECT count(*) FROM audit_logs WHERE action='PLACE_MAY_HAVE_ESCAPED'"));
                        assertEquals(1, facts(endpoint).path("placeRequests").asInt());
                        String orderId = value(reader, "SELECT order_id FROM orders");
                        for (String duplicate : List.of("OKX", "okx", "Okx", "oKx")) {
                            setVenue(nq, duplicate); nq.send("PLACE_B2");
                            assertEquals(orderId, value(reader, "SELECT order_id FROM orders"));
                            assertEquals(1, facts(endpoint).path("placeRequests").asInt());
                        }
                        for (String table : List.of("orders", "ordinary_place_authorities", "trades", "event_store", "ledger_entries")) {
                            proof.set(table, mapper.readTree(value(reader, "SELECT coalesce(jsonb_agg(to_jsonb(t))::text,'[]') FROM " + table + " t")));
                        }
                        proof.set("venue", facts(endpoint));
                    }
                }
                proof.put("result", "PASS");
                Path raw = dir.resolve("raw-proof.json");
                Files.writeString(raw, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
                SyntheticEvidenceExport.write(raw, "B5-VENUE", run);
                System.out.println("B5_VENUE_PASS CASE" + run);
            }
        }
    }

    private static void setVenue(B0Processes.Child child, String input) throws Exception {
        child.send("SET_B5_VENUE " + Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8)));
    }

    private JsonNode facts(String endpoint) throws Exception {
        return mapper.readTree(http.send(HttpRequest.newBuilder(URI.create(endpoint + "/facts"))
                .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
    }

    private static String value(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            assertTrue(rows.next()); return rows.getString(1);
        }
    }
}
