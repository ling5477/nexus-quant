import {expect, test, type Page, type Route} from 'playwright/test';

const SIDE_EFFECT_POLICY = [
    {code: 'NO_DB_WRITE', status: 'FORBIDDEN', message: 'Preview must not write database state.'},
    {code: 'NO_EXTERNAL_IO', status: 'FORBIDDEN', message: 'Preview must not call external systems.'},
    {code: 'NO_CREDENTIAL_ACCESS', status: 'FORBIDDEN', message: 'Preview must not read credential material.'},
    {code: 'NO_PRIVATE_ENDPOINT', status: 'FORBIDDEN', message: 'Preview must not call private exchange endpoint.'},
    {code: 'NO_ORDER_SUBMISSION', status: 'FORBIDDEN', message: 'Preview must not submit orders.'},
    {code: 'NO_LEDGER_MUTATION', status: 'FORBIDDEN', message: 'Preview must not mutate ledger.'},
    {code: 'NO_ACCOUNT_MUTATION', status: 'FORBIDDEN', message: 'Preview must not mutate account state.'},
];

const STRATEGY_OVERVIEW_FIXTURE = {
    generatedAt: '2026-07-08T10:03:00Z',
    diagnosticOnly: true,
    noSideEffect: true,
    notTradingAuthorization: true,
    liveDisabled: true,
    realProviderImplemented: false,
    privateTradingImplemented: false,
    aiDhRuntimeIntegrated: false,
    totalStrategyVersions: 12,
    evaluatedStrategyVersions: 9,
    approvedForValidation: 3,
    rejectedForValidation: 1,
    needsReview: 4,
    blocked: 1,
    latestDecision: {
        strategyVersionId: 'sv-gateq-5',
        datasetId: '11111111-1111-4111-8111-111111111111',
        evaluationReportId: 'eval-gateq-5',
        publishId: 'pub-gateq-5',
        paperRunId: 'paper-gateq-5',
        shadowRunId: 'shadow-preview-only',
        decision: 'APPROVED',
        decisionReasons: ['Fixture validation evidence is sufficient for review only.'],
        limitations: ['Fixture evidence does not authorize trading.'],
        generatedAt: '2026-07-08T10:03:00Z',
        traceId: 'trace-strategy-validation-smoke',
    },
    blockers: [],
    warnings: [
        {
            code: 'REVIEW_ONLY',
            severity: 'WARNING',
            message: 'APPROVED is validation-layer only.',
            sourceType: 'STRATEGY_VALIDATION',
            sourceId: 'sv-gateq-5',
        },
    ],
    nextSteps: [
        {
            code: 'REVIEW_SHADOW_DRILLDOWN',
            owner: 'operator',
            action: 'Review Paper vs Shadow drilldown before later gates.',
            completionCondition: 'Workbench evidence reviewed without enabling execution.',
            boundaryCritical: true,
        },
    ],
    evidenceAnchors: [
        {
            sourceType: 'EVALUATION_REPORT',
            sourceId: 'eval-gateq-5',
            sourceVersion: 'v1',
            sourceTimestamp: '2026-07-08T10:00:00Z',
            checksum: 'fixture-checksum-eval',
        },
    ],
    traceId: 'trace-strategy-overview-smoke',
};

const SHADOW_OVERVIEW_FIXTURE = {
    generatedAt: '2026-07-08T10:04:00Z',
    evidenceMetadata: {
        source: 'LOCAL_DB_SHADOW_FACTS',
        availability: 'PARTIAL',
        lastCalculatedAt: '2026-07-08T10:04:00Z',
        freshnessStatus: 'UNKNOWN',
        ageSeconds: 0,
        staleAfterSeconds: null,
        staleReason: 'SOURCE_PARTIAL',
        diagnosticOnly: true,
        noSideEffect: true,
        notTradingAuthorization: true,
        liveDisabled: true,
    },
    diagnosticOnly: true,
    noSideEffect: true,
    notTradingAuthorization: true,
    liveDisabled: true,
    realProviderImplemented: false,
    privateTradingImplemented: false,
    aiDhRuntimeIntegrated: false,
    totalRuns: 7,
    runningRuns: 1,
    blockedRuns: 2,
    failedRuns: 1,
    completedRuns: 3,
    staleRuns: 1,
    latestRun: {
        shadowRunId: 'shadow-preview-only',
        strategyVersionId: 'sv-gateq-5',
        datasetId: '11111111-1111-4111-8111-111111111111',
        paperRunId: 'paper-gateq-5',
        status: 'RUNNING',
        authorizationBoundary: 'DIAGNOSTIC_ONLY',
        noOrderSubmission: true,
        noCredentialAccess: true,
        noPrivateEndpoint: true,
        noLedgerMutation: true,
        noAccountMutation: true,
        noExternalPrivateIo: true,
        createdAt: '2026-07-08T10:00:00Z',
        updatedAt: '2026-07-08T10:04:00Z',
        startedAt: '2026-07-08T10:01:00Z',
        completedAt: null,
    },
    latestConsistency: {
        reportId: 'consistency-smoke-report',
        shadowRunId: 'shadow-preview-only',
        paperRunId: 'paper-gateq-5',
        comparisonStatus: 'DIVERGED',
        metricDelta: {turnoverDelta: 0.02},
        divergenceReasons: ['fixture shadow drift'],
        limitations: ['fixture limitation'],
        generatedAt: '2026-07-08T10:02:00Z',
        traceId: 'trace-shadow-overview-smoke',
    },
    divergenceSeverity: 'HIGH',
    blockers: [
        {
            code: 'SHADOW_REVIEW_BLOCKED',
            severity: 'BLOCKER',
            message: 'Shadow drilldown must be reviewed.',
            sourceType: 'SHADOW_RUN',
            sourceId: 'shadow-preview-only',
        },
    ],
    warnings: [],
    nextSteps: [
        {
            code: 'CHECK_DIVERGENCE',
            owner: 'operator',
            action: 'Check divergence reasons in the drilldown panel.',
            expectedEvidence: 'Reviewed drilldown evidence anchors.',
            blocking: true,
        },
    ],
    evidenceAnchors: [
        {
            sourceType: 'SHADOW_RUN',
            sourceId: 'shadow-preview-only',
            sourceVersion: 'v1',
            sourceTimestamp: '2026-07-08T10:04:00Z',
            checksum: 'fixture-checksum-shadow',
        },
    ],
    traceId: 'trace-shadow-run-overview-smoke',
};

const PAPER_SHADOW_DRILLDOWN_FIXTURE = {
    generatedAt: '2026-07-08T10:05:00Z',
    diagnosticOnly: true,
    noSideEffect: true,
    notTradingAuthorization: true,
    liveDisabled: true,
    realProviderImplemented: false,
    privateTradingImplemented: false,
    aiDhRuntimeIntegrated: false,
    shadowRun: {
        shadowRunId: 'shadow-preview-only',
        strategyVersionId: 'sv-gateq-5',
        datasetId: '11111111-1111-4111-8111-111111111111',
        evaluationId: 'eval-gateq-5',
        publishId: 'pub-gateq-5',
        paperRunId: 'paper-gateq-5',
        status: 'RUNNING',
        authorizationBoundary: 'DIAGNOSTIC_ONLY',
        noOrderSubmission: true,
        noCredentialAccess: true,
        noPrivateEndpoint: true,
        noLedgerMutation: true,
        noAccountMutation: true,
        noExternalPrivateIo: true,
        createdAt: '2026-07-08T10:00:00Z',
        updatedAt: '2026-07-08T10:05:00Z',
        startedAt: '2026-07-08T10:01:00Z',
        completedAt: null,
    },
    latestConsistency: {
        reportId: 'consistency-smoke-report',
        shadowRunId: 'shadow-preview-only',
        paperRunId: 'paper-gateq-5',
        comparisonStatus: 'DIVERGED',
        metricDelta: {turnoverDelta: 0.02},
        divergenceReasons: ['fixture shadow drift'],
        limitations: ['fixture limitation'],
        generatedAt: '2026-07-08T10:02:00Z',
        traceId: 'trace-drilldown-consistency-smoke',
    },
    comparisonStatus: 'DIVERGED',
    divergenceSeverity: 'HIGH',
    metricDelta: {turnoverDelta: 0.02},
    divergenceReasons: ['fixture shadow drift'],
    limitations: ['fixture limitation'],
    snapshotSummary: {
        totalSnapshots: 5,
        inputMarketdataSnapshots: 1,
        strategyDecisionSnapshots: 2,
        riskPreflightSnapshots: 1,
        orderIntentPreviewSnapshots: 1,
        latestSnapshotAt: '2026-07-08T10:04:30Z',
        latestSnapshotTypes: ['STRATEGY_DECISION', 'RISK_PREFLIGHT'],
    },
    eventSummary: {
        totalEvents: 4,
        latestEventAt: '2026-07-08T10:04:45Z',
        latestEventType: 'STATE_ADVANCED',
        latestReasonCode: 'FIXTURE_ONLY',
    },
    blockers: [],
    warnings: [
        {
            code: 'DIVERGENCE_REVIEW_REQUIRED',
            severity: 'WARNING',
            message: 'Fixture divergence requires review.',
            sourceType: 'CONSISTENCY_REPORT',
            sourceId: 'consistency-smoke-report',
        },
    ],
    nextSteps: [
        {
            code: 'REVIEW_DRILLDOWN',
            owner: 'operator',
            action: 'Review drilldown before any later gate.',
            expectedEvidence: 'Divergence reasons reviewed.',
            blocking: true,
        },
    ],
    evidenceAnchors: [
        {
            sourceType: 'CONSISTENCY_REPORT',
            sourceId: 'consistency-smoke-report',
            sourceVersion: 'v1',
            sourceTimestamp: '2026-07-08T10:05:00Z',
            checksum: 'fixture-checksum-drilldown',
        },
    ],
    traceId: 'trace-drilldown-smoke',
};

