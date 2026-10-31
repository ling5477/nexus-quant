package com.guidinglight.nexusquant.account.domain;

/** 敏感材料仅在请求内存中流转；禁止默认记录器把记录字段展开到日志。 */
public record CredentialOperation(
        Type type,
        Long credentialId,
        String credentialType,
        String apiKey,
        String secretKey,
        String passphrase,
        String privateKeyPem,
        String reason) {
    public enum Type {
        CREATE,
        ROTATE,
        VERIFY,
        DISABLE,
        ENABLE,
        EXPIRE,
        REVOKE
    }

    @Override
    public String toString() {
        return "CredentialOperation[type="
                + type
                + ", credentialId="
                + credentialId
                + ", material=REDACTED]";
    }
}
