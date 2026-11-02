package com.guidinglight.nexusquant.account.infra.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.application.exception.ExchangeAccountNotFoundException;
import com.guidinglight.nexusquant.account.domain.fee.AccountFeeObservation;
import com.guidinglight.nexusquant.account.domain.fee.AccountFeeQuote;
import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms;
import com.guidinglight.nexusquant.account.domain.port.AccountFeeRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 有界费率读取；按owner/操作锁及账户锁串行，保存与成功回执是同一不可变行。 */
public final class JdbcAccountFeeRepository implements AccountFeeRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public JdbcAccountFeeRepository(JdbcTemplate source) {
        this.jdbc = new JdbcTemplate(source.getDataSource());
        this.jdbc.setQueryTimeout(20);
    }

    @Override
    public void lock(long owner, long account, UUID operationId) {
        requireTransaction();
        jdbc.execute("SET LOCAL lock_timeout='1500ms'");
        // 全部同步入口保持同一加锁顺序；并发等待超过限额交由调用方查询原身份收敛。
        jdbc.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
                rs -> {},
                "account-fee:" + owner + ":" + operationId);
        if (jdbc.query(
                        "SELECT exchange_account_id FROM exchange_accounts WHERE owner_user_id=? AND exchange_account_id=? FOR UPDATE",
                        (rs, row) -> rs.getLong(1),
                        owner,
                        account)
                .isEmpty()) throw new ExchangeAccountNotFoundException(account);
    }

    @Override
    public Optional<AccountFeeObservation> findOperation(long owner, UUID id) {
        return jdbc
                .query(
                        "SELECT * FROM account_fee_observations WHERE owner_user_id=? AND operation_id=?",
                        this::map,
                        owner,
                        id)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<AccountFeeObservation> findObservation(long owner, UUID id) {
        return jdbc
                .query(
                        "SELECT * FROM account_fee_observations WHERE owner_user_id=? AND observation_id=?",
                        this::map,
                        owner,
                        id)
                .stream()
                .findFirst();
    }

    @Override
    public List<AccountFeeObservation> history(
            long owner, long account, String symbol, UUID before, int limit) {
        if (limit < 1 || limit > 51)
            throw new IllegalArgumentException("fee history query limit exceeded");
        if (before == null)
            return jdbc.query(
                    """
                SELECT * FROM account_fee_observations WHERE owner_user_id=? AND exchange_account_id=? AND symbol=?
                ORDER BY observed_at DESC,observation_id DESC LIMIT ?
                """,
                    this::map,
                    owner,
                    account,
                    symbol,
                    limit);
        // 时间相同的观察以UUID继续排序，且游标子查询重复范围条件，避免跨账户翻页或漏行。
        return jdbc.query(
                """
                SELECT * FROM account_fee_observations WHERE owner_user_id=? AND exchange_account_id=? AND symbol=?
                AND (observed_at,observation_id)<(SELECT observed_at,observation_id FROM account_fee_observations
                    WHERE owner_user_id=? AND exchange_account_id=? AND symbol=? AND observation_id=?)
                ORDER BY observed_at DESC,observation_id DESC LIMIT ?
                """,
                this::map,
                owner,
                account,
                symbol,
                owner,
                account,
                symbol,
                before,
                limit);
    }

    @Override
    public void save(AccountFeeObservation r) {
        requireTransaction();
        // 观察即成功回执，单行插入让来源证据与幂等结果一起提交；数据库拒绝后续改写或删除。
        jdbc.update(
                """
                INSERT INTO account_fee_observations(observation_id,owner_user_id,exchange_account_id,operation_id,credential_id,
                exchange_code,trade_env,symbol,input_fingerprint,maker_provider_rate,taker_provider_rate,maker_cost_rate,taker_cost_rate,
                fee_tier,provider_at,observed_at,expires_at,source,rate_convention,source_sha256,provider_time_basis,commission_terms)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb)
                """,
                r.observationId(),
                r.ownerUserId(),
                r.exchangeAccountId(),
                r.operationId(),
                r.credentialId(),
                r.exchangeCode(),
                r.tradeEnv(),
                r.symbol(),
                r.inputFingerprint(),
                r.quote().makerProviderRate(),
                r.quote().takerProviderRate(),
                r.quote().makerCostRate(),
                r.quote().takerCostRate(),
                r.quote().tier(),
                r.quote().providerAt() == null ? null : Timestamp.from(r.quote().providerAt()),
                Timestamp.from(r.quote().observedAt()),
                Timestamp.from(r.expiresAt()),
                r.quote().source(),
                r.quote().rateConvention(),
                r.sourceSha256(),
                r.quote().providerTimeBasis(),
                r.quote().commissionTerms() == null
                        ? null
                        : r.quote().commissionTerms().snapshot(mapper).toString());
    }

    private AccountFeeObservation map(ResultSet rs, int index) throws SQLException {
        return new AccountFeeObservation(
                rs.getObject("observation_id", UUID.class),
                rs.getObject("operation_id", UUID.class),
                rs.getLong("owner_user_id"),
                rs.getLong("exchange_account_id"),
                rs.getLong("credential_id"),
                rs.getString("exchange_code"),
                rs.getString("trade_env"),
                rs.getString("symbol"),
                rs.getString("input_fingerprint"),
                new AccountFeeQuote(
                        rs.getBigDecimal("maker_provider_rate"),
                        rs.getBigDecimal("taker_provider_rate"),
                        rs.getString("fee_tier"),
                        rs.getTimestamp("provider_at") == null
                                ? null
                                : rs.getTimestamp("provider_at").toInstant(),
                        rs.getTimestamp("observed_at").toInstant(),
                        rs.getString("source"),
                        rs.getString("rate_convention"),
                        rs.getString("provider_time_basis"),
                        terms(rs.getString("commission_terms"))),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getString("source_sha256"));
    }

    /** 重读只解析已验证的非敏感来源字段；损坏行失败关闭，不把JSON或底层异常串入响应。 */
    private SpotCommissionTerms terms(String json) {
        if (json == null) return null;
        if (json.length() > 4096) throw new IllegalStateException("FEE_STORED_TERMS_INVALID");
        try {
            return SpotCommissionTerms.fromSnapshot(mapper.readTree(json));
        } catch (JsonProcessingException | IllegalArgumentException failure) {
            throw new IllegalStateException("FEE_STORED_TERMS_INVALID");
        }
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("fee evidence transaction required");
    }
}