const SHADOW_VALIDATION_WORKFLOW_FIXTURE = {
    generatedAt: '2026-07-08T13:40:00Z',
    evidenceMetadata: {
        source: 'LOCAL_DB_VALIDATION_WORKFLOW',
        availability: 'AVAILABLE',
        lastCalculatedAt: '2026-07-08T13:39:00Z',
        freshnessStatus: 'FRESH',
        ageSeconds: 60,
        staleAfterSeconds: 604800,
        staleReason: null,
        diagnosticOnly: true,
        noSideEffect: true,
        notTradingAuthorization: true,
        liveDisabled: true,
    },
    diagnosticOnly: true,
    noSideEffect: true,
    notTradingAuthorization: true,
    liveDisabled: true,
    realProviderImplemented: false,
    privateTradingImplemented: false,
    aiDhRuntimeIntegrated: false,
    totalOperatorItems: 3,
    intakeCount: 1,
    evidenceReviewCount: 1,
    needsEvidenceCount: 0,
    readyForOperatorReviewCount: 1,
    blockedCount: 0,
    closedRecommendationCount: 1,
    latestOperatorItem: {
        operatorItemId: 'op-shadow-validation-smoke-1',
        sourceType: 'CONSISTENCY_REPORT',
        sourceId: 'consistency-smoke-report',
        strategyVersionId: 'sv-gateq-5',
        datasetId: '11111111-1111-4111-8111-111111111111',
        evaluationReportId: 'eval-gateq-5',
        paperRunId: 'paper-gateq-5',
        shadowRunId: '22222222-2222-4222-8222-222222222222',
        consistencyReportId: '33333333-3333-4333-8333-333333333333',
        incidentEvidenceId: null,
        workflowState: 'READY_FOR_OPERATOR_REVIEW',
        validationDecision: 'VALIDATION_READY',
        severity: 'HIGH',
        evidenceFreshness: 'FRESH',
        blockers: [],
        warnings: [
            {
                code: 'OPERATOR_REVIEW_REQUIRED',
                severity: 'WARNING',
                message: 'Fixture evidence requires operator review only.',
                sourceType: 'CONSISTENCY_REPORT',
                sourceId: 'consistency-smoke-report',
            },
        ],
        nextSteps: [
            {
                code: 'REVIEW_WORKFLOW_EVIDENCE',
                owner: 'operator',
                action: 'Review workflow evidence anchors.',
                completionCondition: 'Operator review notes are recorded in a later approved workflow.',
                boundaryCritical: true,
            },
        ],
        evidenceAnchors: [
            {
                sourceType: 'CONSISTENCY_REPORT',
                sourceId: 'consistency-smoke-report',
                sourceVersion: 'v1',
                sourceTimestamp: '2026-07-08T13:39:00Z',
                traceId: 'trace-shadow-validation-workflow-smoke',
                description: 'Fixture consistency report anchor.',
            },
        ],
        traceId: 'trace-shadow-validation-workflow-smoke',
        generatedAt: '2026-07-08T13:40:00Z',
        diagnosticOnly: true,
        noSideEffect: true,
        notTradingAuthorization: true,
        liveDisabled: true,
        realProviderImplemented: false,
        privateTradingImplemented: false,
        aiDhRuntimeIntegrated: false,
    },
    operatorItems: [
        {
            operatorItemId: 'op-shadow-validation-smoke-1',
            sourceType: 'CONSISTENCY_REPORT',
            sourceId: 'consistency-smoke-report',
            strategyVersionId: 'sv-gateq-5',
            datasetId: '11111111-1111-4111-8111-111111111111',
            evaluationReportId: 'eval-gateq-5',
            paperRunId: 'paper-gateq-5',
            shadowRunId: '22222222-2222-4222-8222-222222222222',
            consistencyReportId: '33333333-3333-4333-8333-333333333333',
            incidentEvidenceId: null,
            workflowState: 'READY_FOR_OPERATOR_REVIEW',
            validationDecision: 'VALIDATION_READY',
            severity: 'HIGH',
            evidenceFreshness: 'FRESH',
            blockers: [],
            warnings: [
                {
                    code: 'OPERATOR_REVIEW_REQUIRED',
                    severity: 'WARNING',
                    message: 'Fixture evidence requires operator review only.',
                    sourceType: 'CONSISTENCY_REPORT',
                    sourceId: 'consistency-smoke-report',
                },
            ],
            nextSteps: [
                {
                    code: 'REVIEW_WORKFLOW_EVIDENCE',
                    owner: 'operator',
                    action: 'Review workflow evidence anchors.',
                    completionCondition: 'Operator review notes are recorded in a later approved workflow.',
                    boundaryCritical: true,
                },
            ],
            evidenceAnchors: [
                {
                    sourceType: 'CONSISTENCY_REPORT',
                    sourceId: 'consistency-smoke-report',
                    sourceVersion: 'v1',
                    sourceTimestamp: '2026-07-08T13:39:00Z',
                    traceId: 'trace-shadow-validation-workflow-smoke',
                    description: 'Fixture consistency report anchor.',
                },
            ],
            traceId: 'trace-shadow-validation-workflow-smoke',
            generatedAt: '2026-07-08T13:40:00Z',
            diagnosticOnly: true,
            noSideEffect: true,
            notTradingAuthorization: true,
            liveDisabled: true,
            realProviderImplemented: false,
            privateTradingImplemented: false,
            aiDhRuntimeIntegrated: false,
        },
    ],
    blockers: [],
    warnings: [
        {
            code: 'VALIDATION_READY_IS_REVIEW_ONLY',
            severity: 'WARNING',
            message: 'Validation ready means review material only.',
            sourceType: 'SHADOW_VALIDATION_WORKFLOW',
            sourceId: 'workflow-overview',
        },
    ],
    nextSteps: [
        {
            code: 'REVIEW_WORKFLOW_EVIDENCE',
            owner: 'operator',
            action: 'Review workflow evidence anchors.',
            completionCondition: 'No execution action is introduced by this view.',
            boundaryCritical: true,
        },
    ],
    evidenceAnchors: [
        {
            sourceType: 'CONSISTENCY_REPORT',
            sourceId: 'consistency-smoke-report',
            sourceVersion: 'v1',
            sourceTimestamp: '2026-07-08T13:39:00Z',
            traceId: 'trace-shadow-validation-workflow-smoke',
            description: 'Fixture consistency report anchor.',
        },
    ],
    traceId: 'trace-shadow-validation-workflow-overview-smoke',
};

const CONSISTENCY_EVIDENCE_LATEST_ITEM = {
    evidenceItemId: 'cse-consistency-smoke-critical',
    shadowRunId: '22222222-2222-4222-8222-222222222222',
    paperRunId: 'paper-gateq-5',
    consistencyReportId: '33333333-3333-4333-8333-333333333333',
    strategyVersionId: 'sv-gateq-5',
    datasetId: '11111111-1111-4111-8111-111111111111',
    comparisonStatus: 'DIVERGED',
    divergenceSeverity: 'CRITICAL',
    evidenceFreshness: 'STALE',
    metricDelta: {
        metricCount: 2,
        comparableMetricCount: 1,
        nonComparableMetricCount: 1,
        topDeltaMetrics: [
            {
                name: 'turnoverDelta',
                delta: 0.02,
                unit: 'ratio',
                comparable: true,
                limitationCodes: ['DIAGNOSTIC_ONLY'],
            },
        ],
        limitationCodes: ['DIAGNOSTIC_ONLY'],
        sensitiveFieldFilteredCount: 0,
        rawMetricDeltaExposed: false,
        profitConclusionInferred: false,
        tradingSignalInferred: false,
    },
    divergenceReasons: ['fixture shadow consistency drift'],
    limitations: ['fixture metric delta is diagnostic summary only'],
    evidenceAnchors: [
        {
            sourceType: 'SHADOW_CONSISTENCY_REPORT',
            sourceId: '33333333-3333-4333-8333-333333333333',
            sourceVersion: 'v1',
            sourceTimestamp: '2026-07-08T14:10:00Z',
            traceId: 'trace-consistency-evidence-smoke',
            description: 'Fixture consistency evidence anchor.',
        },
    ],
    traceId: 'trace-consistency-evidence-smoke',
    generatedAt: '2026-07-08T14:11:00Z',
    diagnosticOnly: true,
    noSideEffect: true,
    notTradingAuthorization: true,
    liveDisabled: true,
    realProviderImplemented: false,
    privateTradingImplemented: false,
    aiDhRuntimeIntegrated: false,
};

