import {StatusTag, type StatusTone} from '@/nq-design-system/status/StatusTag';

/**
 * @deprecated 新代码直接使用 `@/nq-design-system/status/StatusTag`。
 * 本组件仅保留旧调用面的 props 兼容，不维护独立状态映射或颜色逻辑。
 *
 * canonical 状态颜色规范：
 * RUNNING/ACTIVE/SUCCEEDED=绿，PENDING/CREATED=蓝，PAUSED/SKIPPED=灰，
 * WARNING/DEGRADED=橙，FAILED/BLOCKED/REJECTED=红。
 * 关键约束：渲染文本必须保持后端原始状态值（E2E 与审计依赖原文），不做翻译或改写；
 * 同名状态在不同业务里语义不同时（如告警的 OPEN），通过 tone 显式覆盖。
 */
export type NqStatusTone = Exclude<StatusTone, 'primary'>;

interface NqStatusTagProps {
    status: string | null | undefined;
    /** 同名状态语义冲突时显式覆盖（例如告警的 OPEN 应为 danger）。 */
    tone?: NqStatusTone;
}

export function NqStatusTag({status, tone}: NqStatusTagProps) {
    return <StatusTag status={status} tone={tone} title="" variant="pill"/>;
}
