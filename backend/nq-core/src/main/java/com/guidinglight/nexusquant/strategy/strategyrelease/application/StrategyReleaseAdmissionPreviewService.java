package com.guidinglight.nexusquant.strategy.strategyrelease.application;

import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyValidationDecision;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyValidationOverviewQueryService;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunReleaseBindingMode;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactVerificationResult;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactVerificationResult.FindingCode;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyReleaseManifestFingerprinter;
import com.guidinglight.nexusquant.strategy.strategyrelease.domain.StrategyRelease;
import com.guidinglight.nexusquant.strategy.strategyrelease.domain.StrategyReleaseStatus;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 以 publishRecordId 编排 Strategy Release-to-Shadow admission preview。
 *
 * <p>服务复用 production release verifier、canonical validation evaluator 与 GateX-3 admission service；
 * 只读取本地事实和 artifact，不写库、不创建/启动 Shadow Run、不调用交易/风控/ledger/credential/private API。
 */
@Service
public class StrategyReleaseAdmissionPreviewService {

    private static final Logger log = LoggerFactory.getLogger(StrategyReleaseAdmissionPreviewService.class);

    private final StrategyReleaseProductionService releaseProductionService;
    private final StrategyReleaseAdmissionPreviewFactsRepository factsRepository;
    private final StrategyValidationOverviewQueryService validationQueryService;
    private final ReleaseToShadowAdmissionService admissionService;
    private final StrategyReleaseAdmissionStateRepository admissionStateRepository;
    private final StrategyReleaseManifestFingerprinter manifestFingerprinter;
    private final AdmissionGuardFingerprinter guardFingerprinter;
    private final AdmissionGuardDecisionService guardDecisionService;
    private final boolean guardingEnabled;

    @org.springframework.beans.factory.annotation.Autowired
    public StrategyReleaseAdmissionPreviewService(
            StrategyReleaseProductionService releaseProductionService,
            StrategyReleaseAdmissionPreviewFactsRepository factsRepository,
            StrategyValidationOverviewQueryService validationQueryService,
            ReleaseToShadowAdmissionService admissionService,
            StrategyReleaseAdmissionStateRepository admissionStateRepository,
            StrategyReleaseManifestFingerprinter manifestFingerprinter,
            AdmissionGuardFingerprinter guardFingerprinter,
            AdmissionGuardDecisionService guardDecisionService
    ) {
        this.releaseProductionService = Objects.requireNonNull(releaseProductionService, "releaseProductionService must not be null");
        this.factsRepository = Objects.requireNonNull(factsRepository, "factsRepository must not be null");
        this.validationQueryService = Objects.requireNonNull(validationQueryService, "validationQueryService must not be null");
        this.admissionService = Objects.requireNonNull(admissionService, "admissionService must not be null");
        this.admissionStateRepository = Objects.requireNonNull(
                admissionStateRepository,
                "admissionStateRepository must not be null"
        );
        this.manifestFingerprinter = Objects.requireNonNull(manifestFingerprinter, "manifestFingerprinter must not be null");
        this.guardFingerprinter = Objects.requireNonNull(guardFingerprinter, "guardFingerprinter must not be null");
        this.guardDecisionService = Objects.requireNonNull(guardDecisionService, "guardDecisionService must not be null");
        this.guardingEnabled = true;
    }

    /** 既有纯编排单元测试构造；production Bean 必须使用完整 guard 依赖。 */
    StrategyReleaseAdmissionPreviewService(
            StrategyReleaseProductionService releaseProductionService,
            StrategyReleaseAdmissionPreviewFactsRepository factsRepository,
            StrategyValidationOverviewQueryService validationQueryService,
            ReleaseToShadowAdmissionService admissionService
    ) {
        this.releaseProductionService = Objects.requireNonNull(releaseProductionService);
        this.factsRepository = Objects.requireNonNull(factsRepository);
        this.validationQueryService = Objects.requireNonNull(validationQueryService);
        this.admissionService = Objects.requireNonNull(admissionService);
        this.admissionStateRepository = null;
        this.manifestFingerprinter = null;
        this.guardFingerprinter = null;
        this.guardDecisionService = null;
        this.guardingEnabled = false;
    }

