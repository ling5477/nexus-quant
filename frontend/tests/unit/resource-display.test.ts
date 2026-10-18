import {describe, expect, it} from 'vitest';
import {
    bytesToMiB,
    resourcesAreStale,
    type JvmResourceSnapshot,
} from '../../src/features/runtime/api/resources';

describe('JVM resource measurement semantics', () => {
    it('separates missing measurement from zero and uses binary MiB', () => {
        for (const value of [undefined, null, -1, Infinity, NaN])
            expect(bytesToMiB(value)).toBeNull();
        expect(bytesToMiB(0)).toBe(0);
        expect(bytesToMiB(1_048_576)).toBe(1);
    });
    it('uses actual sample time, never checkedAt, to determine stale data', () => {
        const snapshot = {
            status: 'AVAILABLE',
            lastSampleAt: '2026-10-13T00:00:00Z',
            checkedAt: '2026-10-13T00:05:00Z',
        } as JvmResourceSnapshot;
        const time = Date.parse(snapshot.lastSampleAt!);
        expect(resourcesAreStale(snapshot, time + 45_000)).toBe(false);
        expect(resourcesAreStale(snapshot, time + 45_001)).toBe(true);
        expect(resourcesAreStale({...snapshot, status: 'STALE'}, time)).toBe(true);
        expect(resourcesAreStale({...snapshot, lastSampleAt: 'invalid'}, time)).toBe(true);
    });
});