const CONSISTENCY_EVIDENCE_OVERVIEW_FIXTURE = {
    generatedAt: '2026-07-08T14:12:00Z',
    evidenceMetadata: {
        source: 'LOCAL_DB_SHADOW_CONSISTENCY_REPORTS',
        availability: 'AVAILABLE',
        lastCalculatedAt: '2026-07-08T14:12:00Z',
        freshnessStatus: 'FRESH',
        ageSeconds: 0,
        staleAfterSeconds: 604800,
        staleReason: null,
        diagnosticOnly: true,
        noSideEffect: true,
        notTradingAuthorization: true,
        liveDisabled: true,
    },
    diagnosticOnly: true,
    noSideEffect: true,
    notTradingAuthorization: true,
    liveDisabled: true,
    realProviderImplemented: false,
    privateTradingImplemented: false,
    aiDhRuntimeIntegrated: false,
    totalEvidenceItems: 3,
    consistentCount: 1,
    divergedCount: 2,
    partialCount: 0,
    notComparableCount: 0,
    failedCount: 0,
    staleEvidenceCount: 1,
    highSeverityCount: 1,
    criticalSeverityCount: 1,
    latestEvidenceItem: CONSISTENCY_EVIDENCE_LATEST_ITEM,
    evidenceItems: [
        CONSISTENCY_EVIDENCE_LATEST_ITEM,
        {
            ...CONSISTENCY_EVIDENCE_LATEST_ITEM,
            evidenceItemId: 'cse-consistency-smoke-high',
            comparisonStatus: 'DIVERGED',
            divergenceSeverity: 'HIGH',
            evidenceFreshness: 'FRESH',
            traceId: 'trace-consistency-evidence-high-smoke',
        },
        {
            ...CONSISTENCY_EVIDENCE_LATEST_ITEM,
            evidenceItemId: 'cse-consistency-smoke-consistent',
            comparisonStatus: 'CONSISTENT',
            divergenceSeverity: 'NONE',
            evidenceFreshness: 'FRESH',
            divergenceReasons: [],
            traceId: 'trace-consistency-evidence-consistent-smoke',
        },
    ],
    severityBuckets: {
        NONE: 1,
        HIGH: 1,
        CRITICAL: 1,
    },
    freshnessSummary: {
        FRESH: 2,
        STALE: 1,
    },
    metricDeltaSummary: {
        metricCount: 2,
        comparableMetricCount: 1,
        nonComparableMetricCount: 1,
        topDeltaMetrics: [
            {
                name: 'turnoverDelta',
                delta: 0.02,
                unit: 'ratio',
                comparable: true,
                limitationCodes: ['DIAGNOSTIC_ONLY'],
            },
        ],
        limitationCodes: ['DIAGNOSTIC_ONLY'],
        sensitiveFieldFilteredCount: 0,
        rawMetricDeltaExposed: false,
        profitConclusionInferred: false,
        tradingSignalInferred: false,
    },
    blockers: [
        {
            code: 'CRITICAL_DIVERGENCE_REVIEW_REQUIRED',
            severity: 'CRITICAL',
            message: 'Critical consistency evidence requires diagnostic review.',
            sourceType: 'CONSISTENCY_EVIDENCE',
            sourceId: 'cse-consistency-smoke-critical',
        },
    ],
    warnings: [
        {
            code: 'DIVERGED_IS_DIAGNOSTIC_ONLY',
            severity: 'HIGH',
            message: 'Diverged evidence is diagnostic priority only.',
            sourceType: 'CONSISTENCY_EVIDENCE',
            sourceId: 'cse-consistency-smoke-critical',
        },
    ],
    nextSteps: [
        {
            code: 'INSPECT_CONSISTENCY_EVIDENCE',
            owner: 'operator',
            action: 'Inspect consistency evidence anchors before later GateT work.',
            completionCondition: 'Diagnostic evidence is inspected without introducing runtime actions.',
            boundaryCritical: true,
        },
    ],
    evidenceAnchors: [
        {
            sourceType: 'SHADOW_CONSISTENCY_REPORT',
            sourceId: '33333333-3333-4333-8333-333333333333',
            sourceVersion: 'v1',
            sourceTimestamp: '2026-07-08T14:10:00Z',
            traceId: 'trace-consistency-evidence-smoke',
            description: 'Fixture consistency evidence anchor.',
        },
    ],
    traceId: 'trace-consistency-evidence-overview-smoke',
};

const INCIDENT_REPLAY_REVIEW_LATEST_ITEM = {
    reviewItemId: 'irr-critical-smoke-review',
    sourceType: 'CONSISTENCY_DIVERGENCE',
    sourceId: '33333333-3333-4333-8333-333333333333',
    incidentEvidenceId: 'incident-evidence-smoke',
    replayRecordId: 'replay-record-smoke',
    shadowRunId: '22222222-2222-4222-8222-222222222222',
    paperRunId: 'paper-gateq-5',
    consistencyReportId: '33333333-3333-4333-8333-333333333333',
    operatorItemId: 'op-shadow-validation-smoke-1',
    reviewState: 'ESCALATED_RECOMMENDATION',
    reviewDecision: 'ESCALATE_RECOMMENDED',
    severity: 'CRITICAL',
    evidenceFreshness: 'STALE',
    summary: 'Critical review evidence requires manual escalation review only.',
    limitations: ['Fixture review item is diagnostic only.'],
    blockers: [
        {
            code: 'CRITICAL_REVIEW_REQUIRED',
            severity: 'CRITICAL',
            message: 'Critical review item requires operator review.',
            sourceType: 'INCIDENT_REPLAY_REVIEW',
            sourceId: 'irr-critical-smoke-review',
        },
    ],
    warnings: [
        {
            code: 'ESCALATE_IS_RECOMMENDATION_ONLY',
            severity: 'HIGH',
            message: 'Escalate recommended is not system escalation.',
            sourceType: 'INCIDENT_REPLAY_REVIEW',
            sourceId: 'irr-critical-smoke-review',
        },
    ],
    nextSteps: [
        {
            code: 'REVIEW_REPLAY_ANCHORS',
            owner: 'operator',
            action: 'Review incident replay evidence anchors before later GateT work.',
            completionCondition: 'Manual review notes are captured in a later approved workflow.',
            boundaryCritical: true,
        },
    ],
    evidenceAnchors: [
        {
            sourceType: 'SHADOW_CONSISTENCY_REPORT',
            sourceId: '33333333-3333-4333-8333-333333333333',
            sourceVersion: 'v1',
            sourceTimestamp: '2026-07-09T01:50:00Z',
            traceId: 'trace-incident-replay-review-smoke',
            description: 'Fixture review evidence anchor.',
        },
    ],
    traceId: 'trace-incident-replay-review-smoke',
    generatedAt: '2026-07-09T01:51:00Z',
    diagnosticOnly: true,
    noSideEffect: true,
    notTradingAuthorization: true,
    liveDisabled: true,
    realProviderImplemented: false,
    privateTradingImplemented: false,
    aiDhRuntimeIntegrated: false,
};

const INCIDENT_REPLAY_REVIEW_OVERVIEW_FIXTURE = {
    generatedAt: '2026-07-09T01:52:00Z',
    evidenceMetadata: {
        source: 'LOCAL_DB_INCIDENT_REPLAY_REVIEW',
        availability: 'AVAILABLE',
        lastCalculatedAt: '2026-07-09T01:51:00Z',
        freshnessStatus: 'FRESH',
        ageSeconds: 60,
        staleAfterSeconds: 604800,
        staleReason: null,
        diagnosticOnly: true,
        noSideEffect: true,
        notTradingAuthorization: true,
        liveDisabled: true,
    },
    diagnosticOnly: true,
    noSideEffect: true,
    notTradingAuthorization: true,
    liveDisabled: true,
    realProviderImplemented: false,
    privateTradingImplemented: false,
    aiDhRuntimeIntegrated: false,
    totalReviewItems: 7,
    intakeCount: 1,
    evidenceReviewCount: 1,
    needsOperatorReviewCount: 1,
    acknowledgedRecommendationCount: 1,
    escalatedRecommendationCount: 1,
    closedRecommendationCount: 1,
    blockedCount: 1,
    latestReviewItem: INCIDENT_REPLAY_REVIEW_LATEST_ITEM,
    reviewItems: [
        INCIDENT_REPLAY_REVIEW_LATEST_ITEM,
        {
            ...INCIDENT_REPLAY_REVIEW_LATEST_ITEM,
            reviewItemId: 'irr-ack-smoke-review',
            reviewState: 'ACKNOWLEDGED_RECOMMENDATION',
            reviewDecision: 'ACKNOWLEDGE_RECOMMENDED',
            severity: 'HIGH',
            evidenceFreshness: 'FRESH',
            traceId: 'trace-incident-replay-review-ack-smoke',
            blockers: [],
            warnings: [
                {
                    code: 'ACKNOWLEDGE_IS_RECOMMENDATION_ONLY',
                    severity: 'HIGH',
                    message: 'Acknowledge recommended means manual confirmation only.',
                    sourceType: 'INCIDENT_REPLAY_REVIEW',
                    sourceId: 'irr-ack-smoke-review',
                },
            ],
        },
        {
            ...INCIDENT_REPLAY_REVIEW_LATEST_ITEM,
            reviewItemId: 'irr-close-smoke-review',
            reviewState: 'CLOSED_RECOMMENDATION',
            reviewDecision: 'CLOSEOUT_RECOMMENDED',
            severity: 'INFO',
            evidenceFreshness: 'FRESH',
            traceId: 'trace-incident-replay-review-close-smoke',
            blockers: [],
            warnings: [],
            limitations: ['Closeout recommendation is not a real incident closure.'],
        },
    ],
    severityBuckets: {
        INFO: 1,
        HIGH: 1,
        CRITICAL: 1,
    },
    freshnessSummary: {
        FRESH: 2,
        STALE: 1,
    },
    blockers: [
        {
            code: 'CRITICAL_REVIEW_REQUIRED',
            severity: 'CRITICAL',
            message: 'Critical review item requires manual diagnostic review.',
            sourceType: 'INCIDENT_REPLAY_REVIEW',
            sourceId: 'irr-critical-smoke-review',
        },
    ],
    warnings: [
        {
            code: 'ACKNOWLEDGE_IS_RECOMMENDATION_ONLY',
            severity: 'HIGH',
            message: 'Acknowledge recommendation does not confirm closure.',
            sourceType: 'INCIDENT_REPLAY_REVIEW',
            sourceId: 'irr-ack-smoke-review',
        },
    ],
    nextSteps: [
        {
            code: 'REVIEW_INCIDENT_REPLAY_WORKFLOW',
            owner: 'operator',
            action: 'Review incident replay review items without adding write side actions.',
            completionCondition: 'Diagnostic review notes are handled in a later approved workflow.',
            boundaryCritical: true,
        },
    ],
    evidenceAnchors: [
        {
            sourceType: 'INCIDENT_REPLAY_REVIEW',
            sourceId: 'irr-critical-smoke-review',
            sourceVersion: 'v1',
            sourceTimestamp: '2026-07-09T01:51:00Z',
            traceId: 'trace-incident-replay-review-smoke',
            description: 'Fixture review overview anchor.',
        },
    ],
    traceId: 'trace-incident-replay-review-overview-smoke',
};

