package com.guidinglight.nexusquant.strategy.strategyrelease.application;

import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactManifest;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactVerificationResult;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactVerificationResult.FindingCode;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.TrustedRootStrategyArtifactVerifier;
import com.guidinglight.nexusquant.strategy.strategyrelease.domain.StrategyRelease;
import com.guidinglight.nexusquant.strategy.strategyrelease.domain.StrategyReleaseStatus;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

/**
 * Strategy Release production capability 的最小只读编排服务。
 *
 * <p>职责固定为：读取既有 publish provenance、构建 canonical release view、验证 manifest/provenance、
 * 调用 trusted-root artifact verifier，并返回 immutable VERIFIED/REJECTED aggregate。
 * 本服务无事务和写副作用，不创建/推进 Shadow Run，不执行 artifact，不调用外部 API，不访问 credential，
 * 也不产生 LIVE 或交易授权。
 */
@Service
public class StrategyReleaseProductionService {

    private static final Pattern OPAQUE_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$");
    private static final String INVALID_ANCHOR = "<invalid-release-anchor>";

    private final StrategyReleaseProvenanceRepository provenanceRepository;
    private final TrustedRootStrategyArtifactVerifier artifactVerifier;

    public StrategyReleaseProductionService(
            StrategyReleaseProvenanceRepository provenanceRepository,
            TrustedRootStrategyArtifactVerifier artifactVerifier
    ) {
        this.provenanceRepository = Objects.requireNonNull(
                provenanceRepository,
                "provenanceRepository must not be null"
        );
        this.artifactVerifier = Objects.requireNonNull(artifactVerifier, "artifactVerifier must not be null");
    }

    /**
     * 验证一条 publish-anchored Strategy Release。
     *
     * @param command canonical publish anchor、trusted root 与 manifest；只读且可重复执行
     * @return immutable aggregate；同一稳定事实、manifest 与 artifact 内容得到相同业务结果
     * @implNote 不保存结果、不启动 Shadow、不执行 artifact；repository 异常统一 fail-closed 为安全 reason code
     */
    public StrategyRelease verify(VerificationCommand command) {
        if (command == null) {
            StrategyArtifactManifest empty = StrategyArtifactManifest.empty();
            return rejected(
                    INVALID_ANCHOR,
                    StrategyReleaseProvenanceFacts.missing(INVALID_ANCHOR),
                    empty,
                    StrategyArtifactVerificationResult.rejected(FindingCode.MANIFEST_REQUIRED, "<manifest>")
            );
        }

        String requestedAnchor = normalizeAnchor(command.releaseAnchorId());
        StrategyArtifactManifest manifest = command.artifactManifest() == null
                ? StrategyArtifactManifest.empty()
                : command.artifactManifest();

        StrategyReleaseProvenanceFacts facts;
        try {
            facts = provenanceRepository.loadByPublishRecordId(requestedAnchor);
        } catch (RuntimeException exception) {
            return rejected(
                    requestedAnchor,
                    StrategyReleaseProvenanceFacts.missing(requestedAnchor),
                    manifest,
                    StrategyArtifactVerificationResult.rejected(
                            FindingCode.PROVENANCE_LOAD_FAILED,
                            "<publish-record>"
                    )
            );
        }
        if (facts == null) {
            facts = StrategyReleaseProvenanceFacts.missing(requestedAnchor);
        }

        Optional<StrategyArtifactVerificationResult> manifestFailure = artifactVerifier.validateManifest(manifest);
        if (manifestFailure.isPresent()) {
            return rejected(requestedAnchor, facts, manifest, manifestFailure.get());
        }

        StrategyArtifactVerificationResult provenanceFailure = validateProvenance(requestedAnchor, manifest, facts);
        if (provenanceFailure != null) {
            return rejected(requestedAnchor, facts, manifest, provenanceFailure);
        }

        StrategyArtifactVerificationResult verification = artifactVerifier.verify(command.trustedRoot(), manifest);
        String canonicalPublishId = canonicalPublishId(requestedAnchor, facts);
        return new StrategyRelease(
                canonicalPublishId,
                canonicalPublishId,
                facts.publishStrategyVersionId(),
                facts.datasetId(),
                facts.evaluationId(),
                manifest,
                manifest.artifactDigest(),
                verification.status() == StrategyArtifactVerificationResult.Status.VERIFIED
                        ? StrategyReleaseStatus.VERIFIED
                        : StrategyReleaseStatus.REJECTED,
                verification,
                facts.createdAt(),
                facts.publishedAt()
        );
    }

