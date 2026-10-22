package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.AccountTradeEnvironmentException;
import com.guidinglight.nexusquant.account.infra.jdbc.CanonicalLegacyAccountBridgeService;
import com.guidinglight.nexusquant.account.infra.jdbc.JdbcAccountTradeEnvironmentAuthority;
import com.guidinglight.nexusquant.common.tenant.TenantIdentity;
import com.guidinglight.nexusquant.ledger.application.service.SimCashFundingService;
import com.guidinglight.nexusquant.ledger.infra.jdbc.JdbcLedgerPostingRepository;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** 真实非 owner PostgreSQL 验证专用 PAPER 桥接，不能由现代映射提前或重复获得交易权。 */
@EnabledIfSystemProperty(named="nq.tenant.pg.required",matches="true")
class PublicDemoAccountIdentityPostgresIntegrationTest {
    @Test void paperRequiresDedicatedDemoBridgeAndBoundRunningCanonicalIdentity() throws Exception {
        try (var db=TenantIsolationTestDatabase.create(null)) {
            var demo=db.demo("IDENTITY_DEMO","identity-demo");
            var other=db.demo("IDENTITY_OTHER","identity-other");
            var local=db.local("identity-local");
            var bridge=new CanonicalLegacyAccountBridgeService(db.jdbc);
            var authority=new JdbcAccountTradeEnvironmentAuthority(db.jdbc);
            var tx=new TransactionTemplate(db.manager);
            var supplied=db.as(demo,()->exchange(db,demo,"PAPER","SIM","paper-only"));
            assertThrows(LiveControlException.class,()->db.as(demo,()->tx.execute(s->bridge.resolveOrCreateSim(supplied,"fixture",Instant.now()))));
            assertThrows(LiveControlException.class,()->db.as(other,()->tx.execute(s->bridge.resolveOrCreateDemoSim(supplied,"fixture",Instant.now()))));
            assertThrows(LiveControlException.class,()->db.as(local,()->tx.execute(s->bridge.resolveOrCreateDemoSim(supplied,"fixture",Instant.now()))));
            long account=db.as(demo,()->tx.execute(s->bridge.resolveOrCreateDemoSim(supplied,"fixture",Instant.now())));
            long repeated=db.as(demo,()->tx.execute(s->bridge.resolveOrCreateDemoSim(supplied,"fixture",Instant.now())));
            assertEquals(account,repeated);
            assertEquals(1,db.admin.queryForObject("SELECT count(*) FROM accounts WHERE account_id=?",Integer.class,account));
            assertThrows(AccountTradeEnvironmentException.class,()->db.as(demo,()->authority.resolve(account,"PAPER")));
            String reserved="ptr-"+UUID.randomUUID();
            db.as(demo,()->tx.execute(s->{
                db.jdbc.update("INSERT INTO tenant_sim_accounts(tenant_id,exchange_account_id,account_id,reserved_paper_run_id) VALUES(?,?,?,?)",
                        demo.tenantId().value(),supplied.exchangeAccountId(),account,reserved);
                new SimCashFundingService(new JdbcLedgerPostingRepository(db.jdbc)).fundOnce(account,reserved,new BigDecimal("10000.00000000"),"fixture");
                return null;
            }));
            assertThrows(AccountTradeEnvironmentException.class,()->db.as(demo,()->authority.resolve(account,"PAPER")));
            db.as(demo,()->tx.execute(s->{
                TenantIsolationTestDatabase.attachFundingRun(db.jdbc,account,reserved);
                db.jdbc.update("UPDATE tenant_sim_accounts SET bound_paper_run_id=? WHERE tenant_id=?",reserved,demo.tenantId().value());
                return null;
            }));
            assertThrows(AccountTradeEnvironmentException.class,()->db.as(demo,()->authority.resolve(account,"PAPER")));
            db.as(demo,()->db.jdbc.update("UPDATE paper_trading_runs SET status='RUNNING' WHERE paper_run_id=?",reserved));
            assertEquals("SIM",db.as(demo,()->authority.resolve(account,"PAPER")));
            assertThrows(AccountTradeEnvironmentException.class,()->db.as(other,()->authority.resolve(account,"PAPER")));
            assertThrows(AccountTradeEnvironmentException.class,()->db.as(demo,()->authority.resolve(account,"OKX")));
            db.as(demo,()->db.jdbc.update("UPDATE exchange_accounts SET status='DISABLED' WHERE exchange_account_id=?",supplied.exchangeAccountId()));
            assertThrows(AccountTradeEnvironmentException.class,()->db.as(demo,()->authority.resolve(account,"PAPER")));
            assertEquals(2,db.admin.queryForObject("SELECT count(*) FROM ledger_entries WHERE account_id=?",Integer.class,account));
        }
    }

    @Test void selfHostedVenueAndLiveRulesRemainExactAndCredentialedPaperIsRejected() throws Exception {
        try(var db=TenantIsolationTestDatabase.create(null)) {
            var local=db.local("venue-local");var demo=db.demo("CREDENTIAL_DEMO","credential-demo");
            var bridge=new CanonicalLegacyAccountBridgeService(db.jdbc);
            var authority=new JdbcAccountTradeEnvironmentAuthority(db.jdbc);
            var tx=new TransactionTemplate(db.manager);
            for (String venue:java.util.List.of("OKX","BINANCE")) {
                var supplied=db.as(local,()->exchange(db,local,venue,"SIM","sim-"+venue));
                long account=db.as(local,()->tx.execute(s->bridge.resolveOrCreateSim(supplied,"fixture",Instant.now())));
                assertEquals("SIM",db.as(local,()->authority.resolve(account,venue)));
                assertThrows(LiveControlException.class,()->db.as(local,()->tx.execute(s->bridge.resolveOrCreate(supplied,"fixture",Instant.now()))));
                assertThrows(AccountTradeEnvironmentException.class,()->db.as(local,()->authority.resolve(account,"PAPER")));
            }
            var live=db.as(local,()->exchange(db,local,"OKX","LIVE","live-okx"));
            long liveAccount=db.as(local,()->tx.execute(s->bridge.resolveOrCreate(live,"fixture",Instant.now())));
            assertEquals("LIVE",db.as(local,()->authority.resolve(liveAccount,"OKX")));
            var unsupported=db.as(local,()->exchange(db,local,"BINANCE","LIVE","live-binance"));
            assertThrows(LiveControlException.class,()->db.as(local,()->tx.execute(s->bridge.resolveOrCreate(unsupported,"fixture",Instant.now()))));
            var paper=db.as(demo,()->exchange(db,demo,"PAPER","SIM","with-credential"));
            assertThrows(org.springframework.dao.DataAccessException.class,()->db.admin.update("INSERT INTO exchange_account_credentials(exchange_account_id,credential_type,encrypted_payload,key_version) VALUES(?,'OKX_API_V5',decode('00','hex'),1)",paper.exchangeAccountId()));
        }
    }

    private static ExchangeAccountSummary exchange(TenantIsolationTestDatabase db,TenantIdentity owner,String venue,String environment,String alias) {
        long id=db.jdbc.queryForObject("INSERT INTO exchange_accounts(owner_user_id,exchange_code,trade_env,account_alias) VALUES(?,?,?,?) RETURNING exchange_account_id",Long.class,
                owner.userId(),venue,environment,alias);
        return new ExchangeAccountSummary(id,null,owner.userId(),venue,environment,alias,null,false,"ACTIVE");
    }
}
