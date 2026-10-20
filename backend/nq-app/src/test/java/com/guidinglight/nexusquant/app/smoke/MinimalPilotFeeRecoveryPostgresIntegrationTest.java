package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.guidinglight.nexusquant.app.config.livecontrol.MinimalLivePilotConfiguration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * 在独立 PostgreSQL 验证生产恢复查询的费用分录判断；只证明读取决策，不赋予 LIVE 或完整启动恢复资格。
 * 查询夹具只保留涉及的列，不模拟租约、风险、供应方或正式账务写入；这些能力由各自合同验证。
 */
@EnabledIfSystemProperty(named = "nq.postgres.smoke.required", matches = "true")
class MinimalPilotFeeRecoveryPostgresIntegrationTest {

    @Test
    void completeZeroRoundedZeroChargesAndRebatesAreRecognizedWithoutWrites() throws Exception {
        try (var database = ReleaseBaselineTestDatabase.create()) {
            JdbcTemplate jdbc = queryFixture(database);
            Object runner = runner(jdbc);
            for (String fee :
                    List.of(
                            "0",
                            "0.000000001",
                            "-0.000000001",
                            "0.1",
                            "-0.1",
                            "0.000000005",
                            "-0.000000005")) {
                seed(jdbc, fee);
                BigDecimal persisted =
                        jdbc.queryForObject("SELECT fee FROM trades", BigDecimal.class);
                int entries = persisted.signum() == 0 ? 2 : 4;
                addEntries(jdbc, "trade", entries);
                assertReadOnlyDecision(runner, jdbc, true, "complete fee=" + fee);
                // 另一个成交不能补足当前成交的缺失；每笔成交分别满足零/非零费用合同。
                jdbc.update("INSERT INTO trades VALUES ('second', 'order', 0.1)");
                addEntries(jdbc, "second", 2);
                assertReadOnlyDecision(runner, jdbc, false, "incomplete second trade");
                addEntries(jdbc, "second", 2);
                assertReadOnlyDecision(runner, jdbc, true, "complete mixed trades");
            }
        }
    }

    @Test
    void incompleteUnknownAndNonterminalFactsNeverSatisfyShortcut() throws Exception {
        try (var database = ReleaseBaselineTestDatabase.create()) {
            JdbcTemplate jdbc = queryFixture(database);
            Object runner = runner(jdbc);
            for (String fee : List.of("0", "0.1", "-0.1")) {
                for (int count : List.of(0, 1, 2, 3, 4, 5)) {
                    seed(jdbc, fee);
                    addEntries(jdbc, "trade", count);
                    assertReadOnlyDecision(
                            runner,
                            jdbc,
                            count == (fee.equals("0") ? 2 : 4),
                            "fee=" + fee + " entries=" + count);
                }
            }
            seed(jdbc, "0");
            addEntries(jdbc, "trade", 2);
            jdbc.update("UPDATE trades SET fee=NULL");
            assertReadOnlyDecision(runner, jdbc, false, "missing fee cannot mean zero");
            addEntries(jdbc, "trade", 2);
            assertReadOnlyDecision(runner, jdbc, false, "missing fee cannot mean nonzero");
            seed(jdbc, "0");
            addEntries(jdbc, "trade", 2);
            jdbc.update("DELETE FROM trades");
            assertReadOnlyDecision(runner, jdbc, false, "filled order without trade");
            for (String state : List.of("UNKNOWN", "SEND_STARTED", "CREATED")) {
                seed(jdbc, "0");
                addEntries(jdbc, "trade", 2);
                jdbc.update("UPDATE execution_intents SET state=?", state);
                assertReadOnlyDecision(runner, jdbc, false, "intent=" + state);
            }
            for (String outcome : List.of("TIMEOUT", "UNKNOWN", "TRANSPORT_ERROR")) {
                seed(jdbc, "0");
                addEntries(jdbc, "trade", 2);
                jdbc.update("UPDATE execution_receipts SET outcome=?", outcome);
                assertReadOnlyDecision(runner, jdbc, false, "receipt=" + outcome);
            }
            for (String status : List.of("ACCEPTED", "PARTIALLY_FILLED", "SENT")) {
                seed(jdbc, "0");
                addEntries(jdbc, "trade", 2);
                jdbc.update("UPDATE orders SET status=?", status);
                assertReadOnlyDecision(runner, jdbc, false, "order=" + status);
            }
            seed(jdbc, "0");
            addEntries(jdbc, "trade", 2);
            jdbc.update("UPDATE orders SET trade_env='SIM'");
            assertReadOnlyDecision(runner, jdbc, false, "SIM cannot satisfy LIVE recovery");
        }
    }

