import {describe, expect, it} from 'vitest';
import {supportsValidationPolicy, type ValidationPolicy} from '../../src/features/experiments/validationApi';
import zh from '../../src/i18n/locales/zh-CN/pages.json';
import en from '../../src/i18n/locales/en-US/pages.json';

describe('有限研究验证政策入口', () => {
    const policy = (version: string, barCount: number, supported?: boolean) => ({
        version, barCount, supported, steps: Array.from({length: 16}, (_, index) => ({fold: Math.floor(index / 4) + 1,
            role: 'IN_SAMPLE', startBar: 0, endBar: 1})),
    } as ValidationPolicy);

    it('兼容原720政策并接受服务端明确支持的90/180日政策', () => {
        expect(supportsValidationPolicy(policy('EXPANDING_WALK_FORWARD_V1', 720))).toBe(true);
        for (const count of [2160, 4320]) expect(supportsValidationPolicy(policy('EXPANDING_WALK_FORWARD_V2', count, true))).toBe(true);
    });

    it('不因根数较大就裁切或忽略服务端拒绝、未知政策及缺失步骤', () => {
        for (const count of [0, 721, 1200, 2161, 4321]) expect(supportsValidationPolicy(policy('EXPANDING_WALK_FORWARD_V2', count, true))).toBe(false);
        expect(supportsValidationPolicy(policy('EXPANDING_WALK_FORWARD_V1', 4320, true))).toBe(false);
        expect(supportsValidationPolicy(policy('EXPANDING_WALK_FORWARD_V2', 4320, false))).toBe(false);
        expect(supportsValidationPolicy(policy('EXPANDING_WALK_FORWARD_V3', 4320, true))).toBe(false);
        expect(supportsValidationPolicy({...policy('EXPANDING_WALK_FORWARD_V2', 4320, true), steps: []})).toBe(false);
    });

    it('中英文同时覆盖窗口、预算与已有暴露说明', () => {
        expect(Object.keys(zh.walkForward).sort()).toEqual(Object.keys(en.walkForward).sort());
        expect(Object.keys(zh.experiments).sort()).toEqual(Object.keys(en.experiments).sort());
        expect(zh.walkForward.exposure).toContain('不能视为从未观察');
        expect(en.walkForward.exposure).toContain('not pristine');
    });
});
