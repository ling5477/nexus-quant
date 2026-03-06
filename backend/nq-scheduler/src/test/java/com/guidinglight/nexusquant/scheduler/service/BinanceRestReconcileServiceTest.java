package com.guidinglight.nexusquant.scheduler.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderSnapshot;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceTradeFill;
import com.guidinglight.nexusquant.adapter.binance.service.BinanceExchangeAdapter;
import com.guidinglight.nexusquant.contracts.event.TopicNames;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.core.model.OrderRecord;
import com.guidinglight.nexusquant.core.service.OrderCommandService;
import com.guidinglight.nexusquant.core.service.port.AuditLogRepository;
import com.guidinglight.nexusquant.infra.eventstore.EventStoreAppender;
import com.guidinglight.nexusquant.ledger.model.LedgerPostingResult;
import com.guidinglight.nexusquant.scheduler.model.PaperTradeRecord;
import com.guidinglight.nexusquant.scheduler.service.port.TradeRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * BinanceRestReconcileServiceTest 覆盖 GateC-2 PR-C12 的 REST-first 成交同步与去重语义。
 */
class BinanceRestReconcileServiceTest {

    /**
     * 验证 reconcile 能把 Binance myTrades 转成 trades/event_store/ledger，并通过状态机入口推进订单终态。
     */
    @Test
    void shouldInsertTradesPublishEventAndPostLedger() {
        OrderCommandService orderCommandService = Mockito.mock(OrderCommandService.class);
        BinanceExchangeAdapter binanceExchangeAdapter = Mockito.mock(BinanceExchangeAdapter.class);
        TradeRepository tradeRepository = Mockito.mock(TradeRepository.class);
        TradeLedgerGateway tradeLedgerGateway = Mockito.mock(TradeLedgerGateway.class);
        EventStoreAppender eventStoreAppender = Mockito.mock(EventStoreAppender.class);
        AuditLogRepository auditLogRepository = Mockito.mock(AuditLogRepository.class);

        BinanceRestReconcileService service = new BinanceRestReconcileService(
                orderCommandService,
                binanceExchangeAdapter,
                tradeRepository,
                tradeLedgerGateway,
                eventStoreAppender,
                auditLogRepository
        );

        OrderRecord acceptedOrder = new OrderRecord(
                "ord-binance-rec-1",
                3001L,
                null,
                "BINANCE",
                "BTC-USDT",
                "cid-binance-rec-1",
                "BUY",
                "LIMIT",
                new BigDecimal("30000.12"),
                new BigDecimal("0.010"),
                null,
                OrderStatus.ACCEPTED,
                "TEST",
                "trc-binance-rec-1"
        );
        BinanceTradeFill fill = new BinanceTradeFill(
                "trade-binance-1",
                "90001",
                "BTCUSDT",
                "BTC-USDT",
                "BUY",
                new BigDecimal("30000.12"),
                new BigDecimal("0.010"),
                new BigDecimal("0.10"),
                "USDT",
                Instant.parse("2026-03-06T11:00:00Z")
        );

        when(orderCommandService.findOrdersByStatuses(any(), eq(10))).thenReturn(List.of(acceptedOrder));
        when(binanceExchangeAdapter.getOrder(any())).thenReturn(new AdapterOrderSnapshot(
                acceptedOrder.accountId(),
                acceptedOrder.venue(),
                acceptedOrder.symbol(),
                acceptedOrder.clientOrderId(),
                "90001",
                "FILLED",
                acceptedOrder.traceId()
        ));
        when(orderCommandService.linkExternalOrderId("ord-binance-rec-1", "90001", "trc-binance-rec-1"))
                .thenReturn(acceptedOrder.withExternalOrderId("90001"));
        when(orderCommandService.findByOrderId("ord-binance-rec-1")).thenReturn(
                Optional.of(acceptedOrder.withExternalOrderId("90001").withStatus(OrderStatus.FILLED, "RECONCILE_STATUS_ALIGN"))
        );
        when(binanceExchangeAdapter.listTrades("BTC-USDT", "90001", "trc-binance-rec-1")).thenReturn(List.of(fill));
        when(tradeRepository.findByExchangeAndExchangeTradeId("BINANCE", "trade-binance-1")).thenReturn(Optional.empty());
        when(tradeLedgerGateway.postTrade(any())).thenReturn(new LedgerPostingResult(true, false, "OK"));

        int newTrades = service.reconcileOnce(10);

        assertEquals(1, newTrades);
        verify(orderCommandService).linkExternalOrderId("ord-binance-rec-1", "90001", "trc-binance-rec-1");
        verify(orderCommandService).transitionOrder("ord-binance-rec-1", OrderStatus.FILLED, "RECONCILE_STATUS_ALIGN", "trc-binance-rec-1");
        ArgumentCaptor<PaperTradeRecord> tradeCaptor = ArgumentCaptor.forClass(PaperTradeRecord.class);
        verify(tradeRepository).insert(tradeCaptor.capture());
        assertEquals("90001", tradeCaptor.getValue().externalOrderId());
        assertEquals("trade-binance-1", tradeCaptor.getValue().exchangeTradeId());
        verify(eventStoreAppender).append(eq(TopicNames.TRADE_EVENT_V1), any());
        verify(tradeLedgerGateway).postTrade(any());
    }

