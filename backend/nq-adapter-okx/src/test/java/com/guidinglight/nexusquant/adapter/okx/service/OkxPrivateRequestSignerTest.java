package com.guidinglight.nexusquant.adapter.okx.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OkxPrivateRequestSignerTest {

    @Test
    void matchesFixedClockVectorAndIncludesDeterministicQuery() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-13T00:00:00Z"), ZoneOffset.UTC);
        OkxPrivateRequestSigner signer = new OkxPrivateRequestSigner(clock);
        try (OkxPrivateCredentialContext credential = credential()) {
            String signature = signer.signatureForTest(
                    OkxPrivateReadRequest.accountBalance(List.of("ETH", "BTC")),
                    credential
            );

            assertEquals("2026-07-13T00:00:00.000Z", signer.timestampForTest());
            assertEquals("nDW/RiHQU1irNPwpzyEfsFYQyEQIh+x8RXZfGO2rfac=", signature);
            assertFalse(signer.signatureForTest(OkxPrivateReadRequest.accountBalance(List.of("BTC")), credential)
                    .equals(signature));
        }
    }

    @Test
    void redactsCredentialAndAuthenticatedHeadersToString() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-13T00:00:00Z"), ZoneOffset.UTC);
        OkxPrivateRequestSigner signer = new OkxPrivateRequestSigner(clock);
        try (OkxPrivateCredentialContext credential = credential();
             OkxPrivateRequestSigner.SignedHeaders headers = signer.sign(
                     OkxPrivateReadRequest.accountConfiguration(), credential)) {
            assertEquals("OkxPrivateCredentialContext[REDACTED]", credential.toString());
            assertEquals("SignedHeaders[REDACTED]", headers.toString());
        }
    }

    @Test
    void postSignatureBindsExactCanonicalBody() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-16T12:00:00Z"), ZoneOffset.UTC);
        OkxPrivateRequestSigner signer = new OkxPrivateRequestSigner(clock);
        try (OkxPrivateCredentialContext credential = credential();
             OkxPrivateRequestSigner.SignedHeaders first = signer.sign(
                     "POST", "/api/v5/trade/order", "{\"instId\":\"BTC-USDT\"}", credential);
             OkxPrivateRequestSigner.SignedHeaders changed = signer.sign(
                     "POST", "/api/v5/trade/order", "{\"instId\":\"ETH-USDT\"}", credential)) {
            assertFalse(first.values().get("OK-ACCESS-SIGN")
                    .equals(changed.values().get("OK-ACCESS-SIGN")));
        }
    }

    @Test
    void rejectsCredentialHeaderControlCharactersWithoutLeakingMarker() {
        String marker = "header-marker-should-not-escape";
        for (char control : new char[]{'\r', '\n', '\0', '\u001f', '\u007f'}) {
            IllegalArgumentException apiKeyError = assertThrows(
                    IllegalArgumentException.class,
                    () -> new OkxPrivateCredentialContext(
                            (marker + control).toCharArray(),
                            "test-secret".toCharArray(),
                            "test-passphrase".toCharArray()
                    )
            );
            assertFalse(apiKeyError.getMessage().contains(marker));

            IllegalArgumentException passphraseError = assertThrows(
                    IllegalArgumentException.class,
                    () -> new OkxPrivateCredentialContext(
                            "test-key".toCharArray(),
                            "test-secret".toCharArray(),
                            (marker + control).toCharArray()
                    )
            );
            assertFalse(passphraseError.getMessage().contains(marker));
        }
    }

    private static OkxPrivateCredentialContext credential() {
        return new OkxPrivateCredentialContext(
                "test-key".toCharArray(),
                "test-secret".toCharArray(),
                "test-passphrase".toCharArray()
        );
    }
}
