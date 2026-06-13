import {Tag} from 'antd';

/**
 * NqStatusTag — 全局统一的状态标签。
 *
 * 状态颜色规范（Design System v1）：
 * RUNNING/ACTIVE/SUCCEEDED=绿，PENDING/CREATED=蓝，PAUSED/SKIPPED=灰，
 * WARNING/DEGRADED=橙，FAILED/BLOCKED/REJECTED=红。
 * 关键约束：渲染文本必须保持后端原始状态值（E2E 与审计依赖原文），不做翻译或改写；
 * 同名状态在不同业务里语义不同时（如告警的 OPEN），通过 tone 显式覆盖。
 */
export type NqStatusTone = 'success' | 'info' | 'neutral' | 'warning' | 'danger';

const TONE_TO_TAG_COLOR: Record<NqStatusTone, string> = {
    success: 'success',
    info: 'processing',
    neutral: 'default',
    warning: 'warning',
    danger: 'error',
};

const STATUS_TONE: Record<string, NqStatusTone> = {
    // 运行/成功类
    RUNNING: 'success',
    ACTIVE: 'success',
    SUCCEEDED: 'success',
    PASSED: 'success',
    OK: 'success',
    ENABLED: 'success',
    GENERATED: 'success',
    RESOLVED: 'success',
    FILLED: 'success',
    // 等待/新建类
    PENDING: 'info',
    CREATED: 'info',
    NEW: 'info',
    // 暂停/跳过类
    PAUSED: 'neutral',
    SKIPPED: 'neutral',
    DISABLED: 'neutral',
    STOPPED: 'neutral',
    // 风险提示类
    WARNING: 'warning',
    DEGRADED: 'warning',
    LAGGING: 'warning',
    PARTIAL: 'warning',
    ACKED: 'warning',
    // 失败/阻断类
    FAILED: 'danger',
    BLOCKED: 'danger',
    REJECTED: 'danger',
    CRITICAL: 'danger',
    ERROR: 'danger',
    // 紧急停机已生效属于强风险态
    APPLIED: 'danger',
};

interface NqStatusTagProps {
    status: string | null | undefined;
    /** 同名状态语义冲突时显式覆盖（例如告警的 OPEN 应为 danger）。 */
    tone?: NqStatusTone;
}

export function NqStatusTag({status, tone}: NqStatusTagProps) {
    if (!status) {
        return <Tag>-</Tag>;
    }

    const resolvedTone = tone ?? STATUS_TONE[status.toUpperCase()] ?? 'neutral';

    return <Tag color={TONE_TO_TAG_COLOR[resolvedTone]}>{status}</Tag>;
}
