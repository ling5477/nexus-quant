/** Strategy Release-to-Shadow admission 的只读安全响应。 */
export interface StrategyReleaseAdmissionPreviewResponse {
    publishRecordId: string;
    releaseAnchorId: string;
    strategyVersionId: string | null;
    datasetId: string | null;
    evaluationId: string | null;
    bindingMode: 'LEGACY_UNBOUND' | 'LEGACY_PUBLISH_ONLY' | 'RELEASE_BOUND' | string;
    releaseStatus: 'VERIFIED' | 'REJECTED' | 'UNVERIFIED' | string;
    artifactVerificationStatus: 'VERIFIED' | 'REJECTED' | string;
    validationDecision: string;
    admissionDecision: 'ELIGIBLE' | 'BLOCKED' | string;
    reasonCodes: string[];
    artifactDigest: string | null;
}
