import {describe, expect, it} from 'vitest';
import {
    parseScheduledDelayDraft,
    scheduledDelayLimits,
    validScheduledDelay,
} from '@/features/scheduler/job-delay';
import type {ScheduledJobControl} from '@/features/scheduler/types';

const job: ScheduledJobControl = {
    jobKey: 'CONTINUOUS_SIM_POLL',
    enabled: false,
    fixedDelayMs: 300000,
    minFixedDelayMs: 30000,
    maxFixedDelayMs: 86400000,
    nextRunAt: null,
    lastStartedAt: null,
    lastFinishedAt: null,
    lastStatus: 'NEVER_RUN',
    lastErrorCode: null,
    consecutiveFailures: 0,
    version: 4,
    activeRunId: null,
    updatedBy: null,
    updatedAt: '2026-10-14T00:00:00Z',
};

describe('server-owned scheduler interval constraints', () => {
    it('uses the actual draft rather than a previous valid number or implicit rounding', () => {
        expect(parseScheduledDelayDraft('29999')).toBe(29999);
        expect(parseScheduledDelayDraft('30000')).toBe(30000);
        for (const value of ['', '30000.1', '3e4', '30,000', '-1', 'NaN', '9007199254740992'])
            expect(parseScheduledDelayDraft(value)).toBeNull();
    });
    it('keeps Continuous SIM at the formal 30000ms boundary', () => {
        expect(validScheduledDelay(job, 29999)).toBe(false);
        expect(validScheduledDelay(job, 30000)).toBe(true);
        expect(validScheduledDelay(job, 86400000)).toBe(true);
        expect(validScheduledDelay(job, 86400001)).toBe(false);
    });
    it('keeps Paper matching under its distinct 60000ms ceiling', () => {
        const paper = {
            ...job,
            jobKey: 'PAPER_MATCHING',
            minFixedDelayMs: 1000,
            maxFixedDelayMs: 60000,
        };
        expect(validScheduledDelay(paper, 1000)).toBe(true);
        expect(validScheduledDelay(paper, 60000)).toBe(true);
        expect(validScheduledDelay(paper, 60001)).toBe(false);
    });
    it('rejects missing, reversed, fractional and unsafe metadata without an invented fallback', () => {
        for (const value of [
            {...job, minFixedDelayMs: undefined},
            {...job, maxFixedDelayMs: undefined},
            {...job, minFixedDelayMs: 0},
            {...job, minFixedDelayMs: 0.5},
            {...job, maxFixedDelayMs: 1000},
            {...job, maxFixedDelayMs: Number.MAX_SAFE_INTEGER + 1},
        ])
            expect(scheduledDelayLimits(value)).toBeNull();
        expect(scheduledDelayLimits(null)).toBeNull();
    });
    it('rejects empty, nonfinite and fractional drafts', () => {
        for (const value of [null, NaN, Infinity, 30000.1])
            expect(validScheduledDelay(job, value)).toBe(false);
    });
});
