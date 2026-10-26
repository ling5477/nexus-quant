import {afterEach, describe, expect, it} from 'vitest';
import i18n, {setLocale} from '../../src/i18n';
import {STRATEGY_TYPE_OPTIONS} from '../../src/constants/filter-options';

afterEach(async () => {await setLocale('zh-CN');});
describe('策略类型的业务标签', () => {
    it('在同一组选项上切换语言，执行标识和兼容项保持不变', async () => {
        await setLocale('zh-CN');
        const ids = STRATEGY_TYPE_OPTIONS.map(option => option.value);
        expect(STRATEGY_TYPE_OPTIONS.find(option => option.value === 'E2E_SMOKE')?.label).toBe('端到端冒烟（测试定义）');
        await setLocale('en-US');
        expect(i18n.language).toBe('en-US');
        expect(STRATEGY_TYPE_OPTIONS.find(option => option.value === 'E2E_SMOKE')?.label).toBe('End-to-end smoke (test definition)');
        expect(STRATEGY_TYPE_OPTIONS.map(option => option.value)).toEqual(ids);
        expect(ids).toEqual(['SPOT_SMA_TARGET_V1', 'SPOT_CHANNEL_BREAKOUT_V1', 'GRID', 'BUY_AND_HOLD_FIXTURE', 'E2E_SMOKE']);
    });
});
