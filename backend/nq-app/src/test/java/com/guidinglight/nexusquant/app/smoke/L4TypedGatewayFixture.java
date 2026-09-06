package com.guidinglight.nexusquant.app.smoke;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.guidinglight.nexusquant.adapter.okx.service.*;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.*;
import com.guidinglight.nexusquant.livecontrol.application.PilotExecutionLeaseControlPlane;
import com.guidinglight.nexusquant.livecontrol.domain.*;
import com.guidinglight.nexusquant.livecontrol.domain.port.*;
import com.guidinglight.nexusquant.livecontrol.execution.application.port.ExecutionIntentRepository;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests;
import com.guidinglight.nexusquant.livecontrol.execution.domain.*;
import com.guidinglight.nexusquant.livecontrol.execution.infra.MinimalPilotTradingVenueGateway;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * Test-only frozen binding/clock lookup and no-network typed transport.
 * Provider mapping and gateway interpretation are real. Binding/lease authorization is deliberately
 * stubbed: this proves result interpretation, not pilot authorization or a full typed deployment.
 * A supplied repository allows the recovery probe to retain real PostgreSQL CAS and receipts.
 * This helper is not a correctness oracle. Temporary defective expectations belong only to tagged
 * cases bound by the plan JSON reproductionContract, and must be inverted/replaced by their owner.
 */
public final class L4TypedGatewayFixture {
    public static final Instant NOW = Instant.parse("2026-09-06T08:00:00Z");
    public final AtomicInteger cancelWire = new AtomicInteger();
    public final AtomicInteger queryWire = new AtomicInteger();
    public final AtomicInteger fillWire = new AtomicInteger();
    public final AtomicReference<ExecutionReceiptDraft> cancelReceipt = new AtomicReference<>();
    public final MinimalPilotTradingVenueGateway gateway;
    public final UUID placeIntentId;

    public L4TypedGatewayFixture(OrderRecord order, ExecutionIntentRepository realIntents) throws Exception {
        this(order, realIntents, UUID.randomUUID());
    }

    @SuppressWarnings("unchecked")
    public L4TypedGatewayFixture(OrderRecord order, ExecutionIntentRepository realIntents, UUID placeId) throws Exception {
        placeIntentId = placeId;
        UUID leaseId = UUID.randomUUID(), sessionId = UUID.randomUUID(), bindingId = UUID.randomUUID();
        var binding = mock(ExactPilotBinding.class, RETURNS_DEEP_STUBS);
        when(binding.sessionId()).thenReturn(sessionId);
        when(binding.bindingDigest()).thenReturn("a".repeat(64));
        when(binding.order().exchangeInstrumentId()).thenReturn(order.symbol());
        when(binding.order().side()).thenReturn(ExactPilotBinding.Side.BUY);
        when(binding.order().price()).thenReturn(order.price());
        when(binding.order().quantity()).thenReturn(order.qty());
        when(binding.correlation().traceId()).thenReturn(order.traceId());
        when(binding.correlation().requestId()).thenReturn("l4-typed-request");
        when(binding.pilotWindowStart()).thenReturn(NOW.minusSeconds(60));
        when(binding.pilotWindowEnd()).thenReturn(NOW.plusSeconds(60));
        var lease = mock(PilotExecutionLease.class);
        when(lease.id()).thenReturn(leaseId);
        when(lease.liveSessionId()).thenReturn(sessionId);
        when(lease.bindingId()).thenReturn(bindingId);
        when(lease.bindingDigest()).thenReturn("a".repeat(64));
        var leases = mock(PilotExecutionLeaseRepository.class);
        when(leases.find(leaseId)).thenReturn(Optional.of(lease));
        var bindings = mock(ExactPilotBindingRepository.class);
        when(bindings.find(sessionId, bindingId)).thenReturn(Optional.of(binding));
        JdbcTemplate facts = mock(JdbcTemplate.class);
        ResultSet link = mock(ResultSet.class);
        when(link.getObject(1, UUID.class)).thenReturn(leaseId);
        when(link.getObject(2, UUID.class)).thenReturn(placeId);
        when(facts.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(call ->
                List.of(((RowMapper<?>) call.getArgument(1)).mapRow(link, 0)));
        ResultSet clock = mock(ResultSet.class);
        when(clock.getTimestamp(1)).thenReturn(Timestamp.from(NOW));
        when(clock.getLong(2)).thenReturn(0L);
        when(clock.getLong(3)).thenReturn(1000L);
        when(clock.getLong(4)).thenReturn(60000L);
        when(facts.queryForObject(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(call ->
                ((RowMapper<?>) call.getArgument(1)).mapRow(clock, 0));

        var intents = realIntents == null ? mock(ExecutionIntentRepository.class) : realIntents;
        if (realIntents == null) {
            var send = mock(ExecutionIntent.class);
            when(send.intentId()).thenReturn(UUID.randomUUID());
            when(send.version()).thenReturn(3L);
            when(send.claimToken()).thenReturn(UUID.randomUUID());
            when(intents.createOrGet(any())).thenReturn(send);
            when(intents.claim(any(), anyString(), any(), any())).thenReturn(Optional.of(send));
            when(intents.markSendStarted(any(), anyLong(), any())).thenReturn(Optional.of(send));
            when(intents.appendReceiptAndTransition(any(), anyLong(), any(), any(), any())).thenAnswer(call -> {
                cancelReceipt.set(call.getArgument(3)); return send;
            });
        }
        OkxSpotProviderTransport transport = mock(OkxSpotProviderTransport.class);
        var fill = new RawFill("l4-typed-fill", order.price(), order.qty(), NOW);
        when(transport.queryOrder(any())).thenAnswer(call -> {
            queryWire.incrementAndGet();
            var command = (OrderCommand) call.getArgument(0);
            return new OrderResponse(metadata(OkxSpotProviderOperation.QUERY_ORDER),
                    new RawOrder(command.clientOrderId(), order.externalOrderId(), "filled", order.qty(), order.qty(),
                            BigDecimal.ZERO, List.of()), null);
        });
        when(transport.readOrder(any())).thenAnswer(call -> {
            queryWire.incrementAndGet();
            var command = (OrderCommand) call.getArgument(0);
            return new OrderResponse(metadata(OkxSpotProviderOperation.READ_ORDER),
                    new RawOrder(command.clientOrderId(), order.externalOrderId(), "filled", order.qty(), order.qty(),
                            BigDecimal.ZERO, List.of()), null);
        });
        when(transport.readFills(any())).thenAnswer(call -> {
            fillWire.incrementAndGet(); return new FillResponse(metadata(OkxSpotProviderOperation.READ_FILLS), List.of(fill), true, null);
        });
        when(transport.readClock(any())).thenReturn(new ClockResponse(metadata(OkxSpotProviderOperation.READ_CLOCK), NOW, NOW, Duration.ZERO, null));
        when(transport.cancelOrder(any())).thenAnswer(call -> { cancelWire.incrementAndGet(); throw new AssertionError("terminal cancel must not dispatch"); });
        var provider = new OkxSpotProviderAdapter(transport, new OkxSpotEndpointGuard(),
                new SpotProviderRequests.ResponseBounds(4096, 100), Clock.fixed(NOW, ZoneOffset.UTC));
        gateway = new MinimalPilotTradingVenueGateway(intents, bindings, leases,
                mock(PilotExecutionLeaseControlPlane.class), provider, facts, Clock.fixed(NOW, ZoneOffset.UTC));
    }
    private static ResponseMetadata metadata(OkxSpotProviderOperation operation) {
        return new ResponseMetadata(operation, 256, "l4-synthetic-response", NOW);
    }
}
