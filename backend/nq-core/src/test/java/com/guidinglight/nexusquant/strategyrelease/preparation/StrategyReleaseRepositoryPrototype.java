package com.guidinglight.nexusquant.strategyrelease.preparation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * PRE-GATEX test-only in-memory repository。
 *
 * <p>无数据库、文件 IO、Spring 或 static global state。事件只追加，查询结果均为不可变快照。
 */
final class StrategyReleaseRepositoryPrototype {

    private final Map<String, StrategyReleaseAggregatePrototype> releasesById = new LinkedHashMap<>();
    private final Map<String, String> releaseIdsByPublishId = new LinkedHashMap<>();
    private final Map<String, List<StrategyReleaseEventPrototype>> eventsByReleaseId = new LinkedHashMap<>();
    private final Map<String, StrategyReleaseCommandReceiptPrototype> commandResultsByActionId =
            new LinkedHashMap<>();
    private final Map<String, String> successfulPayloadFingerprintsByState = new LinkedHashMap<>();
    private long nextEventSequence = 1L;

    synchronized void create(
            StrategyReleaseAggregatePrototype aggregate,
            StrategyReleaseEventPrototype createdEvent
    ) {
        Objects.requireNonNull(aggregate, "aggregate must not be null");
        Objects.requireNonNull(createdEvent, "createdEvent must not be null");
        if (releasesById.containsKey(aggregate.releaseId())) {
            throw new IllegalStateException("releaseId already exists");
        }
        if (releaseIdsByPublishId.containsKey(aggregate.publishId())) {
            throw new IllegalStateException("publishId already has a release");
        }
        validateEvent(createdEvent, aggregate.releaseId());
        releasesById.put(aggregate.releaseId(), aggregate);
        releaseIdsByPublishId.put(aggregate.publishId(), aggregate.releaseId());
        appendEventInternal(createdEvent);
    }

    synchronized Optional<StrategyReleaseAggregatePrototype> findById(String releaseId) {
        return Optional.ofNullable(releasesById.get(requireText(releaseId, "releaseId")));
    }

    synchronized Optional<StrategyReleaseAggregatePrototype> findByBusinessAnchor(String publishId) {
        String releaseId = releaseIdsByPublishId.get(requireText(publishId, "publishId"));
        return releaseId == null ? Optional.empty() : Optional.of(releasesById.get(releaseId));
    }

    synchronized boolean saveWithExpectedVersion(
            StrategyReleaseAggregatePrototype aggregate,
            long expectedVersion,
            StrategyReleaseEventPrototype event
    ) {
        Objects.requireNonNull(aggregate, "aggregate must not be null");
        Objects.requireNonNull(event, "event must not be null");
        StrategyReleaseAggregatePrototype current = releasesById.get(aggregate.releaseId());
        if (current == null || current.version() != expectedVersion) {
            return false;
        }
        if (!current.sameImmutableAnchors(aggregate)) {
            throw new IllegalArgumentException("immutable release anchors must not change");
        }
        if (aggregate.version() != expectedVersion + 1) {
            throw new IllegalArgumentException("saved aggregate version must increment exactly once");
        }
        validateEvent(event, aggregate.releaseId());
        releasesById.put(aggregate.releaseId(), aggregate);
        appendEventInternal(event);
        return true;
    }

    synchronized void appendAuditEvent(StrategyReleaseEventPrototype event) {
        Objects.requireNonNull(event, "event must not be null");
        if (!releasesById.containsKey(event.releaseId())) {
            throw new IllegalArgumentException("release does not exist");
        }
        validateEvent(event, event.releaseId());
        appendEventInternal(event);
    }

    synchronized List<StrategyReleaseEventPrototype> findEvents(String releaseId) {
        List<StrategyReleaseEventPrototype> events =
                eventsByReleaseId.getOrDefault(requireText(releaseId, "releaseId"), List.of());
        return List.copyOf(events);
    }

    synchronized Optional<StrategyReleaseCommandReceiptPrototype> findCommandResultByActionId(String actionId) {
        return Optional.ofNullable(commandResultsByActionId.get(requireText(actionId, "actionId")));
    }

    synchronized void saveCommandResult(StrategyReleaseCommandReceiptPrototype receipt) {
        Objects.requireNonNull(receipt, "receipt must not be null");
        StrategyReleaseCommandReceiptPrototype existing = commandResultsByActionId.putIfAbsent(
                receipt.actionId(),
                receipt
        );
        if (existing != null && !existing.equals(receipt)) {
            throw new IllegalStateException("actionId is already bound to another command");
        }
    }

    synchronized Optional<String> findSuccessfulPayloadFingerprint(
            String releaseId,
            StrategyReleaseState state
    ) {
        return Optional.ofNullable(successfulPayloadFingerprintsByState.get(stateKey(releaseId, state)));
    }

    synchronized void rememberSuccessfulPayloadFingerprint(
            String releaseId,
            StrategyReleaseState state,
            String payloadFingerprint
    ) {
        String key = stateKey(releaseId, state);
        String existing = successfulPayloadFingerprintsByState.putIfAbsent(
                key,
                requireText(payloadFingerprint, "payloadFingerprint")
        );
        if (existing != null && !existing.equals(payloadFingerprint)) {
            throw new IllegalStateException("completed state is already bound to another payload");
        }
    }

    synchronized int releaseCount() {
        return releasesById.size();
    }

    synchronized String nextEventId() {
        return "release-event-" + nextEventSequence++;
    }

    private void appendEventInternal(StrategyReleaseEventPrototype event) {
        eventsByReleaseId.computeIfAbsent(event.releaseId(), ignored -> new ArrayList<>()).add(event);
    }

    private static void validateEvent(StrategyReleaseEventPrototype event, String expectedReleaseId) {
        if (!expectedReleaseId.equals(event.releaseId())) {
            throw new IllegalArgumentException("event releaseId does not match aggregate");
        }
        if (event.versionBefore() < -1 || event.versionAfter() < 0) {
            throw new IllegalArgumentException("event versions are invalid");
        }
    }

    private static String stateKey(String releaseId, StrategyReleaseState state) {
        return requireText(releaseId, "releaseId")
                + "|"
                + Objects.requireNonNull(state, "state must not be null").name();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}

record StrategyReleaseEventPrototype(
        String eventId,
        String releaseId,
        String actionId,
        String eventType,
        StrategyReleaseState fromStatus,
        StrategyReleaseState toStatus,
        String artifactDigest,
        long versionBefore,
        long versionAfter,
        Instant occurredAt
) {

    StrategyReleaseEventPrototype {
        eventId = requireText(eventId, "eventId");
        releaseId = requireText(releaseId, "releaseId");
        actionId = requireText(actionId, "actionId");
        eventType = requireText(eventType, "eventType");
        Objects.requireNonNull(fromStatus, "fromStatus must not be null");
        Objects.requireNonNull(toStatus, "toStatus must not be null");
        artifactDigest = requireText(artifactDigest, "artifactDigest");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}

record StrategyReleaseCommandReceiptPrototype(
        String actionId,
        String releaseId,
        String requestFingerprint,
        StrategyReleaseCommandResultPrototype result
) {

    StrategyReleaseCommandReceiptPrototype {
        actionId = requireText(actionId, "actionId");
        releaseId = requireText(releaseId, "releaseId");
        requestFingerprint = requireText(requestFingerprint, "requestFingerprint");
        Objects.requireNonNull(result, "result must not be null");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
