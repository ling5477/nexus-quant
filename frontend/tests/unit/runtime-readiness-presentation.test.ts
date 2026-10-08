import {describe, expect, it} from 'vitest';
import {runtimeReadinessReasonKey} from '../../src/pages/runtime/runtimeReadinessPresentation';

describe('runtime readiness explanations', () => {
    it('keeps known disabled and unavailable facts separate', () => {
        expect(runtimeReadinessReasonKey('LIVE_DISABLED')).toBe('pages:runtimeReasonLiveDisabled');
        expect(runtimeReadinessReasonKey('PENDING_BACKEND_SUPPORT')).toBe('pages:runtimeReasonSummaryUnavailable');
        expect(runtimeReadinessReasonKey('REVIEW_REQUIRED', 'status')).toBe('pages:runtimeStatusReviewRequired');
    });

    it('does not turn unexpected readiness or authorization codes into a known safe explanation', () => {
        for (const code of ['READY', 'LIVE_AUTHORIZED', 'UNKNOWN', '', 'toString', '__proto__']) {
            expect(runtimeReadinessReasonKey(code)).toBe('pages:runtimeReasonUntranslated');
            expect(runtimeReadinessReasonKey(code, 'status')).toBe('pages:runtimeReasonUntranslated');
        }
    });
});
