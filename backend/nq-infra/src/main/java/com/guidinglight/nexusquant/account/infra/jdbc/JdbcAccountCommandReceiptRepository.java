package com.guidinglight.nexusquant.account.infra.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.domain.AccountCommand;
import com.guidinglight.nexusquant.account.domain.AccountCommandReceipt;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.port.AccountCommandReceiptRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

/** 回执只有成功事务才可见；查询不到不能证明原请求失败或停止。 */
public class JdbcAccountCommandReceiptRepository implements AccountCommandReceiptRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcAccountCommandReceiptRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public void lock(Long owner, UUID commandId) {
        requireTransaction();
        // 哈希碰撞只增加串行等待，不决定对象身份；主键和完整输入指纹仍负责幂等判定。
        jdbc.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                result -> {},
                owner + ":" + commandId);
    }

    @Override
    public Optional<AccountCommandReceipt> find(Long owner, UUID commandId) {
        return jdbc
                .query(
                        """
                SELECT command_type, request_sha256, result_snapshot::text, completed_at
                FROM account_command_receipts WHERE owner_user_id=? AND command_id=?
                """,
                        (rs, row) -> {
                            try {
                                return new AccountCommandReceipt(
                                        owner,
                                        commandId,
                                        AccountCommand.Type.valueOf(rs.getString(1)),
                                        rs.getString(2),
                                        mapper.readValue(
                                                rs.getString(3), ExchangeAccountSummary.class),
                                        rs.getTimestamp(4).toInstant());
                            } catch (JsonProcessingException failure) {
                                throw new IllegalStateException(
                                        "account command receipt cannot be read", failure);
                            }
                        },
                        owner,
                        commandId)
                .stream()
                .findFirst();
    }

    /** 只插入成功回执，必须加入原业务事务；序列化或约束失败时不能留下已变更但无回执的账户。 */
    @Override
    public void save(AccountCommandReceipt receipt) {
        requireTransaction();
        try {
            int inserted =
                    jdbc.update(
                            """
                    INSERT INTO account_command_receipts(owner_user_id,command_id,command_type,request_sha256,account_id,result_snapshot,completed_at)
                    VALUES (?,?,?,?,?,CAST(? AS jsonb),?)
                    """,
                            receipt.ownerUserId(),
                            receipt.commandId(),
                            receipt.type().name(),
                            receipt.requestSha256(),
                            receipt.result().exchangeAccountId(),
                            mapper.writeValueAsString(receipt.result()),
                            Timestamp.from(receipt.completedAt()));
            if (inserted != 1)
                throw new IllegalStateException("account command receipt was not stored");
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("account command receipt cannot be stored", failure);
        }
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("account command requires an active transaction");
    }
}
