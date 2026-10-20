import {afterEach, describe, expect, it, vi} from 'vitest';
import {apiClient} from '../../src/api/client';
import {evaluationsApi, validateEvaluationPage} from '../../src/api/evaluations';
import {publishesApi, validatePublishPage} from '../../src/features/publishes/api/publishes';
import {evaluationsQueryKeys, publishesQueryKeys} from '../../src/api/query-keys';
import type {EvaluationPageRequest} from '../../src/types/evaluations';
import type {PublishPageRequest} from '../../src/features/publishes/types/publishes';

const evaluationRequest: EvaluationPageRequest = {
    researchConfigId: '',
    backtestConfigId: '',
    backtestRunId: '',
    evaluationStatus: '',
    limit: 1,
    offset: 520,
};
const publishRequest: PublishPageRequest = {
    researchConfigId: '',
    backtestConfigId: '',
    sourceStrategyId: '',
    strategyVersionId: '',
    publishStatus: '',
    limit: 1,
    offset: 520,
};
const evaluation = {
    evalReportId: 'evr-last',
    backtestRunId: 'brn-last',
    evaluationStatus: 'FAILED',
    metricsJson: null,
};
const publish = {
    publishRecordId: 'pub-last',
    backtestRunId: 'brn-last',
    publishStatus: 'FAILED',
    publishSnapshotJson: null,
};
afterEach(() => vi.restoreAllMocks());

describe('research result list paging', () => {
    it('uses real evaluation paging GET and retains exact numeric response parsing and cancellation', async () => {
        const response = {items: [evaluation], total: 521, limit: 1, offset: 520};
        const get = vi.spyOn(apiClient, 'get').mockResolvedValue({data: response});
        const signal = new AbortController().signal;
        expect(await evaluationsApi.page(evaluationRequest, signal)).toEqual(response);
        expect(get.mock.calls[0][0]).toBe('/evaluations/page');
        const config = get.mock.calls[0][1]!;
        expect(config.params).toEqual(evaluationRequest);
        expect(config.signal).toBe(signal);
        const transforms = config.transformResponse as ((raw: string) => unknown)[];
        const parsed = transforms[0](
            '{"items":[{"initialCapital":9007199254740993.123456789012345678}]}',
        ) as {items: {initialCapital: string}[]};
        expect(parsed.items[0].initialCapital).toBe('9007199254740993.123456789012345678');
    });

    it('uses formal publication GET with all filters instead of loading and filtering the old array', async () => {
        const response = {items: [publish], total: 521, limit: 1, offset: 520};
        const get = vi.spyOn(apiClient, 'get').mockResolvedValue({data: response});
        const signal = new AbortController().signal;
        expect(await publishesApi.page(publishRequest, signal)).toEqual(response);
        expect(get).toHaveBeenCalledWith('/publishes/page', {params: publishRequest, signal});
    });

    it('accepts failed results with missing metrics or snapshots rather than hiding them', () => {
        expect(
            validateEvaluationPage(
                {items: [evaluation], total: 521, limit: 1, offset: 520},
                evaluationRequest,
            ).items[0].evaluationStatus,
        ).toBe('FAILED');
        expect(
            validatePublishPage(
                {items: [publish], total: 521, limit: 1, offset: 520},
                publishRequest,
            ).items[0].publishStatus,
        ).toBe('FAILED');
    });

    it('rejects wrong windows, truncated responses, invalid status and duplicate business identities', () => {
        for (const value of [
            null,
            {items: [], total: 521, limit: 1, offset: 520},
            {items: [evaluation], total: 521, limit: 1, offset: 0},
            {
                items: [{...evaluation, evaluationStatus: 'UNKNOWN'}],
                total: 521,
                limit: 1,
                offset: 520,
            },
            {items: [evaluation], total: Number.MAX_SAFE_INTEGER + 1, limit: 1, offset: 520},
        ])
            expect(() => validateEvaluationPage(value, evaluationRequest)).toThrow();
        expect(() =>
            validatePublishPage(
                {items: [publish, publish], total: 522, limit: 2, offset: 520},
                {...publishRequest, limit: 2},
            ),
        ).toThrow();
        expect(
            validatePublishPage(
                {items: [], total: 521, limit: 1, offset: 530},
                {...publishRequest, offset: 530},
            ).items,
        ).toEqual([]);
    });

    it('includes owner and every evaluation filter/window/version in the cache key', () => {
        const original = evaluationsQueryKeys.page(1, evaluationRequest, 1);
        for (const value of [
            evaluationsQueryKeys.page(2, evaluationRequest, 1),
            ...Object.entries({
                researchConfigId: 'r',
                backtestConfigId: 'c',
                backtestRunId: 'run',
                evaluationStatus: 'FAILED',
                limit: 20,
                offset: 0,
            }).map(([key, value]) =>
                evaluationsQueryKeys.page(1, {...evaluationRequest, [key]: value}, 1),
            ),
            evaluationsQueryKeys.page(1, evaluationRequest, 2),
        ])
            expect(value).not.toEqual(original);
    });

    it('includes owner and every publication filter/window/version in the cache key', () => {
        const original = publishesQueryKeys.page(1, publishRequest, 1);
        for (const value of [
            publishesQueryKeys.page(2, publishRequest, 1),
            ...Object.entries({
                researchConfigId: 'r',
                backtestConfigId: 'c',
                sourceStrategyId: 's',
                strategyVersionId: 'v',
                publishStatus: 'FAILED',
                limit: 20,
                offset: 0,
            }).map(([key, value]) =>
                publishesQueryKeys.page(1, {...publishRequest, [key]: value}, 1),
            ),
            publishesQueryKeys.page(1, publishRequest, 2),
        ])
            expect(value).not.toEqual(original);
    });
});
