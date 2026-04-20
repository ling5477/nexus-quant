package com.guidinglight.nexusquant.account.infra.verification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialMaterial;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialVerificationResult;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialVerifier;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceApiCredentials;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceKeyType;
import com.guidinglight.nexusquant.adapter.binance.service.BinanceRequestSigner;
import com.guidinglight.nexusquant.adapter.okx.model.OkxApiCredentials;
import com.guidinglight.nexusquant.adapter.okx.service.OkxRequestSigner;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * StructuralExchangeAccountCredentialVerifier 提供 RC1-4 首版结构性校验。
 * <p>
 * Why:
 * 本轮只要求“凭证格式/签名能力”真实成立，不要求真实连通外部交易所；
 * 因此这里复用现有 signer/runtime 构造逻辑做结构性校验，并把结果统一回写到 verification 状态流。
 */
public class StructuralExchangeAccountCredentialVerifier implements ExchangeAccountCredentialVerifier {

    private final ObjectMapper objectMapper;
    private final String verificationMode;
    private final OkxRequestSigner okxRequestSigner;
    private final BinanceRequestSigner binanceRequestSigner;

    public StructuralExchangeAccountCredentialVerifier(
            ObjectMapper objectMapper,
            String verificationMode
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.verificationMode = Objects.requireNonNull(verificationMode, "verificationMode must not be null");
        this.okxRequestSigner = new OkxRequestSigner();
        this.binanceRequestSigner = new BinanceRequestSigner();
    }

    @Override
    public ExchangeAccountCredentialVerificationResult verify(ExchangeAccountCredentialMaterial credentialMaterial) {
        if (!"STRUCTURAL".equalsIgnoreCase(verificationMode)) {
            return ExchangeAccountCredentialVerificationResult.failed(
                    "unsupported verification mode: " + verificationMode
            );
        }
        try {
            JsonNode payload = objectMapper.readTree(credentialMaterial.decryptedPayloadJson());
            return switch (normalizeType(credentialMaterial.credentialType())) {
                case "OKX_API_V5" -> verifyOkx(payload);
                case "BINANCE_HMAC" -> verifyBinanceHmac(payload);
                case "BINANCE_ED25519" -> verifyBinanceEd25519(payload);
                default -> ExchangeAccountCredentialVerificationResult.failed(
                        "unsupported credential type: " + credentialMaterial.credentialType()
                );
            };
        } catch (Exception ex) {
            return ExchangeAccountCredentialVerificationResult.failed(safeMessage(ex));
        }
    }

    private ExchangeAccountCredentialVerificationResult verifyOkx(JsonNode payload) {
        OkxApiCredentials credentials = new OkxApiCredentials(
                requiredText(payload, "apiKey"),
                requiredText(payload, "secretKey"),
                requiredText(payload, "passphrase")
        );
        if (!credentials.isConfigured()) {
            return ExchangeAccountCredentialVerificationResult.failed("OKX credentials are not fully configured");
        }
        okxRequestSigner.signHeaders(
                credentials,
                "GET",
                "/api/v5/account/balance",
                "",
                Instant.now().toString()
        );
        return ExchangeAccountCredentialVerificationResult.success();
    }

    private ExchangeAccountCredentialVerificationResult verifyBinanceHmac(JsonNode payload) {
        BinanceApiCredentials credentials = new BinanceApiCredentials(
                requiredText(payload, "apiKey"),
                requiredText(payload, "secretKey")
        );
        if (!credentials.isConfigured()) {
            return ExchangeAccountCredentialVerificationResult.failed("Binance HMAC credentials are not fully configured");
        }
        binanceRequestSigner.sign("timestamp=1", credentials);
        return ExchangeAccountCredentialVerificationResult.success();
    }

    private ExchangeAccountCredentialVerificationResult verifyBinanceEd25519(JsonNode payload) {
        BinanceApiCredentials credentials = new BinanceApiCredentials(
                requiredText(payload, "apiKey"),
                null,
                BinanceKeyType.ED25519,
                requiredText(payload, "privateKeyPem"),
                null
        );
        if (!credentials.isConfigured()) {
            return ExchangeAccountCredentialVerificationResult.failed("Binance Ed25519 credentials are not fully configured");
        }
        binanceRequestSigner.sign("timestamp=1", credentials);
        return ExchangeAccountCredentialVerificationResult.success();
    }

    private String normalizeType(String credentialType) {
        return credentialType == null ? "" : credentialType.trim().toUpperCase(Locale.ROOT);
    }

    private String requiredText(JsonNode payload, String fieldName) {
        JsonNode field = payload.get(fieldName);
        if (field == null || field.isNull() || field.asText().isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return field.asText().trim();
    }

    private String safeMessage(Exception ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank()
                ? ex.getClass().getSimpleName()
                : ex.getMessage();
    }
}
