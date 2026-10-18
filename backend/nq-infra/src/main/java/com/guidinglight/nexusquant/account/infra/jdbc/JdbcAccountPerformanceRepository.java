package com.guidinglight.nexusquant.account.infra.jdbc;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.performance.AccountEquitySample;
import com.guidinglight.nexusquant.account.domain.performance.AccountValuationInput;
import com.guidinglight.nexusquant.account.domain.performance.ClosedTradeStatistics;
import com.guidinglight.nexusquant.account.domain.port.AccountPerformanceRepository;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 同一事务快照读取余额与资金流；估值最多100种资产，价格通过一次批量读取取得。 */
public final class JdbcAccountPerformanceRepository implements AccountPerformanceRepository {
    private final JdbcTemplate jdbc;

    public JdbcAccountPerformanceRepository(JdbcTemplate jdbc) {
        // 不改变共享JdbcTemplate的全局选项，所有采样扫描设置自己的超时。
        this.jdbc = new JdbcTemplate(jdbc.getDataSource());
        this.jdbc.setQueryTimeout(10);
    }

    @Override
    public List<ExchangeAccountSummary> samplingCandidates(long afterAccountId, int limit) {
        if (limit < 1 || limit > 50)
            throw new IllegalArgumentException("sampling batch must be 1..50");
        return jdbc.query(
                """
                SELECT * FROM exchange_accounts WHERE status='ACTIVE' AND legacy_account_id IS NOT NULL
                AND exchange_account_id>? ORDER BY exchange_account_id LIMIT ?
                """,
                (rs, row) ->
                        new ExchangeAccountSummary(
                                rs.getLong("exchange_account_id"),
                                rs.getLong("legacy_account_id"),
                                rs.getLong("owner_user_id"),
                                rs.getString("exchange_code"),
                                rs.getString("trade_env"),
                                rs.getString("account_alias"),
                                rs.getString("external_account_ref"),
                                rs.getBoolean("is_default"),
                                rs.getString("status")),
                afterAccountId,
                limit);
    }

