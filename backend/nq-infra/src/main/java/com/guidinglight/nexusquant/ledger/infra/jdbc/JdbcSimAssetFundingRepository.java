package com.guidinglight.nexusquant.ledger.infra.jdbc;

import com.guidinglight.nexusquant.ledger.contracts.model.SimAssetFundingPlan;
import com.guidinglight.nexusquant.ledger.service.port.SimAssetFundingRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/** receipt 仅冻结初始化来源；账户全部资产为空且绑定明确后才能产生 canonical 分录。 */
@Repository
public class JdbcSimAssetFundingRepository implements SimAssetFundingRepository {
    private final JdbcTemplate jdbc;

    public JdbcSimAssetFundingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void assertBoundSimAccount(long accountId, String paperRunId, boolean newFunding) {
        List<String> statuses =
                jdbc.query(
                        """
                SELECT p.status FROM paper_trading_runs p JOIN accounts a ON a.account_id=p.canonical_account_id
                WHERE p.paper_run_id=? AND p.canonical_account_id=? AND p.trade_env='SIM'
                  AND a.venue='PAPER' AND a.status='ACTIVE'
                  AND NOT EXISTS (SELECT 1 FROM exchange_accounts x WHERE x.legacy_account_id=a.account_id)
                FOR UPDATE OF p,a
                """,
                        (rs, row) -> rs.getString(1),
                        paperRunId,
                        accountId);
        if (statuses.size() != 1 || (newFunding && !"CREATED".equals(statuses.getFirst()))) {
            throw new IllegalStateException("SIM_ASSET_FUNDING_REQUIRES_BOUND_NEW_PAPER_ACCOUNT");
        }
    }

    @Override
    public void assertEmptyAccount(long accountId) {
        // 检查所有币种及零数量占位事实，不能只看 USDT 零余额而掩盖已有订单、仓位或第三资产。
        Boolean nonempty =
                jdbc.queryForObject(
                        """
                SELECT EXISTS(SELECT 1 FROM ledger_entries WHERE account_id=?)
                    OR EXISTS(SELECT 1 FROM account_snapshots WHERE account_id=?)
                    OR EXISTS(SELECT 1 FROM positions WHERE account_id=?)
                    OR EXISTS(SELECT 1 FROM orders WHERE account_id=?)
                    OR EXISTS(SELECT 1 FROM trades WHERE account_id=?)
                    OR EXISTS(SELECT 1 FROM sim_asset_funding_receipts WHERE account_id=?)
                """,
                        Boolean.class,
                        accountId,
                        accountId,
                        accountId,
                        accountId,
                        accountId,
                        accountId);
        if (!Boolean.FALSE.equals(nonempty))
            throw new IllegalStateException("SIM_ASSET_FUNDING_REQUIRES_EMPTY_ACCOUNT");
    }

    @Override
    public Optional<Receipt> findReceipt(long accountId, String paperRunId) {
        List<Receipt> rows =
                jdbc.query(
                        "SELECT * FROM sim_asset_funding_receipts WHERE account_id=? OR paper_run_id=?",
                        JdbcSimAssetFundingRepository::mapReceipt,
                        accountId,
                        paperRunId);
        if (rows.size() > 1
                || (rows.size() == 1
                        && (rows.getFirst().accountId() != accountId
                                || !rows.getFirst().paperRunId().equals(paperRunId)))) {
            throw new IllegalStateException("SIM_ASSET_FUNDING_ACCOUNT_RUN_IDENTITY_DRIFT");
        }
        return rows.stream().findFirst();
    }

