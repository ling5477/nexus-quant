import {afterEach, describe, expect, it, vi} from 'vitest';
import {apiClient} from '../../src/api/client';
import {runQueryKeys} from '../../src/api/query-keys';
import {runsApi} from '../../src/features/runs/api/runs';

const request = {limit: 1, offset: 0};
const event = {
    eventId: 'event',
    orderId: 'order',
    tradeId: null,
    accountId: '9007199254740993',
    eventType: 'OrderCreated',
    topic: 'order.event.v1',
    source: 'nq-core.order-command-write-service',
    association: 'ORDER_ID',
    status: 'NEW',
    decision: null,
    result: null,
    rejectCode: null,
    occurredAt: '2026-10-01T00:00:00.123456789Z',
    publishedAt: '2026-10-01T01:00:00Z',
    recordedAt: '2026-10-01T02:00:00Z',
    traceId: 'trace',
};
afterEach(() => vi.restoreAllMocks());
describe('bounded run business event reads', () => {
    it('passes the exact identity, bounded window and cancellation without loading another event source', async () => {
        const get = vi.spyOn(apiClient, 'get').mockResolvedValue({
            data: {strategyRunId: 'run/1', items: [event], total: 1, ...request},
        });
        const signal = new AbortController().signal;
        expect((await runsApi.businessEvents('run/1', request, signal)).items[0]).toEqual(event);
        expect(get).toHaveBeenCalledWith('/strategy-runs/run%2F1/business-events/page', {
            params: request,
            signal,
        });
    });
    it('keeps missing occurrence/publication time distinct from recording time', async () => {
        const row = {...event, occurredAt: null, publishedAt: null, status: null};
        vi.spyOn(apiClient, 'get').mockResolvedValue({
            data: {strategyRunId: 'run', items: [row], total: 1, ...request},
        });
        expect((await runsApi.businessEvents('run', request)).items[0]).toEqual(row);
    });
    it('rejects another run, rounded accounts, malformed time and damaged windows', async () => {
        for (const change of [
            {strategyRunId: 'other'},
            {items: [{...event, accountId: 9007199254740992}]},
            {items: [{...event, occurredAt: 'invalid'}]},
            {items: [{...event, recordedAt: null}]},
            {items: [{...event, decision: {raw: 'not a code'}}]},
            {items: []},
            {offset: 1},
        ]) {
            vi.spyOn(apiClient, 'get').mockResolvedValue({
                data: {strategyRunId: 'run', items: [event], total: 1, ...request, ...change},
            });
            await expect(runsApi.businessEvents('run', request)).rejects.toThrow();
        }
    });
    it('rejects duplicate event identities rather than hiding a broken association join', async () => {
        vi.spyOn(apiClient, 'get').mockResolvedValue({
            data: {strategyRunId: 'run', items: [event, event], total: 2, limit: 2, offset: 0},
        });
        await expect(runsApi.businessEvents('run', {limit: 2, offset: 0})).rejects.toThrow();
    });
    it('isolates owners, event kind, running identity and page cancellation identities', () => {
        const key = runQueryKeys.related(1, 'run', 'business-events', request);
        for (const other of [
            runQueryKeys.related(2, 'run', 'business-events', request),
            runQueryKeys.related(1, 'run', 'orders', request),
            runQueryKeys.related(1, 'other', 'business-events', request),
            runQueryKeys.related(1, null, 'business-events', request),
            runQueryKeys.related(1, 'run', 'business-events', {...request, offset: 1}),
        ])
            expect(other).not.toEqual(key);
    });
});
