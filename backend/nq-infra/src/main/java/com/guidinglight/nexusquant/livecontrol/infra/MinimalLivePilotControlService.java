package com.guidinglight.nexusquant.livecontrol.infra;

import com.guidinglight.nexusquant.account.application.CredentialPermissionProbeService;
import com.guidinglight.nexusquant.account.application.command.CredentialPermissionProbeCommand;
import com.guidinglight.nexusquant.account.domain.CredentialPermissionProbeSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.account.infra.jdbc.CanonicalLegacyAccountBridgeService;
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
import com.guidinglight.nexusquant.livecontrol.domain.PilotObservationSet;
import com.guidinglight.nexusquant.livecontrol.domain.PilotPrerequisiteObservation;
import com.guidinglight.nexusquant.livecontrol.domain.port.PilotScopeRepository;
import com.guidinglight.nexusquant.marketdata.domain.instrument.InstrumentCatalogItem;
import com.guidinglight.nexusquant.marketdata.domain.instrument.port.InstrumentCatalogReadPort;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Operator 五项输入到 permission refresh、exact scope/binding/active durable lease 的唯一编排。 */
public final class MinimalLivePilotControlService implements MinimalLivePilotControlPlane {

    private static final Duration PILOT_WINDOW = Duration.ofMinutes(2);
    private static final Duration MAXIMUM_PERMISSION_AGE = Duration.ofMinutes(1);
    private static final BigDecimal SAFETY_BUFFER_USDT = new BigDecimal("0.10000000");

    private final ExchangeAccountRepository accounts;
    private final InstrumentCatalogReadPort instruments;
    private final CredentialPermissionProbeService permissionProbeService;
    private final PilotScopeControlPlane scopes;
    private final PilotScopeRepository scopeRepository;
    private final ExactPilotBindingControlPlane bindings;
    private final PilotExecutionLeaseControlPlane leases;
    private final Clock clock;
    private final CanonicalLegacyAccountBridgeService legacyBridge;

