package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.guidinglight.nexusquant.adapter.okx.model.OkxFeeCost;
import com.guidinglight.nexusquant.app.config.livecontrol.MinimalLivePilotConfiguration;
import com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults;
import com.guidinglight.nexusquant.livecontrol.execution.infra.MinimalPilotTradingVenueGateway;
import com.guidinglight.nexusquant.scheduler.integration.LedgerModuleTradeLedgerGateway;
import com.guidinglight.nexusquant.scheduler.port.TradeLedgerGateway;
import com.guidinglight.nexusquant.scheduler.service.port.TradeRepository;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.trading.infra.jdbc.JdbcOrderRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 仅对已有OKX成交消费边界做SIM/临时PG证明；没有PLACE/CANCEL、provider联网或LIVE授权。 */
@EnabledIfSystemProperty(named = "nq.postgres.smoke.required", matches = "true")
class OkxSignedFeeLedgerPostgresIntegrationTest {
    @Test
    void rawProviderFeesCreateSignedTradesAndPairedLedgerThenReplayOriginalIdentity()
            throws Exception {
        try (var fixture = new SignedFeeLedgerPostgresIntegrationTest.Fixture()) {
            for (String currency : List.of("USDT", "BTC"))
                for (String value :
                        List.of(
                                "-0.1",
                                "0",
                                "0.1",
                                "-0.000000005",
                                "0.000000005",
                                "-0.000000000001",
                                "0.000000000001")) {
                    long account = fixture.account();
                    String id = UUID.randomUUID().toString();
                    Instant at = Instant.parse("2026-10-13T00:00:00Z");
                    var order =
                            new OrderRecord(
                                    "ord-" + id,
                                    account,
                                    null,
                                    "OKX",
                                    "BTC-USDT",
                                    "fee" + id.replace("-", "").substring(0, 24),
                                    "BUY",
                                    "MARKET",
                                    d("100"),
                                    BigDecimal.ONE,
                                    "venue-" + id,
                                    OrderStatus.FILLED,
                                    "OKX_SIGNED_FEE_ISOLATED_CONSUMPTION",
                                    "trc-" + id,
                                    "SIM");
                    new JdbcOrderRepository(fixture.jdbc).insert(order, at);
                    var fill =
                            new SpotProviderResults.FillReference(
                                    "fill-" + id, d("100"), BigDecimal.ONE, d(value), currency, at);
                    var observation =
                            new SpotProviderResults.OrderObservation(
                                    SpotProviderResults.OrderState.FILLED,
                                    order.clientOrderId(),
                                    order.externalOrderId(),
                                    BigDecimal.ONE,
                                    BigDecimal.ONE,
                                    BigDecimal.ZERO,
                                    List.of(fill),
                                    null,
                                    at);
                    var result =
                            new MinimalPilotTradingVenueGateway.PilotReconciliation(
                                    mock(ExactPilotBinding.class),
                                    observation,
                                    new SpotProviderResults.FillPage(
                                            order.clientOrderId(), List.of(fill), true, null, at));
                    apply(fixture, order, result);
                    var trade =
                            fixture.app
                                    .getBean(TradeRepository.class)
                                    .findByExchangeAndExchangeTradeId("OKX", fill.exchangeTradeId())
                                    .orElseThrow();
                    assertEquals(OkxFeeCost.amount(d(value)), trade.fee());
                    var request =
                            SignedFeeLedgerPostgresIntegrationTest.request(
                                    fixture.jdbc, trade.tradeId());
                    fixture.assertEconomics(request, BigDecimal.ZERO, d("1000"));
                    String state = fixture.snapshot(account);
                    apply(fixture, order, result);
                    assertEquals(state, fixture.snapshot(account));
                    assertEquals(
                            1,
                            fixture.jdbc.queryForObject(
                                    "SELECT count(*) FROM trades WHERE account_id=?",
                                    Integer.class,
                                    account));
                    // 旧绝对值返佣事实与新原始费用不一致时拒绝，不能静默改账或重复追加一笔成交。
                    var opposite =
                            new SpotProviderResults.FillReference(
                                    fill.exchangeTradeId(),
                                    fill.price(),
                                    fill.quantity(),
                                    fill.fee().negate(),
                                    currency,
                                    at);
                    if (trade.fee().signum() != 0) {
                        var conflicting =
                                new MinimalPilotTradingVenueGateway.PilotReconciliation(
                                        mock(ExactPilotBinding.class),
                                        observation,
                                        new SpotProviderResults.FillPage(
                                                order.clientOrderId(),
                                                List.of(opposite),
                                                true,
                                                null,
                                                at));
                        assertThrows(
                                IllegalStateException.class,
                                () -> apply(fixture, order, conflicting));
                        assertEquals(state, fixture.snapshot(account));
                    }
                }
            assertEquals(
                    0,
                    fixture.jdbc.queryForObject(
                            "SELECT count(*) FROM orders WHERE trade_env='LIVE'", Integer.class));
        }
    }

    /** 反射仅定位已有包级消费入口，测试不增大正式接口可见性；账务端口和持久仓储均为真实实现。 */
    private void apply(
            SignedFeeLedgerPostgresIntegrationTest.Fixture fixture,
            OrderRecord order,
            MinimalPilotTradingVenueGateway.PilotReconciliation result) {
        try {
            Method method =
                    MinimalLivePilotConfiguration.class.getDeclaredMethod(
                            "persistFills",
                            TradeRepository.class,
                            TradeLedgerGateway.class,
                            AuditLogRepository.class,
                            OrderRecord.class,
                            MinimalPilotTradingVenueGateway.PilotReconciliation.class,
                            String.class);
            method.setAccessible(true);
            method.invoke(
                    null,
                    fixture.app.getBean(TradeRepository.class),
                    new LedgerModuleTradeLedgerGateway(fixture.ledger),
                    mock(AuditLogRepository.class),
                    order,
                    result,
                    order.traceId());
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(ex.getCause());
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }
}
