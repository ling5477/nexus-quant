package com.guidinglight.nexusquant.api.service;

import com.guidinglight.nexusquant.api.model.AccountBalanceView;
import com.guidinglight.nexusquant.api.model.OrderView;
import com.guidinglight.nexusquant.api.model.PositionView;
import com.guidinglight.nexusquant.api.model.TradeView;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CoreTradingQueryFacadeTest 验证 GateD 最小查询闭环直接来自事实表只读查询。
 * <p>
 * Why:
 * 第四批把 facade 从单纯订单仓储读取升级为 JDBC 聚合视图，因此测试也需要显式覆盖
 * `order / trade / position / account` 四类查询，而不是继续停留在旧的 OrderRepository stub。
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class CoreTradingQueryFacadeTest {

    @Test
    void shouldQueryOrderViewFromOrdersTable() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("ord-1"))).thenReturn(List.of(new OrderView(
                "ord-1",
                1001L,
                "OKX",
                "BTC-USDT",
                "cid-1",
                "ext-1",
                new BigDecimal("30000.12"),
                new BigDecimal("0.010"),
                OrderStatus.ACCEPTED,
                "trc-1"
        )));
        CoreTradingQueryFacade facade = new CoreTradingQueryFacade(jdbcTemplate);

        var view = facade.queryOrder("ord-1", "trc-query");

        assertTrue(view.isPresent());
        assertEquals("ord-1", view.get().orderId());
        assertEquals("OKX", view.get().venue());
        assertEquals(new BigDecimal("0.010"), view.get().quantity());
        assertEquals("trc-1", view.get().traceId());
    }

    @Test
    void shouldQueryLatestTradeView() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("ord-2"))).thenReturn(List.of(new TradeView(
                "trd-2",
                "ord-2",
                1002L,
                "PAPER",
                "ETH-USDT",
                "paper-ord-2",
                "paper-trd-2",
                new BigDecimal("2000.00"),
                new BigDecimal("0.500"),
                BigDecimal.ZERO,
                "USDT",
                Instant.parse("2026-03-12T08:00:00Z"),
                "trc-2"
        )));
        CoreTradingQueryFacade facade = new CoreTradingQueryFacade(jdbcTemplate);

        var view = facade.queryLatestTrade("ord-2", "trc-query");

        assertTrue(view.isPresent());
        assertEquals("trd-2", view.get().tradeId());
        assertEquals("PAPER", view.get().venue());
        assertEquals(new BigDecimal("0.500"), view.get().quantity());
    }

    @Test
    void shouldQueryPositionView() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1001L), eq("BTC-USDT"))).thenReturn(List.of(new PositionView(
                1001L,
                "PAPER",
                "BTC-USDT",
                new BigDecimal("0.100"),
                new BigDecimal("0.080"),
                new BigDecimal("30000.00"),
                "trc-pos-1"
        )));
        CoreTradingQueryFacade facade = new CoreTradingQueryFacade(jdbcTemplate);

        var view = facade.queryPosition(1001L, "BTC-USDT", "trc-query");

        assertTrue(view.isPresent());
        assertEquals("PAPER", view.get().venue());
        assertEquals(new BigDecimal("0.080"), view.get().availableQuantity());
    }

    @Test
    void shouldQueryAccountView() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1001L))).thenReturn(List.of(
                new AccountBalanceView(
                        "BTC",
                        new BigDecimal("0.100"),
                        new BigDecimal("0.080"),
                        new BigDecimal("0.020"),
                        Instant.parse("2026-03-12T08:00:00Z"),
                        "trc-acc-2"
                ),
                new AccountBalanceView(
                        "USDT",
                        new BigDecimal("1000.00"),
                        new BigDecimal("999.00"),
                        new BigDecimal("1.00"),
                        Instant.parse("2026-03-12T08:00:01Z"),
                        "trc-acc-1"
                )
        ));
        when(jdbcTemplate.query(eq("SELECT venue FROM accounts WHERE account_id = ?"), any(RowMapper.class), eq(1001L)))
                .thenReturn(List.of("PAPER"));
        CoreTradingQueryFacade facade = new CoreTradingQueryFacade(jdbcTemplate);

        var view = facade.queryAccount(1001L, "trc-query");

        assertTrue(view.isPresent());
        assertEquals("PAPER", view.get().venue());
        assertEquals(2, view.get().balances().size());
        assertEquals("BTC", view.get().balances().getFirst().currency());
        assertEquals(new BigDecimal("0.100"), view.get().balances().get(0).balance());
        assertEquals(new BigDecimal("0.080"), view.get().balances().get(0).available());
        assertEquals(new BigDecimal("0.020"), view.get().balances().get(0).frozen());
        assertEquals("USDT", view.get().balances().get(1).currency());
        assertEquals(new BigDecimal("1000.00"), view.get().balances().get(1).balance());
        assertEquals(new BigDecimal("999.00"), view.get().balances().get(1).available());
        assertEquals(new BigDecimal("1.00"), view.get().balances().get(1).frozen());
    }

    @Test
    void shouldReturnEmptyWhenInputIsInvalid() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CoreTradingQueryFacade facade = new CoreTradingQueryFacade(jdbcTemplate);

        assertTrue(facade.queryOrder("   ", "trc-query").isEmpty());
        assertTrue(facade.queryLatestTrade(null, "trc-query").isEmpty());
        assertTrue(facade.queryPosition(0L, "BTC-USDT", "trc-query").isEmpty());
        assertTrue(facade.queryAccount(null, "trc-query").isEmpty());
    }
}
