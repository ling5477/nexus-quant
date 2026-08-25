package com.guidinglight.nexusquant.livecontrol.domain;

import java.util.Objects;

/**
 * `operator-pilot-authority.v1` 固定字段顺序、UTC 与精确数值 canonical encoder。
 */
public final class OperatorPilotAuthorityCanonicalEncoder {

    private OperatorPilotAuthorityCanonicalEncoder() {
    }

    public static String digest(OperatorPilotAuthority value) {
        return CanonicalDigestSupport.sha256(encode(value));
    }

    public static String encode(OperatorPilotAuthority value) {
        Objects.requireNonNull(value, "authority must not be null");
        return "{" +
                "\"schemaVersion\":" + CanonicalDigestSupport.quote(OperatorPilotAuthority.DIGEST_SCHEMA) +
                ",\"authorityId\":" + CanonicalDigestSupport.quote(value.id().toString()) +
                ",\"ownerUserId\":" + value.ownerUserId() +
                ",\"exchangeAccountId\":" + value.exchangeAccountId() +
                ",\"credentialReferenceId\":" + value.credentialReferenceId() +
                ",\"instrument\":" + CanonicalDigestSupport.quote(value.instrument()) +
                ",\"side\":" + CanonicalDigestSupport.quote(value.side().name()) +
                ",\"orderType\":" + CanonicalDigestSupport.quote(value.orderType().name()) +
                ",\"maxNotional\":" + CanonicalDigestSupport.decimal(value.maxNotional()) +
                ",\"maxPlaceCount\":" + value.maxPlaceCount() +
                ",\"maxCancelCount\":" + value.maxCancelCount() +
                ",\"transferAllowed\":" + value.transferAllowed() +
                ",\"withdrawAllowed\":" + value.withdrawAllowed() +
                ",\"validFrom\":" + CanonicalDigestSupport.instant(value.validFrom()) +
                ",\"expiresAt\":" + CanonicalDigestSupport.instant(value.expiresAt()) +
                ",\"createdBy\":" + value.createdBy() +
                ",\"createdAt\":" + CanonicalDigestSupport.instant(value.createdAt()) + "}";
    }
}
