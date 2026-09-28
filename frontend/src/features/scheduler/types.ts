export interface ScheduledJobControl {
    jobKey: string;
    enabled: boolean;
    fixedDelayMs: number;
    nextRunAt: string | null;
    lastStartedAt: string | null;
    lastFinishedAt: string | null;
    lastStatus: 'NEVER_RUN' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'SKIPPED';
    lastErrorCode: string | null;
    consecutiveFailures: number;
    version: number;
    activeRunId: string | null;
    updatedBy: string | null;
    updatedAt: string;
}

export interface ScheduledJobPatch {
    enabled?: boolean;
    fixedDelayMs?: number;
    expectedVersion: number;
}

export interface ScheduledJobRunResult {
    jobKey: string;
    result: 'SUCCESS' | 'FAILED' | 'SKIPPED';
}
