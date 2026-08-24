package com.guidinglight.nexusquant.app.config.livecontrol;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.ledger.contracts.model.LedgerPostingResult;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults;
import com.guidinglight.nexusquant.livecontrol.execution.infra.MinimalPilotTradingVenueGateway;
import com.guidinglight.nexusquant.scheduler.model.PaperTradeRecord;
import com.guidinglight.nexusquant.scheduler.service.TradeLedgerGateway;
import com.guidinglight.nexusquant.scheduler.service.port.TradeRepository;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class MinimalLivePilotConfigurationTest {

    @Test
    void existingTradeStillRepairsIdempotentLedgerAfterCrash() {
        TradeRepository trades = mock(TradeRepository.class);
        TradeLedgerGateway ledger = mock(TradeLedgerGateway.class);
        AuditLogRepository audit = mock(AuditLogRepository.class);
        Instant filledAt = Instant.parse("2026-08-23T00:00:00Z");
        OrderRecord order = new OrderRecord(
                "ord-1", 17L, null, "OKX", "BTC-USDT", "nq-client", "BUY", "LIMIT",
                new BigDecimal("100.00000000"), new BigDecimal("0.01000000"), "okx-order-1",
                OrderStatus.FILLED, "PILOT", "trace");
        PaperTradeRecord existing = new PaperTradeRecord(
                "trd-stable", order.orderId(), order.accountId(), order.symbol(), "OKX",
                order.externalOrderId(), "okx-trade-1", order.price(), order.qty(),
                new BigDecimal("0.00100000"), "USDT", "trace", filledAt);
        var fill = new SpotProviderResults.FillReference(
                existing.exchangeTradeId(), existing.price(), existing.qty(), existing.fee(),
                existing.feeCurrency(), filledAt);
        var observation = new SpotProviderResults.OrderObservation(
                SpotProviderResults.OrderState.FILLED, order.clientOrderId(), order.externalOrderId(),
                order.qty(), order.qty(), BigDecimal.ZERO, List.of(fill), null, filledAt);
        var reconciliation = new MinimalPilotTradingVenueGateway.PilotReconciliation(
                mock(com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding.class),
                observation,
                new SpotProviderResults.FillPage(order.clientOrderId(), List.of(fill), true, null, filledAt));
        when(trades.findByExchangeAndExchangeTradeId("OKX", existing.exchangeTradeId()))
                .thenReturn(Optional.of(existing));
        when(ledger.postTrade(argThat(request -> request.tradeId().equals(existing.tradeId()))))
                .thenReturn(new LedgerPostingResult(true, true, "IDEMPOTENT_HIT"));

        MinimalLivePilotConfiguration.persistFills(
                trades, ledger, audit, order, reconciliation, "trace");

        verify(trades, never()).insert(org.mockito.ArgumentMatchers.any());
        verify(ledger).postTrade(argThat(request -> request.tradeId().equals(existing.tradeId())));
        verify(audit).append(
                org.mockito.ArgumentMatchers.eq("RECONCILE"),
                org.mockito.ArgumentMatchers.eq("GATEY_PILOT_FILL_LEDGER_RECONCILED"),
                org.mockito.ArgumentMatchers.eq(order.orderId()),
                org.mockito.ArgumentMatchers.eq("trace"),
                org.mockito.ArgumentMatchers.anyMap());
    }
}