    /**
     * 查询一条 release admission preview。
     *
     * @param publishRecordId 唯一客户端输入；不得携带 path、digest、validation 或 safety truth
     * @param traceId 服务端 trace reference，仅进入内存 creation-plan preview
     * @return publish 不存在时 empty；其他安全失败均返回 200/BLOCKED 所需模型
     */
    public Optional<StrategyReleaseAdmissionPreview> preview(String publishRecordId, String traceId) {
        return evaluate(publishRecordId, traceId).map(AdmissionEvaluation::preview);
    }

    /**
     * 使用 GET preview 与 POST materialization 共用的 server-owned admission orchestration。
     *
     * <p>返回值只携带安全 preview 和纯 admission decision；artifact 路径、manifest、storage key 与
     * 内部 repository facts 不会越过该边界。调用者只能在本次 decision 为 ELIGIBLE 且 plan 非空时写入。
     */
    public Optional<AdmissionEvaluation> evaluate(String publishRecordId, String traceId) {
        StrategyRelease release = releaseProductionService.verify(publishRecordId);
        StrategyArtifactVerificationResult verification = release.verificationResult();
        if (verification.status() == StrategyArtifactVerificationResult.Status.REJECTED
                && verification.reasonCode() == FindingCode.PUBLISH_RECORD_NOT_FOUND) {
            return Optional.empty();
        }

        StrategyReleaseAdmissionState r0 = guardingEnabled ? ensureBoundAndReadR0(release) : null;
        StrategyReleaseAdmissionPreviewFacts facts = loadFactsFailClosed(release.publishRecordId(), traceId);
        StrategyValidationDecision validationDecision = validationQueryService.evaluateDecision(facts.validationFact());
        ReleaseToShadowAdmissionDecision admission = admissionService.admit(new ReleaseToShadowAdmissionRequest(
                release,
                release.releaseAnchorId(),
                release.publishRecordId(),
                release.artifactDigest(),
                release.strategyVersionId(),
                release.datasetId(),
                release.evaluationId(),
                validationDecision,
                facts.windowStart(),
                facts.windowEnd(),
                facts.authorizationBoundary(),
                facts.sideEffectPolicy(),
                traceId
        ));

        AdmissionGuard guard = null;
        if (admission.decision() == ReleaseToShadowAdmissionDecision.Decision.ELIGIBLE && guardingEnabled) {
            if (guardDecisionService.evaluate(facts) != ReleaseToShadowAdmissionDecision.Decision.ELIGIBLE) {
                admission = blockedForIncompleteGuardFacts(admission);
            } else {
                Instant evaluatedAt = Instant.now();
                guard = guardFingerprinter.issue(r0, facts, evaluatedAt);
                StrategyReleaseAdmissionState r1 = admissionStateRepository.loadByPublishRecordId(
                        release.publishRecordId()
                );
                if (!sameGeneration(r0, r1)) {
                    throw new AdmissionStaleException();
                }
            }
        }

        return Optional.of(new AdmissionEvaluation(
                toPreview(release, verification, validationDecision, admission),
                admission,
                guard
        ));
    }

    private StrategyReleaseAdmissionState ensureBoundAndReadR0(StrategyRelease release) {
        if (release.releaseStatus() != StrategyReleaseStatus.VERIFIED
                || release.verificationResult().status() != StrategyArtifactVerificationResult.Status.VERIFIED) {
            return null;
        }
        VerifiedStrategyReleaseIdentity verified = VerifiedStrategyReleaseIdentity.fromVerifiedRelease(
                release,
                manifestFingerprinter
        );
        StrategyReleaseAdmissionState current = admissionStateRepository.loadByPublishRecordId(
                release.publishRecordId()
        );
        if (!current.identityBound()) {
            admissionStateRepository.bindVerifiedReleaseIdentity(verified);
            current = admissionStateRepository.loadByPublishRecordId(release.publishRecordId());
        }
        if (!current.identityBound()) {
            throw new AdmissionGuardUninitializedException();
        }
        if (current.guardSchemaVersion() != AdmissionGuard.SUPPORTED_GUARD_SCHEMA_VERSION
                || !Objects.equals(current.releaseArtifactDigest(), verified.releaseArtifactDigest())
                || !Objects.equals(current.manifestFingerprint(), verified.manifestFingerprint())
                || !Objects.equals(current.manifestSchemaVersion(), verified.manifestSchemaVersion())) {
            throw new AdmissionStaleException();
        }
        return current;
    }

