package com.guidinglight.nexusquant.app.smoke;

import java.sql.Connection;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 当前隔离进程测试共用的策略输入夹具；恢复行为由正式持久工作和启动回归证明。 */
final class B5StrategyRunRecoveryProcessTest {
    static void seed(B0Fixture fixture) throws Exception {
        try (var owner = DriverManager.getConnection(fixture.url(), "postgres", ""); var s = owner.createStatement()) {
            s.execute("INSERT INTO strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,exchange_code,account_id,trade_env,enabled,config_snapshot) "
                    + "SELECT 'b5-strategy','b5-strategy','B5 recovery fixture','TEST','OKX',account_id,'SIM',true,"
                    + "'{\"symbol\":\"BTC-USDT\",\"side\":\"BUY\",\"orderType\":\"LIMIT\",\"price\":\"100\",\"quantity\":\"10\"}'::jsonb FROM accounts WHERE account_id=(SELECT legacy_account_id FROM exchange_accounts WHERE account_alias='b0-sim')");
            s.execute("INSERT INTO strategy_schedules(schedule_job_id,strategy_id,cron_expr,timezone,enabled,dedup_scope,exchange_code,account_id,trade_env,created_at) "
                    + "SELECT 'b5','b5-strategy','0 0 0 1 1 *','UTC',true,'SCHEDULE_WINDOW','OKX',account_id,'SIM',date_trunc('year',CURRENT_TIMESTAMP)-INTERVAL '1 second' FROM accounts WHERE account_id=(SELECT legacy_account_id FROM exchange_accounts WHERE account_alias='b0-sim')");
            // 测试观察表只记实际状态改变，与业务 writer 同事务提交，不参与任何业务判断。
            s.execute("CREATE TABLE b5_run_transitions(old_status text,new_status text)");
            s.execute("CREATE FUNCTION b5_observe_run() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                    + "IF NEW.status IS DISTINCT FROM OLD.status THEN INSERT INTO b5_run_transitions VALUES(OLD.status,NEW.status); END IF; RETURN NEW; END $$");
            s.execute("CREATE TRIGGER b5_observe_run AFTER UPDATE ON strategy_runs FOR EACH ROW EXECUTE FUNCTION b5_observe_run()");
            s.execute("GRANT INSERT,SELECT ON b5_run_transitions TO nq_b0_app; GRANT SELECT ON b5_run_transitions TO nq_b0_reader");
        }
    }

    static String value(Connection reader, String sql) throws Exception {
        try (var s = reader.createStatement(); var r = s.executeQuery(sql)) { assertTrue(r.next()); return r.getString(1); }
    }
}
