package com.guidinglight.nexusquant.app.logging;

import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.guidinglight.nexusquant.observability.logging.SensitiveLogSanitizer;

import java.nio.charset.StandardCharsets;

/** 在 console 最终编码处保护完整渲染文本，包括 Logback 自动追加的异常堆栈。 */
public class SensitiveLogEncoder extends PatternLayoutEncoder {
    private static final byte[] FAILURE = "[LOG_REDACTION_FAILED]\n".getBytes(StandardCharsets.UTF_8);

    @Override
    public byte[] encode(ILoggingEvent event) {
        try {
            byte[] rendered = super.encode(event);
            if (rendered == null) {
                return FAILURE;
            }
            return sanitize(new String(rendered, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
        } catch (Throwable failure) {
            // 脱敏或渲染失败时绝不回退到原始事件，也不再次写日志，避免泄露与递归。
            return FAILURE;
        }
    }

    protected String sanitize(String rendered) {
        return SensitiveLogSanitizer.sanitize(rendered);
    }
}