    private boolean sameGeneration(StrategyReleaseAdmissionState r0, StrategyReleaseAdmissionState r1) {
        return r0 != null
                && r1 != null
                && r0.admissionRevision() == r1.admissionRevision()
                && r0.guardSchemaVersion() == r1.guardSchemaVersion()
                && Objects.equals(r0.releaseArtifactDigest(), r1.releaseArtifactDigest())
                && Objects.equals(r0.manifestFingerprint(), r1.manifestFingerprint())
                && Objects.equals(r0.manifestSchemaVersion(), r1.manifestSchemaVersion());
    }

    private ReleaseToShadowAdmissionDecision blockedForIncompleteGuardFacts(
            ReleaseToShadowAdmissionDecision eligible
    ) {
        return new ReleaseToShadowAdmissionDecision(
                ReleaseToShadowAdmissionDecision.Decision.BLOCKED,
                List.of(ReleaseToShadowAdmissionDecision.ReasonCode.ADMISSION_FACTS_INCOMPLETE),
                eligible.releaseAnchorId(),
                eligible.artifactDigest(),
                eligible.strategyVersionId(),
                eligible.datasetId(),
                eligible.evaluationId(),
                eligible.sideEffectPolicy(),
                null,
                false,
                false,
                false,
                false
        );
    }

    private StrategyReleaseAdmissionPreviewFacts loadFactsFailClosed(String publishRecordId, String traceId) {
        try {
            StrategyReleaseAdmissionPreviewFacts facts = factsRepository.loadByPublishRecordId(publishRecordId);
            return facts == null ? StrategyReleaseAdmissionPreviewFacts.missing() : facts;
        } catch (RuntimeException exception) {
            log.warn(
                    "strategy release admission preview facts unavailable, publishRecordId={}, traceId={}",
                    publishRecordId,
                    traceId,
                    exception
            );
            return StrategyReleaseAdmissionPreviewFacts.missing();
        }
    }

    private StrategyReleaseAdmissionPreview toPreview(
            StrategyRelease release,
            StrategyArtifactVerificationResult verification,
            StrategyValidationDecision validationDecision,
            ReleaseToShadowAdmissionDecision admission
    ) {
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        if (verification.reasonCode() != null) {
            reasons.add(verification.reasonCode().name());
        }
        admission.reasonCodes().stream().map(Enum::name).forEach(reasons::add);

        return new StrategyReleaseAdmissionPreview(
                release.publishRecordId(),
                release.releaseAnchorId(),
                release.strategyVersionId(),
                release.datasetId(),
                release.evaluationId(),
                bindingMode(release),
                release.releaseStatus(),
                verification.status(),
                validationDecision,
                admission.decision(),
                List.copyOf(reasons),
                release.artifactDigest()
        );
    }

    private ShadowRunReleaseBindingMode bindingMode(StrategyRelease release) {
        try {
            return ShadowRunReleaseBindingMode.derive(release.publishRecordId(), release.artifactDigest());
        } catch (IllegalArgumentException exception) {
            return ShadowRunReleaseBindingMode.LEGACY_PUBLISH_ONLY;
        }
    }

    /** GET 与 POST 共享的最小 evaluation 结果，不暴露 filesystem 或 raw manifest。 */
    public record AdmissionEvaluation(
            StrategyReleaseAdmissionPreview preview,
            ReleaseToShadowAdmissionDecision admission,
            AdmissionGuard guard
    ) {
        public AdmissionEvaluation(
                StrategyReleaseAdmissionPreview preview,
                ReleaseToShadowAdmissionDecision admission
        ) {
            this(preview, admission, null);
        }

        public AdmissionEvaluation {
            Objects.requireNonNull(preview, "preview must not be null");
            Objects.requireNonNull(admission, "admission must not be null");
        }
    }
}
