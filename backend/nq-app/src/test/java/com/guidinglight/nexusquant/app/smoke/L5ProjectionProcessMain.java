package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.eventstore.infra.EventStoreAppender;
import com.guidinglight.nexusquant.ledger.contracts.model.TradeLedgerRequest;
import com.guidinglight.nexusquant.ledger.infra.jdbc.JdbcLedgerPostingRepository;
import com.guidinglight.nexusquant.ledger.infra.jdbc.JdbcLedgerRiskAuditRepository;
import com.guidinglight.nexusquant.ledger.service.TradeLedgerPostingService;
import com.guidinglight.nexusquant.ledger.service.port.LedgerPostingRepository;
import com.guidinglight.nexusquant.ledger.service.port.TradeLedgerPort;
import com.guidinglight.nexusquant.scheduler.infra.jdbc.JdbcTradeRepository;
import com.guidinglight.nexusquant.trading.infra.query.JdbcTradingQueryFacade;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 只运行真实 NQ 记账服务与事务代理的独立 JVM；屏障不替换 SQL、返回值或业务事实。 */
public final class L5ProjectionProcessMain {
    @Configuration
    @EnableTransactionManagement
    static class Transactions { }

    private L5ProjectionProcessMain() { }

    static AnnotationConfigApplicationContext context(DriverManagerDataSource source,
                                                       AtomicReference<String> cut) {
        var context = new AnnotationConfigApplicationContext();
        context.register(Transactions.class);
        context.registerBean(JdbcTemplate.class, () -> new JdbcTemplate(source));
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
        context.registerBean("transactionManager", DataSourceTransactionManager.class,
                () -> new DataSourceTransactionManager(source));
        context.registerBean(LedgerPostingRepository.class, () -> {
            var proxy = new ProxyFactory(new JdbcLedgerPostingRepository(context.getBean(JdbcTemplate.class)));
            proxy.addAdvice((MethodInterceptor) invocation -> {
                Object result = invocation.proceed();
                String point = invocation.getMethod().getName();
                String armed = cut.get();
                if (Set.of("findPosition", "findAssetPosition").contains(point)
                        && point.equals(armed) && cut.compareAndSet(armed, "NONE")) {
                    B0Fixture.require(TransactionSynchronizationManager.isActualTransactionActive());
                    Files.writeString(Path.of("projection-cut.txt"), point);
                    long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
                    while (!Files.exists(Path.of("projection-release.txt"))) {
                        if (System.nanoTime() > deadline) throw new IllegalStateException("projection barrier timeout");
                        Thread.sleep(20);
                    }
                }
                return result;
            });
            return (LedgerPostingRepository) proxy.getProxy();
        });
        context.register(JdbcTradeRepository.class, EventStoreAppender.class,
                JdbcLedgerRiskAuditRepository.class, TradeLedgerPostingService.class, JdbcTradingQueryFacade.class);
        try {
            context.refresh();
            return context;
        } catch (RuntimeException | Error failure) {
            context.close();
            throw failure;
        }
    }

    static TradeLedgerRequest request(JdbcTemplate jdbc, String tradeId) {
        return jdbc.queryForObject("""
                SELECT t.*, o.side FROM trades t JOIN orders o ON o.order_id=t.order_id
                WHERE t.trade_id=? AND t.account_id=o.account_id AND t.symbol=o.symbol
                  AND t.trade_env=o.trade_env
                """, (row, index) -> new TradeLedgerRequest(row.getString("trade_id"), row.getString("order_id"),
                row.getLong("account_id"), row.getString("symbol"), OrderSide.valueOf(row.getString("side")),
                row.getBigDecimal("price"), row.getBigDecimal("qty"), row.getBigDecimal("fee"),
                row.getString("fee_currency"), row.getString("trace_id"), row.getTimestamp("ts").toInstant()), tradeId);
    }

    public static void main(String[] args) throws Exception {
        B0Fixture.require(args.length == 0);
        String db = System.getenv("NQ_B0_DB");
        String name = URI.create(db.substring(5)).getPath().substring(1);
        B0Fixture.validate(db, name, System.getenv("NQ_B0_PROFILE"), System.getenv("NQ_B0_VENUE"), System.getenv(), false);
        ExchangeNoOutboundGuard.install();
        var source = new DriverManagerDataSource(db, B0Fixture.APP, "");
        var cut = new AtomicReference<>("NONE");
        try (var context = context(source, cut);
             var input = new BufferedReader(new InputStreamReader(System.in))) {
            var jdbc = context.getBean(JdbcTemplate.class);
            B0Fixture.require(name.equals(jdbc.queryForObject("SELECT identity FROM b0_fixture_identity", String.class)));
            B0Fixture.require(B0Fixture.APP.equals(jdbc.queryForObject("SELECT current_user", String.class)));
            var ledger = context.getBean(TradeLedgerPort.class);
            System.out.println("B0_READY " + ProcessHandle.current().pid());
            System.out.flush();
            String command;
            while ((command = input.readLine()) != null && !"STOP".equals(command)) {
                String result;
                if (command.startsWith("APPLY ") && command.substring(6).matches("lp-trade-[0-9]{1,4}")) {
                    var posted = ledger.postTrade(request(jdbc, command.substring(6)));
                    result = posted.reason();
                } else if (command.startsWith("ARM ")
                        && Set.of("findPosition", "findAssetPosition").contains(command.substring(4))) {
                    B0Fixture.require(cut.compareAndSet("NONE", command.substring(4)));
                    Files.deleteIfExists(Path.of("projection-cut.txt"));
                    Files.deleteIfExists(Path.of("projection-release.txt"));
                    result = "ARMED";
                } else if ("WIRE".equals(command)) {
                    B4TransactionFaults.arm(ledger, jdbc, "postTrade", "WIRE");
                    result = "ARMED";
                } else {
                    throw new IllegalArgumentException("unsupported projection proof command");
                }
                System.out.println("B0_RESULT " + result);
                System.out.flush();
            }
        }
    }
}
