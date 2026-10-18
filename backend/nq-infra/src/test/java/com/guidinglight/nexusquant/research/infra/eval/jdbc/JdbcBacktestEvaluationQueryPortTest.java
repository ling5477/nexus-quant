package com.guidinglight.nexusquant.research.infra.eval.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/** 查询次数与参数边界必须匹配运行窗口；空值、重复身份和超限输入不能产生逐项数据库读取。 */
class JdbcBacktestEvaluationQueryPortTest {
    @Test
    void twoHundredSourcesUseOneBoundedQueryWithTheExistingFeePredicate() {
        var jdbc = new RecordingJdbc();
        var repository = new JdbcBacktestEvaluationQueryPort(jdbc);
        var ids = IntStream.range(0, 200).mapToObj(index -> "run-" + index).toList();
        assertTrue(repository.findByBacktestRunIds(ids).isEmpty());
        assertEquals(1, jdbc.queries);
        assertEquals(ids, Arrays.asList(jdbc.parameters));
        assertTrue(jdbc.sql.contains("backtest_run_id IN ("));
        assertTrue(jdbc.sql.contains("EXISTS (SELECT 1 FROM backtest_runs nq_fee_run"));
        assertTrue(jdbc.sql.contains("feeSourceOwnerUserId"));
        assertTrue(jdbc.sql.contains("= '-1'"));
    }

    @Test
    void nullEmptyAndDuplicateInputsDoNotAmplifyReads() {
        var jdbc = new RecordingJdbc();
        var repository = new JdbcBacktestEvaluationQueryPort(jdbc);
        assertTrue(repository.findByBacktestRunIds(null).isEmpty());
        assertTrue(repository.findByBacktestRunIds(List.of()).isEmpty());
        assertEquals(0, jdbc.queries);
        repository.findByBacktestRunIds(Arrays.asList(null, "", " ", "run-1", "run-1"));
        assertEquals(1, jdbc.queries);
        assertEquals(List.of("run-1"), Arrays.asList(jdbc.parameters));
    }

    @Test
    void oversizedInputFailsBeforeAnyQuery() {
        var jdbc = new RecordingJdbc();
        var repository = new JdbcBacktestEvaluationQueryPort(jdbc);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        repository.findByBacktestRunIds(
                                IntStream.range(0, 201)
                                        .mapToObj(index -> "run-" + index)
                                        .toList()));
        assertEquals(0, jdbc.queries);
    }

    @Test
    void suppliedIdentifiersRemainParametersRatherThanSql() {
        var jdbc = new RecordingJdbc();
        var repository = new JdbcBacktestEvaluationQueryPort(jdbc);
        String unusual = "id') OR TRUE --";
        repository.findByBacktestRunIds(List.of(unusual));
        assertTrue(!jdbc.sql.contains(unusual));
        assertEquals(List.of(unusual), Arrays.asList(jdbc.parameters));
    }

    private static final class RecordingJdbc extends JdbcTemplate {
        int queries;
        String sql;
        Object[] parameters;

        @Override
        public <T> List<T> query(String statement, RowMapper<T> mapper, Object... arguments) {
            queries++;
            sql = statement;
            parameters = arguments;
            return List.of();
        }
    }
}
