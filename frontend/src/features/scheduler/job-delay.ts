import type {ScheduledJobControl} from './types';

/** 间隔草稿只接受完整十进制整数；不能由旧控件数值、空文本或隐式舍入替代当前输入。 */
export function parseScheduledDelayDraft(text: string): number | null {
    if (!/^\d+$/.test(text)) return null;
    const value = Number(text);
    return Number.isSafeInteger(value) ? value : null;
}

/** 只采用同一任务的正式目录约束；旧服务或异常元数据不回退到宽泛范围。 */
export function scheduledDelayLimits(
    job: ScheduledJobControl | null,
): {min: number; max: number} | null {
    const min = job?.minFixedDelayMs,
        max = job?.maxFixedDelayMs;
    return typeof min === 'number' &&
        typeof max === 'number' &&
        Number.isSafeInteger(min) &&
        Number.isSafeInteger(max) &&
        min > 0 &&
        max >= min
        ? {min, max}
        : null;
}

/** 不将空值、小数或范围外草稿提交；最终校验和并发版本约束仍由正式服务端拥有。 */
export function validScheduledDelay(
    job: ScheduledJobControl | null,
    delay: number | null,
): boolean {
    const limits = scheduledDelayLimits(job);
    return Boolean(
        limits &&
            delay !== null &&
            Number.isSafeInteger(delay) &&
            delay >= limits.min &&
            delay <= limits.max,
    );
}