const EVALUATION_ARTIFACT_PREVIEW_OVERVIEW_FIXTURE = {
    generatedAt: '2026-07-09T06:40:00Z',
    evidenceMetadata: {
        source: 'LOCAL_NO_FILE_EVALUATION_ARTIFACT_PREVIEW',
        availability: 'UNAVAILABLE',
        lastCalculatedAt: null,
        freshnessStatus: 'UNKNOWN',
        ageSeconds: null,
        staleAfterSeconds: null,
        staleReason: 'LAST_CALCULATED_AT_MISSING',
        diagnosticOnly: true,
        noSideEffect: true,
        notTradingAuthorization: true,
        liveDisabled: true,
    },
    diagnosticOnly: true,
    noSideEffect: true,
    notTradingAuthorization: true,
    liveDisabled: true,
    realProviderImplemented: false,
    privateTradingImplemented: false,
    aiDhRuntimeIntegrated: false,
    pythonMlReady: false,
    pythonLiveExecutionReady: false,
    totalArtifactPreviews: 0,
    validArtifactCount: 0,
    invalidArtifactCount: 0,
    staleArtifactCount: 0,
    checksumFailedCount: 0,
    latestArtifactPreview: null,
    artifactPreviews: [],
    schemaVersionSummary: {
        'python-evaluation-artifact.v1': 0,
        NO_ARTIFACT_SOURCE_CONFIGURED: 1,
    },
    checksumSummary: {
        VALID: 0,
        INVALID: 0,
        MISSING: 0,
        NOT_CHECKED: 1,
        UNKNOWN: 0,
    },
    metricSummaryCoverage: {
        PRESENT: 0,
        INCOMPLETE: 0,
        FAKE_FIXTURE_ONLY: 0,
        MISSING: 0,
        UNKNOWN: 1,
    },
    blockers: [
        {
            code: 'NOT_TRADING_AUTHORIZATION',
            severity: 'CRITICAL',
            message: 'Python artifact preview is not trading authorization.',
            sourceType: 'SYSTEM_BOUNDARY',
            sourceId: null,
        },
    ],
    warnings: [
        {
            code: 'NO_ARTIFACT_SOURCE_CONFIGURED',
            severity: 'WARNING',
            message: 'No artifact file, manifest or runtime source is configured for GateT-4 No-file baseline.',
            sourceType: 'NO_FILE_BASELINE',
            sourceId: null,
        },
        {
            code: 'FAKE_FIXTURE_ONLY_NOT_REAL_PERFORMANCE',
            severity: 'WARNING',
            message: 'FAKE_FIXTURE_ONLY metrics must stay fixture-only and are not real strategy performance.',
            sourceType: 'METRIC_SUMMARY',
            sourceId: null,
        },
    ],
    nextSteps: [
        {
            code: 'KEEP_NO_FILE_BASELINE',
            owner: 'backend',
            action: 'Keep this overview GET-only and No-file baseline until a separate source review is approved.',
            completionCondition: 'No artifact file, manifest, path query, upload, request body, Python subprocess, network or DB import is added.',
            boundaryCritical: true,
        },
    ],
    evidenceAnchors: [
        {
            sourceType: 'EVALUATION_ARTIFACT_CONTRACT',
            sourceId: 'python-evaluation-artifact.v1',
            sourceVersion: 'python-evaluation-artifact.v1',
            sourceTimestamp: '2026-07-09T06:40:00Z',
            traceId: 'trace-evaluation-artifact-preview-smoke',
            description: 'Offline Python EvaluationArtifact contract; no artifact file is read by this endpoint.',
        },
    ],
    traceId: 'trace-evaluation-artifact-preview-overview-smoke',
};

const INCIDENT_REPLAY_OVERVIEW_FIXTURE = {
    generatedAt: '2026-07-08T13:41:00Z',
    diagnosticOnly: true,
    noSideEffect: true,
    notTradingAuthorization: true,
    liveDisabled: true,
    realProviderImplemented: false,
    privateTradingImplemented: false,
    aiDhRuntimeIntegrated: false,
    totalEvidenceItems: 1,
    shadowEventCount: 1,
    consistencyDivergenceCount: 1,
    paperAlertCount: 0,
    recoveryEventCount: 0,
    replayEventCount: 0,
    latestEvidence: [
        {
            evidenceType: 'CONSISTENCY_DIVERGENCE',
            sourceId: 'consistency-smoke-report',
            sourceStatus: 'AVAILABLE',
            summary: 'Fixture divergence evidence for display only.',
            occurredAt: '2026-07-08T13:40:00Z',
            traceId: 'trace-incident-replay-smoke',
        },
    ],
    incidentSeverity: 'WARNING',
    blockers: [],
    warnings: [
        {
            code: 'REPLAY_REVIEW_ONLY',
            severity: 'WARNING',
            message: 'Incident replay fixture is diagnostic only.',
            sourceType: 'INCIDENT_REPLAY',
            sourceId: 'consistency-smoke-report',
        },
    ],
    nextSteps: [
        {
            code: 'KEEP_REPLAY_DIAGNOSTIC',
            owner: 'operator',
            action: 'Review replay evidence without starting runtime actions.',
            completionCondition: 'Evidence reviewed in a later approved workflow.',
            boundaryCritical: true,
        },
    ],
    evidenceAnchors: [
        {
            sourceType: 'CONSISTENCY_REPORT',
            sourceId: 'consistency-smoke-report',
            sourceVersion: 'v1',
            sourceTimestamp: '2026-07-08T13:40:00Z',
            checksum: 'fixture-checksum-incident',
        },
    ],
    traceId: 'trace-incident-replay-overview-smoke',
};

const EVALUATION_GATE_FIXTURE = {
    scope: {
        strategyId: 'strategy-gateq',
        strategyVersionId: 'sv-gateq-5',
        datasetId: '11111111-1111-4111-8111-111111111111',
        evaluationId: 'eval-gateq-5',
        publishId: 'pub-gateq-5',
        paperRunId: 'paper-gateq-5',
    },
    strategyId: 'strategy-gateq',
    strategyVersionId: 'sv-gateq-5',
    datasetId: '11111111-1111-4111-8111-111111111111',
    evaluationId: 'eval-gateq-5',
    publishId: 'pub-gateq-5',
    paperRunId: 'paper-gateq-5',
    gateStatus: 'READY_FOR_SHADOW_REVIEW',
    gateDecision: 'REVIEW_ONLY',
    evaluationStatus: 'SUCCEEDED',
    datasetQualityStatus: 'SATISFIED',
    paperEvidenceStatus: 'SATISFIED',
    publishTraceStatus: 'SATISFIED',
    requiredEvidence: [
        {code: 'STRATEGY_VERSION', status: 'SATISFIED', message: 'Strategy version fact is present.'},
        {code: 'DATASET', status: 'SATISFIED', message: 'Dataset quality facts are present.'},
        {code: 'EVALUATION', status: 'SATISFIED', message: 'Evaluation report is present.'},
    ],
    missingEvidence: [],
    blockers: [],
    warnings: [
        {code: 'READONLY_BOUNDARY', severity: 'WARNING', message: 'Evaluation gate is review evidence only.'},
    ],
    nextSteps: ['Review Paper vs Shadow comparison evidence before any later gate.'],
    generatedAt: '2026-07-05T10:00:00Z',
};

