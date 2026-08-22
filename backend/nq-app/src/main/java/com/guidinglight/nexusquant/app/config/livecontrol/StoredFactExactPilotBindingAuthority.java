package com.guidinglight.nexusquant.app.config.livecontrol;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.livecontrol.application.AuthenticatedLiveControlActor;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingAuthority;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingCommand;
import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSessionState;
import com.guidinglight.nexusquant.livecontrol.domain.PilotObservationSet;
import com.guidinglight.nexusquant.livecontrol.domain.PilotPrerequisiteObservation;
import com.guidinglight.nexusquant.livecontrol.domain.PilotScopeBinding;
import com.guidinglight.nexusquant.livecontrol.domain.PilotScopeFreshnessPolicy;
import com.guidinglight.nexusquant.livecontrol.domain.RiskLimitSet;
import com.guidinglight.nexusquant.livecontrol.domain.port.LiveControlRepository;
import com.guidinglight.nexusquant.livecontrol.domain.port.PilotScopeRepository;
import com.guidinglight.nexusquant.marketdata.domain.instrument.InstrumentCatalogItem;
import com.guidinglight.nexusquant.marketdata.domain.instrument.port.InstrumentCatalogReadPort;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.risk.service.KillSwitchStatus;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseAdmissionState;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseAdmissionStateRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 仅从本地 stored facts 与 immutable runtime identity 重建 exact binding authority。
 *
 * <p>本类只读取 credential summary，绝不调用 material/decrypt/provider；任一缺失、漂移或未知状态
 * 都转换为同一个 fail-closed control-plane error。</p>
 */
public final class StoredFactExactPilotBindingAuthority implements ExactPilotBindingAuthority {

    private final LiveControlRepository liveControlRepository;
    private final PilotScopeRepository pilotScopeRepository;
    private final ExchangeAccountRepository accountRepository;
    private final ExchangeAccountCredentialRepository credentialRepository;
    private final StrategyReleaseAdmissionStateRepository admissionRepository;
    private final InstrumentCatalogReadPort instrumentCatalog;
    private final KillSwitchService killSwitchService;
    private final ExactPilotRuntimeIdentity runtimeIdentity;
    private final PilotScopeFreshnessPolicy freshnessPolicy = new PilotScopeFreshnessPolicy();

    public StoredFactExactPilotBindingAuthority(
            LiveControlRepository liveControlRepository,
            PilotScopeRepository pilotScopeRepository,
            ExchangeAccountRepository accountRepository,
            ExchangeAccountCredentialRepository credentialRepository,
            StrategyReleaseAdmissionStateRepository admissionRepository,
            InstrumentCatalogReadPort instrumentCatalog,
            KillSwitchService killSwitchService,
            ExactPilotRuntimeIdentity runtimeIdentity
    ) {
        this.liveControlRepository = Objects.requireNonNull(liveControlRepository);
        this.pilotScopeRepository = Objects.requireNonNull(pilotScopeRepository);
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.credentialRepository = Objects.requireNonNull(credentialRepository);
        this.admissionRepository = Objects.requireNonNull(admissionRepository);
        this.instrumentCatalog = Objects.requireNonNull(instrumentCatalog);
        this.killSwitchService = Objects.requireNonNull(killSwitchService);
        this.runtimeIdentity = Objects.requireNonNull(runtimeIdentity);
    }

    @Override
    public ExactPilotBinding.AuthoritativeFacts resolveForCreation(
            AuthenticatedLiveControlActor actor,
            ExactPilotBindingCommand command,
            Instant decisionAt
    ) {
        Objects.requireNonNull(command, "command must not be null");
        return resolve(
                actor, command.sessionId(), command.pilotScopeId(), command.observationSetId(),
                command.order(), command.pilotWindowStart(), command.pilotWindowEnd(), decisionAt
        );
    }

    @Override
    public ExactPilotBinding.AuthoritativeFacts resolveCurrent(
            AuthenticatedLiveControlActor actor,
            ExactPilotBinding binding,
            Instant decisionAt
    ) {
        Objects.requireNonNull(binding, "binding must not be null");
        return resolve(
                actor, binding.sessionId(), binding.pilotScopeId(), binding.observationSetId(),
                binding.order(), binding.pilotWindowStart(), binding.pilotWindowEnd(), decisionAt
        );
    }

