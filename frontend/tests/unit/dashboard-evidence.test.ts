import {describe, expect, it} from 'vitest';
import {hasCurrentFocusEvidence} from '@/pages/dashboard/dashboard-evidence';

const complete = {isSuccess: true, isFetching: false};
describe('dashboard focus evidence', () => {
    it('requires successful current queries and an explicit OK heartbeat', () => {
        expect(hasCurrentFocusEvidence(complete, complete, 'OK')).toBe(true);
    });
    it.each([undefined, 'UNKNOWN', 'NEW_STATUS', 'LAGGING', 'STOPPED'])('does not infer health from %s', status => {
        expect(hasCurrentFocusEvidence(complete, complete, status)).toBe(false);
    });
    it.each([{isSuccess: false, isFetching: false}, {isSuccess: false, isFetching: true}, {isSuccess: true, isFetching: true}])('rejects missing, failed or refreshing query evidence %j', state => {
        expect(hasCurrentFocusEvidence(state, complete, 'OK')).toBe(false);
        expect(hasCurrentFocusEvidence(complete, state, 'OK')).toBe(false);
    });
});
