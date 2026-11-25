package com.guidinglight.nexusquant.scheduler.paper;

import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.ledger.contracts.model.AccountBalanceBasis;
import com.guidinglight.nexusquant.ledger.contracts.model.AccountSnapshotProjection;
import com.guidinglight.nexusquant.ledger.contracts.model.PositionProjection;
import com.guidinglight.nexusquant.ledger.contracts.model.TradeLedgerRequest;
import com.guidinglight.nexusquant.ledger.service.port.LedgerPostingRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SimAccountProjectionRepository {
    private static final String SYMBOL = "BTC-USDT";
    private static final String QUOTE = "USDT";
    private static final String BASE = "BTC";
    private static final String SIM = "SIM";
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(8);

    private final JdbcTemplate jdbc;
    private final LedgerPostingRepository ledger;
    private final StrategySimDecisionRepository decisions;

    public SimAccountProjectionRepository(JdbcTemplate jdbc, LedgerPostingRepository ledger,
            StrategySimDecisionRepository decisions) {
        this.jdbc = jdbc;
        this.ledger = ledger;
        this.decisions = decisions;
    }

    void lock(long account) {
        decisions.lockAccount(account);
        ledger.lockSnapshotCurrencies(account, List.of(BASE, QUOTE));
    }

    /** 先验证经济事实，再按同账户锁内生成的 POSTED 序号重放。成交时间和随机 ID 不代表入账顺序。 */
    Expected expected(String run, long account, BigDecimal budget) {
        var settlement = decisions.settlement(run, account);
        if (settlement.blockingReason() != null) {
            throw new IllegalStateException(settlement.blockingReason());
        }
        if (settlement.pendingOrders() != 0) {
            throw new IllegalStateException("SIM_PREVIOUS_EXECUTION_PENDING");
        }
        return expectedFromSettledFacts(run, account, budget);
    }

    String verifyLocalProjection(String run, long account) {
        var budgets = jdbc.query("""
                SELECT (p.config_snapshot_json->>'budget')::numeric
                FROM paper_trading_runs p JOIN accounts a ON a.account_id=p.canonical_account_id
                JOIN tenants tenant ON tenant.tenant_id=p.tenant_id
                WHERE p.paper_run_id=? AND a.account_id=? AND tenant.tenant_kind='LOCAL'
                    AND a.venue='PAPER' AND a.account_code='STRATEGY-SIM-'||substring(p.paper_run_id from 5)
                """, (rs, row) -> rs.getBigDecimal(1), run, account);
        if (budgets.isEmpty()) return null;
        try {
            var expected = expectedFromSettledFacts(run, account, budgets.getFirst());
            var actual = actual(account, expected.position() != null);
            if (!Objects.equals(actual.holding(), expected.holding())) return "SIM_POSITION_PROJECTION_MISMATCH";
            return null;
        } catch (IllegalStateException failure) {
            return failure.getMessage();
        }
    }

    private Expected expectedFromSettledFacts(String run, long account, BigDecimal budget) {
        var postings = jdbc.query("""
                WITH entries AS MATERIALIZED (
                    SELECT * FROM ledger_entries WHERE account_id=? LIMIT 40003
                )
                SELECT e.ref_type,e.ref_id,e.idempotency_key,e.delta,e.balance_after,e.currency,
                       e.direction,v.ledger_event_id,v.valid,
                       o.side,t.trade_id,t.order_id,t.price,t.qty,t.fee,t.fee_currency,t.ts,t.trace_id AS trade_trace,
                       jsonb_build_array(to_jsonb(e),v.event_json,to_jsonb(t),to_jsonb(o))::text AS fact
                FROM entries e
                LEFT JOIN trades t ON e.ref_type='TRADE' AND t.trade_id=e.ref_id AND t.account_id=e.account_id
                LEFT JOIN orders o ON o.order_id=t.order_id AND o.account_id=t.account_id
                LEFT JOIN LATERAL (
                    SELECT min(x.ledger_event_id) AS ledger_event_id,
                           count(*)=1 AND bool_and(x.trace_id=e.trace_id AND x.payload_json=CASE
                               WHEN e.ref_type='TRADE' THEN jsonb_build_object('entry_id',e.entry_id,'delta',e.delta)
                               ELSE jsonb_build_object('source','STRATEGY_SIM_SIM_FUNDING') END) AS valid,
                           jsonb_agg(to_jsonb(x) ORDER BY x.ledger_event_id) AS event_json
                    FROM (SELECT * FROM ledger_events le WHERE le.entry_id=e.entry_id
                          AND le.event_type='POSTED' ORDER BY le.ledger_event_id LIMIT 2) x
                ) v ON true
                ORDER BY v.ledger_event_id NULLS LAST,e.entry_id
                """, (rs, row) -> posting(rs, account), account);
        if (postings.size() < 2 || postings.size() > 40002 || budget == null || budget.signum() <= 0) {
            throw new IllegalStateException("SIM_FUNDING_FACTS_UNPROVEN");
        }
        if (postings.stream().anyMatch(posting -> !posting.eventValid() || posting.eventId() <= 0)) {
            throw new IllegalStateException("SIM_LEDGER_POSTED_FACT_UNPROVEN");
        }
        funding(postings.get(0), run, budget, "CASH", "CREDIT");
        funding(postings.get(1), run, budget, "CONTRA", "DEBIT");

        var digest = digest();
        // 无成交订单也属于终态依据，水位必须绑定它们的状态与身份。
        jdbc.query("SELECT to_jsonb(o)::text FROM orders o WHERE account_id=? ORDER BY order_id LIMIT 10000",
                rs -> { addFact(digest, rs.getString(1)); }, account);
        BigDecimal cash = budget;
        PositionProjection position = null;
        for (int i = 0; i < postings.size(); i++) {
            Posting posting = postings.get(i);
            addFact(digest, posting.fact());
            if (i < 2) continue;
            var trade = posting.trade();
            if (trade == null || !QUOTE.equals(posting.currency()) || !"TRADE".equals(posting.type())) {
                throw new IllegalStateException("SIM_PROJECTION_SOURCE_UNPROVEN");
            }
            if (isMainLeg(posting, trade)) {
                verifyPostingGroup(postings, i, trade);
                position = position == null
                        ? new PositionProjection(account, SYMBOL, ZERO, ZERO, ZERO, trade.traceId())
                        : position;
                position = position.afterTrade(trade, BASE, trade.fee());
                if (position.qty().signum() < 0) {
                    throw new IllegalStateException("SIM_POSITION_FACTS_UNPROVEN");
                }
            }
            BigDecimal legBalance = cash.add(posting.delta());
            if (!equal(legBalance, posting.balanceAfter())) {
                throw new IllegalStateException("SIM_LEDGER_RUNNING_BALANCE_MISMATCH");
            }
            if (isMainLeg(posting, trade) || posting.key().equals(trade.tradeId() + ":LEDGER:FEE_1")) {
                cash = legBalance;
            }
        }
        if (cash.signum() < 0 || !equal(cash, ledger.currentBalance(account, QUOTE, SIM))) {
            throw new IllegalStateException("SIM_LEDGER_CASH_FACTS_MISMATCH");
        }
        return new Expected(cash, position, new Watermark(postings.getLast().eventId(),
                HexFormat.of().formatHex(digest.digest())));
    }

    private static void verifyPostingGroup(List<Posting> postings, int offset, TradeLedgerRequest trade) {
        // 同账户事务锁序列化整笔入账，同一成交的后续分录须紧随主腿。
        List<String> suffixes = trade.fee().signum() == 0 ? List.of("2") : List.of("2", "FEE_1", "FEE_2");
        for (String suffix : suffixes) {
            if (++offset >= postings.size() || !(trade.tradeId() + ":LEDGER:" + suffix).equals(postings.get(offset).key())) {
                throw new IllegalStateException("SIM_LEDGER_POSTING_ORDER_UNPROVEN");
            }
        }
    }

    private static void addFact(MessageDigest digest, String fact) {
        digest.update(fact.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '\n');
    }

    private static boolean isMainLeg(Posting posting, TradeLedgerRequest trade) {
        return posting.key().equals(trade.tradeId() + ":LEDGER:1");
    }

    private static void funding(Posting row, String run, BigDecimal budget, String leg, String direction) {
        if (!("SIM_FUNDING_" + leg).equals(row.type()) || !run.equals(row.refId())
                || !(run + ":SIM_FUND:" + leg).equals(row.key()) || !QUOTE.equals(row.currency())
                || !direction.equals(row.direction()) || !equal(budget, row.balanceAfter())
                || !equal("CASH".equals(leg) ? budget : budget.negate(), row.delta())) {
            throw new IllegalStateException("SIM_FUNDING_FACTS_UNPROVEN");
        }
    }

    Actual actual(long account, boolean hadFills) {
        var assets = jdbc.query("""
                SELECT DISTINCT ON (currency) snapshot_id,currency,balance,available,frozen,
                       trade_env,balance_basis,balance_scope,to_jsonb(s)::text AS version
                FROM account_snapshots s WHERE account_id=? ORDER BY currency,snapshot_id DESC LIMIT 3
                """, (rs, n) -> new Snapshot(rs.getLong(1), rs.getString(2),
                        new Balance(amount(rs.getBigDecimal(3)), amount(rs.getBigDecimal(4)),
                                amount(rs.getBigDecimal(5)), rs.getString(6), rs.getString(7), rs.getString(8)),
                        rs.getString(9)), account);
        var positions = jdbc.query("""
                SELECT symbol,qty,available_qty,frozen_qty,avg_price,to_jsonb(p)::text
                FROM positions p WHERE account_id=? ORDER BY symbol LIMIT 2
                """, (rs, n) -> new StoredPosition(rs.getString(1), new Holding(amount(rs.getBigDecimal(2)),
                        amount(rs.getBigDecimal(3)), amount(rs.getBigDecimal(4)), amount(rs.getBigDecimal(5))),
                        rs.getString(6)), account);
        if (assets.stream().anyMatch(s -> !QUOTE.equals(s.currency()) && !(hadFills && BASE.equals(s.currency())))
                || positions.stream().anyMatch(p -> !SYMBOL.equals(p.symbol()) || !hadFills)
                || Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM account_snapshots
                        WHERE account_id=? AND trade_env IS DISTINCT FROM 'SIM')
                        """, Boolean.class, account))) {
            throw new IllegalStateException("SIM_PROJECTION_SCOPE_UNPROVEN");
        }
        return new Actual(assets.stream().filter(s -> QUOTE.equals(s.currency())).findFirst().orElse(null),
                assets.stream().filter(s -> BASE.equals(s.currency())).findFirst().orElse(null),
                positions.isEmpty() ? null : positions.getFirst());
    }

    void restore(long account, Expected expected, Actual actual, Instant at, String trace) {
        if (!Objects.equals(actual.cash(), expected.cash())) {
            append(account, QUOTE, expected.cashAmount(), at, trace, AccountBalanceBasis.LEDGER_CASH_PROJECTION);
        }
        if (!Objects.equals(actual.holding(), expected.holding())) {
            var position = Objects.requireNonNull(expected.position());
            ledger.upsertPosition(new PositionProjection(account, SYMBOL, position.qty(),
                    position.availableQty(), position.avgPrice(), trace), at);
        }
        if (!Objects.equals(actual.asset(), expected.asset())) {
            append(account, BASE, expected.position().qty(), at, trace, AccountBalanceBasis.POSITION_PROJECTION);
        }
    }

    private void append(long account, String currency, BigDecimal value, Instant at, String trace,
            AccountBalanceBasis basis) {
        ledger.insertAccountSnapshot(new AccountSnapshotProjection(account, currency, value, value, ZERO,
                at, trace, SIM, basis));
    }

    private static Posting posting(ResultSet rs, long account) throws SQLException {
        String tradeId = rs.getString("trade_id");
        TradeLedgerRequest trade = tradeId == null ? null : new TradeLedgerRequest(tradeId, rs.getString("order_id"),
                account, SYMBOL, OrderSide.valueOf(rs.getString("side")), rs.getBigDecimal("price"),
                rs.getBigDecimal("qty"), rs.getBigDecimal("fee"), rs.getString("fee_currency"),
                rs.getString("trade_trace"), rs.getTimestamp("ts").toInstant(), SIM);
        return new Posting(rs.getString("ref_type"), rs.getString("ref_id"), rs.getString("idempotency_key"),
                rs.getBigDecimal("delta"), rs.getBigDecimal("balance_after"), rs.getString("currency"),
                rs.getString("direction"), rs.getLong("ledger_event_id"), rs.getBoolean("valid"),
                rs.getString("fact"), trade);
    }

    static String sha256(String value) {
        return HexFormat.of().formatHex(digest().digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }

    private static boolean equal(BigDecimal left, BigDecimal right) {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    private static String amount(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static Balance balance(BigDecimal value, AccountBalanceBasis basis) {
        return new Balance(amount(value), amount(value), "0", SIM, basis.name(), "NQ_MANAGED_ACCOUNT");
    }

    record Expected(BigDecimal cashAmount, PositionProjection position, Watermark watermark) {
        Balance cash() { return balance(cashAmount, AccountBalanceBasis.LEDGER_CASH_PROJECTION); }
        Balance asset() { return position == null ? null : balance(position.qty(), AccountBalanceBasis.POSITION_PROJECTION); }
        Holding holding() {
            return position == null ? null : new Holding(amount(position.qty()), amount(position.availableQty()),
                    "0", amount(position.avgPrice()));
        }
    }

    record Actual(Snapshot quoteSnapshot, Snapshot baseSnapshot, StoredPosition position) {
        Balance cash() { return quoteSnapshot == null ? null : quoteSnapshot.balance(); }
        Balance asset() { return baseSnapshot == null ? null : baseSnapshot.balance(); }
        Holding holding() { return position == null ? null : position.holding(); }
        String version() {
            return sha256((quoteSnapshot == null ? "" : quoteSnapshot.version()) + "\n"
                    + (baseSnapshot == null ? "" : baseSnapshot.version()) + "\n"
                    + (position == null ? "" : position.version()));
        }
    }

    public record Balance(String balance, String available, String frozen, String tradeEnv, String basis, String scope) {}
    public record Holding(String qty, String available, String frozen, String avgPrice) {}
    public record Watermark(long postedThrough, String sha256) {}
    private record Snapshot(long id, String currency, Balance balance, String version) {}
    private record StoredPosition(String symbol, Holding holding, String version) {}
    private record Posting(String type, String refId, String key, BigDecimal delta, BigDecimal balanceAfter,
            String currency, String direction, long eventId, boolean eventValid, String fact, TradeLedgerRequest trade) {}
}
