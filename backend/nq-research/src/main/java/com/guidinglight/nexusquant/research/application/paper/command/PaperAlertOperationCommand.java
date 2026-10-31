package com.guidinglight.nexusquant.research.application.paper.command;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlertSeverity;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 完整输入随原编号保持；不把当前告警状态或浏览器自报操作者当作原命令身份。 */
public record PaperAlertOperationCommand(
        Type type,
        String alertId,
        String alertType,
        String severity,
        String title,
        String message,
        String source,
        String eventSnapshotJson) {
    private static final ObjectMapper SNAPSHOT_JSON = new ObjectMapper();

    public enum Type {
        CREATE,
        ACK,
        RESOLVE
    }

    public void validate() {
        if (type == null) throw new IllegalArgumentException("INVALID_PAPER_ALERT_OPERATION");
        if (type == Type.CREATE) {
            if (alertId != null)
                throw new IllegalArgumentException("INVALID_PAPER_ALERT_OPERATION");
            text(alertType, 64, true);
            text(severity, 16, true);
            PaperRunAlertSeverity.valueOf(severity);
            text(title, 255, true);
            text(message, 2048, false);
            text(source, 64, false);
            text(eventSnapshotJson, 8192, false);
            // 保留原接口允许的JSON值类型，只在业务写入前拒绝无效或多段JSON。
            if (eventSnapshotJson != null) {
                try {
                    var parsed =
                            SNAPSHOT_JSON
                                    .reader()
                                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                                    .readTree(eventSnapshotJson);
                    if (parsed == null || parsed.isMissingNode())
                        throw new IllegalArgumentException("INVALID_PAPER_ALERT_SNAPSHOT");
                } catch (JsonProcessingException failure) {
                    throw new IllegalArgumentException("INVALID_PAPER_ALERT_SNAPSHOT");
                }
            }
        } else {
            text(alertId, 128, true);
            if (alertType != null
                    || severity != null
                    || title != null
                    || message != null
                    || source != null
                    || eventSnapshotJson != null)
                throw new IllegalArgumentException("INVALID_PAPER_ALERT_OPERATION");
        }
    }

    /** 长度前缀区分null、空串和字段边界；拒绝不完整UTF-16，避免编码替换产生同一指纹。 */
    public String fingerprint(long owner, String paperRunId) {
        validate();
        text(paperRunId, 128, true);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String[] fields = {
                "paper-alert-operation.v1",
                Long.toString(owner),
                paperRunId,
                type.name(),
                alertId,
                alertType,
                severity,
                title,
                message,
                source,
                eventSnapshotJson
            };
            for (String value : fields) {
                byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
                digest.update(
                        ByteBuffer.allocate(4).putInt(value == null ? -1 : bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("paper alert fingerprint unavailable", failure);
        }
    }

    private static void text(String value, int maximum, boolean required) {
        if (value == null) {
            if (required) throw new IllegalArgumentException("INVALID_PAPER_ALERT_OPERATION");
            return;
        }
        if (value.length() > maximum || (required && value.isBlank()))
            throw new IllegalArgumentException("INVALID_PAPER_ALERT_OPERATION");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\0')
                throw new IllegalArgumentException("INVALID_PAPER_ALERT_OPERATION");
            if (Character.isHighSurrogate(character)) {
                if (++index >= value.length() || !Character.isLowSurrogate(value.charAt(index)))
                    throw new IllegalArgumentException("INVALID_PAPER_ALERT_OPERATION");
            } else if (Character.isLowSurrogate(character)) {
                throw new IllegalArgumentException("INVALID_PAPER_ALERT_OPERATION");
            }
        }
    }
}
