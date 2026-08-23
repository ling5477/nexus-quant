package com.guidinglight.nexusquant.livecontrol.infra;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.livecontrol.application.AuthenticatedLiveControlActor;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingCommand;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingControlPlane;
import com.guidinglight.nexusquant.livecontrol.application.MinimalLivePilotCommand;
import com.guidinglight.nexusquant.livecontrol.application.MinimalLivePilotControlPlane;
import com.guidinglight.nexusquant.livecontrol.application.MinimalLivePilotPermit;
import com.guidinglight.nexusquant.livecontrol.application.MinimalPilotMaterializationCommand;
import com.guidinglight.nexusquant.livecontrol.application.PilotExecutionLeaseControlPlane;
import com.guidinglight.nexusquant.livecontrol.application.PilotScopeControlPlane;
import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.marketdata.domain.instrument.InstrumentCatalogItem;
import com.guidinglight.nexusquant.marketdata.domain.instrument.port.InstrumentCatalogReadPort;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Operator七项输入到exact scope/binding/active durable lease的唯一编排；本类不调用provider。 */
public final class MinimalLivePilotControlService implements MinimalLivePilotControlPlane {

    private static final Duration PILOT_WINDOW = Duration.ofMinutes(2);

    private final ExchangeAccountRepository accounts;
    private final InstrumentCatalogReadPort instruments;
    private final PilotScopeControlPlane scopes;
    private final ExactPilotBindingControlPlane bindings;
    private final PilotExecutionLeaseControlPlane leases;
    private final Clock clock;

    public MinimalLivePilotControlService(
            ExchangeAccountRepository accounts,
            InstrumentCatalogReadPort instruments,
            PilotScopeControlPlane scopes,
            ExactPilotBindingControlPlane bindings,
            PilotExecutionLeaseControlPlane leases,
            Clock clock
    ) {
        this.accounts = Objects.requireNonNull(accounts, "accounts must not be null");
        this.instruments = Objects.requireNonNull(instruments, "instruments must not be null");
        this.scopes = Objects.requireNonNull(scopes, "scopes must not be null");
        this.bindings = Objects.requireNonNull(bindings, "bindings must not be null");
        this.leases = Objects.requireNonNull(leases, "leases must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public MinimalLivePilotPermit prepare(MinimalLivePilotCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        ExchangeAccountSummary account = accounts.findById(command.exchangeAccountId())
                .orElseThrow(() -> rejected("PILOT_ACCOUNT_REFERENCE_MISMATCH"));
        if (!"OKX".equals(account.exchangeCode()) || !"LIVE".equals(account.tradeEnv())
                || !"ACTIVE".equals(account.status())) {
            throw rejected("PILOT_ACCOUNT_REFERENCE_MISMATCH");
        }
        List<InstrumentCatalogItem> catalog = instruments.findByExchangeAndSymbols("OKX", List.of(command.instrument()));
        if (catalog.size() != 1 || catalog.getFirst().instrumentId() == null) {
            throw rejected("PILOT_INSTRUMENT_REFERENCE_MISMATCH");
        }
        Instant start = clock.instant();
        Instant end = start.plus(PILOT_WINDOW);
        UUID operationId = UUID.randomUUID();
        String requestId = "pilot-request-" + operationId;
        String traceId = "pilot-trace-" + operationId;
        String idempotencyKey = "pilot-idempotency-" + operationId;
        ExactPilotBinding.Correlation correlation = new ExactPilotBinding.Correlation(
                requestId, traceId, idempotencyKey);
        UUID sessionId = UUID.randomUUID();
        UUID pilotScopeId = UUID.randomUUID();
        AuthenticatedLiveControlActor actor = new AuthenticatedLiveControlActor(account.ownerUserId());
        var materialized = scopes.materializeMinimal(actor, new MinimalPilotMaterializationCommand(
                sessionId, pilotScopeId, command.exchangeAccountId(), command.credentialReferenceId(),
                command.instrument(), command.configuredPilotMaxNotional(), start, end,
                idempotencyKey, requestId, traceId));
        ExactPilotBinding.OrderEnvelope order = new ExactPilotBinding.OrderEnvelope(
                catalog.getFirst().instrumentId(), command.instrument(), command.side(),
                ExactPilotBinding.OrderType.LIMIT, command.limitPrice(), command.quantity(), command.notional());
        UUID bindingId = UUID.randomUUID();
        ExactPilotBinding binding = bindings.create(actor, new ExactPilotBindingCommand(
                bindingId, sessionId, pilotScopeId, materialized.observationSetId(), order,
                start, end, correlation, end));
        var validation = bindings.validate(actor, sessionId, bindingId);
        if (validation.lifecycle() != ExactPilotBinding.Lifecycle.VERIFIED) {
            throw rejected("PILOT_BINDING_VALIDATION_FAILED");
        }
        var lease = leases.createAndActivate(
                actor, binding, command.configuredPilotMaxNotional(), end, correlation);
        UUID placeIntentId = UUID.randomUUID();
        String clientOrderId = com.guidinglight.nexusquant.livecontrol.execution.domain
                .ExecutionIntentCanonicalEncoder.stableClientOrderId(placeIntentId);
        return new MinimalLivePilotPermit(
                actor.userId(), sessionId, binding.id(), binding.bindingDigest(), lease.id(),
                placeIntentId, clientOrderId, requestId, traceId);
    }

    private static LiveControlException rejected(String code) {
        return new LiveControlException(code, "minimal live pilot preparation rejected");
    }
}
