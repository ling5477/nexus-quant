import {describe, expect, it} from 'vitest';
import {formatNqNumber as componentFormat} from '../../src/components/nq/NqNumericText';
import {formatNqNumber as designFormat} from '../../src/nq-design-system/format/nqFormat';
import {statusTone} from '../../src/pages/shadow-runs/statusTone';

describe('收敛后的前端合同', () => {
    it('数字组件原出口使用设计系统实现并保留边界输出', () => {
        expect(componentFormat).toBe(designFormat);
        expect(componentFormat(null)).toBe('-');
        expect(componentFormat('')).toBe('-');
        expect(componentFormat('not-a-number')).toBe('not-a-number');
        expect(componentFormat('1234.5', {precision: 2, signed: true})).toBe('+1,234.50');
        expect(componentFormat(-0.25, {precision: 2, signed: true})).toBe('-0.25');
    });

    it('Shadow Run 的诊断状态优先级保持领域规则', () => {
        expect(statusTone('READY')).toBe('success');
        expect(statusTone('PARTIAL_FAILED')).toBe('danger');
        expect(statusTone('DIVERGED')).toBe('warning');
        expect(statusTone('UNKNOWN')).toBe('neutral');
        expect(statusTone(null)).toBe('info');
    });
});
