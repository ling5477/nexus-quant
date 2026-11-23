/** 窗口原文参与创建身份；只校验而不回写解析结果，避免高精度JSON数值被浮点转换。 */
export interface ScheduleCreationInput {
    strategyId: string;
    scheduleType: 'CRON';
    cronExpr: string;
    timezone: string;
    enabled: boolean;
    windowConfig: string;
    dedupScope: 'SCHEDULE_WINDOW' | 'REQUEST' | 'STRATEGY';
}

export const scheduleCreationFields = [
    'strategyId',
    'scheduleType',
    'cronExpr',
    'timezone',
    'enabled',
    'windowConfig',
    'dedupScope',
] as const;

export function normalizeScheduleCreation(value: ScheduleCreationInput): ScheduleCreationInput {
    const text = (raw: unknown, limit: number): string => {
        if (
            typeof raw !== 'string' ||
            raw.length > limit ||
            !raw.trim() ||
            /[\u0000-\u001f\u007f-\u009f]/.test(raw)
        )
            throw new Error('Invalid schedule creation field');
        return raw.trim();
    };
    const result: ScheduleCreationInput = {
        strategyId: text(value.strategyId, 128),
        scheduleType: 'CRON',
        cronExpr: text(value.cronExpr, 128),
        timezone: text(value.timezone, 64),
        enabled: value.enabled,
        windowConfig: value.windowConfig?.trim() || '{}',
        dedupScope: text(value.dedupScope, 32).toUpperCase() as ScheduleCreationInput['dedupScope'],
    };
    if (
        value.scheduleType !== 'CRON' ||
        typeof result.enabled !== 'boolean' ||
        !['SCHEDULE_WINDOW', 'REQUEST', 'STRATEGY'].includes(result.dedupScope) ||
        new TextEncoder().encode(result.windowConfig).length > 8192
    )
        throw new Error('Invalid schedule creation input');
    const window: unknown = JSON.parse(result.windowConfig);
    if (!window || typeof window !== 'object' || Array.isArray(window))
        throw new Error('Schedule window must be an object');
    return result;
}

/** 每日时段不是绝对日期范围：允许跨午夜，开始等于结束按正式扫描合同表示全天。 */
export function dailyScheduleWindow(start: string, end: string, days: string[]): string {
    const time = /^([01]\d|2[0-3]):[0-5]\d:[0-5]\d$/;
    const allowed = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
    if (
        !time.test(start) ||
        !time.test(end) ||
        days.some((day) => !allowed.includes(day)) ||
        new Set(days).size !== days.length
    )
        throw new Error('Invalid daily schedule window');
    return JSON.stringify({enabled: true, startTime: start, endTime: end, daysOfWeek: days});
}