    public MinimalLivePilotControlService(
            ExchangeAccountRepository accounts,
            InstrumentCatalogReadPort instruments,
            CredentialPermissionProbeService permissionProbeService,
            PilotScopeControlPlane scopes,
            PilotScopeRepository scopeRepository,
            ExactPilotBindingControlPlane bindings,
            PilotExecutionLeaseControlPlane leases,
            Clock clock,
            CanonicalLegacyAccountBridgeService legacyBridge
    ) {
        this.accounts = Objects.requireNonNull(accounts, "accounts must not be null");
        this.instruments = Objects.requireNonNull(instruments, "instruments must not be null");
        this.permissionProbeService = Objects.requireNonNull(
                permissionProbeService, "permissionProbeService must not be null");
        this.scopes = Objects.requireNonNull(scopes, "scopes must not be null");
        this.scopeRepository = Objects.requireNonNull(scopeRepository, "scopeRepository must not be null");
        this.bindings = Objects.requireNonNull(bindings, "bindings must not be null");
        this.leases = Objects.requireNonNull(leases, "leases must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.legacyBridge = Objects.requireNonNull(legacyBridge, "legacyBridge must not be null");
    }

    public MinimalLivePilotControlService(
            ExchangeAccountRepository accounts,
            InstrumentCatalogReadPort instruments,
            CredentialPermissionProbeService permissionProbeService,
            PilotScopeControlPlane scopes,
            PilotScopeRepository scopeRepository,
            ExactPilotBindingControlPlane bindings,
            PilotExecutionLeaseControlPlane leases,
            Clock clock
    ) {
        this.accounts = Objects.requireNonNull(accounts, "accounts must not be null");
        this.instruments = Objects.requireNonNull(instruments, "instruments must not be null");
        this.permissionProbeService = Objects.requireNonNull(permissionProbeService);
        this.scopes = Objects.requireNonNull(scopes, "scopes must not be null");
        this.scopeRepository = Objects.requireNonNull(scopeRepository, "scopeRepository must not be null");
        this.bindings = Objects.requireNonNull(bindings, "bindings must not be null");
        this.leases = Objects.requireNonNull(leases, "leases must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.legacyBridge = null;
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
        UUID operationId = UUID.randomUUID();
        String requestId = "pilot-request-" + operationId;
        String traceId = "pilot-trace-" + operationId;
        String idempotencyKey = "pilot-idempotency-" + operationId;
        ExactPilotBinding.Correlation correlation = new ExactPilotBinding.Correlation(
                requestId, traceId, idempotencyKey);
        if (legacyBridge == null) {
            if (account.legacyAccountId() == null) {
                throw rejected("CANONICAL_LEGACY_ACCOUNT_BRIDGE_REQUIRED");
            }
        } else {
            legacyBridge.resolveOrCreate(account, traceId, clock.instant().truncatedTo(ChronoUnit.MICROS));
        }
        AuthenticatedLiveControlActor actor = new AuthenticatedLiveControlActor(account.ownerUserId());
        var replacement = leases.prepareZeroIntentReplacement(
                actor, command.exchangeAccountId(), command.credentialReferenceId(),
                command.instrument(), command.configuredPilotMaxNotional(), correlation);
        refreshPermission(account, command, traceId);
        // PostgreSQL TIMESTAMPTZ 与 canonical digest 共用微秒精度，必须在 Clock 边界统一。
        Instant start = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant end = start.plus(PILOT_WINDOW);
        UUID sessionId = UUID.randomUUID();
        UUID pilotScopeId = UUID.randomUUID();
        var materialized = scopes.materializeMinimal(actor, new MinimalPilotMaterializationCommand(
                sessionId, pilotScopeId, command.exchangeAccountId(), command.credentialReferenceId(),
                command.instrument(), command.configuredPilotMaxNotional(), start, end,
                idempotencyKey, requestId, traceId));
        PilotObservationSet observations = scopeRepository.findObservationSet(
                        materialized.pilotScopeId(), materialized.observationSetId())
                .orElseThrow(() -> rejected("PILOT_PREREQUISITE_FACTS_NOT_FOUND"));
        List<InstrumentCatalogItem> catalog = instruments.findByExchangeAndSymbols(
                "OKX", List.of(command.instrument()));
        if (catalog.size() != 1 || catalog.getFirst().instrumentId() == null) {
            throw rejected("PILOT_INSTRUMENT_REFERENCE_MISMATCH");
        }
        SafeOrderParameters parameters = calculateOrderParameters(
                command, catalog.getFirst(), observations);
        ExactPilotBinding.OrderEnvelope order = new ExactPilotBinding.OrderEnvelope(
                catalog.getFirst().instrumentId(), command.instrument(), command.side(),
                ExactPilotBinding.OrderType.LIMIT, parameters.limitPrice(),
                parameters.quantity(), parameters.notional());
        UUID bindingId = UUID.randomUUID();
        ExactPilotBinding binding = bindings.create(actor, new ExactPilotBindingCommand(
                bindingId, materialized.sessionId(), materialized.pilotScopeId(),
                materialized.observationSetId(), order,
                start, end, correlation, end));
        var validation = bindings.validate(actor, sessionId, bindingId);
        if (validation.lifecycle() != ExactPilotBinding.Lifecycle.VERIFIED) {
            throw rejected("PILOT_BINDING_VALIDATION_FAILED");
        }
        var lease = replacement.isPresent()
                ? leases.createReplacementAndActivate(
                actor, binding, command.configuredPilotMaxNotional(), end, correlation, replacement.get())
                : leases.createAndActivate(
                actor, binding, command.configuredPilotMaxNotional(), end, correlation);
        UUID placeIntentId = UUID.randomUUID();
        String clientOrderId = com.guidinglight.nexusquant.livecontrol.execution.domain
                .ExecutionIntentCanonicalEncoder.stableClientOrderId(placeIntentId);
        return new MinimalLivePilotPermit(
                actor.userId(), materialized.sessionId(), binding.id(), binding.bindingDigest(), lease.id(),
                placeIntentId, clientOrderId, requestId, traceId,
                parameters.limitPrice(), parameters.quantity(), parameters.notional());
    }

    private void refreshPermission(
            ExchangeAccountSummary account,
            MinimalLivePilotCommand command,
            String traceId
    ) {
        CredentialPermissionProbeSummary summary = permissionProbeService.probe(
                account.ownerUserId(), command.exchangeAccountId(), command.credentialReferenceId(),
                "gatey-minimal-live-pilot",
                new CredentialPermissionProbeCommand(
                        "GateY minimal pilot prerequisite refresh", true,
                        "GATEY_PILOT_READINESS", true),
                traceId);
        Instant now = clock.instant();
        boolean fresh = summary.lastPermissionProbeAt() != null
                && !summary.lastPermissionProbeAt().isAfter(now.plusSeconds(5))
                && !summary.lastPermissionProbeAt().plus(MAXIMUM_PERMISSION_AGE).isBefore(now);
        if (!Objects.equals(summary.accountId(), command.exchangeAccountId())
                || !Objects.equals(summary.credentialId(), command.credentialReferenceId())
                || !"OKX_API_V5".equals(summary.credentialType())
                || !"OKX".equals(summary.exchange())
                || !"SUCCEEDED".equals(summary.permissionProbeStatus())
                || !"TRADE".equals(summary.permissionScope())
                || summary.withdrawEnabled()
                || !"PASSED".equals(summary.ipAllowlistProbeStatus())
                || !fresh) {
            throw rejected("PILOT_CREDENTIAL_PERMISSION_REFRESH_REJECTED");
        }
    }

    private static SafeOrderParameters calculateOrderParameters(
            MinimalLivePilotCommand command,
            InstrumentCatalogItem catalog,
            PilotObservationSet observations
    ) {
        PilotPrerequisiteObservation.InstrumentItem instrument = observations.instrumentMetadata().items().stream()
                .filter(value -> value.symbol().equals(command.instrument()))
                .findFirst()
                .orElseThrow(() -> rejected("PILOT_INSTRUMENT_REFERENCE_MISMATCH"));
        var market = observations.marketSnapshot();
        if (!command.instrument().equals(market.instrument())
                || instrument.tradingStatus() != PilotPrerequisiteObservation.TradingStatus.LIVE
                || !"LIVE".equals(catalog.status())
                || market.bestAsk().remainder(instrument.tickSize()).compareTo(BigDecimal.ZERO) != 0) {
            throw rejected("BTC_USDT_VENUE_MINIMUM_EXCEEDS_PILOT_CAP");
        }

        BigDecimal capital = observations.balanceSnapshot().availableBalance()
                .min(command.configuredPilotMaxNotional())
                .setScale(8, RoundingMode.UNNECESSARY);
        BigDecimal feeRate = observations.feeSchedule().takerFeeRate().abs();
        BigDecimal feeReserve = capital.multiply(feeRate).setScale(8, RoundingMode.CEILING);
        BigDecimal usable = capital.subtract(feeReserve).subtract(SAFETY_BUFFER_USDT);
        if (usable.signum() <= 0) {
            throw rejected("BTC_USDT_VENUE_MINIMUM_EXCEEDS_PILOT_CAP");
        }

        BigDecimal price = market.bestAsk();
        BigDecimal lotCost = price.multiply(instrument.lotSize());
        BigDecimal lotUnits = usable.divide(lotCost, 0, RoundingMode.DOWN);
        BigDecimal canonicalLotUnits = alignLotUnitsForCanonicalNotional(lotCost, lotUnits);
        BigDecimal quantity = canonicalLotUnits.multiply(instrument.lotSize())
                .setScale(8, RoundingMode.UNNECESSARY);
        BigDecimal notional = price.multiply(quantity).setScale(8, RoundingMode.UNNECESSARY);
        BigDecimal actualFeeReserve = notional.multiply(feeRate).setScale(8, RoundingMode.CEILING);
        boolean minimumValueSatisfied = instrument.minimumOrderValue() == null
                || notional.compareTo(instrument.minimumOrderValue()) >= 0;
        boolean exactCatalog = catalog.tickSize().compareTo(instrument.tickSize()) == 0
                && catalog.stepSize().compareTo(instrument.lotSize()) == 0
                && catalog.minQuantity().compareTo(instrument.minimumOrderSize()) == 0;
        boolean sufficient = quantity.signum() > 0
                && quantity.compareTo(instrument.minimumOrderSize()) >= 0
                && quantity.remainder(instrument.lotSize()).compareTo(BigDecimal.ZERO) == 0
                && notional.signum() > 0
                && notional.compareTo(command.configuredPilotMaxNotional()) <= 0
                && notional.compareTo(MinimalLivePilotCommand.HARD_CAP) <= 0
                && notional.add(actualFeeReserve).add(SAFETY_BUFFER_USDT)
                .compareTo(observations.balanceSnapshot().availableBalance()) <= 0
                && minimumValueSatisfied
                && exactCatalog;
        if (!sufficient) {
            throw rejected("BTC_USDT_VENUE_MINIMUM_EXCEEDS_PILOT_CAP");
        }
        return new SafeOrderParameters(price, quantity, notional);
    }

    private static BigDecimal alignLotUnitsForCanonicalNotional(
            BigDecimal lotCost,
            BigDecimal maximumLotUnits
    ) {
        BigDecimal normalizedLotCost = lotCost.stripTrailingZeros();
        int excessScale = Math.max(0, normalizedLotCost.scale() - 8);
        if (excessScale == 0) {
            return maximumLotUnits;
        }
        BigInteger scaleDivisor = BigInteger.TEN.pow(excessScale);
        BigInteger unscaledLotCost = normalizedLotCost.unscaledValue().abs();
        BigInteger requiredLotMultiple = scaleDivisor.divide(unscaledLotCost.gcd(scaleDivisor));
        BigInteger maximumUnits = maximumLotUnits.toBigIntegerExact();
        // 向下对齐lot数量，保证price*quantity可精确写入NUMERIC(38,8)，不得低估notional。
        return new BigDecimal(maximumUnits.divide(requiredLotMultiple).multiply(requiredLotMultiple));
    }

    private record SafeOrderParameters(BigDecimal limitPrice, BigDecimal quantity, BigDecimal notional) {
    }

    private static LiveControlException rejected(String code) {
        return new LiveControlException(code, "minimal live pilot preparation rejected");
    }
}