    @Override
    public boolean recentlySampled(long accountId, String currency, Instant now) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        """
                SELECT EXISTS(SELECT 1 FROM account_equity_measurements
                WHERE exchange_account_id=? AND quote_currency=? AND sampled_at>?)
                """,
                        Boolean.class,
                        accountId,
                        currency,
                        Timestamp.from(now.minusSeconds(900))));
    }

    @Override
    public AccountValuationInput readValuation(
            ExchangeAccountSummary account, String quote, Instant now) {
        long id = account.legacyAccountId();
        var snapshots =
                jdbc.query(
                        """
                SELECT DISTINCT ON(currency) snapshot_id,currency,balance,available,frozen,balance_basis,recorded_at,ts
                FROM account_snapshots WHERE account_id=? AND trade_env=? ORDER BY currency,snapshot_id DESC LIMIT 101
                """,
                        (rs, row) ->
                                new Balance(
                                        rs.getLong("snapshot_id"),
                                        rs.getString("currency"),
                                        rs.getBigDecimal("balance"),
                                        rs.getBigDecimal("available"),
                                        rs.getBigDecimal("frozen"),
                                        rs.getString("balance_basis"),
                                        rs.getTimestamp("recorded_at").toInstant(),
                                        rs.getTimestamp("ts").toInstant()),
                        id,
                        account.tradeEnv());
        boolean ambiguous =
                Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                """
                SELECT EXISTS(SELECT 1 FROM account_snapshots WHERE account_id=? AND (trade_env IS NULL OR trade_env<>?))
                OR EXISTS(SELECT 1 FROM trades WHERE account_id=? AND trade_env<>?)
                OR EXISTS(SELECT 1 FROM positions p WHERE p.account_id=? AND NOT EXISTS(
                    SELECT 1 FROM account_snapshots s WHERE s.account_id=p.account_id AND s.currency=split_part(replace(p.symbol,'/','-'),'-',1)
                    AND s.trade_env=? AND s.balance_basis='POSITION_PROJECTION'
                    AND s.snapshot_id=(SELECT max(s2.snapshot_id) FROM account_snapshots s2 WHERE s2.account_id=s.account_id AND s2.currency=s.currency)
                    AND s.balance=(SELECT sum(p2.qty) FROM positions p2 WHERE p2.account_id=p.account_id
                        AND split_part(replace(p2.symbol,'/','-'),'-',1)=s.currency)))
                """,
                                Boolean.class,
                                id,
                                account.tradeEnv(),
                                id,
                                account.tradeEnv(),
                                id,
                                account.tradeEnv()));
        var symbols =
                snapshots.stream()
                        .limit(100)
                        .filter(s -> s.balance().signum() != 0 && !quote.equals(s.currency()))
                        .map(s -> s.currency() + "-" + quote)
                        .distinct()
                        .toList();
        Map<String, Price> prices = new HashMap<>();
        if (!symbols.isEmpty()) {
            var args =
                    new ArrayList<Object>(
                            List.of(
                                    account.exchangeCode(),
                                    Timestamp.from(now),
                                    Timestamp.from(now),
                                    Timestamp.from(now.minusSeconds(7200))));
            args.addAll(symbols);
            String placeholders = String.join(",", Collections.nCopies(symbols.size(), "?"));
            jdbc.query(
                    """
                    SELECT DISTINCT ON(symbol) symbol,marketdata_bar_id,close_price,close_time,available_at,source
                    FROM marketdata_bars WHERE exchange_code=? AND market_type='SPOT' AND quality_status='OK'
                    AND "interval" IN ('1m','5m','15m','1h') AND close_time<=? AND available_at<=? AND close_time>=?
                    AND close_price>0 AND symbol IN (
                    """
                            + placeholders
                            + ") ORDER BY symbol,close_time DESC,marketdata_bar_id DESC LIMIT 100",
                    rs -> {
                        prices.put(
                                rs.getString("symbol"),
                                new Price(
                                        rs.getLong("marketdata_bar_id"),
                                        rs.getBigDecimal("close_price"),
                                        rs.getTimestamp("close_time").toInstant(),
                                        rs.getTimestamp("available_at").toInstant(),
                                        rs.getString("source")));
                    },
                    args.toArray());
        }
        boolean flowComplete =
                "SIM".equals(account.tradeEnv())
                        && !ambiguous
                        && !Boolean.TRUE.equals(
                                jdbc.queryForObject(
                                        """
                SELECT EXISTS(SELECT 1 FROM ledger_entries e WHERE account_id=? AND (
                    ref_type NOT IN ('SIM_FUNDING_CASH','SIM_FUNDING_CONTRA','TRADE')
                    OR (ref_type IN ('SIM_FUNDING_CASH','SIM_FUNDING_CONTRA') AND currency<>?)
                    OR ts>? OR created_at>?
                    OR (ref_type='TRADE' AND NOT EXISTS(SELECT 1 FROM trades t WHERE t.trade_id=e.ref_id AND t.account_id=e.account_id AND t.trade_env='SIM'))))
                OR EXISTS(SELECT 1 FROM ledger_entries WHERE account_id=? AND ref_type IN('SIM_FUNDING_CASH','SIM_FUNDING_CONTRA')
                    GROUP BY ref_id,currency HAVING count(*) FILTER(WHERE ref_type='SIM_FUNDING_CASH')<>1
                    OR count(*) FILTER(WHERE ref_type='SIM_FUNDING_CONTRA')<>1 OR sum(delta)<>0)
                """,
                                        Boolean.class,
                                        id,
                                        quote,
                                        Timestamp.from(now),
                                        Timestamp.from(now),
                                        id));
        BigDecimal funding =
                flowComplete
                        ? jdbc.queryForObject(
                                "SELECT coalesce(sum(delta),0) FROM ledger_entries WHERE account_id=? AND currency=? AND ref_type='SIM_FUNDING_CASH'",
                                BigDecimal.class,
                                id,
                                quote)
                        : null;
        var assets =
                snapshots.stream()
                        .limit(100)
                        .map(
                                s -> {
                                    Price p = prices.get(s.currency() + "-" + quote);
                                    return new AccountValuationInput.Asset(
                                            s.id(),
                                            s.currency(),
                                            s.balance(),
                                            s.available(),
                                            s.frozen(),
                                            s.basis(),
                                            s.recordedAt(),
                                            quote.equals(s.currency())
                                                    ? BigDecimal.ONE
                                                    : p == null ? null : p.value(),
                                            p == null ? null : p.id(),
                                            p == null ? null : p.at(),
                                            p == null ? null : p.availableAt(),
                                            quote.equals(s.currency())
                                                    ? "QUOTE_CURRENCY"
                                                    : p == null ? "UNAVAILABLE" : p.source());
                                })
                        .toList();
        boolean complete =
                !ambiguous
                        && snapshots.size() <= 100
                        && snapshots.stream()
                                .allMatch(
                                        s ->
                                                !s.recordedAt().isAfter(now)
                                                        && !s.eventAt().isAfter(now)
                                                        && ("POSITION_PROJECTION".equals(s.basis())
                                                                || "LEDGER_CASH_PROJECTION"
                                                                        .equals(s.basis())));
        var fundingEntries =
                jdbc.query(
                        """
                SELECT entry_id,ref_id,currency,delta,ts FROM ledger_entries WHERE account_id=? AND ref_type='SIM_FUNDING_CASH'
                ORDER BY ts DESC,entry_id LIMIT 100
                """,
                        (rs, row) ->
                                new AccountValuationInput.FundingEntry(
                                        rs.getString("entry_id"),
                                        rs.getString("ref_id"),
                                        rs.getString("currency"),
                                        rs.getBigDecimal("delta"),
                                        rs.getTimestamp("ts").toInstant()),
                        id);
        long entryCount =
                jdbc.queryForObject(
                        "SELECT count(*) FROM ledger_entries WHERE account_id=? AND ref_type='SIM_FUNDING_CASH'",
                        Long.class,
                        id);
        return new AccountValuationInput(
                assets,
                complete,
                funding,
                flowComplete ? "SIM_CANONICAL_FUNDING" : "UNAVAILABLE_OR_AMBIGUOUS",
                fundingEntries,
                entryCount);
    }

    @Override
    public void save(AccountEquitySample sample) {
        jdbc.update(
                """
                INSERT INTO account_equity_measurements(sample_id,exchange_account_id,quote_currency,sampled_at,sampling_bucket,
                equity,cumulative_net_flow,status,source_json) VALUES(?,?,?,?,?,?,?,?,?::jsonb)
                ON CONFLICT(exchange_account_id,quote_currency,sampling_bucket) DO NOTHING
                """,
                sample.sampleId(),
                sample.exchangeAccountId(),
                sample.quoteCurrency(),
                Timestamp.from(sample.sampledAt()),
                Math.floorDiv(sample.sampledAt().getEpochSecond(), 900),
                sample.equity(),
                sample.cumulativeNetFlow(),
                sample.status(),
                sample.sourceJson());
    }

    @Override
    public List<AccountEquitySample> findRange(
            long accountId, String currency, Instant from, Instant to, int limit) {
        if (limit < 1 || limit > 1001)
            throw new IllegalArgumentException("measurement query limit exceeded");
        return jdbc.query(
                """
                SELECT * FROM account_equity_measurements WHERE exchange_account_id=? AND quote_currency=? AND sampled_at BETWEEN ? AND ?
                ORDER BY sampled_at DESC LIMIT ?
                """,
                (rs, row) ->
                        new AccountEquitySample(
                                rs.getObject("sample_id", UUID.class),
                                rs.getLong("exchange_account_id"),
                                rs.getString("quote_currency"),
                                rs.getTimestamp("sampled_at").toInstant(),
                                rs.getBigDecimal("equity"),
                                rs.getBigDecimal("cumulative_net_flow"),
                                rs.getString("status"),
                                rs.getString("source_json")),
                accountId,
                currency,
                Timestamp.from(from),
                Timestamp.from(to),
                limit);
    }

    /** 只清理超过366天的派生测量，最多500条；不删除投影、订单或账务来源。 */
    public void pruneDerivedHistory(Instant now) {
        jdbc.update(
                """
                DELETE FROM account_equity_measurements WHERE sample_id IN(
                SELECT sample_id FROM account_equity_measurements WHERE sampled_at<? ORDER BY sampled_at LIMIT 500)
                """,
                Timestamp.from(now.minusSeconds(366L * 86400)));
    }

    @Override
    public TradeHistory readTradeHistory(
            ExchangeAccountSummary account, String quote, Instant through) {
        if (account.legacyAccountId() == null) return new TradeHistory(List.of(), false);
        long id = account.legacyAccountId();
        // 当前只有SIM能证明从零仓位开始；LIVE同步余额没有完整开仓成本，不能据局部成交估算胜率。
        boolean complete =
                "SIM".equals(account.tradeEnv())
                        && !Boolean.TRUE.equals(
                                jdbc.queryForObject(
                                        """
                SELECT EXISTS(SELECT 1 FROM ledger_entries WHERE account_id=? AND ts<=? AND (
                    ref_type NOT IN ('SIM_FUNDING_CASH','SIM_FUNDING_CONTRA','TRADE')
                    OR (ref_type IN ('SIM_FUNDING_CASH','SIM_FUNDING_CONTRA') AND currency<>?)))
                OR EXISTS(SELECT 1 FROM trades t LEFT JOIN orders o ON o.order_id=t.order_id
                    WHERE t.account_id=? AND t.ts<=? AND (t.trade_env<>? OR t.exchange_code<>? OR t.created_at>?
                        OR o.order_id IS NULL OR o.account_id<>t.account_id OR o.trade_env<>t.trade_env OR o.exchange_code<>t.exchange_code))
                """,
                                        Boolean.class,
                                        id,
                                        Timestamp.from(through),
                                        quote,
                                        id,
                                        Timestamp.from(through),
                                        account.tradeEnv(),
                                        account.exchangeCode(),
                                        Timestamp.from(through)));
        var trades =
                jdbc.query(
                        """
                SELECT t.trade_id,t.symbol,o.side,t.qty,t.price,t.fee,t.fee_currency,t.ts
                FROM trades t JOIN orders o ON o.order_id=t.order_id AND o.account_id=t.account_id
                WHERE t.account_id=? AND t.trade_env=? AND t.exchange_code=? AND t.ts<=?
                ORDER BY t.ts,t.trade_id LIMIT 10001
                """,
                        (rs, row) ->
                                new ClosedTradeStatistics.Trade(
                                        rs.getString(1),
                                        rs.getString(2),
                                        rs.getString(3),
                                        rs.getBigDecimal(4),
                                        rs.getBigDecimal(5),
                                        rs.getBigDecimal(6),
                                        rs.getString(7),
                                        rs.getTimestamp(8).toInstant()),
                        id,
                        account.tradeEnv(),
                        account.exchangeCode(),
                        Timestamp.from(through));
        return new TradeHistory(trades, complete);
    }

    private record Balance(
            long id,
            String currency,
            BigDecimal balance,
            BigDecimal available,
            BigDecimal frozen,
            String basis,
            Instant recordedAt,
            Instant eventAt) {}

    private record Price(
            long id, BigDecimal value, Instant at, Instant availableAt, String source) {}
}
