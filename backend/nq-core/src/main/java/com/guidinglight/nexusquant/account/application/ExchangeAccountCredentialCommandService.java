package com.guidinglight.nexusquant.account.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCredentialUpsertCommand;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

import org.springframework.transaction.annotation.Transactional;

/**
 * ExchangeAccountCredentialCommandService 提供凭证新增与轮换写侧。
 * <p>
 * Why:
 * 凭证版本链必须始终满足“同账户同类型只有一个 active”，
 * 因此新增版本、失效旧版本和 masked key 生成要在一个应用服务里统一完成。
 */
public class ExchangeAccountCredentialCommandService {

    private static final String DEFAULT_CIPHER_SUITE = "PGP_SYM_AES256";

    private final ExchangeAccountRepository exchangeAccountRepository;
    private final ExchangeAccountCredentialRepository exchangeAccountCredentialRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ExchangeAccountCredentialCommandService(
            ExchangeAccountRepository exchangeAccountRepository,
            ExchangeAccountCredentialRepository exchangeAccountCredentialRepository,
            ObjectMapper objectMapper
    ) {
        this(exchangeAccountRepository, exchangeAccountCredentialRepository, objectMapper, Clock.systemUTC());
    }

    ExchangeAccountCredentialCommandService(
            ExchangeAccountRepository exchangeAccountRepository,
            ExchangeAccountCredentialRepository exchangeAccountCredentialRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.exchangeAccountRepository = Objects.requireNonNull(
                exchangeAccountRepository,
                "exchangeAccountRepository must not be null"
        );
        this.exchangeAccountCredentialRepository = Objects.requireNonNull(
                exchangeAccountCredentialRepository,
                "exchangeAccountCredentialRepository must not be null"
        );
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public ExchangeAccountCredentialSummary upsert(
            Long ownerUserId,
            Long exchangeAccountId,
            ExchangeAccountCredentialUpsertCommand command,
            int keyVersion
    ) {
        requireOwnedAccount(ownerUserId, exchangeAccountId);
        String credentialType = normalizeCredentialType(command.credentialType());
        validatePayload(credentialType, command);
        Instant now = Instant.now(clock);
        Long rotatedFromCredentialId = exchangeAccountCredentialRepository.findActiveByAccountAndType(exchangeAccountId, credentialType)
                .map(ExchangeAccountCredentialSummary::credentialId)
                .orElse(null);
        if (rotatedFromCredentialId != null) {
            exchangeAccountCredentialRepository.deactivateActiveByAccountAndType(exchangeAccountId, credentialType, now);
        }
        return exchangeAccountCredentialRepository.insertNewVersion(
                exchangeAccountId,
                credentialType,
                toPayloadJson(credentialType, command),
                keyVersion,
                DEFAULT_CIPHER_SUITE,
                maskAccessKey(command.apiKey()),
                rotatedFromCredentialId,
                now
        );
    }

    public ExchangeAccountCredentialSummary requireActiveSummary(Long ownerUserId, Long exchangeAccountId) {
        return exchangeAccountCredentialRepository.findActiveSummary(
                requirePositive(ownerUserId, "ownerUserId"),
                requirePositive(exchangeAccountId, "exchangeAccountId")
        ).orElseThrow(() -> new ExchangeAccountCredentialNotFoundException(exchangeAccountId));
    }

    public ExchangeAccountCredentialSummary findActiveSummaryOrNull(Long ownerUserId, Long exchangeAccountId) {
        return exchangeAccountCredentialRepository.findActiveSummary(
                requirePositive(ownerUserId, "ownerUserId"),
                requirePositive(exchangeAccountId, "exchangeAccountId")
        ).orElse(null);
    }

    private void requireOwnedAccount(Long ownerUserId, Long exchangeAccountId) {
        exchangeAccountRepository.findByIdForOwner(
                requirePositive(ownerUserId, "ownerUserId"),
                requirePositive(exchangeAccountId, "exchangeAccountId")
        ).orElseThrow(() -> new ExchangeAccountNotFoundException(exchangeAccountId));
    }

    private String normalizeCredentialType(String credentialType) {
        String normalized = normalizeText(credentialType, "credentialType").toUpperCase(Locale.ROOT);
        if (!"OKX_API_V5".equals(normalized)
                && !"BINANCE_HMAC".equals(normalized)
                && !"BINANCE_ED25519".equals(normalized)) {
            throw new IllegalArgumentException("unsupported credentialType: " + credentialType);
        }
        return normalized;
    }

    private void validatePayload(String credentialType, ExchangeAccountCredentialUpsertCommand command) {
        normalizeText(command.apiKey(), "apiKey");
        switch (credentialType) {
            case "OKX_API_V5" -> {
                normalizeText(command.secretKey(), "secretKey");
                normalizeText(command.passphrase(), "passphrase");
            }
            case "BINANCE_HMAC" -> normalizeText(command.secretKey(), "secretKey");
            case "BINANCE_ED25519" -> normalizeText(command.privateKeyPem(), "privateKeyPem");
            default -> throw new IllegalArgumentException("unsupported credentialType: " + credentialType);
        }
    }

    private String toPayloadJson(String credentialType, ExchangeAccountCredentialUpsertCommand command) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("credentialType", credentialType);
        payload.put("apiKey", normalizeText(command.apiKey(), "apiKey"));
        payload.put("secretKey", normalizeNullableText(command.secretKey()));
        payload.put("passphrase", normalizeNullableText(command.passphrase()));
        payload.put("privateKeyPem", normalizeNullableText(command.privateKeyPem()));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize credential payload", ex);
        }
    }

    private String maskAccessKey(String apiKey) {
        String normalized = normalizeText(apiKey, "apiKey");
        if (normalized.length() <= 6) {
            return normalized.substring(0, Math.min(2, normalized.length())) + "***";
        }
        return normalized.substring(0, 3) + "***" + normalized.substring(normalized.length() - 2);
    }

    private Long requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    private String normalizeText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private String normalizeNullableText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
