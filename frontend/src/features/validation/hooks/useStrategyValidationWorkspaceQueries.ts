import {useMemo} from 'react';

import {useConsistencyEvidenceOverview} from '@/hooks/useConsistencyEvidenceOverview';
import {useEvaluationArtifactPreviewOverview} from '@/hooks/useEvaluationArtifactPreviewOverview';
import {useIncidentReplayOverview} from '@/hooks/useIncidentReplayOverview';
import {useIncidentReplayReviewOverview} from '@/hooks/useIncidentReplayReviewOverview';
import {
    usePaperShadowConsistencyDrilldown,
    useShadowRunOverview,
} from '@/hooks/useShadowRunQueries';
import {useShadowValidationWorkflowOverview} from '@/hooks/useShadowValidationWorkflowQueries';
import {useStrategyReleaseAdmissionPreview} from '@/hooks/useStrategyReleaseQueries';
import {
    usePaperShadowComparisonQuery,
    useShadowLivePreviewQuery,
    useStrategyEvaluationGateQuery,
    useStrategyValidationOverview,
} from '@/hooks/useStrategyValidationQueries';
import {useValidationOperationsRuntimeEvidenceOverview} from '@/hooks/useValidationOperationsRuntimeEvidenceOverview';
import type {StrategyValidationOverviewResponse, StrategyValidationQuery} from '@/types/strategy-validation';
import type {ShadowRunOverviewResponse} from '@/types/shadow-runs';

function firstText(...values: Array<string | null | undefined>): string | null {
    const matched = values.find((value) => Boolean(value?.trim()));
    return matched?.trim() ?? null;
}

function resolveShadowRunId(
    submittedQuery: StrategyValidationQuery | null,
    strategyOverview?: StrategyValidationOverviewResponse,
    shadowOverview?: ShadowRunOverviewResponse,
): string | null {
    return firstText(
        submittedQuery?.shadowRunId,
        strategyOverview?.latestDecision?.shadowRunId,
        shadowOverview?.latestRun?.shadowRunId,
    );
}

/**
 * 集中持有 validation workspace 既有只读 query 组合，不改变 query key、启用条件或缓存语义。
 */
export function useStrategyValidationWorkspaceQueries(submittedQuery: StrategyValidationQuery | null) {
    const overviewQuery = useStrategyValidationOverview();
    const shadowValidationWorkflowQuery = useShadowValidationWorkflowOverview();
    const consistencyEvidenceQuery = useConsistencyEvidenceOverview();
    const evaluationArtifactPreviewQuery = useEvaluationArtifactPreviewOverview();
    const incidentReplayReviewQuery = useIncidentReplayReviewOverview();
    const runtimeEvidenceQuery = useValidationOperationsRuntimeEvidenceOverview();
    const incidentReplayQuery = useIncidentReplayOverview();
    const shadowOverviewQuery = useShadowRunOverview();
    const evaluationGateQuery = useStrategyEvaluationGateQuery(submittedQuery);
    const paperShadowQuery = usePaperShadowComparisonQuery(submittedQuery);
    const shadowLivePreviewQuery = useShadowLivePreviewQuery(submittedQuery);
    const releaseAdmissionPreviewQuery = useStrategyReleaseAdmissionPreview(
        firstText(submittedQuery?.publishId),
    );
    const selectedShadowRunId = useMemo(
        () => resolveShadowRunId(submittedQuery, overviewQuery.data, shadowOverviewQuery.data),
        [submittedQuery, overviewQuery.data, shadowOverviewQuery.data],
    );
    const consistencyDrilldownQuery = usePaperShadowConsistencyDrilldown(selectedShadowRunId);
    const loading = overviewQuery.isFetching
        || shadowValidationWorkflowQuery.isFetching
        || consistencyEvidenceQuery.isFetching
        || evaluationArtifactPreviewQuery.isFetching
        || incidentReplayReviewQuery.isFetching
        || incidentReplayQuery.isFetching
        || shadowOverviewQuery.isFetching
        || consistencyDrilldownQuery.isFetching
        || evaluationGateQuery.isFetching
        || paperShadowQuery.isFetching
        || shadowLivePreviewQuery.isFetching
        || releaseAdmissionPreviewQuery.isFetching;

    return {
        overviewQuery,
        shadowValidationWorkflowQuery,
        consistencyEvidenceQuery,
        evaluationArtifactPreviewQuery,
        incidentReplayReviewQuery,
        runtimeEvidenceQuery,
        incidentReplayQuery,
        shadowOverviewQuery,
        evaluationGateQuery,
        paperShadowQuery,
        shadowLivePreviewQuery,
        releaseAdmissionPreviewQuery,
        selectedShadowRunId,
        consistencyDrilldownQuery,
        loading,
    };
}
