package com.guidinglight.nexusquant.observability.logging;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 对最终日志文本应用统一的凭证值保护规则。 */
public final class SensitiveLogSanitizer {
    private static final String REDACTED = "[REDACTED]";
    private static final String KEY = "(?:authorization|cookie|api[-_]?key|api[-_]?secret|signing[-_]?secret|secret|passphrase|password|access[-_]?token|refresh[-_]?token|token|jwt|master[-_]?key)";
    private static final String SAFE_FIELD = "(?:traceId|trace_id|errorCode|errorKey|eventType|orderId|clientOrderId|strategyRunId|venue|symbol|logger|thread|level|timestamp)";
    private static final String NEXT_FIELD = "(?=\\s+" + SAFE_FIELD + "\\s*[:=]|[\\r\\n]|$)";
    private static final Pattern HEADER = Pattern.compile("(?i)(?<![\\p{Alnum}_-])(authorization|cookie)(\\s*[:=]\\s*)(.*?)(" + NEXT_FIELD + ")");
    private static final Pattern BEARER = Pattern.compile("(?i)(?<![\\p{Alnum}_-])(bearer\\s+)([^\\s,;)}\\]]+)");
    private static final Pattern ASSIGNMENT = Pattern.compile("(?i)(?<![\\p{Alnum}_-])((?:\\\"?" + KEY + "\\\"?)\\s*[:=]\\s*)(\\\"(?:\\\\.|[^\\\"\\\\])*\\\"|'(?:\\\\.|[^'\\\\])*'|(?:(?!\\s+" + SAFE_FIELD + "\\s*[:=])[^,;&}\\]\\)\\r\\n])+)");

    private SensitiveLogSanitizer() {
    }

    public static String sanitize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String result = replaceAssignments(input);
        result = replaceHeaders(result);
        return replaceBearer(result);
    }

    private static String replaceHeaders(String input) {
        Matcher matcher = HEADER.matcher(input);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String value = matcher.group(3);
            String replacement = matcher.group(1) + matcher.group(2)
                    + (value.regionMatches(true, 0, "Bearer ", 0, 7) ? "Bearer " : "") + REDACTED;
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String replaceBearer(String input) {
        Matcher matcher = BEARER.matcher(input);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(1) + REDACTED));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String replaceAssignments(String input) {
        Matcher matcher = ASSIGNMENT.matcher(input);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String value = matcher.group(2);
            String replacement = matcher.group(1)
                    + (value.length() >= 2 && (value.charAt(0) == '"' || value.charAt(0) == '\'')
                    ? value.charAt(0) + REDACTED + value.charAt(0) : REDACTED);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
