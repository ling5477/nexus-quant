import {afterEach, describe, expect, it, vi} from 'vitest';
import {apiClient} from '../../src/api/client';
import {experimentQueryKeys} from '../../src/api/query-keys';
import {researchSimComparisonApi} from '../../src/features/experiments/researchSimComparisonApi';

afterEach(() => vi.restoreAllMocks());

describe('研究与SIM对照的只读客户端合同', () => {
    it('恢复仅GET原验证和显式身份，未知费用不转换为零且金额保留十进制精度', async () => {
        const get = vi.spyOn(apiClient, 'get').mockResolvedValue({data: {validationId: 'validation'}});
        const post = vi.spyOn(apiClient, 'post');
        const selection = {selectedTrialId: 'trial', strategyVersionId: 'version', publishId: 'publication', paperRunId: 'sim'};
        const signal = new AbortController().signal;
        await researchSimComparisonApi.get('validation', selection, signal);
        expect(get).toHaveBeenCalledWith('/strategy-experiments/validations/validation/sim-comparison', expect.objectContaining({params: selection, signal}));
        const config = get.mock.calls[0][1] as {transformResponse: ((raw: string) => unknown)[]};
        expect(config.transformResponse[0]('{"netPnl":123456789012345.123456789,"fee":null,"slippage":-0.000000001,"tradeCount":2}'))
            .toEqual({netPnl: '123456789012345.123456789', fee: null, slippage: '-0.000000001', tradeCount: 2});
        expect(post).not.toHaveBeenCalled();
    });

    it('原发布SIM窗口拒绝其他发布身份、重复身份和超范围窗口', async () => {
        const get = vi.spyOn(apiClient, 'get');
        for (const response of [
            {items: [{paperRunId: 'sim', publishId: 'other', status: 'RUNNING'}], total: 1, limit: 10, offset: 0},
            {items: Array(2).fill({paperRunId: 'sim', publishId: 'publication', status: 'RUNNING'}), total: 2, limit: 10, offset: 0},
            {items: [], total: 0, limit: 20, offset: 0},
        ]) {
            get.mockResolvedValueOnce({data: response});
            await expect(researchSimComparisonApi.sims('publication', 0)).rejects.toThrow();
        }
        get.mockResolvedValueOnce({data: {items: [], total: 0, limit: 10, offset: 0}});
        expect((await researchSimComparisonApi.sims('publication', 0)).items).toEqual([]);
    });

    it('比较缓存同时绑定用户、验证、源候选、版本、发布与SIM', () => {
        const key = experimentQueryKeys.comparison(1, 'validation', 'trial', 'version', 'publication', 'sim');
        for (const changed of [
            experimentQueryKeys.comparison(2, 'validation', 'trial', 'version', 'publication', 'sim'),
            experimentQueryKeys.comparison(1, 'other', 'trial', 'version', 'publication', 'sim'),
            experimentQueryKeys.comparison(1, 'validation', 'other', 'version', 'publication', 'sim'),
            experimentQueryKeys.comparison(1, 'validation', 'trial', 'other', 'publication', 'sim'),
            experimentQueryKeys.comparison(1, 'validation', 'trial', 'version', 'other', 'sim'),
            experimentQueryKeys.comparison(1, 'validation', 'trial', 'version', 'publication', 'other'),
        ]) expect(changed).not.toEqual(key);
    });
});
