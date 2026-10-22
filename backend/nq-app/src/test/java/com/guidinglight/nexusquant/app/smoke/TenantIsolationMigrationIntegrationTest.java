package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.common.tenant.TenantId;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** 迁移证明使用独立历史夹具，既有业务列逐行比较；不把补造的收益作为 SIM 验收。 */
@EnabledIfSystemProperty(named="nq.tenant.pg.required",matches="true")
class TenantIsolationMigrationIntegrationTest {
    static final List<String> ROOTS=List.of("accounts","exchange_accounts","strategy_definitions","strategy_versions",
            "research_configs","backtest_configs","paper_trading_runs","marketdata_datasets");

    @Test void upgradesBatchedHistoryAndRestoresRealBackupWithoutChangingEconomicOrFrozenFacts() throws Exception {
        try(var db=TenantIsolationTestDatabase.create("35");var restored=ReleaseBaselineTestDatabase.create("nq.tenant.pg")) {
            seed(db);
            var before=snapshot(db.admin,null);
            var checksums=db.admin.queryForList("SELECT version,checksum FROM flyway_schema_history ORDER BY installed_rank");
            String name=db.admin.queryForObject("SELECT current_database()",String.class);
            String restoreName=new JdbcTemplate(restored.source()).queryForObject("SELECT current_database()",String.class);
            String dump="/tmp/tenant-core-"+UUID.randomUUID()+".dump";
            docker("pg_dump","-U",System.getProperty("nq.tenant.pg.user"),"-Fc","--no-owner","--no-acl","-d",name,"-f",dump);
            db.migrate(null);
            assertEquals(before,snapshot(db.admin,before.keySet()));
            assertEquals(checksums,db.admin.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE version::int<=35 ORDER BY installed_rank"));
            for(String table:ROOTS) {
                assertEquals(0,db.admin.queryForObject("SELECT count(*) FROM "+table+" WHERE tenant_id IS DISTINCT FROM ?",Integer.class,TenantId.LOCAL.value()),table);
            }
            assertEquals(1006,db.admin.queryForObject("SELECT count(*) FROM accounts",Integer.class));
            assertEquals(0,db.migration(null).migrate().migrationsExecuted);
            docker("pg_restore","-U",System.getProperty("nq.tenant.pg.user"),"--no-owner","--no-acl","--exit-on-error","-d",restoreName,dump);
            assertEquals(before,snapshot(new JdbcTemplate(restored.source()),before.keySet()));
            docker("rm","--",dump);
        }
    }

    @Test void failedOwnershipMigrationRollsBackMetadataAndRetriesWithoutRepair() throws Exception {
        try(var db=TenantIsolationTestDatabase.create("35")) {
            seed(db);var before=snapshot(db.admin,null);
            db.admin.execute("""
                    CREATE FUNCTION fail_tenant_test_migration() RETURNS event_trigger LANGUAGE plpgsql AS $$
                    BEGIN
                        IF EXISTS(SELECT 1 FROM pg_event_trigger_ddl_commands() WHERE object_identity LIKE '%trg_preserve_business_tenant%') THEN
                            RAISE EXCEPTION 'synthetic ownership migration interruption';
                        END IF;
                    END $$
                    """);
            db.admin.execute("CREATE EVENT TRIGGER tenant_test_fail ON ddl_command_end WHEN TAG IN ('CREATE TRIGGER') EXECUTE FUNCTION fail_tenant_test_migration()");
            assertThrows(FlywayException.class,()->db.migration("36").migrate());
            assertEquals(before,snapshot(db.admin,before.keySet()));
            assertEquals(0,db.admin.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND column_name='tenant_id' AND table_name='accounts'",Integer.class));
            assertEquals("35",db.migration("35").info().current().getVersion().toString());
            db.admin.execute("DROP EVENT TRIGGER tenant_test_fail");db.admin.execute("DROP FUNCTION fail_tenant_test_migration()");
            db.migrate(null);
            assertEquals(before,snapshot(db.admin,before.keySet()));
        }
    }

    static Map<String,List<String>> snapshot(JdbcTemplate jdbc,Collection<String> names) {
        if(names==null)names=jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename<>'flyway_schema_history' ORDER BY tablename",String.class);
        var result=new TreeMap<String,List<String>>();
        for(String table:names) {
            if(!table.matches("[a-z_]+"))throw new IllegalArgumentException("invalid fixture table");
            String projection=ROOTS.contains(table)?"to_jsonb(t)-'tenant_id'-'visibility'":"to_jsonb(t)";
            result.put(table,jdbc.queryForList("SELECT ("+projection+")::text FROM "+table+" t ORDER BY ("+projection+")::text",String.class));
        }
        return result;
    }

    static void seed(TenantIsolationTestDatabase db) {
        var jdbc=db.admin;var user=db.local("historical-local");
        jdbc.update("INSERT INTO accounts(account_code,venue) SELECT 'batch-'||n,'PAPER' FROM generate_series(1,1005) n");
        long account=jdbc.queryForObject("INSERT INTO accounts(account_code,venue) VALUES('history','PAPER') RETURNING account_id",Long.class);
        jdbc.update("INSERT INTO exchange_accounts(owner_user_id,exchange_code,trade_env,account_alias) VALUES(?,'OKX','SIM','historical')",user.userId());
        jdbc.update("INSERT INTO strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,exchange_code,account_id,trade_env) VALUES('history-strategy','history','History','SPOT_SMA_TARGET_V1','OKX',?,'SIM')",account);
        jdbc.update("INSERT INTO strategy_versions(strategy_version_id,strategy_code,version,version_name,status,checksum) VALUES('history-version','history',1,'v1','ACTIVE',?)","a".repeat(64));
        jdbc.update("INSERT INTO research_configs(research_config_id,source_strategy_id,name,strategy_snapshot) VALUES('history-research','history-strategy','History','{}')");
        jdbc.update("INSERT INTO backtest_configs(backtest_config_id,research_config_id,name,strategy_version_id) VALUES('history-config','history-research','History','history-version')");
        jdbc.update("""
                INSERT INTO backtest_runs(backtest_run_id,backtest_config_id,research_config_id,source_strategy_id,status,strategy_snapshot,backtest_config_snapshot,requested_at,strategy_version_id)
                VALUES('history-backtest','history-config','history-research','history-strategy','SUCCEEDED','{}','{}',now(),'history-version')
                """);
        jdbc.update("""
                INSERT INTO backtest_publish_records(publish_record_id,backtest_run_id,research_config_id,backtest_config_id,source_strategy_id,publish_status,publish_name,strategy_version_id)
                VALUES('history-publish','history-backtest','history-research','history-config','history-strategy','SUCCEEDED','History','history-version')
                """);
        jdbc.update("""
                INSERT INTO paper_trading_runs(paper_run_id,publish_id,strategy_version_id,status,trade_env,exchange_code,market_type,symbol,interval_code,created_by,created_at,updated_at,canonical_account_id)
                VALUES('history-paper','history-publish','history-version','STOPPED','SIM','OKX','SPOT','BTC-USDT','1m','historical',now(),now(),?)
                """,account);
        jdbc.update("""
                INSERT INTO marketdata_datasets(dataset_id,dataset_name,exchange_code,market_type,symbol,"interval",start_time,end_time,status,quality_status,source,created_by)
                VALUES(?,'History','OKX','SPOT','BTC-USDT','1m',now()-interval '1 hour',now(),'READY','OK','SYNTHETIC','historical')
                """,UUID.randomUUID());
        jdbc.update("""
                INSERT INTO orders(order_id,account_id,symbol,client_order_id,side,type,price,qty,status,trace_id,venue,exchange_code,trade_env)
                VALUES('history-order',?,'BTC-USDT','history-client','BUY','LIMIT',100,1,'FILLED','history-trace','PAPER','PAPER','SIM')
                """,account);
        jdbc.update("""
                INSERT INTO trades(trade_id,order_id,account_id,symbol,exchange,price,qty,fee,fee_currency,trace_id,ts,exchange_code,trade_env)
                VALUES('history-trade','history-order',?,'BTC-USDT','PAPER',100,1,0.01,'USDT','history-trace',now(),'PAPER','SIM')
                """,account);
        jdbc.update("""
                INSERT INTO ledger_entries(entry_id,account_id,currency,delta,balance_after,direction,ref_type,ref_id,idempotency_key,trace_id,ts)
                VALUES('history-entry',?,'USDT',-100.01,9899.99,'DEBIT','TRADE','history-trade','history-key','history-trace',now())
                """,account);
        jdbc.update("INSERT INTO ledger_events(entry_id,event_type,payload_json,trace_id) VALUES('history-entry','POSTED','{}','history-trace')");
        jdbc.update("INSERT INTO positions(account_id,symbol,qty,available_qty,frozen_qty,avg_price,trace_id,updated_at) VALUES(?,'BTC-USDT',1,1,0,100,'history-trace',now())",account);
    }

    static void docker(String... args) throws Exception {
        var command=new ArrayList<>(List.of("docker","exec","nq-tenant-core-pg-20261021"));command.addAll(List.of(args));
        var process=new ProcessBuilder(command).redirectErrorStream(true).start();
        assertTrue(process.waitFor(45,TimeUnit.SECONDS),"task PostgreSQL backup command timed out");
        assertEquals(0,process.exitValue(),new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
    }
}
