package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.common.tenant.TenantIdentity;
import com.guidinglight.nexusquant.ledger.application.service.SimCashFundingService;
import com.guidinglight.nexusquant.ledger.infra.jdbc.JdbcLedgerPostingRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

/** 旧隔离证明只补齐新的账户初始化前提；注册与邮箱产品路径由独立 HTTP 用例覆盖。 */
final class PublicDemoAccountTestFixture {
    private PublicDemoAccountTestFixture() {}

    static String initialize(TenantIsolationTestDatabase db,TenantIdentity identity,long account) {
        return db.as(identity,()->new TransactionTemplate(db.manager).execute(status->{
            long exchange=db.jdbc.queryForObject("INSERT INTO exchange_accounts(owner_user_id,exchange_code,trade_env,account_alias) VALUES(?,'PAPER','SIM','Demo fixture') RETURNING exchange_account_id",
                    Long.class,identity.userId());
            db.jdbc.update("UPDATE accounts SET account_code=? WHERE account_id=?","nq-paper-sim-"+exchange,account);
            db.jdbc.update("UPDATE exchange_accounts SET legacy_account_id=? WHERE exchange_account_id=?",account,exchange);
            String reserved="ptr-"+UUID.randomUUID();
            db.jdbc.update("INSERT INTO tenant_sim_accounts(tenant_id,exchange_account_id,account_id,reserved_paper_run_id) VALUES(?,?,?,?)",
                    identity.tenantId().value(),exchange,account,reserved);
            new SimCashFundingService(new JdbcLedgerPostingRepository(db.jdbc)).fundOnce(account,reserved,new BigDecimal("10000.00000000"),"fixture");
            return reserved;
        }));
    }
}
