package com.guidinglight.nexusquant.livecontrol.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 一次 pilot attempt 的 immutable exact binding；仅表达候选事实，不授予交易权限。
 *
 * <p>binding 经权威事实解析后直接进入 {@link Lifecycle#VERIFIED}。消费只表示该事实合同已被
 * 某次后续 attempt 占用；本类型没有 AUTHORIZED、EXECUTING、FILLED 或 LIVE 状态。</p>
 */
public record ExactPilotBinding(
        UUID id,
        UUID sessionId,
        UUID pilotScopeId,
        UUID observationSetId,
        DeploymentIdentity deployment,
        AccountIdentity account,
        OrderEnvelope order,
        ObservationIdentities observations,
        RiskPolicyIdentity riskPolicy,
        OperatorPilotAuthorityIdentity operatorPilotAuthority,
        Instant pilotWindowStart,
        Instant pilotWindowEnd,
        Correlation correlation,
        Instant bindingCreatedAt,
        Instant bindingExpiresAt,
        String bindingDigest
) {
    public static final String SCHEMA_VERSION = "exact-pilot-binding.v2";
    public static final String OPERATOR_PILOT_SCHEMA_VERSION = "exact-pilot-binding.operator-pilot.v1";
    public static final Duration MAXIMUM_LIFETIME = Duration.ofMinutes(15);
    private static final String ZERO_DIGEST = "0".repeat(64);

    /**
     * v2 source-compatible constructor；既有调用继续表达 STRATEGY risk identity。
     */
    public ExactPilotBinding(
            UUID id,
            UUID sessionId,
            UUID pilotScopeId,
            UUID observationSetId,
            DeploymentIdentity deployment,
            AccountIdentity account,
            OrderEnvelope order,
            ObservationIdentities observations,
            RiskPolicyIdentity riskPolicy,
            Instant pilotWindowStart,
            Instant pilotWindowEnd,
            Correlation correlation,
            Instant bindingCreatedAt,
            Instant bindingExpiresAt,
            String bindingDigest
    ) {
        this(id, sessionId, pilotScopeId, observationSetId, deployment, account, order, observations,
                riskPolicy, null, pilotWindowStart, pilotWindowEnd, correlation, bindingCreatedAt,
                bindingExpiresAt, bindingDigest);
    }

    public ExactPilotBinding {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(pilotScopeId, "pilotScopeId must not be null");
        Objects.requireNonNull(observationSetId, "observationSetId must not be null");
        Objects.requireNonNull(deployment, "deployment must not be null");
        Objects.requireNonNull(account, "account must not be null");
        Objects.requireNonNull(order, "order must not be null");
        Objects.requireNonNull(observations, "observations must not be null");
        require((riskPolicy != null) != (operatorPilotAuthority != null),
                "exactly one strategy or operator pilot authority is required");
        if (operatorPilotAuthority != null) {
            require(operatorPilotAuthority.instrument().equals(order.exchangeInstrumentId())
                            && operatorPilotAuthority.side() == order.side()
                            && operatorPilotAuthority.orderType() == order.orderType()
                            && order.notional().compareTo(operatorPilotAuthority.maxNotional()) <= 0,
                    "order exceeds operator pilot authority");
        }
        Objects.requireNonNull(pilotWindowStart, "pilotWindowStart must not be null");
        Objects.requireNonNull(pilotWindowEnd, "pilotWindowEnd must not be null");
        Objects.requireNonNull(correlation, "correlation must not be null");
        Objects.requireNonNull(bindingCreatedAt, "bindingCreatedAt must not be null");
        Objects.requireNonNull(bindingExpiresAt, "bindingExpiresAt must not be null");
        require(pilotWindowEnd.isAfter(pilotWindowStart), "pilot window must be non-empty");
        require(bindingExpiresAt.isAfter(bindingCreatedAt), "binding expiry must be after creation");
        require(!bindingExpiresAt.isAfter(pilotWindowEnd), "binding expiry exceeds pilot window");
        require(!Duration.between(bindingCreatedAt, bindingExpiresAt).minus(MAXIMUM_LIFETIME).isPositive(),
                "binding lifetime exceeds the hard upper bound");
        requireDigest(bindingDigest, "bindingDigest");
    }

    public static ExactPilotBinding verified(
            UUID id,
            UUID sessionId,
            UUID pilotScopeId,
            UUID observationSetId,
            DeploymentIdentity deployment,
            AccountIdentity account,
            OrderEnvelope order,
            ObservationIdentities observations,
            RiskPolicyIdentity riskPolicy,
            Instant pilotWindowStart,
            Instant pilotWindowEnd,
            Correlation correlation,
            Instant bindingCreatedAt,
            Instant bindingExpiresAt
    ) {
        ExactPilotBinding draft = new ExactPilotBinding(
                id, sessionId, pilotScopeId, observationSetId, deployment, account, order, observations,
                riskPolicy, null, pilotWindowStart, pilotWindowEnd, correlation, bindingCreatedAt,
                bindingExpiresAt, ZERO_DIGEST
        );
        return draft.withDigest(ExactPilotBindingCanonicalEncoder.digest(draft));
    }

    public static ExactPilotBinding verified(
            UUID id,
            UUID sessionId,
            UUID pilotScopeId,
            UUID observationSetId,
            DeploymentIdentity deployment,
            AccountIdentity account,
            OrderEnvelope order,
            ObservationIdentities observations,
            RiskPolicyIdentity riskPolicy,
            OperatorPilotAuthorityIdentity operatorPilotAuthority,
            Instant pilotWindowStart,
            Instant pilotWindowEnd,
            Correlation correlation,
            Instant bindingCreatedAt,
            Instant bindingExpiresAt
    ) {
        ExactPilotBinding draft = new ExactPilotBinding(
                id, sessionId, pilotScopeId, observationSetId, deployment, account, order, observations,
                riskPolicy, operatorPilotAuthority, pilotWindowStart, pilotWindowEnd, correlation,
                bindingCreatedAt, bindingExpiresAt, ZERO_DIGEST);
        return draft.withDigest(ExactPilotBindingCanonicalEncoder.digest(draft));
    }

    public boolean hasCanonicalDigest() {
        return ExactPilotBindingCanonicalEncoder.constantTimeEquals(
                bindingDigest, ExactPilotBindingCanonicalEncoder.digest(this));
    }

    public boolean matchesAuthoritativeFacts(AuthoritativeFacts facts) {
        Objects.requireNonNull(facts, "facts must not be null");
        return sessionId.equals(facts.sessionId())
                && pilotScopeId.equals(facts.pilotScopeId())
                && observationSetId.equals(facts.observationSetId())
                && deployment.equals(facts.deployment())
                && account.equals(facts.account())
                && order.equals(facts.order())
                && observations.equals(facts.observations())
                && Objects.equals(riskPolicy, facts.riskPolicy())
                && Objects.equals(operatorPilotAuthority, facts.operatorPilotAuthority())
                && pilotWindowStart.equals(facts.pilotWindowStart())
                && pilotWindowEnd.equals(facts.pilotWindowEnd());
    }

    private ExactPilotBinding withDigest(String digest) {
        return new ExactPilotBinding(
                id, sessionId, pilotScopeId, observationSetId, deployment, account, order, observations,
                riskPolicy, operatorPilotAuthority, pilotWindowStart, pilotWindowEnd, correlation, bindingCreatedAt,
                bindingExpiresAt, digest
        );
    }

    public enum Lifecycle {
        VERIFIED,
        CONSUMED,
        EXPIRED,
        INVALID
    }

    public enum Side {
        BUY,
        SELL
    }

    public enum OrderType {
        LIMIT
    }

    public record DeploymentIdentity(
            String sourceCommit,
            String releaseId,
            String manifestSha256,
            String serverIdentity,
            String runtimeProfile
    ) {
        public static final String RUNTIME_PROFILE = "gatey-readonly-qualification";

        public DeploymentIdentity {
            requireCommit(sourceCommit, "sourceCommit");
            requireCommit(releaseId, "releaseId");
            require(sourceCommit.equals(releaseId), "sourceCommit and releaseId must identify one deployment");
            requireDigest(manifestSha256, "manifestSha256");
            requireExactText(serverIdentity, 128, "serverIdentity");
            require(RUNTIME_PROFILE.equals(runtimeProfile), "runtimeProfile is unsupported");
        }
    }

    public record AccountIdentity(
            String exchange,
            String environment,
            long ownerId,
            long exchangeAccountId,
            long credentialReferenceId
    ) {
        public static final String EXCHANGE = "OKX";
        public static final String ENVIRONMENT = "LIVE";

        public AccountIdentity {
            require(EXCHANGE.equals(exchange), "exchange must be OKX");
            require(ENVIRONMENT.equals(environment), "environment must be LIVE");
            require(ownerId > 0 && exchangeAccountId > 0 && credentialReferenceId > 0,
                    "account identity references must be positive");
        }
    }

    public record OrderEnvelope(
            long instrumentId,
            String exchangeInstrumentId,
            Side side,
            OrderType orderType,
            BigDecimal price,
            BigDecimal quantity,
            BigDecimal notional
    ) {
        public OrderEnvelope {
            require(instrumentId > 0, "instrumentId must be positive");
            require(exchangeInstrumentId != null
                            && exchangeInstrumentId.matches("[A-Z0-9]{2,20}-USDT"),
                    "exchangeInstrumentId must be one exact OKX Spot instrument");
            Objects.requireNonNull(side, "side must not be null");
            require(orderType == OrderType.LIMIT, "orderType must be LIMIT");
            price = CanonicalDigestSupport.money(price, "price");
            quantity = CanonicalDigestSupport.money(quantity, "quantity");
            notional = CanonicalDigestSupport.money(notional, "notional");
            require(price.signum() > 0 && quantity.signum() > 0 && notional.signum() > 0,
                    "price, quantity and notional must be positive");
            require(price.multiply(quantity).compareTo(notional) == 0,
                    "notional must equal exact price multiplied by quantity");
        }
    }

    public record ObservationIdentities(
            UUID instrumentSnapshotIdentity,
            UUID feeSnapshotIdentity,
            UUID balanceSnapshotIdentity,
            UUID exchangeTimeSnapshotIdentity,
            UUID marketSnapshotIdentity,
            String marketSnapshotDigest
    ) {
        public ObservationIdentities {
            Objects.requireNonNull(instrumentSnapshotIdentity, "instrumentSnapshotIdentity must not be null");
            Objects.requireNonNull(feeSnapshotIdentity, "feeSnapshotIdentity must not be null");
            Objects.requireNonNull(balanceSnapshotIdentity, "balanceSnapshotIdentity must not be null");
            Objects.requireNonNull(exchangeTimeSnapshotIdentity, "exchangeTimeSnapshotIdentity must not be null");
            Objects.requireNonNull(marketSnapshotIdentity, "marketSnapshotIdentity must not be null");
            requireDigest(marketSnapshotDigest, "marketSnapshotDigest");
            require(java.util.Set.of(
                            instrumentSnapshotIdentity, feeSnapshotIdentity,
                            balanceSnapshotIdentity, exchangeTimeSnapshotIdentity,
                            marketSnapshotIdentity).size() == 5,
                    "observation identities must be distinct");
        }
    }

    public record RiskPolicyIdentity(
            UUID riskLimitSetId,
            int riskPolicyVersion,
            String riskPolicyDigest,
            String killSwitchState
    ) {
        public static final String REQUIRED_KILL_SWITCH_STATE = "ENGAGED";

        public RiskPolicyIdentity {
            Objects.requireNonNull(riskLimitSetId, "riskLimitSetId must not be null");
            require(riskPolicyVersion > 0, "riskPolicyVersion must be positive");
            requireDigest(riskPolicyDigest, "riskPolicyDigest");
            require(REQUIRED_KILL_SWITCH_STATE.equals(killSwitchState), "kill switch must be ENGAGED");
        }
    }

    public record OperatorPilotAuthorityIdentity(
            UUID authorityId,
            String authorityDigest,
            String instrument,
            Side side,
            OrderType orderType,
            BigDecimal maxNotional,
            int maxPlaceCount,
            int maxCancelCount,
            boolean transferAllowed,
            boolean withdrawAllowed,
            String killSwitchState
    ) {
        public OperatorPilotAuthorityIdentity {
            Objects.requireNonNull(authorityId, "authorityId must not be null");
            requireDigest(authorityDigest, "authorityDigest");
            require(instrument != null && instrument.matches("[A-Z0-9]{2,20}-USDT"),
                    "operator authority instrument is invalid");
            Objects.requireNonNull(side, "operator authority side must not be null");
            require(orderType == OrderType.LIMIT, "operator authority order type must be LIMIT");
            maxNotional = CanonicalDigestSupport.money(maxNotional, "maxNotional");
            require(maxNotional.signum() > 0 && maxNotional.compareTo(OperatorPilotAuthority.HARD_CAP) <= 0,
                    "operator authority maxNotional is invalid");
            require(maxPlaceCount == 1 && maxCancelCount == 1,
                    "operator authority count limits are invalid");
            require(!transferAllowed && !withdrawAllowed, "operator authority cannot permit funding mutation");
            require(RiskPolicyIdentity.REQUIRED_KILL_SWITCH_STATE.equals(killSwitchState),
                    "kill switch must be ENGAGED");
        }
    }

    public record Correlation(String requestId, String traceId, String idempotencyKey) {
        public Correlation {
            requireExactText(requestId, 128, "requestId");
            requireExactText(traceId, 128, "traceId");
            requireExactText(idempotencyKey, 128, "idempotencyKey");
        }
    }

    public record AuthoritativeFacts(
            UUID sessionId,
            UUID pilotScopeId,
            UUID observationSetId,
            DeploymentIdentity deployment,
            AccountIdentity account,
            OrderEnvelope order,
            ObservationIdentities observations,
            RiskPolicyIdentity riskPolicy,
            OperatorPilotAuthorityIdentity operatorPilotAuthority,
            Instant pilotWindowStart,
            Instant pilotWindowEnd
    ) {
        /**
         * v2 source-compatible constructor。
         */
        public AuthoritativeFacts(
                UUID sessionId,
                UUID pilotScopeId,
                UUID observationSetId,
                DeploymentIdentity deployment,
                AccountIdentity account,
                OrderEnvelope order,
                ObservationIdentities observations,
                RiskPolicyIdentity riskPolicy,
                Instant pilotWindowStart,
                Instant pilotWindowEnd
        ) {
            this(sessionId, pilotScopeId, observationSetId, deployment, account, order, observations,
                    riskPolicy, null, pilotWindowStart, pilotWindowEnd);
        }

        public AuthoritativeFacts {
            Objects.requireNonNull(sessionId, "sessionId must not be null");
            Objects.requireNonNull(pilotScopeId, "pilotScopeId must not be null");
            Objects.requireNonNull(observationSetId, "observationSetId must not be null");
            Objects.requireNonNull(deployment, "deployment must not be null");
            Objects.requireNonNull(account, "account must not be null");
            Objects.requireNonNull(order, "order must not be null");
            Objects.requireNonNull(observations, "observations must not be null");
            require((riskPolicy != null) != (operatorPilotAuthority != null),
                    "exactly one authoritative risk identity is required");
            Objects.requireNonNull(pilotWindowStart, "pilotWindowStart must not be null");
            Objects.requireNonNull(pilotWindowEnd, "pilotWindowEnd must not be null");
            require(pilotWindowEnd.isAfter(pilotWindowStart), "pilot window must be non-empty");
        }
    }

    static void requireDigest(String value, String name) {
        require(value != null && value.matches("[0-9a-f]{64}"), name + " must be lowercase SHA-256");
    }

    static void requireExactText(String value, int maximumLength, String name) {
        require(value != null && !value.isBlank() && value.equals(value.trim())
                        && value.length() <= maximumLength && !isDriftingReference(value),
                name + " is invalid");
    }

    private static boolean isDriftingReference(String value) {
        return "*".equals(value)
                || "latest".equalsIgnoreCase(value)
                || "current".equalsIgnoreCase(value)
                || "head".equalsIgnoreCase(value);
    }

    private static void requireCommit(String value, String name) {
        require(value != null && value.matches("[0-9a-f]{40}"), name + " must be an exact commit");
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
