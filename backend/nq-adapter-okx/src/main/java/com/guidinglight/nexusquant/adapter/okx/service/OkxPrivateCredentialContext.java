package com.guidinglight.nexusquant.adapter.okx.service;

import java.util.Arrays;
import java.util.Objects;

/**
 * transport-scoped credential buffer；只能同步使用并在 finally/close 中覆盖。
 */
public final class OkxPrivateCredentialContext implements AutoCloseable {

    private final char[] apiKey;
    private final char[] secretKey;
    private final char[] passphrase;

    public OkxPrivateCredentialContext(char[] apiKey, char[] secretKey, char[] passphrase) {
        // 先完成全部校验再分配副本，避免后续字段校验失败时遗留无法清理的部分副本。
        validateRequired(apiKey, "apiKey");
        validateRequired(secretKey, "secretKey");
        validateRequired(passphrase, "passphrase");
        this.apiKey = Arrays.copyOf(apiKey, apiKey.length);
        this.secretKey = Arrays.copyOf(secretKey, secretKey.length);
        this.passphrase = Arrays.copyOf(passphrase, passphrase.length);
    }

    char[] apiKey() {
        return apiKey;
    }

    char[] secretKey() {
        return secretKey;
    }

    char[] passphrase() {
        return passphrase;
    }

    @Override
    public void close() {
        Arrays.fill(apiKey, '\0');
        Arrays.fill(secretKey, '\0');
        Arrays.fill(passphrase, '\0');
    }

    @Override
    public String toString() {
        return "OkxPrivateCredentialContext[REDACTED]";
    }

    private static void validateRequired(char[] value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.length == 0) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        for (char character : value) {
            if (Character.isISOControl(character)) {
                // 在进入 JDK header builder 前拒绝控制字符，避免异常 message 回显 credential value。
                throw new IllegalArgumentException(field + " contains prohibited control characters");
            }
        }
    }
}
