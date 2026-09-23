package com.guidinglight.nexusquant.observability.logging;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveLogSanitizerTest {
    @Test
    void redactsCredentialFormsWithoutErasingContext() {
        Map<String, String> cases = Map.ofEntries(
                Map.entry("Authorization: Bearer value-one", "Authorization: [REDACTED]"),
                Map.entry("Authorization=value-two", "Authorization=[REDACTED]"),
                Map.entry("Bearer value-three", "Bearer [REDACTED]"),
                Map.entry("Cookie: session=value-four", "Cookie: [REDACTED]"),
                Map.entry("apiKey=value-five", "apiKey=[REDACTED]"),
                Map.entry("api_key: value-six", "api_key: [REDACTED]"),
                Map.entry("api-secret=value-seven", "api-secret=[REDACTED]"),
                Map.entry("PASSphrase=value-eight", "PASSphrase=[REDACTED]"),
                Map.entry("{\"jwt\":\"value-nine\"}", "{\"jwt\":\"[REDACTED]\"}"),
                Map.entry("jdbc:postgresql://localhost/nq?password=value-ten&ssl=true", "jdbc:postgresql://localhost/nq?password=[REDACTED]&ssl=true"),
                Map.entry("{api_key=value-eleven}", "{api_key=[REDACTED]}"),
                Map.entry("CredentialDto(secret=value-twelve)", "CredentialDto(secret=[REDACTED])"),
                Map.entry("/api?refresh-token=value-thirteen", "/api?refresh-token=[REDACTED]")
        );
        cases.forEach((input, expected) -> assertEquals(expected, SensitiveLogSanitizer.sanitize(input), input));
    }

    @Test
    void preservesSafeDiagnosticsAndNearMatches() {
        String safe = "trace_id=trace-safe-123 errorCode=NQ-TRD-1001 errorKey=ORDER_VERSION_CONFLICT "
                + "eventType=TradeExecuted orderId=o-1 clientOrderId=c-1 strategyRunId=s-1 "
                + "venue=OKX symbol=BTC-USDT tokenCount=2 passwordPolicy=strict secretary=available";
        assertEquals(safe, SensitiveLogSanitizer.sanitize(safe));
        String mixed = safe + " master_key=hidden";
        String result = SensitiveLogSanitizer.sanitize(mixed);
        assertTrue(result.startsWith(safe));
        assertTrue(result.endsWith("master_key=[REDACTED]"));
        assertFalse(result.contains("hidden"));
    }

    @Test
    void redactsCompleteValuesAndPreservesFollowingFields() {
        assertEquals("passphrase=[REDACTED] errorCode=NQ-TRD-1001",
                SensitiveLogSanitizer.sanitize("passphrase=two words errorCode=NQ-TRD-1001"));
        assertEquals("{\"apiKey\":\"[REDACTED]\"} orderId=o-1",
                SensitiveLogSanitizer.sanitize("{\"apiKey\":\"abc\\\"def\"} orderId=o-1"));
        assertEquals("trace_id=t Authorization: [REDACTED] errorCode=NQ-TRD-1001 orderId=o-1",
                SensitiveLogSanitizer.sanitize("trace_id=t Authorization: Bearer abc errorCode=NQ-TRD-1001 orderId=o-1"));
        assertEquals("Cookie: [REDACTED] errorCode=NQ-TRD-1001",
                SensitiveLogSanitizer.sanitize("Cookie: session=one; user=two errorCode=NQ-TRD-1001"));
        assertEquals("passphrase=[REDACTED] orderId=o-1",
                SensitiveLogSanitizer.sanitize("passphrase=two recovery=phrase orderId=o-1"));
    }
}
