package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.common.tenant.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** 使用真实非 owner 角色及单连接池证明 SQL 防线，不用 mock 或管理员连接代替隔离证据。 */
@EnabledIfSystemProperty(named="nq.tenant.pg.required",matches="true")
class TenantIsolationPostgresIntegrationTest {
    @Test void isolatesRootsAndReusesOneConnectionAcrossTenantsAndEmptyContext() throws Exception {
        try (var db = TenantIsolationTestDatabase.create(null)) {
            var a=db.demo("TENANT_A","tenant-a"); var b=db.demo("TENANT_B","tenant-b");
            long accountA=account(db,a,"account-a"), accountB=account(db,b,"account-b");
            db.as(a,()->{ strategy(db,"strategy-a","SAME_CODE",accountA); return null; });
            db.as(b,()->{ strategy(db,"strategy-b","SAME_CODE",accountB); return null; });
            assertEquals(List.of("strategy-a"),db.as(a,()->db.jdbc.queryForList("SELECT strategy_id FROM strategy_definitions",String.class)));
            assertEquals(List.of("strategy-b"),db.as(b,()->db.jdbc.queryForList("SELECT strategy_id FROM strategy_definitions",String.class)));
            assertEquals(0,db.jdbc.queryForObject("SELECT count(*) FROM strategy_definitions",Integer.class));
            assertEquals(0,db.as(a,()->db.jdbc.update("UPDATE accounts SET status='DISABLED' WHERE account_id=?",accountB)));
            assertThrows(DataAccessException.class,()->db.as(a,()->{ strategy(db,"cross-strategy","CROSS",accountB); return null; }));
            assertThrows(DataAccessException.class,()->db.as(a,()->db.jdbc.update("UPDATE accounts SET tenant_id=? WHERE account_id=?",b.tenantId().value(),accountA)));
            assertEquals(1,db.as(a,()->db.jdbc.queryForObject("SELECT count(*) FROM accounts",Integer.class)));
            try (var connection=db.pool.getConnection();var statement=connection.createStatement()) {
                assertThrows(java.sql.SQLException.class,()->statement.execute("SET ROLE "+db.owner));
                assertThrows(java.sql.SQLException.class,()->statement.execute("TRUNCATE accounts CASCADE"));
                connection.setAutoCommit(false);
                statement.execute("SELECT set_config('nq.tenant_id','invalid',true)");
                assertThrows(java.sql.SQLException.class,()->statement.executeQuery("SELECT count(*) FROM accounts"));
                connection.rollback();
            }
            db.admin.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection->{
                try (var statement=connection.createStatement()) {
                    statement.execute("SET ROLE "+db.owner);
                    try (var result=statement.executeQuery("SELECT count(*) FROM accounts")) {
                        result.next(); assertEquals(0,result.getInt(1));
                    } finally { statement.execute("RESET ROLE"); }
                }
                return null;
            });
        }
    }

    @Test void bindsRequiresNewAndRollbacksWithoutLeakingOrSwitchingExistingTransactions() throws Exception {
        try (var db=TenantIsolationTestDatabase.create(null)) {
            var a=db.demo("TX_A","tx-a"); var b=db.demo("TX_B","tx-b");
            account(db,a,"tx-account-a"); account(db,b,"tx-account-b");
            var outer=new TransactionTemplate(db.manager);
            var inner=new TransactionTemplate(db.manager); inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            // 单连接池明确验证 REQUIRES_NEW 需要独立连接，不把暂停的 A 连接误当作 B 连接。
            db.pool.setMaximumPoolSize(2);
            db.as(a,()->outer.execute(status->{
                assertEquals(a.tenantId().toString(),db.jdbc.queryForObject("SELECT current_setting('nq.tenant_id')",String.class));
                db.as(b,()->inner.execute(nested->{
                    assertEquals(b.tenantId().toString(),db.jdbc.queryForObject("SELECT current_setting('nq.tenant_id')",String.class));
                    return null;
                }));
                assertThrows(IllegalStateException.class,()->db.as(b,()->db.jdbc.queryForObject("SELECT count(*) FROM accounts",Integer.class)));
                status.setRollbackOnly(); return null;
            }));
            assertThrows(IllegalArgumentException.class,()->db.as(a,()->outer.execute(status->{
                db.jdbc.update("INSERT INTO accounts(account_code,venue) VALUES('rolled-back','PAPER')");
                throw new IllegalArgumentException("fixture failure");
            })));
            assertEquals(0,db.admin.queryForObject("SELECT count(*) FROM accounts WHERE account_code='rolled-back'",Integer.class));
            assertEquals(0,db.jdbc.queryForObject("SELECT count(*) FROM accounts",Integer.class));
            assertTrue(TenantContext.currentTenantId().isEmpty());
        }
    }

    static long account(TenantIsolationTestDatabase db,TenantIdentity identity,String code) {
        return db.as(identity,()->db.jdbc.queryForObject("INSERT INTO accounts(account_code,venue) VALUES(?,'PAPER') RETURNING account_id",Long.class,code));
    }

    @Test void scopesImmutableEventsAndRefusesUnsafeRuntimeOrDirectMetadataMutation() throws Exception {
        try(var db=TenantIsolationTestDatabase.create(null)) {
            var a=db.demo("EVENT_A","event-a");var b=db.demo("EVENT_B","event-b");
            db.as(a,()->db.jdbc.update("INSERT INTO audit_logs(domain,action,actor_id,trace_id,detail_json) VALUES('same','TEST','same','same','{}')"));
            db.as(b,()->db.jdbc.update("INSERT INTO audit_logs(domain,action,actor_id,trace_id,detail_json) VALUES('same','TEST','same','same','{}')"));
            assertEquals(1,db.as(a,()->db.jdbc.queryForObject("SELECT count(*) FROM audit_logs",Integer.class)));
            assertEquals(1,db.as(b,()->db.jdbc.queryForObject("SELECT count(*) FROM audit_logs",Integer.class)));
            assertEquals(0,db.jdbc.queryForObject("SELECT count(*) FROM audit_logs",Integer.class));
            assertEquals(2,db.admin.queryForObject("SELECT count(*) FROM audit_log_tenant_scopes WHERE classification='TENANT_PRIVATE'",Integer.class));
            for(String table:List.of("tenant_background_work","tenant_continuous_slots","tenant_work_scan_cursors","audit_log_tenant_scopes","tenant_execution_controls"))
                assertThrows(DataAccessException.class,()->db.as(a,()->db.jdbc.update("DELETE FROM "+table)),table);
            var unsafeManager=new com.guidinglight.nexusquant.tenant.infra.jdbc.TenantTransactionManager(db.database.source());
            var unsafe=com.guidinglight.nexusquant.tenant.infra.jdbc.TenantJdbcTemplateFactory.create(db.database.source(),unsafeManager);
            assertThrows(org.springframework.transaction.CannotCreateTransactionException.class,()->db.as(a,()->unsafe.queryForObject("SELECT count(*) FROM accounts",Integer.class)));
        }
    }
    static void strategy(TenantIsolationTestDatabase db,String id,String code,long account) {
        db.jdbc.update("INSERT INTO strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,exchange_code,account_id,trade_env) VALUES(?,?,?,'SPOT_SMA_TARGET_V1','OKX',?,'SIM')",id,code,code,account);
    }

    @Test void rotatesBoundedPersistedWorkWithoutTenantLeakageAndRejectsDisabledWork() throws Exception {
        try(var db=TenantIsolationTestDatabase.create(null)) {
            var a=db.demo("SCAN_A","scan-a");var b=db.demo("SCAN_B","scan-b");var c=db.demo("SCAN_C","scan-c");
            long aa=account(db,a,"scan-account-a"),ab=account(db,b,"scan-account-b"),ac=account(db,c,"scan-account-c");
            var work=new com.guidinglight.nexusquant.tenant.infra.jdbc.JdbcTenantWorkAccess(db.jdbc);
            var seen=new java.util.HashSet<String>();
            for(int i=0;i<6;i++) {
                var ids=work.next(TenantWorkAccess.Kind.ACCOUNT,1);assertEquals(1,ids.size());String id=ids.getFirst();seen.add(id);
                work.execute(TenantWorkAccess.Kind.ACCOUNT,id,()->{
                    assertEquals(1,db.jdbc.queryForObject("SELECT count(*) FROM accounts",Integer.class));return null;
                });
                assertTrue(TenantContext.currentTenantId().isEmpty());
            }
            assertEquals(java.util.Set.of(Long.toString(aa),Long.toString(ab),Long.toString(ac)),seen);
            assertThrows(DataAccessException.class,()->db.as(a,()->work.restore(TenantWorkAccess.Kind.ACCOUNT,Long.toString(ab))));
            assertThrows(IllegalStateException.class,()->db.as(a,()->work.next(TenantWorkAccess.Kind.ACCOUNT,1)));
            assertThrows(IllegalStateException.class,()->work.next(TenantWorkAccess.Kind.ACCOUNT,65));
            db.admin.update("UPDATE tenant_execution_controls SET suspended=true,requires_manual_resume=true WHERE tenant_id=?",a.tenantId().value());
            // 暂停只阻止经济推进，不能令只读对账失去已有账户事实。
            assertTrue(work.next(TenantWorkAccess.Kind.ACCOUNT,64).contains(Long.toString(aa)));
            work.execute(TenantWorkAccess.Kind.ACCOUNT,Long.toString(aa),()->{
                assertEquals(1,db.jdbc.queryForObject("SELECT count(*) FROM accounts",Integer.class));
                assertThrows(DataAccessException.class,()->db.jdbc.queryForObject("SELECT nq_require_tenant_execution(?)",Object.class,a.tenantId().value()));
                return null;
            });
            assertThrows(IllegalArgumentException.class,()->work.execute(TenantWorkAccess.Kind.ACCOUNT,Long.toString(ab),()->{throw new IllegalArgumentException("synthetic worker failure");}));
            assertTrue(TenantContext.currentTenantId().isEmpty());
        }
    }
}