    @Override
    public void insertReceipt(Receipt r) {
        SimAssetFundingPlan p = r.plan();
        jdbc.update(
                """
                INSERT INTO sim_asset_funding_receipts(paper_run_id,account_id,total_budget,quote_currency,asset_currency,
                    asset_budget_fraction,conversion_price,conversion_price_unit,observed_at,visible_at,as_of,
                    source_sha256,source_json,funded_at,trace_id,cash_entry_id,cash_contra_entry_id,asset_entry_id,asset_contra_entry_id)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                r.paperRunId(),
                r.accountId(),
                p.totalBudget(),
                p.quoteCurrency(),
                p.assetCurrency(),
                p.assetBudgetFraction(),
                p.conversionPrice(),
                p.conversionPriceUnit(),
                Timestamp.from(p.observedAt()),
                Timestamp.from(p.visibleAt()),
                Timestamp.from(p.asOf()),
                p.sourceSha256(),
                p.sourceJson(),
                Timestamp.from(r.fundedAt()),
                r.traceId(),
                r.cashEntryId(),
                r.cashContraEntryId(),
                r.assetEntryId(),
                r.assetContraEntryId());
    }

    @Override
    public void assertCompletePosting(Receipt r) {
        verifyCurrency(
                r,
                r.plan().quoteCurrency(),
                r.plan().cashBudget(),
                r.cashEntryId(),
                r.cashContraEntryId());
        verifyCurrency(
                r,
                r.plan().assetCurrency(),
                r.plan().assetQuantity(),
                r.assetEntryId(),
                r.assetContraEntryId());
        Integer count =
                jdbc.queryForObject(
                        """
                SELECT count(*) FROM ledger_entries WHERE account_id=? AND ref_id=? AND ref_type IN ('SIM_FUNDING_CASH','SIM_FUNDING_CONTRA')
                """,
                        Integer.class,
                        r.accountId(),
                        r.paperRunId());
        if (!Integer.valueOf(4).equals(count))
            throw new IllegalStateException("INCOMPLETE_SIM_ASSET_FUNDING");
    }

    private void verifyCurrency(
            Receipt r, String currency, BigDecimal amount, String cashId, String contraId) {
        verifyEntry(r, currency, amount, amount, cashId, "CASH", "SIM_FUNDING_CASH");
        verifyEntry(r, currency, amount.negate(), amount, contraId, "CONTRA", "SIM_FUNDING_CONTRA");
        Integer snapshots =
                jdbc.queryForObject(
                        """
                SELECT count(*) FROM account_snapshots WHERE account_id=? AND currency=? AND balance=? AND available=?
                  AND frozen=0 AND trade_env='SIM' AND balance_basis='LEDGER_CASH_PROJECTION'
                  AND balance_scope='NQ_MANAGED_ACCOUNT' AND ts=? AND trace_id=? AND recorded_at IS NOT NULL
                """,
                        Integer.class,
                        r.accountId(),
                        currency,
                        amount,
                        amount,
                        Timestamp.from(r.fundedAt()),
                        r.traceId());
        if (!Integer.valueOf(1).equals(snapshots))
            throw new IllegalStateException("INCOMPLETE_SIM_ASSET_FUNDING_SNAPSHOT");
    }

    private void verifyEntry(
            Receipt r,
            String currency,
            BigDecimal delta,
            BigDecimal balance,
            String id,
            String role,
            String refType) {
        Integer entries =
                jdbc.queryForObject(
                        """
                SELECT count(*) FROM ledger_entries e WHERE entry_id=? AND account_id=? AND currency=? AND delta=?
                  AND balance_after=? AND direction=? AND ref_type=? AND ref_id=? AND idempotency_key=? AND trace_id=? AND ts=?
                  AND (SELECT count(*) FROM ledger_events v WHERE v.entry_id=e.entry_id AND v.event_type='POSTED'
                      AND v.trace_id=? AND v.payload_json=jsonb_build_object('source','STRATEGY_SIM_ASSET_FUNDING',
                          'sourceSha256',?::text,'paperRunId',?::text,'currency',?::text,'role',?::text))=1
                """,
                        Integer.class,
                        id,
                        r.accountId(),
                        currency,
                        delta,
                        balance,
                        delta.signum() >= 0 ? "CREDIT" : "DEBIT",
                        refType,
                        r.paperRunId(),
                        r.paperRunId() + ":SIM_FUND:" + currency + ":" + role,
                        r.traceId(),
                        Timestamp.from(r.fundedAt()),
                        r.traceId(),
                        r.plan().sourceSha256(),
                        r.paperRunId(),
                        currency,
                        role);
        if (!Integer.valueOf(1).equals(entries))
            throw new IllegalStateException("INCOMPLETE_SIM_ASSET_FUNDING_POSTING");
    }

    private static Receipt mapReceipt(ResultSet rs, int row) throws SQLException {
        SimAssetFundingPlan plan =
                new SimAssetFundingPlan(
                        rs.getBigDecimal("total_budget"),
                        rs.getString("quote_currency"),
                        rs.getString("asset_currency"),
                        rs.getBigDecimal("asset_budget_fraction"),
                        rs.getBigDecimal("conversion_price"),
                        rs.getString("conversion_price_unit"),
                        rs.getTimestamp("observed_at").toInstant(),
                        rs.getTimestamp("visible_at").toInstant(),
                        rs.getTimestamp("as_of").toInstant(),
                        rs.getString("source_sha256"),
                        rs.getString("source_json"));
        return new Receipt(
                rs.getLong("account_id"),
                rs.getString("paper_run_id"),
                plan,
                rs.getTimestamp("funded_at").toInstant(),
                rs.getString("trace_id"),
                rs.getString("cash_entry_id"),
                rs.getString("cash_contra_entry_id"),
                rs.getString("asset_entry_id"),
                rs.getString("asset_contra_entry_id"));
    }
}
