package com.guidinglight.nexusquant.account.infra.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.domain.CredentialOperation;
import com.guidinglight.nexusquant.account.domain.CredentialOperationReceipt;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.port.CredentialOperationRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 仅持久化HMAC和脱敏摘要，指纹密钥从现有主密钥分域派生，Mac实例不在线程间共享。 */
public class JdbcCredentialOperationRepository implements CredentialOperationRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final byte[] fingerprintKey;
    private final String keyId;

    public JdbcCredentialOperationRepository(
            JdbcTemplate jdbc, ObjectMapper mapper, String masterKey) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        if (masterKey == null || masterKey.isBlank())
            throw new IllegalArgumentException("credential fingerprint key required");
        byte[] master = masterKey.getBytes(StandardCharsets.UTF_8);
        try {
            fingerprintKey =
                    hmac(
                            master,
                            "NQ/credential-operation/fingerprint/v1"
                                    .getBytes(StandardCharsets.UTF_8));
        } finally {
            Arrays.fill(master, (byte) 0);
        }
        keyId =
                HexFormat.of()
                        .formatHex(
                                hmac(
                                        fingerprintKey,
                                        "key-identity".getBytes(StandardCharsets.UTF_8)));
    }

    /** 按操作身份后账户行的固定顺序加锁；不同操作也不能并发改写同账户活动版本链。 */
    @Override
    public void lock(Long owner, Long account, UUID operationId) {
        requireTransaction();
        jdbc.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
                rs -> {},
                "credential-operation:" + owner + ":" + account + ":" + operationId);
        // 不同命令也按账户串行，首次创建的检查与写入不会在新入口之间竞争。
        var locked =
                jdbc.query(
                        "SELECT exchange_account_id FROM exchange_accounts WHERE owner_user_id=? AND exchange_account_id=? FOR UPDATE",
                        (rs, row) -> rs.getLong(1),
                        owner,
                        account);
        if (locked.isEmpty())
            throw new IllegalStateException("credential operation account is no longer available");
    }

    @Override
    public Optional<CredentialOperationReceipt> find(Long owner, Long account, UUID operationId) {
        return jdbc
                .query(
                        """
                SELECT operation_type,input_hmac,fingerprint_key_id,key_version,result_snapshot::text,completed_at
                FROM credential_operation_receipts WHERE owner_user_id=? AND account_id=? AND operation_id=?
                """,
                        (rs, row) -> {
                            try {
                                return new CredentialOperationReceipt(
                                        owner,
                                        account,
                                        operationId,
                                        CredentialOperation.Type.valueOf(rs.getString(1)),
                                        rs.getString(2),
                                        rs.getString(3),
                                        rs.getInt(4),
                                        mapper.readValue(
                                                rs.getString(5),
                                                ExchangeAccountCredentialSummary.class),
                                        rs.getTimestamp(6).toInstant());
                            } catch (JsonProcessingException failure) {
                                throw new IllegalStateException(
                                        "credential operation receipt cannot be read");
                            }
                        },
                        owner,
                        account,
                        operationId)
                .stream()
                .findFirst();
    }

    @Override
    public String fingerprint(byte[] input) {
        return HexFormat.of().formatHex(hmac(fingerprintKey, input));
    }

    @Override
    public String fingerprintKeyId() {
        return keyId;
    }

    /** 仅保存HMAC及白名单凭据摘要；和生命周期、验证及审计共用事务，不持久化原材料或原因文本。 */
    @Override
    public void save(CredentialOperationReceipt r) {
        requireTransaction();
        try {
            if (jdbc.update(
                            """
                    INSERT INTO credential_operation_receipts(owner_user_id,account_id,operation_id,operation_type,input_hmac,
                        fingerprint_key_id,key_version,credential_id,result_snapshot,completed_at)
                    VALUES(?,?,?,?,?,?,?,?,CAST(? AS jsonb),?)
                    """,
                            r.ownerUserId(),
                            r.accountId(),
                            r.operationId(),
                            r.type().name(),
                            r.inputHmac(),
                            r.fingerprintKeyId(),
                            r.keyVersion(),
                            r.result().credentialId(),
                            mapper.writeValueAsString(r.result()),
                            Timestamp.from(r.completedAt()))
                    != 1)
                throw new IllegalStateException("credential operation receipt was not stored");
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("credential operation receipt cannot be stored");
        }
    }

    private static byte[] hmac(byte[] key, byte[] input) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(input);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("credential operation fingerprint unavailable");
        }
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("credential operation requires an active transaction");
    }
}