    private ExactPilotBinding.AuthoritativeFacts resolve(
            AuthenticatedLiveControlActor actor,
            UUID sessionId,
            UUID pilotScopeId,
            UUID observationSetId,
            ExactPilotBinding.OrderEnvelope order,
            Instant pilotWindowStart,
            Instant pilotWindowEnd,
            Instant decisionAt
    ) {
        try {
            Objects.requireNonNull(actor, "actor must not be null");
            Objects.requireNonNull(decisionAt, "decisionAt must not be null");
            LiveSession session = requireSession(actor, sessionId);
            PilotScopeBinding scope = requireScope(session, pilotScopeId);
            PilotObservationSet observations = requireObservations(scope, observationSetId);
            RiskLimitSet risk = requireRisk(session);
            requireApprovalAndFreshness(scope, observations, session, decisionAt);
            requireCurrentReferences(session, actor, risk);
            requireExactWindow(session, pilotWindowStart, pilotWindowEnd, decisionAt);
            requireExactOrder(order, observations, risk, session.capitalCap());
            requireKillEngaged();
            return new ExactPilotBinding.AuthoritativeFacts(
                    session.id(), scope.id(), observations.id(), runtimeIdentity.deployment(),
                    new ExactPilotBinding.AccountIdentity(
                            ExactPilotBinding.AccountIdentity.EXCHANGE,
                            ExactPilotBinding.AccountIdentity.ENVIRONMENT,
                            session.ownerId(), session.exchangeAccountId(), session.credentialReference()),
                    order,
                    new ExactPilotBinding.ObservationIdentities(
                            observations.instrumentMetadata().id(), observations.feeSchedule().id(),
                            observations.balanceSnapshot().id(), observations.clockSync().id()),
                    new ExactPilotBinding.RiskPolicyIdentity(
                            risk.id(), risk.version(), risk.canonicalDigest(),
                            ExactPilotBinding.RiskPolicyIdentity.REQUIRED_KILL_SWITCH_STATE),
                    pilotWindowStart, pilotWindowEnd
            );
        } catch (LiveControlException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw denied(exception);
        }
    }

    private LiveSession requireSession(AuthenticatedLiveControlActor actor, UUID sessionId) {
        LiveSession session = liveControlRepository.findSession(sessionId).orElseThrow(
                StoredFactExactPilotBindingAuthority::denied);
        if (session.ownerId() != actor.userId()
                || session.state() != LiveSessionState.APPROVAL_PENDING
                || !session.hasCanonicalApprovalScopeHash()
                || !liveControlRepository.lockAndValidateSessionReferences(session)) {
            throw denied();
        }
        return session;
    }

    private PilotScopeBinding requireScope(LiveSession session, UUID pilotScopeId) {
        PilotScopeBinding scope = pilotScopeRepository.findBySessionId(session.id()).orElseThrow(
                StoredFactExactPilotBindingAuthority::denied);
        if (!scope.id().equals(pilotScopeId) || !scope.hasCanonicalHash(session)) {
            throw denied();
        }
        return scope;
    }

    private PilotObservationSet requireObservations(PilotScopeBinding scope, UUID observationSetId) {
        PilotObservationSet observations = pilotScopeRepository.findObservationSet(scope.id(), observationSetId)
                .orElseThrow(StoredFactExactPilotBindingAuthority::denied);
        PilotObservationSet latest = pilotScopeRepository.findLatestCompleteObservationSet(scope.id())
                .orElseThrow(StoredFactExactPilotBindingAuthority::denied);
        if (!latest.id().equals(observations.id()) || !latest.equals(observations)) {
            throw denied();
        }
        return observations;
    }

    private RiskLimitSet requireRisk(LiveSession session) {
        RiskLimitSet risk = liveControlRepository.findRiskLimitSet(session.riskLimitSetId())
                .orElseThrow(StoredFactExactPilotBindingAuthority::denied);
        if (!risk.canonicalDigest().equals(session.riskLimitSetDigest())) {
            throw denied();
        }
        return risk;
    }

    private void requireApprovalAndFreshness(
            PilotScopeBinding scope,
            PilotObservationSet observations,
            LiveSession session,
            Instant decisionAt
    ) {
        if (pilotScopeRepository.findValidPilotApproval(scope, decisionAt).isEmpty()
                || !freshnessPolicy.evaluate(scope, observations, session.capitalCap(), decisionAt).eligible()) {
            throw denied();
        }
    }

    private void requireCurrentReferences(
            LiveSession session,
            AuthenticatedLiveControlActor actor,
            RiskLimitSet risk
    ) {
        ExchangeAccountSummary account = accountRepository.findByIdForOwner(
                actor.userId(), session.exchangeAccountId()).orElseThrow(
                        StoredFactExactPilotBindingAuthority::denied);
        if (!Objects.equals(account.exchangeAccountId(), session.exchangeAccountId())
                || !Objects.equals(account.ownerUserId(), session.ownerId())
                || !"OKX".equals(account.exchangeCode()) || !"LIVE".equals(account.tradeEnv())
                || !"ACTIVE".equals(account.status())) {
            throw denied();
        }
        ExchangeAccountCredentialSummary credential = credentialRepository.findByCredentialIdForOwner(
                actor.userId(), session.exchangeAccountId(), session.credentialReference()).orElseThrow(
                        StoredFactExactPilotBindingAuthority::denied);
        if (!credentialIsEligible(credential, session)) {
            throw denied();
        }
        StrategyReleaseAdmissionState admission = admissionRepository.loadByPublishRecordId(
                session.strategyReleaseId());
        if (!admission.identityBound()
                || admission.admissionRevision() != session.releaseAdmissionRevision()
                || !session.releaseDigest().equals(admission.releaseArtifactDigest())
                || !risk.canonicalDigest().equals(session.riskLimitSetDigest())) {
            throw denied();
        }
    }

