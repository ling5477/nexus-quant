package com.guidinglight.nexusquant.strategyrelease.preparation;

import java.time.Instant;
import java.util.Objects;

/**
 * PRE-GATEX Strategy Release test-only immutable aggregate。
 *
 * <p>该聚合只表达 release artifact 的准备状态，不启动 Shadow Run，不产生 LIVE 或交易授权。
 */
record StrategyReleaseAggregatePrototype(
        String releaseId,
        String publishId,
        String strategyVersionId,
        String datasetId,
        String evaluationId,
        String manifestSchemaVersion,
        String artifactDigest,
        StrategyReleaseState state,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant verifiedAt,
        Instant publishedAt,
        Instant retiredAt
) {

    StrategyReleaseAggregatePrototype {
        releaseId = requireText(releaseId, "releaseId");
        publishId = requireText(publishId, "publishId");
        strategyVersionId = requireText(strategyVersionId, "strategyVersionId");
        datasetId = requireText(datasetId, "datasetId");
        evaluationId = requireText(evaluationId, "evaluationId");
        manifestSchemaVersion = requireText(manifestSchemaVersion, "manifestSchemaVersion");
        artifactDigest = requireText(artifactDigest, "artifactDigest");
        Objects.requireNonNull(state, "state must not be null");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    StrategyReleaseAggregatePrototype transitionTo(StrategyReleaseState targetState, Instant occurredAt) {
        Objects.requireNonNull(targetState, "targetState must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        return new StrategyReleaseAggregatePrototype(
                releaseId,
                publishId,
                strategyVersionId,
                datasetId,
                evaluationId,
                manifestSchemaVersion,
                artifactDigest,
                targetState,
                version + 1,
                createdAt,
                occurredAt,
                targetState == StrategyReleaseState.VERIFIED ? occurredAt : verifiedAt,
                targetState == StrategyReleaseState.PUBLISHED ? occurredAt : publishedAt,
                targetState == StrategyReleaseState.RETIRED ? occurredAt : retiredAt
        );
    }

    boolean sameImmutableAnchors(StrategyReleaseAggregatePrototype other) {
        return other != null
                && releaseId.equals(other.releaseId)
                && publishId.equals(other.publishId)
                && strategyVersionId.equals(other.strategyVersionId)
                && datasetId.equals(other.datasetId)
                && evaluationId.equals(other.evaluationId)
                && manifestSchemaVersion.equals(other.manifestSchemaVersion)
                && artifactDigest.equals(other.artifactDigest);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
