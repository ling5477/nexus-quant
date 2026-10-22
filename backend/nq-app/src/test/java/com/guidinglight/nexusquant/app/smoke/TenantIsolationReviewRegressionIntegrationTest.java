package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.audit.infra.jdbc.JdbcAuditLogRepository;
import com.guidinglight.nexusquant.common.tenant.TenantContext;
import com.guidinglight.nexusquant.scheduler.infra.jdbc.JdbcLedgerReconcileRepository;
import com.guidinglight.nexusquant.scheduler.scheduling.LedgerReconcileScheduler;
import com.guidinglight.nexusquant.tenant.infra.jdbc.JdbcTenantWorkAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

/** 独立检查发现的升级、只读核验及函数解析回归，均使用任务自有 PostgreSQL。 */
@EnabledIfSystemProperty(named="nq.tenant.pg.required",matches="true")
class TenantIsolationReviewRegressionIntegrationTest {
    @Test void firstJdbcOperationAlsoRejectsAnIncompleteRlsInstallation() throws Exception {
        try(var db=TenantIsolationTestDatabase.create(null)) {
            var guard=com.guidinglight.nexusquant.app.config.TenantPersistenceConfiguration.restrictedReadOnlyDataSources();
            var unsafe=(javax.sql.DataSource)guard.postProcessAfterInitialization(db.database.source(),"unsafe");
            assertThrows(java.sql.SQLException.class,unsafe::getConnection);
            var safe=(javax.sql.DataSource)guard.postProcessAfterInitialization(db.pool,"runtime");
            try(var connection=safe.getConnection()){assertFalse(connection.isClosed());}
            db.admin.execute("ALTER TABLE accounts DISABLE ROW LEVEL SECURITY");
            assertThrows(java.sql.SQLException.class,safe::getConnection);
            assertThrows(org.springframework.transaction.CannotCreateTransactionException.class,
                    ()->db.jdbc.queryForObject("SELECT count(*) FROM accounts",Integer.class));
        }
    }
    @Test void upgradesMultipleHistoricalLocalRunsWithoutRewritingFactsOrAdmittingAnotherRun() throws Exception {
        try(var db=TenantIsolationTestDatabase.create("35")) {
            TenantIsolationMigrationIntegrationTest.seed(db);
            long account=db.admin.queryForObject("INSERT INTO accounts(account_code,venue) VALUES('history-second','PAPER') RETURNING account_id",Long.class);
            db.admin.update("""
                    INSERT INTO paper_trading_runs(paper_run_id,publish_id,strategy_version_id,status,trade_env,
                        exchange_code,market_type,symbol,interval_code,created_by,created_at,updated_at,canonical_account_id)
                    VALUES('history-paper-two','history-publish','history-version','RUNNING','SIM','OKX','SPOT',
                        'BTC-USDT','1h','historical',now(),now(),?)
                    """,account);
            for(String run:new String[]{"history-paper","history-paper-two"}) db.admin.update("""
                    INSERT INTO continuous_sim_runs(paper_run_id,status,strategy_version_id,strategy_checksum,
                        cost_sha256,seed_last_open_time,last_processed_open_time,last_processed_close_time,
                        last_processed_sha256,last_observed_open_time,started_at,updated_at)
                    VALUES(?,'RUNNING','history-version',?,?,now()-interval '1 hour',now()-interval '1 hour',
                        now(),?,now()-interval '1 hour',now(),now())
                    """,run,"a".repeat(64),"b".repeat(64),"c".repeat(64));
            var before=TenantIsolationMigrationIntegrationTest.snapshot(db.admin,null);
            db.migrate(null);
            assertEquals(before,TenantIsolationMigrationIntegrationTest.snapshot(db.admin,before.keySet()));
            assertEquals(2,db.admin.queryForObject("SELECT count(*) FROM tenant_continuous_slots WHERE active",Integer.class));
            db.runtime();
            var local=db.local("historical-capacity-check");
            var failure=assertThrows(DataAccessException.class,()->db.as(local,()->
                    db.jdbc.queryForObject("SELECT nq_require_continuous_capacity()",Object.class)));
            assertTrue(failure.getMostSpecificCause().getMessage().contains("CAPACITY_LIMIT"));
        }
    }