    /** 私有 runner 的查询直接使用真实 JDBC，其余端口仅为未调用的构造占位；禁止执行真实交易入口。 */
    private static Object runner(JdbcTemplate jdbc) throws Exception {
        Class<?> type =
                Arrays.stream(MinimalLivePilotConfiguration.class.getDeclaredClasses())
                        .filter(candidate -> candidate.getSimpleName().equals("MinimalPilotRunner"))
                        .findFirst()
                        .orElseThrow();
        Constructor<?> constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object[] parameters =
                Arrays.stream(constructor.getParameterTypes())
                        .map(parameter -> parameter == JdbcTemplate.class ? jdbc : mock(parameter))
                        .toArray();
        return constructor.newInstance(parameters);
    }

    /** 新数据库仅由本测试拥有；缩减表明确是读取夹具，不能冒称正式 Flyway 或经济事实证明。 */
    private static JdbcTemplate queryFixture(ReleaseBaselineTestDatabase database) {
        JdbcTemplate jdbc = new JdbcTemplate(database.source());
        jdbc.setQueryTimeout(10);
        jdbc.execute(
                "CREATE TABLE execution_intents(intent_id text, local_order_id text, state text)");
        jdbc.execute("CREATE TABLE execution_receipts(intent_id text, outcome text)");
        jdbc.execute("CREATE TABLE orders(order_id text, trade_env text, status text)");
        jdbc.execute("CREATE TABLE trades(trade_id text, order_id text, fee numeric(38,8))");
        jdbc.execute("CREATE TABLE ledger_entries(ref_type text, ref_id text)");
        return jdbc;
    }

    private static void seed(JdbcTemplate jdbc, String fee) {
        jdbc.execute(
                "TRUNCATE execution_intents, execution_receipts, orders, trades, ledger_entries");
        jdbc.update("INSERT INTO execution_intents VALUES ('intent', 'order', 'RECONCILED')");
        jdbc.update("INSERT INTO execution_receipts VALUES ('intent', 'QUERY_CONFIRMED')");
        jdbc.update("INSERT INTO orders VALUES ('order', 'LIVE', 'FILLED')");
        jdbc.update("INSERT INTO trades VALUES ('trade', 'order', ?)", new BigDecimal(fee));
    }

    private static void addEntries(JdbcTemplate jdbc, String trade, int count) {
        for (int i = 0; i < count; i++)
            jdbc.update("INSERT INTO ledger_entries VALUES ('TRADE', ?)", trade);
    }

    /** 对全部查询输入做整行快照，确保正确和拒绝决策都没有修改持久化事实。 */
    private static void assertReadOnlyDecision(
            Object runner, JdbcTemplate jdbc, boolean expected, String scenario) throws Exception {
        String before = snapshot(jdbc);
        Method method =
                runner.getClass()
                        .getDeclaredMethod("hasCompleteDurableReconciliation", String.class);
        method.setAccessible(true);
        assertEquals(expected, method.invoke(runner, "order"), scenario);
        assertEquals(before, snapshot(jdbc), scenario + " must be read-only");
    }

    private static String snapshot(JdbcTemplate jdbc) {
        return jdbc.queryForObject(
                """
                SELECT jsonb_build_object(
                  'intents',(SELECT jsonb_agg(to_jsonb(i) ORDER BY intent_id) FROM execution_intents i),
                  'receipts',(SELECT jsonb_agg(to_jsonb(r) ORDER BY intent_id) FROM execution_receipts r),
                  'orders',(SELECT jsonb_agg(to_jsonb(o) ORDER BY order_id) FROM orders o),
                  'trades',(SELECT jsonb_agg(to_jsonb(t) ORDER BY trade_id) FROM trades t),
                  'ledger',(SELECT jsonb_agg(to_jsonb(e) ORDER BY ref_id) FROM ledger_entries e))::text
                """,
                String.class);
    }
}
