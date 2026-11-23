import {t} from '@/i18n';
import type {FormInstance} from 'antd';
import type {KeyboardEvent} from 'react';

/** Enter由日期控件确认当前时间，不应同时触发浏览器的隐式业务表单提交。 */
export function confirmTimeWithoutSubmittingForm(event: KeyboardEvent<HTMLElement>): void {
    if (event.key === 'Enter') event.preventDefault();
}

export function instantMillis(value: unknown): number | null {
    if (typeof value === 'string' && value.trim()) {
        const result = Date.parse(value);
        return Number.isFinite(result) ? result : null;
    }
    if (value && typeof value === 'object' && 'valueOf' in value) {
        const result = Number(value.valueOf());
        return Number.isFinite(result) ? result : null;
    }
    return null;
}

/** 比较完整Instant而非日期文本；相同秒和逆序区间均不能提交。 */
export function endAfterStartRule(startField: string) {
    return ({getFieldValue}: Pick<FormInstance, 'getFieldValue'>) => ({
        validator: async (_rule: unknown, value: unknown) => {
            const start = instantMillis(getFieldValue(startField)),
                end = instantMillis(value);
            if (start !== null && end !== null && end <= start)
                throw new Error(t('markets.rangeInvalid'));
        },
    });
}

export function intervalSeconds(interval?: string): number | undefined {
    const match = /^(\d+)([mhd])$/.exec(interval ?? '');
    return match ? Number(match[1]) * ({m: 60, h: 3600, d: 86400}[match[2]] ?? 0) : undefined;
}