    /**
     * 验证重复 tradeId 会命中去重路径，不会重复写 trades 或重复触发 ledger。
     */
    @Test
    void shouldSkipDuplicateTradeIdWithoutLedgerSideEffect() {
        OrderCommandService orderCommandService = Mockito.mock(OrderCommandService.class);
        BinanceExchangeAdapter binanceExchangeAdapter = Mockito.mock(BinanceExchangeAdapter.class);
        TradeRepository tradeRepository = Mockito.mock(TradeRepository.class);
        TradeLedgerGateway tradeLedgerGateway = Mockito.mock(TradeLedgerGateway.class);
        EventStoreAppender eventStoreAppender = Mockito.mock(EventStoreAppender.class);
        AuditLogRepository auditLogRepository = Mockito.mock(AuditLogRepository.class);

        BinanceRestReconcileService service = new BinanceRestReconcileService(
                orderCommandService,
                binanceExchangeAdapter,
                tradeRepository,
                tradeLedgerGateway,
                eventStoreAppender,
                auditLogRepository
        );

        OrderRecord acceptedOrder = new OrderRecord(
                "ord-binance-rec-2",
                3001L,
                null,
                "BINANCE",
                "BTC-USDT",
                "cid-binance-rec-2",
                "BUY",
                "LIMIT",
                new BigDecimal("30000.12"),
                new BigDecimal("0.010"),
                "90002",
                OrderStatus.ACCEPTED,
                "TEST",
                "trc-binance-rec-2"
        );
        BinanceTradeFill duplicateFill = new BinanceTradeFill(
                "trade-binance-dup-1",
                "90002",
                "BTCUSDT",
                "BTC-USDT",
                "BUY",
                new BigDecimal("30000.12"),
                new BigDecimal("0.010"),
                new BigDecimal("0.10"),
                "USDT",
                Instant.parse("2026-03-06T11:05:00Z")
        );
        PaperTradeRecord existingTrade = new PaperTradeRecord(
                "trd-existing-1",
                acceptedOrder.orderId(),
                acceptedOrder.accountId(),
                acceptedOrder.symbol(),
                "BINANCE",
                acceptedOrder.externalOrderId(),
                duplicateFill.exchangeTradeId(),
                duplicateFill.price(),
                duplicateFill.qty(),
                duplicateFill.fee(),
                duplicateFill.feeCurrency(),
                acceptedOrder.traceId(),
                duplicateFill.ts()
        );

        when(orderCommandService.findOrdersByStatuses(any(), eq(10))).thenReturn(List.of(acceptedOrder));
        when(binanceExchangeAdapter.getOrder(any())).thenReturn(new AdapterOrderSnapshot(
                acceptedOrder.accountId(),
                acceptedOrder.venue(),
                acceptedOrder.symbol(),
                acceptedOrder.clientOrderId(),
                acceptedOrder.externalOrderId(),
                "PARTIALLY_FILLED",
                acceptedOrder.traceId()
        ));
        when(orderCommandService.findByOrderId("ord-binance-rec-2")).thenReturn(Optional.of(acceptedOrder));
        when(binanceExchangeAdapter.listTrades("BTC-USDT", "90002", "trc-binance-rec-2")).thenReturn(List.of(duplicateFill));
        when(tradeRepository.findByExchangeAndExchangeTradeId("BINANCE", "trade-binance-dup-1"))
                .thenReturn(Optional.of(existingTrade));

        int newTrades = service.reconcileOnce(10);

        assertEquals(0, newTrades);
        verify(tradeRepository, never()).insert(any());
        verify(tradeLedgerGateway, never()).postTrade(any());
        verify(eventStoreAppender, never()).append(eq(TopicNames.TRADE_EVENT_V1), any());
        verify(auditLogRepository, times(1)).append(eq("RECONCILE"), eq("BINANCE_FILL_DEDUP_HIT"), eq("ord-binance-rec-2"), eq("trc-binance-rec-2"), any());
    }
}