const PAPER_SHADOW_FIXTURE = {
    scope: {
        strategyId: 'strategy-gateq',
        strategyVersionId: 'sv-gateq-5',
        datasetId: '11111111-1111-4111-8111-111111111111',
        evaluationId: 'eval-gateq-5',
        publishId: 'pub-gateq-5',
        paperRunId: 'paper-gateq-5',
        shadowRunId: 'shadow-preview-only',
    },
    strategyId: 'strategy-gateq',
    strategyVersionId: 'sv-gateq-5',
    datasetId: '11111111-1111-4111-8111-111111111111',
    evaluationId: 'eval-gateq-5',
    publishId: 'pub-gateq-5',
    paperRunId: 'paper-gateq-5',
    shadowRunId: 'shadow-preview-only',
    paperRunStatus: 'SUCCEEDED',
    shadowRunStatus: 'NOT_IMPLEMENTED',
    comparisonStatus: 'READY_FOR_COMPARISON',
    evaluationGateStatus: 'READY_FOR_SHADOW_REVIEW',
    paperEvidenceStatus: 'SATISFIED',
    shadowEvidenceStatus: 'NOT_IMPLEMENTED',
    dataQualityStatus: 'SATISFIED',
    comparable: true,
    requiredEvidence: [
        {code: 'PAPER_EVIDENCE', status: 'SATISFIED', message: 'Paper run evidence is available.'},
        {code: 'SHADOW_FACT_SOURCE', status: 'NOT_IMPLEMENTED', message: 'Shadow fact source is not implemented.'},
    ],
    missingEvidence: [
        {code: 'SHADOW_RUN_FACTS', status: 'NOT_IMPLEMENTED', message: 'Shadow run facts are not available.'},
    ],
    blockers: [],
    warnings: [
        {code: 'SHADOW_NOT_IMPLEMENTED', severity: 'WARNING', message: 'Shadow runner remains not implemented.'},
    ],
    nextSteps: ['Keep comparison read-only until a later approved Shadow fact source exists.'],
    generatedAt: '2026-07-05T10:01:00Z',
};

const SHADOW_PREVIEW_FIXTURE = {
    scope: {
        strategyId: 'strategy-gateq',
        strategyVersionId: 'sv-gateq-5',
        datasetId: '11111111-1111-4111-8111-111111111111',
        evaluationId: 'eval-gateq-5',
        publishId: 'pub-gateq-5',
        paperRunId: 'paper-gateq-5',
        shadowRunId: 'shadow-preview-only',
    },
    strategyId: 'strategy-gateq',
    strategyVersionId: 'sv-gateq-5',
    datasetId: '11111111-1111-4111-8111-111111111111',
    evaluationId: 'eval-gateq-5',
    publishId: 'pub-gateq-5',
    paperRunId: 'paper-gateq-5',
    shadowRunId: 'shadow-preview-only',
    runnerStatus: 'SKELETON_AVAILABLE',
    previewStatus: 'PREVIEW_BLOCKED_SHADOW_FACTS_NOT_AVAILABLE',
    evaluationGateStatus: 'READY_FOR_SHADOW_REVIEW',
    paperShadowComparisonStatus: 'READY_FOR_COMPARISON',
    sideEffectPolicy: SIDE_EFFECT_POLICY,
    inputFactStatus: 'PARTIAL',
    traceStatus: 'PARTIAL',
    orderIntentPreviewStatus: 'NOT_EXECUTED',
    riskPreflightPreviewStatus: 'NOT_EXECUTED',
    requiredEvidence: [
        {code: 'TRACE_CHAIN', status: 'SATISFIED', message: 'Trace chain is visible.'},
        {code: 'SHADOW_FACTS', status: 'NOT_AVAILABLE', message: 'Shadow facts are not available.'},
    ],
    missingEvidence: [
        {
            code: 'SHADOW_FACTS',
            status: 'NOT_AVAILABLE',
            message: 'Shadow run facts are required before preview review.'
        },
    ],
    blockers: [
        {
            code: 'PREVIEW_BLOCKED_SHADOW_FACTS_NOT_AVAILABLE',
            severity: 'BLOCKER',
            message: 'Shadow facts are not available.'
        },
    ],
    warnings: [
        {code: 'NO_SIDE_EFFECT_ONLY', severity: 'WARNING', message: 'Preview cannot execute strategy logic.'},
    ],
    nextSteps: ['Provide approved Shadow read-only facts in a later gate before preview review.'],
    generatedAt: '2026-07-05T10:02:00Z',
};

const UNKNOWN_GATE_FIXTURE = {
    ...EVALUATION_GATE_FIXTURE,
    gateStatus: 'UNKNOWN',
    gateDecision: 'NOT_AVAILABLE',
    evaluationStatus: 'UNKNOWN',
    datasetQualityStatus: 'UNKNOWN',
    requiredEvidence: [
        {code: 'STRATEGY_VERSION', status: 'UNKNOWN', message: 'Strategy version fact is unknown.'},
    ],
    missingEvidence: [
        {code: 'DATASET', status: 'NOT_AVAILABLE', message: 'Dataset is not available.'},
    ],
    blockers: [
        {code: 'DATASET_MISSING', severity: 'BLOCKER', message: 'Dataset fact is missing.'},
    ],
    warnings: [],
    nextSteps: ['Resolve missing dataset facts before review.'],
};