    @Test void reconcilesSuspendedAndInactiveAccountsAndReportsUnreadableTenantsAsFailure() throws Exception {
        try(var db=TenantIsolationTestDatabase.create(null)) {
            var a=db.demo("RECONCILE_A","reconcile-a");
            long account=TenantIsolationPostgresIntegrationTest.account(db,a,"reconcile-account");
            db.as(a,()->db.jdbc.update("""
                    INSERT INTO account_snapshots(account_id,currency,balance,available,frozen,ts,trace_id)
                    VALUES(?,'USDT',100,100,0,now(),'reconcile-review')
                    """,account));
            var repository=new JdbcLedgerReconcileRepository(db.jdbc);
            var scheduler=new LedgerReconcileScheduler(repository,new JdbcAuditLogRepository(db.jdbc,new ObjectMapper()));
            ReflectionTestUtils.setField(scheduler,"tenantWork",new JdbcTenantWorkAccess(db.jdbc));
            assertEquals(1,scheduler.reconcileOnce());
            db.admin.update("UPDATE tenant_execution_controls SET suspended=true,requires_manual_resume=true WHERE tenant_id=?",a.tenantId().value());
            assertEquals(1,scheduler.reconcileOnce());
            db.as(a,()->db.jdbc.update("UPDATE accounts SET status='DISABLED' WHERE account_id=?",account));
            assertEquals(1,scheduler.reconcileOnce());
            assertEquals(3,db.admin.queryForObject("SELECT count(*) FROM audit_logs WHERE action='RECONCILE_DIFF_FOUND'",Integer.class));
            assertEquals(0,db.admin.queryForObject("SELECT count(*) FROM audit_logs WHERE action='RECONCILE_MATCH'",Integer.class));
            db.admin.update("UPDATE tenants SET status='DISABLED' WHERE tenant_id=?",a.tenantId().value());
            assertThrows(DataAccessException.class,scheduler::reconcileOnce);
            assertTrue(TenantContext.currentTenantId().isEmpty());
        }
    }

    @Test void temporaryRelationsCannotChangeThePersistedExecutionDecision() throws Exception {
        try(var db=TenantIsolationTestDatabase.create(null)) {
            var a=db.demo("RESOLUTION_A","resolution-a");
            TenantIsolationPostgresIntegrationTest.account(db,a,"resolution-account");
            db.admin.update("UPDATE tenant_execution_controls SET suspended=true,requires_manual_resume=true WHERE tenant_id=?",a.tenantId().value());
            assertSuspended(db,a);
            // 保留运行角色本来拥有的 TEMP 能力，验证函数解析本身绑定正式控制表。
            try(var connection=db.pool.getConnection();var statement=connection.createStatement()) {
                statement.execute("CREATE TEMP TABLE tenant_execution_controls (LIKE public.tenant_execution_controls INCLUDING ALL)");
                statement.execute("GRANT SELECT,UPDATE ON pg_temp.tenant_execution_controls TO "+db.owner);
                try(var insert=connection.prepareStatement("INSERT INTO pg_temp.tenant_execution_controls(tenant_id,suspended,requires_manual_resume,execution_epoch) VALUES(?,false,false,2)")) {
                    insert.setObject(1,a.tenantId().value());insert.executeUpdate();
                }
            }
            assertSuspended(db,a);
            assertTrue(db.admin.queryForObject("SELECT suspended FROM tenant_execution_controls WHERE tenant_id=?",Boolean.class,a.tenantId().value()));
            assertEquals(0,db.admin.queryForObject("""
                    SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
                    WHERE n.nspname='public' AND p.prosecdef
                        AND NOT coalesce('search_path=pg_catalog, public, pg_temp'=ANY(p.proconfig),false)
                    """,Integer.class));
            db.admin.execute("GRANT CREATE ON SCHEMA public TO "+db.runtime);
            assertThrows(IllegalStateException.class,()->
                    com.guidinglight.nexusquant.tenant.infra.jdbc.TenantTransactionManager.verifyRuntime(db.pool));
            db.admin.execute("REVOKE CREATE ON SCHEMA public FROM "+db.runtime);
        }
    }

    private void assertSuspended(TenantIsolationTestDatabase db,com.guidinglight.nexusquant.common.tenant.TenantIdentity identity) {
        var failure=assertThrows(DataAccessException.class,()->db.as(identity,()->
                db.jdbc.queryForObject("SELECT nq_require_tenant_execution(?)",Object.class,identity.tenantId().value())));
        assertTrue(failure.getMostSpecificCause().getMessage().contains("TENANT_SUSPENDED"));
    }
}