    private static boolean credentialIsEligible(
            ExchangeAccountCredentialSummary credential,
            LiveSession session
    ) {
        return Objects.equals(credential.credentialId(), session.credentialReference())
                && Objects.equals(credential.exchangeAccountId(), session.exchangeAccountId())
                && "OKX_API_V5".equals(credential.credentialType())
                && "ACTIVE".equals(credential.credentialStatus())
                && credential.isActive()
                && "VERIFIED".equals(credential.verificationStatus())
                && "SUCCEEDED".equals(credential.permissionProbeStatus())
                && "TRADE".equals(credential.permissionScope())
                && !credential.withdrawEnabled()
                && "PASSED".equals(credential.ipAllowlistProbeStatus())
                && credential.lastPermissionProbeAt() != null
                && credential.revokedAt() == null
                && credential.rotatedAt() == null;
    }

    private static void requireExactWindow(
            LiveSession session,
            Instant pilotWindowStart,
            Instant pilotWindowEnd,
            Instant decisionAt
    ) {
        if (pilotWindowStart.isBefore(session.executionWindowStart())
                || pilotWindowEnd.isAfter(session.executionWindowEnd())
                || !pilotWindowEnd.isAfter(pilotWindowStart)
                || !decisionAt.isBefore(pilotWindowEnd)) {
            throw denied();
        }
    }

    private void requireExactOrder(
            ExactPilotBinding.OrderEnvelope order,
            PilotObservationSet observations,
            RiskLimitSet risk,
            BigDecimal sessionCapitalCap
    ) {
        List<InstrumentCatalogItem> catalog = instrumentCatalog.findByExchangeAndSymbols(
                ExactPilotBinding.AccountIdentity.EXCHANGE, List.of(order.exchangeInstrumentId()));
        if (catalog.size() != 1) {
            throw denied();
        }
        InstrumentCatalogItem item = catalog.getFirst();
        PilotPrerequisiteObservation.InstrumentItem observed = observations.instrumentMetadata().items().stream()
                .filter(value -> value.symbol().equals(order.exchangeInstrumentId()))
                .findFirst().orElseThrow(StoredFactExactPilotBindingAuthority::denied);
        boolean exactCatalog = item.instrumentId() != null && item.instrumentId() == order.instrumentId()
                && "OKX".equals(item.exchangeCode()) && "SPOT".equals(item.instrumentType())
                && order.exchangeInstrumentId().equals(item.exchangeSymbol())
                && "LIVE".equals(item.status())
                && sameDecimal(item.tickSize(), observed.tickSize())
                && sameDecimal(item.stepSize(), observed.lotSize())
                && sameDecimal(item.minQuantity(), observed.minimumOrderSize());
        boolean exactPrecision = isMultiple(order.price(), observed.tickSize())
                && isMultiple(order.quantity(), observed.lotSize())
                && order.quantity().compareTo(observed.minimumOrderSize()) >= 0;
        boolean withinRisk = order.notional().compareTo(risk.maxOrderNotional()) <= 0
                && order.notional().compareTo(risk.maxSymbolPositionNotional()) <= 0
                && order.notional().compareTo(risk.capitalCap()) <= 0
                && order.notional().compareTo(sessionCapitalCap) <= 0;
        boolean minimumValue = observed.minimumOrderValue() == null
                || order.notional().compareTo(observed.minimumOrderValue()) >= 0;
        if (!exactCatalog || observed.tradingStatus() != PilotPrerequisiteObservation.TradingStatus.LIVE
                || !exactPrecision || !withinRisk || !minimumValue
                || !risk.symbolAllowlist().contains(order.exchangeInstrumentId())) {
            throw denied();
        }
    }

    private void requireKillEngaged() {
        if (killSwitchService.snapshot().status() != KillSwitchStatus.ENGAGED) {
            throw denied();
        }
    }

    private static boolean isMultiple(BigDecimal value, BigDecimal step) {
        return value.remainder(step).compareTo(BigDecimal.ZERO) == 0;
    }

    private static boolean sameDecimal(BigDecimal left, BigDecimal right) {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    private static LiveControlException denied() {
        return new LiveControlException(
                "EXACT_PILOT_BINDING_AUTHORITY_REJECTED",
                "current authoritative facts do not permit exact binding"
        );
    }

    private static LiveControlException denied(Throwable cause) {
        LiveControlException exception = denied();
        exception.initCause(cause);
        return exception;
    }
}
