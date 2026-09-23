package com.guidinglight.nexusquant.app.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SensitiveLogEncoderTest {
    @Test
    void sanitizerFailureNeverFallsBackToRawMessageOrThrowable() {
        var context = new LoggerContext();
        var encoder = new SensitiveLogEncoder() {
            @Override
            protected String sanitize(String rendered) {
                throw new IllegalStateException("synthetic sanitizer failure");
            }
        };
        encoder.setContext(context);
        encoder.setPattern("%msg%n");
        encoder.start();
        var event = new LoggingEvent(getClass().getName(), context.getLogger("test"), Level.ERROR,
                "password=synthetic-secret", new IllegalStateException("secret=synthetic-cause"), null);
        String output = new String(encoder.encode(event), StandardCharsets.UTF_8);
        assertEquals("[LOG_REDACTION_FAILED]\n", output);
        assertFalse(output.contains("synthetic-secret"));
        assertFalse(output.contains("synthetic-cause"));
    }
}
