package com.guidinglight.nexusquant.adapter.binance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.adapter.binance.model.BinanceApiCredentials;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceKeyType;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

import org.junit.jupiter.api.Test;

/**
 * BinanceRequestSignerTest 覆盖 HMAC 与 Ed25519 两条签名路径。
 * <p>
 * Why:
 * 这个 PR 的唯一目标是补齐 Ed25519 signer，同时不能破坏现有 HMAC Testnet 路径。
 * 因此签名分发器本身必须有独立测试，避免问题只在 HttpClient/运行态才暴露。
 */
class BinanceRequestSignerTest {

    @Test
    void shouldKeepHmacSha256SignatureStable() {
        BinanceRequestSigner signer = new BinanceRequestSigner();
        BinanceApiCredentials credentials = new BinanceApiCredentials("api-key", "secret-key");

        String signature = signer.sign("symbol=BTCUSDT&timestamp=1700000000123", credentials);

        assertEquals("42c1a9372eb3e22596962c9af2b57b5754b14aff1c9ecb5e02315edb555baf02", signature);
    }

    @Test
    void shouldSignWithEd25519PrivateKey() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String privateKeyPem = toPem(keyPair);
        BinanceRequestSigner signer = new BinanceRequestSigner();
        BinanceApiCredentials credentials = new BinanceApiCredentials(
                "api-key",
                "",
                BinanceKeyType.ED25519,
                privateKeyPem,
                null
        );
        String query = "symbol=BTCUSDT&timestamp=1700000000123&recvWindow=5000";

        String signatureBase64 = signer.sign(query, credentials);

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(keyPair.getPublic());
        verifier.update(query.getBytes(StandardCharsets.UTF_8));
        assertTrue(verifier.verify(Base64.getDecoder().decode(signatureBase64)));
    }

    private String toPem(KeyPair keyPair) {
        return "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
    }
}
