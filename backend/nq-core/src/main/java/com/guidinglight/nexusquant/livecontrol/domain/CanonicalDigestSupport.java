package com.guidinglight.nexusquant.livecontrol.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.util.HexFormat;
import java.util.Locale;

/** canonical digest 的低层确定性编码工具；不依赖普通 JSON serializer。 */
final class CanonicalDigestSupport {

    private static final DateTimeFormatter INSTANT_FORMAT = new DateTimeFormatterBuilderHolder().formatter;

    private CanonicalDigestSupport() {
    }

    static String sha256(String canonicalValue) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalValue.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    static BigDecimal money(BigDecimal value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        try {
            return value.setScale(8, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(name + " must have at most 8 decimal places", ex);
        }
    }

    static String decimal(BigDecimal value) {
        return quote(money(value, "canonical decimal").toPlainString());
    }

    static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 2).append('"');
        value.codePoints().forEach(codePoint -> {
            switch (codePoint) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (codePoint < 0x20) {
                        escaped.append(String.format(Locale.ROOT, "\\u%04x", codePoint));
                    } else {
                        escaped.appendCodePoint(codePoint);
                    }
                }
            }
        });
        return escaped.append('"').toString();
    }

    static String instant(Instant value) {
        if (value == null) {
            throw new IllegalArgumentException("canonical instant must not be null");
        }
        if (value.getNano() % 1_000 != 0) {
            throw new IllegalArgumentException("canonical instant must have at most microsecond precision");
        }
        return quote(INSTANT_FORMAT.format(value));
    }

    private static final class DateTimeFormatterBuilderHolder {
        private final DateTimeFormatter formatter = new java.time.format.DateTimeFormatterBuilder()
                .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
                .appendFraction(ChronoField.NANO_OF_SECOND, 6, 6, true)
                .appendLiteral('Z')
                .toFormatter()
                .withZone(ZoneOffset.UTC);
    }
}
