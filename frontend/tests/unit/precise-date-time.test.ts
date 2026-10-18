import {describe, expect, it} from 'vitest';
import {formatDateTimeMilliseconds} from '@/utils/formatters';

describe('回测时间显示保留真实边界', () => {
    it('同一秒内不同毫秒仍可区分，相同Instant的时区表示保持一致', () => {
        const start = formatDateTimeMilliseconds('2026-10-13T02:11:12.123Z');
        expect(start).toContain('123');
        expect(formatDateTimeMilliseconds('2026-10-13T02:11:12.124Z')).not.toBe(start);
        expect(formatDateTimeMilliseconds('2026-10-13T10:11:12.123+08:00')).toBe(start);
    });
    it('缺失或非法边界不呈现为有效时间', () => {
        expect(formatDateTimeMilliseconds(null)).toBe('-');
        expect(formatDateTimeMilliseconds('invalid')).toBe('-');
    });
});
