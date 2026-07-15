package com.guidinglight.nexusquant.account.infra.gatew;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateCredentialContext;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadError;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadException;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadTransport;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 按 owner/account/type 唯一选择并在 JDBC infrastructure 内临时解密 OKX credential。
 *
 * <p>先 count 后 decrypt，确保 0/>1 候选不会解密；callback 同步执行且 context 在 finally 清零。
 * PostgreSQL/JDBC 解密结果必须短暂经过不可清理 String，这是已接受 P2，绝不跨出本类。</p>
 */
public final class JdbcOkxPrivateCredentialExecutor implements OkxPrivateCredentialExecutor {

    public static final String OKX_API_V5 = "OKX_API_V5";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String masterKey;
    private final OkxPrivateReadTransport transport;

    public JdbcOkxPrivateCredentialExecutor(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            String masterKey,
            OkxPrivateReadTransport transport
    ) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalArgumentException("credential master key must be configured");
        }
        this.masterKey = masterKey;
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
    }

    @Override
    public <T> T withActiveCredential(
            Long ownerId,
            Long exchangeAccountId,
            String credentialType,
            CredentialCallback<T> callback
    ) {
        try {
            return executeScoped(ownerId, exchangeAccountId, null, credentialType, callback);
        } catch (OkxPrivateReadException ex) {
            throw ex;
        } catch (Exception ex) {
            // JDBC/decrypt/Jackson cause 可能携带 payload 或参数片段，不保留原始 cause/message。
            throw new OkxPrivateReadException(OkxPrivateReadError.AUTHENTICATION_FAILURE);
        }
    }

    @Override
    public <T> T withActiveCredential(
            Long ownerId,
            Long exchangeAccountId,
            Long credentialId,
            String credentialType,
            CredentialCallback<T> callback
    ) {
        try {
            return executeScoped(ownerId, exchangeAccountId, credentialId, credentialType, callback);
        } catch (OkxPrivateReadException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new OkxPrivateReadException(OkxPrivateReadError.AUTHENTICATION_FAILURE);
        }
    }

    private <T> T executeScoped(
            Long ownerId,
            Long exchangeAccountId,
            Long credentialId,
            String credentialType,
            CredentialCallback<T> callback
    ) throws Exception {
        requirePositive(ownerId, "ownerId");
        requirePositive(exchangeAccountId, "exchangeAccountId");
        if (credentialId != null) {
            requirePositive(credentialId, "credentialId");
        }
        if (!OKX_API_V5.equals(credentialType)) {
            throw new OkxPrivateReadException(OkxPrivateReadError.CREDENTIAL_UNAVAILABLE);
        }
        Objects.requireNonNull(callback, "callback must not be null");

        Integer count = jdbcTemplate.queryForObject(
                credentialSelectionSql("COUNT(1)", credentialId != null),
                Integer.class,
                selectionArguments(exchangeAccountId, credentialType, ownerId, credentialId)
        );
        int candidates = count == null ? 0 : count;
        if (candidates == 0) {
            throw new OkxPrivateReadException(OkxPrivateReadError.CREDENTIAL_UNAVAILABLE);
        }
        if (candidates != 1) {
            throw new OkxPrivateReadException(OkxPrivateReadError.CREDENTIAL_CONFLICT);
        }

        String decryptedPayload = jdbcTemplate.queryForObject(
                credentialSelectionSql("pgp_sym_decrypt(c.encrypted_payload, ?)", credentialId != null),
                String.class,
                decryptArguments(exchangeAccountId, credentialType, ownerId, credentialId)
        );
        char[] apiKey = null;
        char[] secretKey = null;
        char[] passphrase = null;
        try {
            JsonNode payload = objectMapper.readTree(decryptedPayload);
            apiKey = required(payload, "apiKey").toCharArray();
            secretKey = required(payload, "secretKey").toCharArray();
            passphrase = required(payload, "passphrase").toCharArray();
            try (OkxPrivateCredentialContext credential = new OkxPrivateCredentialContext(
                    apiKey,
                    secretKey,
                    passphrase
            )) {
                Thread ownerThread = Thread.currentThread();
                AtomicBoolean active = new AtomicBoolean(true);
                CredentialSession session = (request, environment) -> {
                    if (!active.get() || Thread.currentThread() != ownerThread) {
                        throw new OkxPrivateReadException(OkxPrivateReadError.AUTHENTICATION_FAILURE);
                    }
                    return transport.execute(request, credential, environment);
                };
                try {
                    return Objects.requireNonNull(
                            callback.execute(session),
                            "credential callback result must not be null"
                    );
                } finally {
                    // session 即使被外部捕获也会立即失效，且跨线程调用始终拒绝。
                    active.set(false);
                }
            }
        } finally {
            clear(apiKey);
            clear(secretKey);
            clear(passphrase);
        }
    }

    private static String credentialSelectionSql(String selection, boolean exactCredential) {
        return """
                SELECT %s
                FROM exchange_account_credentials c
                JOIN exchange_accounts a
                  ON a.exchange_account_id = c.exchange_account_id
                WHERE c.exchange_account_id = ?
                  AND c.credential_type = ?
                  AND c.is_active = TRUE
                  AND c.credential_status = 'ACTIVE'
                  AND c.revoked_at IS NULL
                  AND c.rotated_at IS NULL
                  AND a.owner_user_id = ?
                  AND a.exchange_code = 'OKX'
                  AND a.status = 'ACTIVE'
                %s
                """.formatted(
                selection,
                exactCredential ? "  AND c.credential_id = ?" : ""
        );
    }

    private static Object[] selectionArguments(
            Long exchangeAccountId,
            String credentialType,
            Long ownerId,
            Long credentialId
    ) {
        return credentialId == null
                ? new Object[]{exchangeAccountId, credentialType, ownerId}
                : new Object[]{exchangeAccountId, credentialType, ownerId, credentialId};
    }

    private Object[] decryptArguments(
            Long exchangeAccountId,
            String credentialType,
            Long ownerId,
            Long credentialId
    ) {
        return credentialId == null
                ? new Object[]{masterKey, exchangeAccountId, credentialType, ownerId}
                : new Object[]{masterKey, exchangeAccountId, credentialType, ownerId, credentialId};
    }

    @Override
    public String toString() {
        return "JdbcOkxPrivateCredentialExecutor[REDACTED]";
    }

    private static String required(JsonNode payload, String field) {
        JsonNode value = payload == null ? null : payload.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("credential payload is incomplete");
        }
        return value.asText();
    }

    private static void clear(char[] value) {
        if (value != null) {
            Arrays.fill(value, '\0');
        }
    }

    private static void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }
}
