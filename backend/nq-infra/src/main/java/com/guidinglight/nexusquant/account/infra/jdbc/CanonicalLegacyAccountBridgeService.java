package com.guidinglight.nexusquant.account.infra.jdbc;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.port.SimAccountIdentityBridge;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Locale;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 为既有order/ledger链物化稳定的一对一legacy account identity；不读取credential。 */
public class CanonicalLegacyAccountBridgeService implements SimAccountIdentityBridge {

    private static final Set<String> SIM_VENUES = Set.of("OKX", "BINANCE");

    private final JdbcTemplate jdbc;

    public CanonicalLegacyAccountBridgeService(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Transactional
    public long resolveOrCreate(ExchangeAccountSummary supplied, String traceId, Instant occurredAt) {
        return resolveOrCreate(supplied, traceId, occurredAt, "LIVE");
    }

    @Override
    @Transactional
    public long resolveOrCreateSim(ExchangeAccountSummary supplied, String traceId, Instant occurredAt) {
        return resolveOrCreate(supplied, traceId, occurredAt, "SIM");
    }

    private long resolveOrCreate(ExchangeAccountSummary supplied, String traceId,
            Instant occurredAt, String expectedEnvironment) {
        if (supplied == null || traceId == null || traceId.isBlank() || occurredAt == null) {
            throw new IllegalArgumentException("canonical legacy bridge input is required");
        }
        List<ExchangeAccountSummary> locked = jdbc.query("""
                SELECT exchange_account_id,legacy_account_id,owner_user_id,exchange_code,trade_env,
                       account_alias,external_account_ref,is_default,status
                FROM exchange_accounts WHERE exchange_account_id=? FOR UPDATE
                """, (row, ignored) -> new ExchangeAccountSummary(
                row.getLong("exchange_account_id"), (Long) row.getObject("legacy_account_id"),
                row.getLong("owner_user_id"), row.getString("exchange_code"),
                row.getString("trade_env"), row.getString("account_alias"),
                row.getString("external_account_ref"), row.getBoolean("is_default"),
                row.getString("status")), supplied.exchangeAccountId());
        if (locked.size() != 1) {
            throw rejected("CANONICAL_LEGACY_ACCOUNT_BRIDGE_ACCOUNT_NOT_FOUND");
        }
        ExchangeAccountSummary account = locked.getFirst();
        if (!account.ownerUserId().equals(supplied.ownerUserId())
                || !account.exchangeAccountId().equals(supplied.exchangeAccountId())
                || !expectedEnvironment.equals(account.tradeEnv())
                || !("LIVE".equals(expectedEnvironment) ? "OKX".equals(account.exchangeCode())
                        : SIM_VENUES.contains(account.exchangeCode()))
                || !"ACTIVE".equals(account.status())) {
            throw rejected("CANONICAL_LEGACY_ACCOUNT_BRIDGE_SCOPE_MISMATCH");
        }
        String accountCode = "nq-" + account.exchangeCode().toLowerCase(Locale.ROOT)
                + "-" + expectedEnvironment.toLowerCase(Locale.ROOT) + "-" + account.exchangeAccountId();
        if (account.legacyAccountId() == null) {
            jdbc.update("""
                    INSERT INTO accounts(account_code,venue,status,created_at)
                    VALUES (?,?,'ACTIVE',?) ON CONFLICT (account_code) DO NOTHING
                    """, accountCode, account.exchangeCode(), Timestamp.from(occurredAt));
            Long legacyId = jdbc.queryForObject("""
                    SELECT account_id FROM accounts
                    WHERE account_code=? AND venue=? AND status='ACTIVE' FOR UPDATE
                    """, Long.class, accountCode, account.exchangeCode());
            if (legacyId == null || jdbc.update("""
                    UPDATE exchange_accounts SET legacy_account_id=?,updated_at=?
                    WHERE exchange_account_id=? AND owner_user_id=? AND legacy_account_id IS NULL
                    """, legacyId, Timestamp.from(occurredAt), account.exchangeAccountId(),
                    account.ownerUserId()) != 1) {
                throw rejected("CANONICAL_LEGACY_ACCOUNT_BRIDGE_WRITE_CONFLICT");
            }
            jdbc.update("""
                    INSERT INTO audit_logs(domain,action,actor_id,trace_id,detail_json,created_at)
                    VALUES ('ACCOUNT','CANONICAL_LEGACY_ACCOUNT_BRIDGE_CREATED',?,?,
                        jsonb_build_object('exchange_account_id',?,'legacy_account_id',?),?)
                    """, String.valueOf(account.ownerUserId()), traceId,
                    account.exchangeAccountId(), legacyId, Timestamp.from(occurredAt));
            return legacyId;
        }
        Integer exact = jdbc.queryForObject("""
                SELECT count(*) FROM accounts
                WHERE account_id=? AND account_code=? AND venue=? AND status='ACTIVE'
                """, Integer.class, account.legacyAccountId(), accountCode, account.exchangeCode());
        if (exact == null || exact != 1) {
            throw rejected("CANONICAL_LEGACY_ACCOUNT_BRIDGE_READBACK_MISMATCH");
        }
        return account.legacyAccountId();
    }

    private static LiveControlException rejected(String code) {
        return new LiveControlException(code, "canonical legacy account bridge rejected");
    }
}
