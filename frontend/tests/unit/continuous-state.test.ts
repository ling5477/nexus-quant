import {describe, expect, it} from 'vitest';
import {
    continuousAction,
    continuousStateMatches,
    continuousStatusKey,
    parseContinuousSimStatus,
} from '@/features/paper-trading/continuous-state';

const state = {
    paperRunId: 'paper-one',
    status: 'RUNNING',
    startedAt: '2026-10-15T00:00:00Z',
    lastPollAt: null,
    lastSuccessfulPollAt: null,
    lastProcessedBar: '2026-10-14T22:00:00Z',
    nextExpectedBar: '2026-10-14T23:00:00Z',
    lastObservedBar: '2026-10-15T00:00:00Z',
    gapStartBar: null,
    lastDecisionId: null,
    lastDecisionStatus: null,
    blockReason: null,
    consecutivePollFailures: 0,
};

describe('continuous state boundaries', () => {
    it('accepts absence only from an empty success and preserves unknown raw states', () => {
        expect(parseContinuousSimStatus(null, 'paper-one')).toBeNull();
        expect(parseContinuousSimStatus('', 'paper-one')).toBeNull();
        const unknown = parseContinuousSimStatus({...state, status: 'FUTURE_STATE'}, 'paper-one');
        expect(unknown?.status).toBe('FUTURE_STATE');
        expect(continuousStatusKey(unknown!.status)).toBe('unknown');
        expect(continuousAction(unknown!, 'RUNNING')).toBeNull();
    });
    it('rejects another run and invalid or fabricated polling facts', () => {
        for (const value of [
            false,
            [],
            {},
            {...state, paperRunId: 'paper-two'},
            {...state, lastProcessedBar: 'bad'},
            {...state, lastSuccessfulPollAt: undefined},
            {...state, consecutivePollFailures: -1},
            {...state, consecutivePollFailures: 1.5},
        ]) {
            expect(() => parseContinuousSimStatus(value, 'paper-one')).toThrow();
        }
        expect(continuousStateMatches({...state, paperRunId: 'paper-two'}, 'paper-one')).toBe(
            false,
        );
    });
    it('separates pausing a cursor from stopping its parent and blocks revised inputs', () => {
        expect(continuousAction(state, 'RUNNING')).toBe('pause');
        expect(continuousAction({...state, status: 'STALLED'}, 'RUNNING')).toBe('pause');
        expect(continuousAction({...state, status: 'STOPPED'}, 'RUNNING')).toBe('resume');
        expect(
            continuousAction(
                {...state, status: 'STOPPED', blockReason: 'DATA_REVISION_DETECTED'},
                'RUNNING',
            ),
        ).toBeNull();
        expect(continuousAction({...state, status: 'STOPPED'}, 'STOPPED')).toBeNull();
        expect(continuousAction(state, 'CREATED')).toBeNull();
    });
});
