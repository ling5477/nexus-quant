package com.guidinglight.nexusquant.scheduler.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.contracts.event.port.EventPublisherPort;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.ledger.contracts.model.LedgerPostingResult;
import com.guidinglight.nexusquant.ledger.contracts.model.TradeLedgerRequest;
import com.guidinglight.nexusquant.scheduler.integration.AdapterBackedTradingVenueGateway;
import com.guidinglight.nexusquant.scheduler.integration.PaperTradingAdapter;
import com.guidinglight.nexusquant.scheduler.model.PaperTradeRecord;
import com.guidinglight.nexusquant.scheduler.port.OrderExecutionGateway;
import com.guidinglight.nexusquant.scheduler.port.TradeLedgerGateway;
import com.guidinglight.nexusquant.scheduler.service.port.TradeRepository;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class PaperMatchingTimeTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void firstMatchAndReplayUseTheDurableTimeForTheRunMode(boolean bounded) {
        Instant end = Instant.parse("2026-11-23T06:00:00Z");
        Instant quoteAt = end.minusSeconds(60);
        Instant firstFillAt = end.plusSeconds(1);
        BigDecimal price = new BigDecimal("100.00000000");
        var order = new OrderRecord("order-1", 7L, "strategy-1", "PAPER", "BTC-USDT", "coid-sim-1",
                "BUY", "MARKET", null, BigDecimal.ONE, "external-1", OrderStatus.ACCEPTED, "ACCEPTED", "trace-1");
        var decision = new StrategySimDecisionRepository.DecisionView("decision-1", "paper-1", "version", "sha",
                BigDecimal.ONE, "input", "bar", quoteAt.minusSeconds(3600), quoteAt.minusSeconds(1), quoteAt,
                "ACCEPTED", "EXECUTION_ACCEPTED", "BUY", BigDecimal.ONE, price, new BigDecimal("0.001"),
                BigDecimal.ZERO, order.strategyRunId(), order.orderId());
        var orders = mock(OrderExecutionGateway.class);
        var trades = mock(TradeRepository.class);
        var decisions = mock(StrategySimDecisionRepository.class);
        var ledger = mock(TradeLedgerGateway.class);
        var databaseTime = new AtomicReference<>(quoteAt);
        when(orders.findMatchableOrders(1)).thenReturn(List.of(order));
        when(decisions.<Boolean>withAccountMutex(eq(order.accountId()), any())).thenAnswer(call ->
                call.<Supplier<Boolean>>getArgument(1).get());
        when(decisions.findByOrderId(order.orderId())).thenReturn(Optional.of(decision));
        when(decisions.boundedMatchingTime(decision.paperRunId())).thenAnswer(call -> bounded ? databaseTime.get() : null);
        when(ledger.postTrade(any())).thenReturn(new LedgerPostingResult(true, false, "POSTED"));
        var matcher = new PaperMatchingService(orders, trades, ledger, mock(EventPublisherPort.class),
                mock(AuditLogRepository.class), new AdapterBackedTradingVenueGateway(List.of(new PaperTradingAdapter())),
                decisions, null);

        assertEquals(end.minusSeconds(60), databaseTime.get());
        assertEquals(OrderStatus.ACCEPTED, orders.findMatchableOrders(1).getFirst().status());
        assertEquals(databaseTime.get(), decisions.findByOrderId(order.orderId()).orElseThrow().executionOpenTime());
        assertTrue(trades.findByOrderId(order.orderId()).isEmpty());
        verify(trades, never()).insert(any());
        verifyNoInteractions(ledger);

        databaseTime.set(firstFillAt);
        assertEquals(1, matcher.matchOnce(1));
        var inserted = ArgumentCaptor.forClass(PaperTradeRecord.class);
        verify(trades).insert(inserted.capture());
        PaperTradeRecord first = inserted.getValue();
        assertEquals(bounded ? firstFillAt : quoteAt, first.ts());
        assertEquals(price, first.price());
        assertEquals(0, new BigDecimal("0.10").compareTo(first.fee()));

        // 让撮合器再次看到原已受理快照，覆盖仅靠订单变为 FILLED 即跳过的测试不能到达的重放分支。
        when(trades.findByOrderId(order.orderId())).thenReturn(Optional.of(first));
        databaseTime.set(end.plusSeconds(30));
        assertEquals(0, matcher.matchOnce(1));
        verify(trades, times(1)).insert(any());
        var postings = ArgumentCaptor.forClass(TradeLedgerRequest.class);
        verify(ledger, times(2)).postTrade(postings.capture());
        var posting = postings.getAllValues().getFirst();
        assertEquals(first.tradeId(), posting.tradeId());
        assertEquals(first.ts(), posting.ts());
        assertEquals(first.fee(), posting.fee());
        assertEquals(posting, postings.getAllValues().getLast());
    }
}
