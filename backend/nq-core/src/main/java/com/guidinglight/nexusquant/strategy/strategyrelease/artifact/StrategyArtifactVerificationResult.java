package com.guidinglight.nexusquant.strategy.strategyrelease.artifact;

import java.util.Objects;

/**
 * 不可变 artifact/provenance 验证结果。
 *
 * <p>失败结果只携带固定 reason code 与安全相对标识，不携带绝对路径、原始异常或 artifact 内容。
 */
public record StrategyArtifactVerificationResult(
        Status status,
        FindingCode reasonCode,
        String safeRelativeIdentifier,
        String artifactDigest,
        int verifiedFileCount,
        long verifiedSizeBytes
) {
    public StrategyArtifactVerificationResult {
        Objects.requireNonNull(status, "status must not be null");
        if (status == Status.VERIFIED) {
            Objects.requireNonNull(artifactDigest, "verified artifactDigest must not be null");
            if (reasonCode != null || verifiedFileCount <= 0 || verifiedSizeBytes <= 0) {
                throw new IllegalArgumentException("verified result must contain positive verification facts only");
            }
        } else if (reasonCode == null) {
            throw new IllegalArgumentException("rejected result must contain a reason code");
        }
    }

    public static StrategyArtifactVerificationResult verified(
            String artifactDigest,
            int verifiedFileCount,
            long verifiedSizeBytes
    ) {
        return new StrategyArtifactVerificationResult(
                Status.VERIFIED,
                null,
                null,
                artifactDigest,
                verifiedFileCount,
                verifiedSizeBytes
        );
    }

    public static StrategyArtifactVerificationResult rejected(FindingCode reasonCode, String safeRelativeIdentifier) {
        return new StrategyArtifactVerificationResult(
                Status.REJECTED,
                Objects.requireNonNull(reasonCode, "reasonCode must not be null"),
                safeRelativeIdentifier,
                null,
                0,
                0
        );
    }

    public enum Status {
        VERIFIED,
        REJECTED
    }

    public enum FindingCode {
        MANIFEST_REQUIRED,
        MANIFEST_FIELD_MISSING,
        UNSUPPORTED_SCHEMA_VERSION,
        INVALID_IDENTIFIER,
        INVALID_DIGEST,
        INVALID_ARTIFACT_METADATA,
        UNSUPPORTED_MEDIA_TYPE,
        DUPLICATE_ARTIFACT,
        ARTIFACT_DIGEST_MISMATCH,
        SENSITIVE_METADATA,
        PUBLISH_RECORD_NOT_FOUND,
        PUBLISH_IDENTITY_MISMATCH,
        PUBLISH_NOT_SUCCEEDED,
        STRATEGY_VERSION_MISMATCH,
        DATASET_MISMATCH,
        EVALUATION_MISMATCH,
        PROVENANCE_INCOMPLETE,
        PROVENANCE_LOAD_FAILED,
        ARTIFACT_ROOT_NOT_CONFIGURED,
        ARTIFACT_ROOT_INVALID,
        ARTIFACT_LOCATION_UNBOUND,
        ARTIFACT_LOCATION_NOT_FOUND,
        ARTIFACT_LOCATION_UNSAFE,
        ARTIFACT_MANIFEST_NOT_FOUND,
        ARTIFACT_MANIFEST_INVALID,
        ARTIFACT_RELEASE_IDENTITY_MISMATCH,
        TRUSTED_ROOT_INVALID,
        INVALID_RELATIVE_PATH,
        PATH_ESCAPES_TRUSTED_ROOT,
        SYMLINK_OR_REPARSE_NOT_ALLOWED,
        SPECIAL_FILE_NOT_ALLOWED,
        ARTIFACT_NOT_FOUND,
        UNDECLARED_ARTIFACT,
        ARTIFACT_NOT_REGULAR_FILE,
        ARTIFACT_TOO_LARGE,
        ARTIFACT_COUNT_LIMIT_EXCEEDED,
        TOTAL_SIZE_LIMIT_EXCEEDED,
        SIZE_MISMATCH,
        DIGEST_MISMATCH,
        SENSITIVE_ARTIFACT_VALUE,
        ARTIFACT_CHANGED_DURING_VERIFICATION,
        PLATFORM_LINK_GUARANTEE_UNAVAILABLE,
        CANONICALIZATION_FAILED,
        VERIFICATION_IO_FAILED
    }
}