async function seedAuthAndGateQStubs(
    page: Page,
    overrides: {
        evaluationGate?: Record<string, unknown>;
        paperShadow?: Record<string, unknown>;
        preview?: Record<string, unknown>;
        consistencyEvidence?: Record<string, unknown>;
        incidentReplayReview?: Record<string, unknown>;
    } = {},
): Promise<string[]> {
    const requests: string[] = [];

    // Why: GateQ-5 smoke 只验证前端只读展示，不启动后端、不外联、不读取真实 credential。
    await page.addInitScript(() => {
        window.localStorage.setItem('nexus-quant.console.auth', JSON.stringify({
            accessToken: 'strategy-validation-smoke-session',
            tokenType: 'Bearer',
            expiresAt: '2999-01-01T00:00:00Z',
            username: 'e2e-operator',
            roles: ['ADMIN'],
        }));
    });

    page.on('request', (request) => requests.push(request.url()));

    await page.route(/^https?:\/\/[^/]+\/api\//, (route: Route) => route.fulfill({status: 200, json: []}));

    await page.route('**/api/auth/me', (route: Route) => route.fulfill({
        status: 200,
        json: {
            userId: 1,
            username: 'e2e-operator',
            roles: ['ADMIN'],
            authenticated: true,
            defaultExchangeAccountId: 101,
            defaultExchangeCode: 'BINANCE',
            defaultTradeEnv: 'SIM',
            defaultAccountAlias: 'strategy-validation-smoke',
        },
    }));

    await page.route('**/api/exchange-accounts', (route: Route) => route.fulfill({
        status: 200,
        json: [{
            exchangeAccountId: 101,
            legacyAccountId: null,
            exchangeCode: 'BINANCE',
            tradeEnv: 'SIM',
            accountAlias: 'strategy-validation-smoke',
            externalAccountRef: null,
            isDefault: true,
            status: 'ACTIVE',
        }],
    }));

    await page.route('**/api/strategy-validation/overview', (route: Route) => route.fulfill({
        status: 200,
        json: STRATEGY_OVERVIEW_FIXTURE,
    }));

    await page.route('**/api/shadow-runs/overview', (route: Route) => route.fulfill({
        status: 200,
        json: SHADOW_OVERVIEW_FIXTURE,
    }));

    await page.route('**/api/paper-shadow/consistency/drilldown**', (route: Route) => route.fulfill({
        status: 200,
        json: PAPER_SHADOW_DRILLDOWN_FIXTURE,
    }));

    await page.route('**/api/shadow-validation/workflow/overview', (route: Route) => route.fulfill({
        status: 200,
        json: SHADOW_VALIDATION_WORKFLOW_FIXTURE,
    }));

    await page.route('**/api/paper-shadow/consistency/evidence/overview', (route: Route) => route.fulfill({
        status: 200,
        json: overrides.consistencyEvidence ?? CONSISTENCY_EVIDENCE_OVERVIEW_FIXTURE,
    }));

    await page.route('**/api/incidents/replay/review/overview', (route: Route) => route.fulfill({
        status: 200,
        json: overrides.incidentReplayReview ?? INCIDENT_REPLAY_REVIEW_OVERVIEW_FIXTURE,
    }));

    await page.route('**/api/strategy-validation/evaluation-artifacts/preview/overview', (route: Route) => route.fulfill({
        status: 200,
        json: EVALUATION_ARTIFACT_PREVIEW_OVERVIEW_FIXTURE,
    }));

    await page.route('**/api/incidents/replay/overview', (route: Route) => route.fulfill({
        status: 200,
        json: INCIDENT_REPLAY_OVERVIEW_FIXTURE,
    }));

    await page.route('**/api/strategies/evaluation-gate**', (route: Route) => route.fulfill({
        status: 200,
        json: overrides.evaluationGate ?? EVALUATION_GATE_FIXTURE,
    }));

    await page.route('**/api/strategies/paper-shadow/comparison**', (route: Route) => route.fulfill({
        status: 200,
        json: overrides.paperShadow ?? PAPER_SHADOW_FIXTURE,
    }));

    await page.route('**/api/strategies/shadow-live/preview**', (route: Route) => route.fulfill({
        status: 200,
        json: overrides.preview ?? SHADOW_PREVIEW_FIXTURE,
    }));

    return requests;
}

function validationUrl(): string {
    const params = new URLSearchParams({
        strategyId: 'strategy-gateq',
        strategyVersionId: 'sv-gateq-5',
        datasetId: '11111111-1111-4111-8111-111111111111',
        evaluationId: 'eval-gateq-5',
        publishId: 'pub-gateq-5',
        paperRunId: 'paper-gateq-5',
        shadowRunId: 'shadow-preview-only',
    });
    return `/strategies/validation?${params.toString()}`;
}

function expectNoForbiddenCopy(page: Page) {
    return expect(page.locator('body')).not.toContainText(/ready\s+to\s+trade|live\s+ready|trade[_\s]+approved|can\s+trade|authorizedForTrading|tradingReady|liveReady|SHADOW LIVE TRADING ENABLED|REAL PROVIDER ENABLED|PRIVATE TRADING ENABLED|REAL PERMISSION PROBE ENABLED|AI STARTED|DH INTEGRATED|Integration-1 RUNTIME STARTED|placeOrder|cancelOrder|withdraw|transfer|apiKey|secret|passphrase|token|private key|ML_READY\s*[:=]\s*true|PYTHON ML READY\s+YES|PYTHON LIVE READY\s+YES/i);
}

function expectNoForbiddenRequests(requests: string[]): void {
    const forbiddenApiPattern = /credential|permission-probe|withdraw|transfer|\/order|\/cancel|\/amend|wallet|subaccount|\/private|listenKey/i;
    const forbiddenHostPattern = /okx|binance|bybit|coinbase|kraken|gate\.io/i;

    for (const requestUrl of requests) {
        const hostname = new URL(requestUrl).hostname;
        expect(requestUrl, `forbidden private/credential API request: ${requestUrl}`).not.toMatch(forbiddenApiPattern);
        expect(hostname, `forbidden real exchange host request: ${requestUrl}`).not.toMatch(forbiddenHostPattern);
    }
}

async function expectNoSuccessTagForStatuses(page: Page, statuses: string[]): Promise<void> {
    for (const status of statuses) {
        await expect(page.locator('.ant-tag-success').filter({hasText: status})).toHaveCount(0);
    }
}

test.describe('strategy validation Paper / Shadow comparison view', () => {
    test('展示 evaluation gate、comparison、preview、blockers、nextSteps 与 sideEffectPolicy', async ({page}) => {
        const requests = await seedAuthAndGateQStubs(page);

        await page.goto(validationUrl());

        const view = page.getByTestId('strategy-validation-page');
        await expect(view).toBeVisible();
        await expect(view).toContainText('Validation Operations Workbench');
        await expect(view).toContainText('GateT-5 · 只读运营复核');
        await expect(view).toContainText('只读验证');
        await expect(view).toContainText('不代表交易授权');
        await expect(view).toContainText('不代表 LIVE 已启用');
        await expect(view).toContainText('不提交真实订单');
        await expect(view).toContainText('不读取真实凭证');
        await expect(view).toContainText('不调用 private endpoint');
        await expect(view).toContainText('不写真实账户 / 资金 / ledger');
        await expect(view).toContainText('不接 AI / DH runtime 执行链路');

        const operationsWorkbench = page.getByTestId('validation-operations-workbench');
        await expect(operationsWorkbench).toBeVisible();

        const operationsSummary = page.getByTestId('validation-operations-top-summary');
        await expect(operationsSummary).toBeVisible();
        await expect(operationsSummary).toContainText('Shadow validation workflow');
        await expect(operationsSummary).toContainText('Evaluation artifact preview');
        await expect(operationsSummary).toContainText('READY_FOR_OPERATOR_REVIEW（可人工复核，非交易授权）');
        await expect(operationsSummary).toContainText('DIVERGED（证据偏离）');
        await expect(operationsSummary).toContainText('NO_ARTIFACT_SOURCE_CONFIGURED');

        const operationsEvidence = page.getByTestId('validation-operations-evidence-matrix');
        await expect(operationsEvidence).toBeVisible();
        await expect(operationsEvidence).toContainText('strategy validation');
        await expect(operationsEvidence).toContainText('Python artifact preview');
        await expect(operationsEvidence).toContainText('checksum VALID 不表示策略有效');

        const operationsQueue = page.getByTestId('validation-operations-operator-queue');
        await expect(operationsQueue).toBeVisible();
        await expect(operationsQueue).toContainText('derived operator item');
        await expect(operationsQueue).toContainText('review item');
        await expect(operationsQueue).toContainText('ACKNOWLEDGE_RECOMMENDED（建议人工确认，非自动处置）');

        const operationsBoundary = page.getByTestId('validation-operations-boundary-strip');
        await expect(operationsBoundary).toBeVisible();
        await expect(operationsBoundary).toContainText('LIVE DISABLED');
        await expect(operationsBoundary).toContainText('Not trading authorization');
        await expect(operationsBoundary).toContainText('Python ML ready NO');
        await expect(operationsBoundary).toContainText('AI/DH runtime not integrated');

        const detailSections = page.getByTestId('validation-operations-detail-sections');
        await expect(detailSections).toBeVisible();
        await expect(detailSections).toContainText('Detail sections / 只读详情区');
        await expect(view.getByRole('button', {name: /上传|导入|执行 Python|upload|import|start|stop|trade|approve|reject|acknowledge|escalate|closeout/i})).toHaveCount(0);
        await expect(view.locator('input[placeholder*="artifact"], input[placeholder*="path"], input[aria-label*="artifact"], input[aria-label*="path"]')).toHaveCount(0);

        const workbench = page.getByTestId('strategy-validation-shadow-workbench');
        await expect(workbench).toBeVisible();
        await expect(view).toContainText('Strategy Validation / Shadow Workbench');
        await expect(workbench).toContainText('totalStrategyVersions');
        await expect(workbench).toContainText('12');
        await expect(workbench).toContainText('evaluatedStrategyVersions');
        await expect(workbench).toContainText('9');
        await expect(workbench).toContainText('approvedForValidation');
        await expect(workbench).toContainText('3');
        await expect(workbench).toContainText('totalRuns');
        await expect(workbench).toContainText('7');
        await expect(workbench).toContainText('runningRuns');
        await expect(workbench).toContainText('1');
        await expect(workbench).toContainText('blockedRuns');
        await expect(workbench).toContainText('2');
        await expect(workbench).toContainText('latestDecision.decision');
        await expect(workbench).toContainText('APPROVED（验证层通过，非交易授权）');
        await expect(workbench).toContainText('latestRun.status');
        await expect(workbench).toContainText('RUNNING（诊断运行中）');
        await expect(workbench).toContainText('DIVERGED（证据偏离）');
        await expect(workbench).toContainText('HIGH（高偏离）');
        await expect(workbench).toContainText('LIVE DISABLED（LIVE 关闭）');
        await expect(workbench).toContainText('Real provider NOT IMPLEMENTED（真实 provider 未实现）');
        await expect(workbench).toContainText('Private trading NOT IMPLEMENTED（私有交易未实现）');
        await expect(workbench).toContainText('Validation is not trading authorization（验证不是交易授权）');
        await expect(workbench).toContainText('Shadow Run is diagnostic only（Shadow Run 仅诊断）');
        await expect(workbench).toContainText('AI/DH runtime not integrated（AI/DH runtime 未集成）');
        await expect(workbench).toContainText('trace-strategy-validation-smoke');
        await expect(workbench).toContainText('trace-drilldown-smoke');
        await expect(workbench).toContainText('REVIEW_SHADOW_DRILLDOWN');
        await expect(workbench).toContainText('CHECK_DIVERGENCE');
        await expect(workbench).toContainText('fixture-checksum-drilldown');

        const workflow = page.getByTestId('shadow-validation-workflow-panel');
        await expect(workflow).toBeVisible();
        await expect(view).toContainText('Shadow Validation Workflow Overview');
        await expect(workflow).toContainText('totalOperatorItems');
        await expect(workflow).toContainText('3');
        await expect(workflow).toContainText('readyForOperatorReviewCount');
        await expect(workflow).toContainText('1');
        await expect(workflow).toContainText('workflowState');
        await expect(workflow).toContainText('READY_FOR_OPERATOR_REVIEW（可人工复核，非交易授权）');
        await expect(workflow).toContainText('validationDecision');
        await expect(workflow).toContainText('VALIDATION_READY（验证材料可复核，非交易授权）');
        await expect(workflow).toContainText('HIGH（高诊断优先级）');
        await expect(workflow).toContainText('FRESH（证据新鲜，仍需复核）');
        await expect(workflow).toContainText('op-shadow-validation-smoke-1');
        await expect(workflow).toContainText('VALIDATION_READY_IS_REVIEW_ONLY');
        await expect(workflow).toContainText('REVIEW_WORKFLOW_EVIDENCE');
        await expect(workflow).toContainText('trace-shadow-validation-workflow-smoke');
        await expect(workflow).toContainText('LIVE DISABLED（LIVE 关闭）');
        await expect(workflow).toContainText('Real provider NOT IMPLEMENTED（真实 provider 未实现）');
        await expect(workflow).toContainText('Private trading NOT IMPLEMENTED（私有交易未实现）');
        await expect(workflow).toContainText('Validation workflow is diagnostic only（验证 workflow 仅诊断）');
        await expect(workflow).toContainText('Not trading authorization（非交易授权）');
        await expect(workflow).toContainText('AI/DH runtime not integrated（AI/DH runtime 未集成）');

        const consistencyEvidence = page.getByTestId('consistency-evidence-overview-panel');
        await expect(consistencyEvidence).toBeVisible();
        await expect(view).toContainText('Consistency Evidence Overview');
        await expect(consistencyEvidence).toContainText('totalEvidenceItems');
        await expect(consistencyEvidence).toContainText('3');
        await expect(consistencyEvidence).toContainText('consistentCount');
        await expect(consistencyEvidence).toContainText('1');
        await expect(consistencyEvidence).toContainText('divergedCount');
        await expect(consistencyEvidence).toContainText('2');
        await expect(consistencyEvidence).toContainText('staleEvidenceCount');
        await expect(consistencyEvidence).toContainText('criticalSeverityCount');
        await expect(consistencyEvidence).toContainText('latestEvidenceItem.comparisonStatus');
        await expect(consistencyEvidence).toContainText('DIVERGED（Paper / Shadow 证据不一致）');
        await expect(consistencyEvidence).toContainText('CRITICAL（严重诊断优先级）');
        await expect(consistencyEvidence).toContainText('HIGH（高诊断优先级）');
        await expect(consistencyEvidence).toContainText('STALE（证据过期）');
        await expect(consistencyEvidence).toContainText('turnoverDelta');
        await expect(consistencyEvidence).toContainText('metricDeltaSummary');
        await expect(consistencyEvidence).toContainText('rawMetricDeltaExposed');
        await expect(consistencyEvidence).toContainText('false（raw metricDelta 不应暴露）');
        await expect(consistencyEvidence).toContainText('INSPECT_CONSISTENCY_EVIDENCE');
        await expect(consistencyEvidence).toContainText('trace-consistency-evidence-smoke');
        await expect(consistencyEvidence.getByTestId('consistency-evidence-metadata')).toContainText('LOCAL_DB_SHADOW_CONSISTENCY_REPORTS');
        await expect(consistencyEvidence.getByTestId('consistency-evidence-metadata')).toContainText('可用性：AVAILABLE');
        await expect(consistencyEvidence.getByTestId('consistency-evidence-metadata')).toContainText('新鲜度：FRESH（新鲜）');
        await expect(consistencyEvidence.getByTestId('consistency-evidence-metadata')).toContainText('最近计算时间：');
        await expect(consistencyEvidence).toContainText('LIVE DISABLED（LIVE 关闭）');
        await expect(consistencyEvidence).toContainText('Real provider NOT IMPLEMENTED（真实 provider 未实现）');
        await expect(consistencyEvidence).toContainText('Private trading NOT IMPLEMENTED（私有交易未实现）');
        await expect(consistencyEvidence).toContainText('Consistency evidence is diagnostic only（一致性证据仅诊断）');
        await expect(consistencyEvidence).toContainText('Not trading authorization（非交易授权）');
        await expect(consistencyEvidence).toContainText('AI/DH runtime not integrated（AI/DH runtime 未集成）');

        const incidentReplayReview = page.getByTestId('incident-replay-review-overview-panel');
        await expect(incidentReplayReview).toBeVisible();
        await expect(view).toContainText('Incident / Replay Review Overview');
        await expect(incidentReplayReview).toContainText('totalReviewItems');
        await expect(incidentReplayReview).toContainText('7');
        await expect(incidentReplayReview).toContainText('acknowledgedRecommendationCount');
        await expect(incidentReplayReview).toContainText('escalatedRecommendationCount');
        await expect(incidentReplayReview).toContainText('closedRecommendationCount');
        await expect(incidentReplayReview).toContainText('blockedCount');
        await expect(incidentReplayReview).toContainText('latestReviewItem.reviewState');
        await expect(incidentReplayReview).toContainText('ESCALATED_RECOMMENDATION（建议人工升级复核）');
        await expect(incidentReplayReview).toContainText('latestReviewItem.reviewDecision');
        await expect(incidentReplayReview).toContainText('ESCALATE_RECOMMENDED（建议人工升级复核，非系统已升级）');
        await expect(incidentReplayReview).toContainText('ACKNOWLEDGE_RECOMMENDED（建议人工确认，非自动处置）');
        await expect(incidentReplayReview).toContainText('CLOSEOUT_RECOMMENDED（建议形成诊断闭环，非真实关闭）');
        await expect(incidentReplayReview).toContainText('CRITICAL（严重诊断优先级）');
        await expect(incidentReplayReview).toContainText('HIGH（高诊断优先级）');
        await expect(incidentReplayReview).toContainText('STALE（证据过期）');
        await expect(incidentReplayReview).toContainText('Review summaries');
        await expect(incidentReplayReview).toContainText('REVIEW_INCIDENT_REPLAY_WORKFLOW');
        await expect(incidentReplayReview).toContainText('trace-incident-replay-review-smoke');
        await expect(incidentReplayReview).toContainText('trace-incident-replay-review-overview-smoke');
        await expect(incidentReplayReview.getByTestId('incident-replay-review-evidence-metadata')).toContainText('LOCAL_DB_INCIDENT_REPLAY_REVIEW');
        await expect(incidentReplayReview.getByTestId('incident-replay-review-evidence-metadata')).toContainText('可用性：AVAILABLE');
        await expect(incidentReplayReview.getByTestId('incident-replay-review-evidence-metadata')).toContainText('新鲜度：FRESH（新鲜）');
        await expect(incidentReplayReview.getByTestId('incident-replay-review-evidence-metadata')).toContainText('最近计算时间：');
        await expect(incidentReplayReview).toContainText('LIVE DISABLED（LIVE 关闭）');
        await expect(incidentReplayReview).toContainText('Real provider NOT IMPLEMENTED（真实 provider 未实现）');
        await expect(incidentReplayReview).toContainText('Private trading NOT IMPLEMENTED（私有交易未实现）');
        await expect(incidentReplayReview).toContainText('Incident / Replay review is diagnostic only（Incident / Replay review 仅诊断）');
        await expect(incidentReplayReview).toContainText('Not trading authorization（非交易授权）');
        await expect(incidentReplayReview).toContainText('AI/DH runtime not integrated（AI/DH runtime 未集成）');

        const artifactPreview = page.getByTestId('evaluation-artifact-preview-overview-panel');
        await expect(artifactPreview).toBeVisible();
        await expect(view).toContainText('Python Evaluation Artifact Preview');
        await expect(artifactPreview).toContainText('totalArtifactPreviews');
        await expect(artifactPreview).toContainText('validArtifactCount');
        await expect(artifactPreview).toContainText('invalidArtifactCount');
        await expect(artifactPreview).toContainText('staleArtifactCount');
        await expect(artifactPreview).toContainText('checksumFailedCount');
        await expect(artifactPreview).toContainText('NO_ARTIFACT_SOURCE_CONFIGURED');
        await expect(artifactPreview).toContainText('当前未配置 artifact source');
        await expect(artifactPreview).toContainText('No-file baseline');
        await expect(artifactPreview).toContainText('success 不表示盈利，danger 不表示下跌');
        await expect(artifactPreview).toContainText('NOT_CHECKED（未检查）');
        await expect(artifactPreview).toContainText('UNKNOWN（未知状态）');
        await expect(artifactPreview).toContainText('FAKE_FIXTURE_ONLY');
        await expect(artifactPreview).toContainText('pythonMlReady');
        await expect(artifactPreview).toContainText('false（Python ML ready NO）');
        await expect(artifactPreview).toContainText('pythonLiveExecutionReady');
        await expect(artifactPreview).toContainText('false（Python live execution ready NO）');
        await expect(artifactPreview.getByTestId('evaluation-artifact-preview-evidence-metadata')).toContainText('LOCAL_NO_FILE_EVALUATION_ARTIFACT_PREVIEW');
        await expect(artifactPreview.getByTestId('evaluation-artifact-preview-evidence-metadata')).toContainText('可用性：UNAVAILABLE');
        await expect(artifactPreview.getByTestId('evaluation-artifact-preview-evidence-metadata')).toContainText('新鲜度：UNKNOWN（无法判断新鲜度）');
        await expect(artifactPreview.getByTestId('evaluation-artifact-preview-evidence-metadata')).toContainText('最近计算时间：未提供');
        await expect(artifactPreview).toContainText('LIVE DISABLED（LIVE 关闭）');
        await expect(artifactPreview).toContainText('Real provider NOT IMPLEMENTED（真实 provider 未实现）');
        await expect(artifactPreview).toContainText('Private trading NOT IMPLEMENTED（私有交易未实现）');
        await expect(artifactPreview).toContainText('Python artifact preview is diagnostic only（Python artifact preview 仅诊断）');
        await expect(artifactPreview).toContainText('Not trading authorization（非交易授权）');
        await expect(artifactPreview).toContainText('Python ML ready NO（Python ML ready 否）');
        await expect(artifactPreview).toContainText('Python live execution ready NO（Python live execution ready 否）');
        await expect(artifactPreview).toContainText('AI/DH runtime not integrated（AI/DH runtime 未集成）');
        await expect(artifactPreview).toContainText('trace-evaluation-artifact-preview-overview-smoke');
        await expect(artifactPreview.getByRole('button', {name: /上传|导入|执行|upload|import/i})).toHaveCount(0);
        await expect(artifactPreview.locator('input')).toHaveCount(0);

        await expect(view).toContainText('状态解释');
        await expect(view).toContainText('VALID_FOR_BINDING_PREVIEW');
        await expect(view).toContainText('UNKNOWN / NOT_AVAILABLE / NOT_IMPLEMENTED / BLOCKED_*');
        await expect(view).toContainText('生命周期追溯链');
        await expect(view).toContainText('strategyVersion -> dataset -> evaluation -> publish -> paper -> shadow');
        await expect(view).toContainText('Strategy Version');
        await expect(view).toContainText('Dataset');
        await expect(view).toContainText('Evaluation Gate');
        await expect(view).toContainText('Publish Trace');
        await expect(view).toContainText('Paper Run');
        await expect(view).toContainText('Paper / Shadow Comparison');
        await expect(view).toContainText('Shadow Live Preview');
        await expect(view).toContainText('Python Artifact Binding Preview');
        await expect(view).toContainText('NO_ARTIFACT_SOURCE_CONFIGURED（未配置 artifact source）');
        await expect(view).toContainText('trace-evaluation-artifact-preview-overview-smoke');

        await expect(view).toContainText('Evidence Matrix / 证据矩阵');
        await expect(view).toContainText('requiredEvidence');
        await expect(view).toContainText('missingEvidence');
        await expect(view).toContainText('blockers');
        await expect(view).toContainText('warnings');
        await expect(view).toContainText('nextSteps');

        await expect(view).toContainText('READY_FOR_SHADOW_REVIEW（可进入 Shadow 评审）');
        await expect(view).toContainText('READY_FOR_COMPARISON（可查看只读对照）');
        await expect(view).toContainText('PREVIEW_BLOCKED_SHADOW_FACTS_NOT_AVAILABLE（Shadow facts 不可用）');
        await expect(view).toContainText('NOT_IMPLEMENTED（能力未实现）');
        await expect(view).toContainText('PREVIEW_BLOCKED_SHADOW_FACTS_NOT_AVAILABLE');
        await expect(view).toContainText('Provide approved Shadow read-only facts');

        for (const policy of SIDE_EFFECT_POLICY) {
            await expect(view).toContainText(policy.code);
            await expect(view).toContainText(policy.status);
        }

        await expect(view).toContainText('strategyVersion');
        await expect(view).toContainText('dataset');
        await expect(view).toContainText('evaluation');
        await expect(view).toContainText('publish');
        await expect(view).toContainText('paper');
        await expect(view).toContainText('shadow');

        await expectNoForbiddenCopy(page);
        await expectNoSuccessTagForStatuses(page, [
            'NOT_IMPLEMENTED',
            'NOT_AVAILABLE',
            'UNKNOWN',
            'PENDING_FRONTEND_SUPPORT',
            'NO_ARTIFACT_SOURCE_CONFIGURED',
            'NO_FILE_BASELINE',
            'NOT_CHECKED',
            'FAKE_FIXTURE_ONLY',
            'VALIDATION_READY',
            'READY_FOR_OPERATOR_REVIEW',
            'ACKNOWLEDGE_RECOMMENDED',
            'ESCALATE_RECOMMENDED',
            'CLOSEOUT_RECOMMENDED',
            'ESCALATED_RECOMMENDATION',
            'DIVERGED',
            'HIGH',
            'CRITICAL',
        ]);
        expectNoForbiddenRequests(requests);
        expect(requests.some((url) => url.includes('/api/strategy-validation/overview'))).toBeTruthy();
        expect(requests.some((url) => url.includes('/api/shadow-runs/overview'))).toBeTruthy();
        expect(requests.some((url) => url.includes('/api/paper-shadow/consistency/drilldown'))).toBeTruthy();
        expect(requests.some((url) => url.includes('/api/shadow-validation/workflow/overview'))).toBeTruthy();
        await expect(page.getByTestId('read-model-evidence-metadata')).toContainText('LOCAL_DB_VALIDATION_WORKFLOW');
        await expect(page.getByTestId('read-model-evidence-metadata')).toContainText('可用性：AVAILABLE');
        await expect(page.getByTestId('read-model-evidence-metadata')).toContainText('新鲜度：FRESH（新鲜）');
        expect(requests.some((url) => url.includes('/api/paper-shadow/consistency/evidence/overview'))).toBeTruthy();
        expect(requests.some((url) => url.includes('/api/incidents/replay/review/overview'))).toBeTruthy();
        expect(requests.some((url) => url.includes('/api/strategy-validation/evaluation-artifacts/preview/overview'))).toBeTruthy();
        expect(requests.some((url) => url.includes('/api/strategies/evaluation-gate'))).toBeTruthy();
        expect(requests.some((url) => url.includes('/api/strategies/paper-shadow/comparison'))).toBeTruthy();
        expect(requests.some((url) => url.includes('/api/strategies/shadow-live/preview'))).toBeTruthy();
        expect(requests.some((url) => url.includes('/api/research/evaluation-artifacts'))).toBeFalsy();
    });

    test('UNKNOWN / NOT_AVAILABLE 不显示为成功态', async ({page}) => {
        const requests = await seedAuthAndGateQStubs(page, {
            evaluationGate: UNKNOWN_GATE_FIXTURE,
            paperShadow: {
                ...PAPER_SHADOW_FIXTURE,
                comparisonStatus: 'NOT_AVAILABLE',
                evaluationGateStatus: 'UNKNOWN',
                comparable: false,
                requiredEvidence: [],
                missingEvidence: [
                    {code: 'PAPER_EVIDENCE', status: 'NOT_AVAILABLE', message: 'Paper evidence is not available.'},
                ],
                blockers: [
                    {code: 'PAPER_EVIDENCE_MISSING', severity: 'BLOCKER', message: 'Paper evidence is missing.'},
                ],
            },
            preview: {
                ...SHADOW_PREVIEW_FIXTURE,
                previewStatus: 'UNKNOWN',
                evaluationGateStatus: 'UNKNOWN',
                paperShadowComparisonStatus: 'NOT_AVAILABLE',
                requiredEvidence: [],
                missingEvidence: [
                    {code: 'TRACE_CHAIN', status: 'UNKNOWN', message: 'Trace chain is unknown.'},
                ],
                blockers: [
                    {code: 'UNKNOWN_INPUT_FACTS', severity: 'BLOCKER', message: 'Input facts are unknown.'},
                ],
            },
            consistencyEvidence: {
                ...CONSISTENCY_EVIDENCE_OVERVIEW_FIXTURE,
                evidenceMetadata: {
                    ...CONSISTENCY_EVIDENCE_OVERVIEW_FIXTURE.evidenceMetadata,
                    availability: 'UNAVAILABLE',
                    lastCalculatedAt: null,
                    freshnessStatus: 'UNKNOWN',
                    ageSeconds: null,
                    staleReason: 'LAST_CALCULATED_AT_MISSING',
                },
            },
            incidentReplayReview: {
                ...INCIDENT_REPLAY_REVIEW_OVERVIEW_FIXTURE,
                evidenceMetadata: {
                    ...INCIDENT_REPLAY_REVIEW_OVERVIEW_FIXTURE.evidenceMetadata,
                    availability: 'PARTIAL',
                    lastCalculatedAt: null,
                    freshnessStatus: 'UNKNOWN',
                    ageSeconds: null,
                    staleReason: 'LAST_CALCULATED_AT_MISSING',
                },
            },
        });

        await page.goto(validationUrl());

        const view = page.getByTestId('strategy-validation-page');
        await expect(view).toContainText('UNKNOWN（未知）');
        await expect(view).toContainText('NOT_AVAILABLE（不可用）');
        await expect(view).toContainText('查询结果不是通过态');
        await expect(view).toContainText('Resolve missing dataset facts before review.');
        await expect(view).not.toContainText('READY_FOR_SHADOW_REVIEW（可进入 Shadow 评审）');
        await expect(view).not.toContainText('READY_FOR_NO_SIDE_EFFECT_PREVIEW（可生成无副作用预览）');
        const consistencyEvidence = page.getByTestId('consistency-evidence-overview-panel');
        await expect(consistencyEvidence.getByTestId('consistency-evidence-metadata')).toContainText('可用性：UNAVAILABLE');
        await expect(consistencyEvidence.getByTestId('consistency-evidence-metadata')).toContainText('新鲜度：UNKNOWN（无法判断新鲜度）');
        await expect(consistencyEvidence.getByTestId('consistency-evidence-metadata')).toContainText('最近计算时间：未提供');
        const incidentReplayReview = page.getByTestId('incident-replay-review-overview-panel');
        await expect(incidentReplayReview.getByTestId('incident-replay-review-evidence-metadata')).toContainText('可用性：PARTIAL');
        await expect(incidentReplayReview.getByTestId('incident-replay-review-evidence-metadata')).toContainText('新鲜度：UNKNOWN（无法判断新鲜度）');
        await expect(incidentReplayReview.getByTestId('incident-replay-review-evidence-metadata')).toContainText('最近计算时间：未提供');

        await expectNoForbiddenCopy(page);
        await expectNoSuccessTagForStatuses(page, ['UNKNOWN', 'NOT_AVAILABLE', 'NOT_IMPLEMENTED']);
        expectNoForbiddenRequests(requests);
    });
});
