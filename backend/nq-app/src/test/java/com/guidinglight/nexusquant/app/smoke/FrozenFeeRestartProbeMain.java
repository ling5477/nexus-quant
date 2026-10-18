package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.application.service.AccountFeeModelService;
import com.guidinglight.nexusquant.account.domain.fee.FrozenAccountFeeModel;
import com.guidinglight.nexusquant.account.infra.jdbc.JdbcAccountFeeRepository;
import com.guidinglight.nexusquant.account.infra.jdbc.JdbcExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.infra.jdbc.JdbcExchangeAccountRepository;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/** 仅供拥有的临时PG跨进程复核：重读冻结来源，不同步、不解密或提交任何交易。 */
public final class FrozenFeeRestartProbeMain {
    private FrozenFeeRestartProbeMain() {}

    public static void main(String[] args) throws Exception {
        String url = System.getenv("NQ_FROZEN_FEE_DB_URL");
        URI uri = URI.create(url.substring("jdbc:".length()));
        if (!url.startsWith("jdbc:postgresql://127.0.0.1:")
                || !uri.getPath().matches("/nq_baseline_test_[a-f0-9]{32}")
                || uri.getQuery() != null
                || uri.getUserInfo() != null
                || args.length != 2
                || !args[0].matches("brn-[a-f0-9-]{36}")
                || !args[1].matches("ptr-[a-f0-9-]{36}")) {
            throw new IllegalArgumentException(
                    "owned disposable database and exact run identities required");
        }
        var dataSource =
                new DriverManagerDataSource(
                        url + "?connectTimeout=5&socketTimeout=10",
                        System.getenv("NQ_FROZEN_FEE_DB_USER"),
                        System.getenv("NQ_FROZEN_FEE_DB_PASSWORD"));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(Transactions.class);
            context.registerBean(
                    "transactionManager",
                    DataSourceTransactionManager.class,
                    () -> new DataSourceTransactionManager(dataSource));
            context.registerBean(
                    JdbcTemplate.class,
                    () -> {
                        var jdbc = new JdbcTemplate(dataSource);
                        jdbc.setQueryTimeout(5);
                        return jdbc;
                    });
            context.registerBean(
                    AccountFeeModelService.class,
                    () -> {
                        var jdbc = context.getBean(JdbcTemplate.class);
                        return new AccountFeeModelService(
                                new JdbcAccountFeeRepository(jdbc),
                                new JdbcExchangeAccountRepository(jdbc),
                                new JdbcExchangeAccountCredentialRepository(
                                        jdbc, "signed-fee-synthetic-master-key-123456789"));
                    });
            context.refresh();
            var jdbc = context.getBean(JdbcTemplate.class);
            var mapper = new ObjectMapper();
            var config =
                    mapper.readTree(
                            jdbc.queryForObject(
                                    "SELECT config_snapshot_json::text FROM backtest_runs WHERE backtest_run_id=?",
                                    String.class,
                                    args[0]));
            var spec = config.path("executionSpec");
            var snapshot = mapper.readTree(spec.path("feeSourceSnapshotJson").asText());
            var original =
                    context.getBean(AccountFeeModelService.class)
                            .historical(
                                    Long.parseLong(snapshot.path("ownerUserId").asText()),
                                    UUID.fromString(snapshot.path("observationId").asText()),
                                    Long.parseLong(snapshot.path("exchangeAccountId").asText()),
                                    snapshot.path("exchangeCode").asText(),
                                    snapshot.path("tradeEnv").asText(),
                                    snapshot.path("symbol").asText(),
                                    Instant.parse(snapshot.path("frozenAt").asText()));
            if (!original.snapshot(mapper).equals(snapshot)
                    || !FrozenAccountFeeModel.digest(snapshot)
                            .equals(spec.path("feeSourceSnapshotSha256").asText())
                    || !snapshot.path("selectedRate")
                            .asText()
                            .equals(spec.path("feeRate").asText())) {
                throw new IllegalStateException("persisted source changed after reopening");
            }
            var paper =
                    mapper.readTree(
                            jdbc.queryForObject(
                                    "SELECT config_snapshot_json::text FROM paper_trading_runs WHERE paper_run_id=?",
                                    String.class,
                                    args[1]));
            if (!paper.path("executionSpec").equals(spec))
                throw new IllegalStateException("SIM inherited source differs");
            System.out.println("FROZEN_SOURCE_REOPEN=PASS");
        }
    }

    /** 独立进程也使用正式事务代理读取原持久化观察，防止内存状态代替重启证据。 */
    @Configuration
    @EnableTransactionManagement
    static class Transactions {}
}
