import {Skeleton} from 'antd';
import type {ReactNode} from 'react';

/**
 * NqMetricCard — 指标卡片（Dashboard / 详情页状态摘要用）。
 *
 * 关键约束：
 * 1) 数值区强制 tabular-nums（样式见 index.css 的 .nq-metric-card__value）；
 * 2) tone 只表达语义（success/warning/danger/up/down/muted），取色来自 Design System tokens；
 * 3) 无数据时由调用方传 '-' 或使用 muted tone，不在组件内编造默认值。
 */
export type NqMetricTone = 'default' | 'success' | 'warning' | 'danger' | 'up' | 'down' | 'muted';

interface NqMetricCardProps {
    label: string;
    value: ReactNode;
    tone?: NqMetricTone;
    /** 次要说明（如统计窗口、数据来源）。 */
    footer?: ReactNode;
    loading?: boolean;
}

export function NqMetricCard({label, value, tone = 'default', footer, loading = false}: NqMetricCardProps) {
    const valueClassName = tone === 'default'
        ? 'nq-metric-card__value'
        : `nq-metric-card__value nq-metric-card__value--${tone}`;

    return (
        <div className="nq-metric-card">
            <span className="nq-metric-card__label">{label}</span>
            {loading ? (
                <Skeleton.Input active size="small" style={{width: 96, minWidth: 96}}/>
            ) : (
                <span className={valueClassName}>{value}</span>
            )}
            {footer ? <span className="nq-metric-card__footer">{footer}</span> : null}
        </div>
    );
}
