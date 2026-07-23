package com.guidinglight.nexusquant.strategyrelease.preparation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * PRE-GATEX Strategy Release test-only application prototype。
 *
 * <p>只消费不可变 artifact verification result；不读取文件、不接数据库、不注册 Spring Bean，
 * 不启动 Shadow Run，也不产生 LIVE 或交易授权。
 */
final class StrategyReleaseServicePrototype {

    static final String SUPPORTED_MANIFEST_SCHEMA = "strategy-release-manifest.v1";

    private static final Pattern SHA_256 = Pattern.compile("^[0-9a-f]{64}$");
    private static final Map<StrategyReleaseState, Set<StrategyReleaseState>> TRANSITIONS = transitions();
    private static final Set<TrustedRootArtifactVerifierPrototype.FindingCode> BLOCKING_FINDINGS =
            EnumSet.allOf(TrustedRootArtifactVerifierPrototype.FindingCode.class);

    private final StrategyReleaseRepositoryPrototype repository;
    private final Clock clock;

    StrategyReleaseServicePrototype(StrategyReleaseRepositoryPrototype repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    StrategyReleaseCommandResultPrototype create(CreateCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String actionId = requireText(command.actionId(), "actionId");
        String releaseId = requireText(command.releaseId(), "releaseId");
        String publishId = requireText(command.publishId(), "publishId");
        String strategyVersionId = requireText(command.strategyVersionId(), "strategyVersionId");
        String datasetId = requireText(command.datasetId(), "datasetId");
        String evaluationId = requireText(command.evaluationId(), "evaluationId");
        String schemaVersion = requireText(command.manifestSchemaVersion(), "manifestSchemaVersion");
        String artifactDigest = requireDigest(command.artifactDigest());
        String fingerprint = fingerprint(
                "CREATE",
                releaseId,
                publishId,
                strategyVersionId,
                datasetId,
                evaluationId,
                schemaVersion,
                artifactDigest,
                Long.toString(command.expectedVersion())
        );

        Optional<StrategyReleaseCommandResultPrototype> replay =
                replayOrConflict(actionId, releaseId, fingerprint);
        if (replay.isPresent()) {
            return replay.get();
        }
        if (command.expectedVersion() != 0) {
            return cacheFailure(actionId, releaseId, fingerprint, "VERSION_CONFLICT", null);
        }

        StrategyReleaseAggregatePrototype requested = new StrategyReleaseAggregatePrototype(
                releaseId,
                publishId,
                strategyVersionId,
                datasetId,
                evaluationId,
                schemaVersion,
                artifactDigest,
                StrategyReleaseState.DRAFT,
                0,
                Instant.now(clock),
                Instant.now(clock),
                null,
                null,
                null
        );
        Optional<StrategyReleaseAggregatePrototype> existingByReleaseId = repository.findById(releaseId);
        Optional<StrategyReleaseAggregatePrototype> existingByPublishId = repository.findByBusinessAnchor(publishId);
        if (existingByReleaseId.isPresent() || existingByPublishId.isPresent()) {
            StrategyReleaseAggregatePrototype existing =
                    existingByPublishId.orElseGet(existingByReleaseId::orElseThrow);
            if (existing.sameImmutableAnchors(requested)) {
                return cacheSuccess(actionId, releaseId, fingerprint, existing);
            }
            return cacheFailure(
                    actionId,
                    releaseId,
                    fingerprint,
                    existing.publishId().equals(publishId) ? "BUSINESS_IDENTITY_CONFLICT" : "RELEASE_ID_CONFLICT",
                    existing
            );
        }

        StrategyReleaseEventPrototype event = event(
                releaseId,
                actionId,
                "RELEASE_CREATED",
                StrategyReleaseState.DRAFT,
                StrategyReleaseState.DRAFT,
                artifactDigest,
                -1,
                0,
                requested.createdAt()
        );
        repository.create(requested, event);
        return cacheSuccess(actionId, releaseId, fingerprint, requested);
    }

    StrategyReleaseCommandResultPrototype markCandidate(StateCommand command) {
        return transition(command, StrategyReleaseState.CANDIDATE, "RELEASE_MARKED_CANDIDATE", null);
    }

    StrategyReleaseCommandResultPrototype verify(VerifyCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        ArtifactVerificationResultPrototype verification =
                Objects.requireNonNull(command.verification(), "verification must not be null");
        return transition(
                new StateCommand(command.actionId(), command.releaseId(), command.expectedVersion()),
                StrategyReleaseState.VERIFIED,
                "ARTIFACT_VERIFIED",
                verification
        );
    }

    StrategyReleaseCommandResultPrototype publish(StateCommand command) {
        return transition(command, StrategyReleaseState.PUBLISHED, "RELEASE_PUBLISHED", null);
    }

    StrategyReleaseCommandResultPrototype reject(StateCommand command) {
        return transition(command, StrategyReleaseState.REJECTED, "RELEASE_REJECTED", null);
    }

    StrategyReleaseCommandResultPrototype retire(StateCommand command) {
        return transition(command, StrategyReleaseState.RETIRED, "RELEASE_RETIRED", null);
    }

    private StrategyReleaseCommandResultPrototype transition(
            StateCommand command,
            StrategyReleaseState targetState,
            String eventType,
            ArtifactVerificationResultPrototype verification
    ) {
        Objects.requireNonNull(command, "command must not be null");
        String actionId = requireText(command.actionId(), "actionId");
        String releaseId = requireText(command.releaseId(), "releaseId");
        String fingerprint = transitionFingerprint(command, targetState, verification);
        Optional<StrategyReleaseCommandResultPrototype> replay =
                replayOrConflict(actionId, releaseId, fingerprint);
        if (replay.isPresent()) {
            return replay.get();
        }

        StrategyReleaseAggregatePrototype current = repository.findById(releaseId).orElse(null);
        if (current == null) {
            return cacheFailure(actionId, releaseId, fingerprint, "RELEASE_NOT_FOUND", null);
        }
        if (current.version() != command.expectedVersion()) {
            return cacheFailure(actionId, releaseId, fingerprint, "VERSION_CONFLICT", current);
        }
        if (current.state() == targetState) {
            String successfulPayloadFingerprint = successfulPayloadFingerprint(targetState, verification);
            Optional<String> completedPayload =
                    repository.findSuccessfulPayloadFingerprint(releaseId, targetState);
            if (completedPayload.isEmpty()
                    || !completedPayload.get().equals(successfulPayloadFingerprint)) {
                return cacheFailure(
                        actionId,
                        releaseId,
                        fingerprint,
                        "STATE_PAYLOAD_CONFLICT",
                        current
                );
            }
            return cacheSuccess(actionId, releaseId, fingerprint, current);
        }

        String verificationFailure = verificationFailure(current, targetState, verification);
        if (verificationFailure != null) {
            appendRejectedAudit(current, actionId, "ARTIFACT_VERIFICATION_REJECTED");
            return cacheFailure(actionId, releaseId, fingerprint, verificationFailure, current);
        }
        if (!TRANSITIONS.getOrDefault(current.state(), Set.of()).contains(targetState)) {
            String reasonCode = illegalTransitionReason(current.state(), targetState);
            appendRejectedAudit(current, actionId, "ILLEGAL_TRANSITION_REJECTED");
            return cacheFailure(actionId, releaseId, fingerprint, reasonCode, current);
        }

        Instant occurredAt = Instant.now(clock);
        StrategyReleaseAggregatePrototype updated = current.transitionTo(targetState, occurredAt);
        StrategyReleaseEventPrototype event = event(
                releaseId,
                actionId,
                eventType,
                current.state(),
                targetState,
                current.artifactDigest(),
                current.version(),
                updated.version(),
                occurredAt
        );
        if (!repository.saveWithExpectedVersion(updated, command.expectedVersion(), event)) {
            return cacheFailure(actionId, releaseId, fingerprint, "VERSION_CONFLICT", current);
        }
        repository.rememberSuccessfulPayloadFingerprint(
                releaseId,
                targetState,
                successfulPayloadFingerprint(targetState, verification)
        );
        return cacheSuccess(actionId, releaseId, fingerprint, updated);
    }

    private Optional<StrategyReleaseCommandResultPrototype> replayOrConflict(
            String actionId,
            String releaseId,
            String requestFingerprint
    ) {
        Optional<StrategyReleaseCommandReceiptPrototype> cached =
                repository.findCommandResultByActionId(actionId);
        if (cached.isEmpty()) {
            return Optional.empty();
        }
        StrategyReleaseCommandReceiptPrototype receipt = cached.get();
        if (receipt.releaseId().equals(releaseId)
                && receipt.requestFingerprint().equals(requestFingerprint)) {
            return Optional.of(receipt.result());
        }
        return Optional.of(StrategyReleaseCommandResultPrototype.failure(
                "IDEMPOTENCY_CONFLICT",
                repository.findById(releaseId).orElse(null)
        ));
    }

    private StrategyReleaseCommandResultPrototype cacheSuccess(
            String actionId,
            String releaseId,
            String fingerprint,
            StrategyReleaseAggregatePrototype aggregate
    ) {
        StrategyReleaseCommandResultPrototype result = StrategyReleaseCommandResultPrototype.success(aggregate);
        repository.saveCommandResult(new StrategyReleaseCommandReceiptPrototype(
                actionId,
                releaseId,
                fingerprint,
                result
        ));
        return result;
    }

    private StrategyReleaseCommandResultPrototype cacheFailure(
            String actionId,
            String releaseId,
            String fingerprint,
            String reasonCode,
            StrategyReleaseAggregatePrototype aggregate
    ) {
        StrategyReleaseCommandResultPrototype result =
                StrategyReleaseCommandResultPrototype.failure(reasonCode, aggregate);
        repository.saveCommandResult(new StrategyReleaseCommandReceiptPrototype(
                actionId,
                releaseId,
                fingerprint,
                result
        ));
        return result;
    }

    private void appendRejectedAudit(
            StrategyReleaseAggregatePrototype current,
            String actionId,
            String eventType
    ) {
        repository.appendAuditEvent(event(
                current.releaseId(),
                actionId,
                eventType,
                current.state(),
                current.state(),
                current.artifactDigest(),
                current.version(),
                current.version(),
                Instant.now(clock)
        ));
    }

    private String verificationFailure(
            StrategyReleaseAggregatePrototype current,
            StrategyReleaseState targetState,
            ArtifactVerificationResultPrototype verification
    ) {
        if (targetState != StrategyReleaseState.VERIFIED) {
            return null;
        }
        if (!SUPPORTED_MANIFEST_SCHEMA.equals(current.manifestSchemaVersion())) {
            return "UNSUPPORTED_MANIFEST_SCHEMA";
        }
        if (verification == null || verification.status() == TrustedRootArtifactVerifierPrototype.Status.UNKNOWN) {
            return "VERIFICATION_UNKNOWN";
        }
        if (verification.status() != TrustedRootArtifactVerifierPrototype.Status.VERIFIED) {
            return "VERIFICATION_REJECTED";
        }
        if (!current.artifactDigest().equals(verification.artifactDigest())) {
            return "ARTIFACT_DIGEST_MISMATCH";
        }
        if (verification.verifiedSizeBytes() <= 0) {
            return "VERIFIED_SIZE_INVALID";
        }
        if (verification.findingCodes().stream().anyMatch(BLOCKING_FINDINGS::contains)) {
            return "BLOCKING_VERIFICATION_FINDING";
        }
        return null;
    }

    private String transitionFingerprint(
            StateCommand command,
            StrategyReleaseState targetState,
            ArtifactVerificationResultPrototype verification
    ) {
        if (verification == null) {
            return fingerprint(
                    targetState.name(),
                    requireText(command.releaseId(), "releaseId"),
                    Long.toString(command.expectedVersion())
            );
        }
        String findings = verification.findingCodes().stream()
                .map(Enum::name)
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return fingerprint(
                targetState.name(),
                requireText(command.releaseId(), "releaseId"),
                Long.toString(command.expectedVersion()),
                verification.status().name(),
                nullToEmpty(verification.artifactDigest()),
                Long.toString(verification.verifiedSizeBytes()),
                findings
        );
    }

    private String successfulPayloadFingerprint(
            StrategyReleaseState targetState,
            ArtifactVerificationResultPrototype verification
    ) {
        if (verification == null) {
            return fingerprint(targetState.name());
        }
        String findings = verification.findingCodes().stream()
                .map(Enum::name)
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return fingerprint(
                targetState.name(),
                verification.status().name(),
                nullToEmpty(verification.artifactDigest()),
                Long.toString(verification.verifiedSizeBytes()),
                findings
        );
    }

    private StrategyReleaseEventPrototype event(
            String releaseId,
            String actionId,
            String eventType,
            StrategyReleaseState fromState,
            StrategyReleaseState toState,
            String artifactDigest,
            long versionBefore,
            long versionAfter,
            Instant occurredAt
    ) {
        return new StrategyReleaseEventPrototype(
                repository.nextEventId(),
                releaseId,
                actionId,
                eventType,
                fromState,
                toState,
                artifactDigest,
                versionBefore,
                versionAfter,
                occurredAt
        );
    }

    private static String illegalTransitionReason(StrategyReleaseState from, StrategyReleaseState to) {
        if (from.terminal()) {
            return "RELEASE_TERMINAL_STATE_LOCKED";
        }
        if (to == StrategyReleaseState.PUBLISHED && from != StrategyReleaseState.VERIFIED) {
            return "RELEASE_NOT_VERIFIED";
        }
        return "RELEASE_ILLEGAL_STATE_TRANSITION";
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String requireDigest(String value) {
        String digest = requireText(value, "artifactDigest");
        if (!SHA_256.matcher(digest).matches()) {
            throw new IllegalArgumentException("artifactDigest must be lowercase SHA-256");
        }
        return digest;
    }

    private static String fingerprint(String... fields) {
        StringBuilder canonical = new StringBuilder();
        for (String field : fields) {
            String value = nullToEmpty(field);
            canonical.append(value.length()).append(':').append(value).append('|');
        }
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonical.toString().getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static Map<StrategyReleaseState, Set<StrategyReleaseState>> transitions() {
        Map<StrategyReleaseState, Set<StrategyReleaseState>> transitions =
                new EnumMap<>(StrategyReleaseState.class);
        transitions.put(
                StrategyReleaseState.DRAFT,
                EnumSet.of(StrategyReleaseState.CANDIDATE, StrategyReleaseState.REJECTED)
        );
        transitions.put(
                StrategyReleaseState.CANDIDATE,
                EnumSet.of(StrategyReleaseState.VERIFIED, StrategyReleaseState.REJECTED)
        );
        transitions.put(
                StrategyReleaseState.VERIFIED,
                EnumSet.of(StrategyReleaseState.PUBLISHED, StrategyReleaseState.REJECTED)
        );
        transitions.put(StrategyReleaseState.PUBLISHED, EnumSet.of(StrategyReleaseState.RETIRED));
        return Map.copyOf(transitions);
    }

    record CreateCommand(
            String actionId,
            String releaseId,
            String publishId,
            String strategyVersionId,
            String datasetId,
            String evaluationId,
            String manifestSchemaVersion,
            String artifactDigest,
            long expectedVersion
    ) {
    }

    record StateCommand(String actionId, String releaseId, long expectedVersion) {
    }

    record VerifyCommand(
            String actionId,
            String releaseId,
            long expectedVersion,
            ArtifactVerificationResultPrototype verification
    ) {
    }
}

record ArtifactVerificationResultPrototype(
        TrustedRootArtifactVerifierPrototype.Status status,
        String artifactDigest,
        long verifiedSizeBytes,
        List<TrustedRootArtifactVerifierPrototype.FindingCode> findingCodes
) {

    ArtifactVerificationResultPrototype {
        Objects.requireNonNull(status, "status must not be null");
        findingCodes = findingCodes == null ? List.of() : List.copyOf(findingCodes);
    }
}

record StrategyReleaseCommandResultPrototype(
        boolean accepted,
        String reasonCode,
        StrategyReleaseAggregatePrototype aggregate,
        boolean diagnosticOnly,
        boolean notTradingAuthorization,
        boolean liveDisabled
) {

    StrategyReleaseCommandResultPrototype {
        reasonCode = Objects.requireNonNull(reasonCode, "reasonCode must not be null");
        if (!diagnosticOnly || !notTradingAuthorization || !liveDisabled) {
            throw new IllegalArgumentException("prototype safety boundary flags must remain true");
        }
    }

    static StrategyReleaseCommandResultPrototype success(StrategyReleaseAggregatePrototype aggregate) {
        return new StrategyReleaseCommandResultPrototype(true, "OK", aggregate, true, true, true);
    }

    static StrategyReleaseCommandResultPrototype failure(
            String reasonCode,
            StrategyReleaseAggregatePrototype aggregate
    ) {
        return new StrategyReleaseCommandResultPrototype(false, reasonCode, aggregate, true, true, true);
    }
}