    private StrategyArtifactVerificationResult validateProvenance(
            String requestedAnchor,
            StrategyArtifactManifest manifest,
            StrategyReleaseProvenanceFacts facts
    ) {
        if (!facts.present()) {
            return rejection(FindingCode.PUBLISH_RECORD_NOT_FOUND, "<publish-record>");
        }
        if (!requestedAnchor.equals(facts.publishRecordId())) {
            return rejection(FindingCode.PUBLISH_IDENTITY_MISMATCH, "<publish-record>");
        }
        if (!"SUCCEEDED".equalsIgnoreCase(facts.publishStatus())) {
            return rejection(FindingCode.PUBLISH_NOT_SUCCEEDED, "<publish-record>");
        }
        if (!facts.strategyVersionPresent()
                || !hasOpaqueId(facts.publishStrategyVersionId())
                || !hasOpaqueId(facts.runStrategyVersionId())) {
            return rejection(FindingCode.PROVENANCE_INCOMPLETE, "<strategy-version>");
        }
        if (!facts.publishStrategyVersionId().equals(facts.runStrategyVersionId())
                || !facts.publishStrategyVersionId().equals(manifest.strategyVersionId())) {
            return rejection(FindingCode.STRATEGY_VERSION_MISMATCH, "<strategy-version>");
        }
        if (!facts.datasetPresent() || facts.datasetId() == null) {
            return rejection(FindingCode.PROVENANCE_INCOMPLETE, "<dataset>");
        }
        if (!facts.datasetId().equals(manifest.datasetId())) {
            return rejection(FindingCode.DATASET_MISMATCH, "<dataset>");
        }
        if (!hasOpaqueId(facts.evaluationId())
                || !hasOpaqueId(facts.backtestRunId())
                || !facts.backtestRunId().equals(facts.evaluationBacktestRunId())) {
            return rejection(FindingCode.PROVENANCE_INCOMPLETE, "<evaluation>");
        }
        if (!facts.evaluationId().equals(manifest.evaluationId())
                || !"SUCCEEDED".equalsIgnoreCase(facts.evaluationStatus())) {
            return rejection(FindingCode.EVALUATION_MISMATCH, "<evaluation>");
        }
        return null;
    }

    private StrategyRelease rejected(
            String requestedAnchor,
            StrategyReleaseProvenanceFacts facts,
            StrategyArtifactManifest manifest,
            StrategyArtifactVerificationResult rejection
    ) {
        String canonicalPublishId = canonicalPublishId(requestedAnchor, facts);
        String strategyVersionId = firstText(facts.publishStrategyVersionId(), manifest.strategyVersionId());
        UUID datasetId = facts.datasetId() == null ? manifest.datasetId() : facts.datasetId();
        String evaluationId = firstText(facts.evaluationId(), manifest.evaluationId());
        return new StrategyRelease(
                canonicalPublishId,
                canonicalPublishId,
                strategyVersionId,
                datasetId,
                evaluationId,
                manifest,
                manifest.artifactDigest(),
                StrategyReleaseStatus.REJECTED,
                rejection,
                facts.createdAt(),
                facts.publishedAt()
        );
    }

    private StrategyArtifactVerificationResult rejection(FindingCode code, String identifier) {
        return StrategyArtifactVerificationResult.rejected(code, identifier);
    }

    private String canonicalPublishId(String requestedAnchor, StrategyReleaseProvenanceFacts facts) {
        return hasOpaqueId(facts.publishRecordId()) ? facts.publishRecordId() : requestedAnchor;
    }

    private String normalizeAnchor(String value) {
        return hasOpaqueId(value) ? value.trim() : INVALID_ANCHOR;
    }

    private boolean hasOpaqueId(String value) {
        return value != null && OPAQUE_ID.matcher(value.trim()).matches();
    }

    private String firstText(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        return fallback == null || fallback.isBlank() ? null : fallback.trim();
    }

    /**
     * 只读验证命令；trustedRoot 由受信调用方提供，manifest 不能覆盖 releaseAnchorId。
     */
    public record VerificationCommand(
            String releaseAnchorId,
            Path trustedRoot,
            StrategyArtifactManifest artifactManifest
    ) {
    }
}
