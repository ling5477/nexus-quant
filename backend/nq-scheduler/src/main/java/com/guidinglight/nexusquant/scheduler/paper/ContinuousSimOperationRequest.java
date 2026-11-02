package com.guidinglight.nexusquant.scheduler.paper;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** 原编号冻结主体和完整业务输入；同一预算的等值十进制表示使用同一身份。 */
public record ContinuousSimOperationRequest(
        UUID operationId, Type type, String publishId, BigDecimal budget, String paperRunId) {
    public enum Type {
        CREATE,
        PAUSE,
        RESUME
    }

    public void validate() {
        if (operationId == null || type == null)
            throw new IllegalArgumentException("INVALID_CONTINUOUS_SIM_OPERATION");
        if (type == Type.CREATE) {
            if (!identity(publishId)
                    || paperRunId != null
                    || budget == null
                    || budget.signum() <= 0
                    || budget.scale() > 8
                    || budget.compareTo(new BigDecimal("100000")) > 0) {
                throw new IllegalArgumentException("INVALID_CONTINUOUS_SIM_CREATE_INPUT");
            }
        } else if (!identity(paperRunId) || publishId != null || budget != null) {
            throw new IllegalArgumentException("INVALID_CONTINUOUS_SIM_STATE_INPUT");
        }
    }

    /** 长度前缀与null标记阻止分隔符歧义；ID限定ASCII，避免损坏Unicode的替换编码碰撞。 */
    public String fingerprint(long owner) {
        validate();
        if (owner <= 0) throw new IllegalArgumentException("INVALID_CONTINUOUS_SIM_ACTOR");
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            for (String value :
                    new String[] {
                        "CONTINUOUS_SIM_OPERATION_V1",
                        Long.toString(owner),
                        type.name(),
                        publishId,
                        budget == null ? null : budget.stripTrailingZeros().toPlainString(),
                        paperRunId
                    }) {
                byte[] bytes = value == null ? null : value.getBytes(StandardCharsets.UTF_8);
                hash.update(
                        ByteBuffer.allocate(Integer.BYTES)
                                .putInt(bytes == null ? -1 : bytes.length)
                                .array());
                if (bytes != null) hash.update(bytes);
            }
            return HexFormat.of().formatHex(hash.digest());
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }

    private static boolean identity(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    }
}
