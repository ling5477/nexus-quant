// DataFreshness.tsx — 数据源新鲜度与"看不见什么"(Design Tokens v2)
// 灵感来自 World Monitor 的 intelligence gap:数据源断了不能静默隐藏,要显式标状态。

import {useTranslation} from 'react-i18next';

export type FreshnessState =
    | 'fresh'
    | 'stale'
    | 'very_stale'
    | 'delayed'
    | 'degraded'
    | 'no_data'
    | 'error'
    | 'disabled';

const STATE: Record<FreshnessState, {color: string; label: string}> = {
    fresh: {color: 'var(--nq-success)', label: 'Fresh'},
    stale: {color: 'var(--nq-warning)', label: 'Stale'},
    very_stale: {color: 'var(--nq-warning)', label: 'Very stale'},
    delayed: {color: 'var(--nq-warning)', label: 'Delayed'},
    degraded: {color: 'var(--nq-warning)', label: 'Degraded'},
    no_data: {color: 'var(--nq-text-tertiary)', label: 'No data'},
    error: {color: 'var(--nq-danger)', label: 'Error'},
    disabled: {color: 'var(--nq-text-disabled)', label: 'Disabled'},
};

interface DataFreshnessProps {
    /** 数据源名,例如 "OKX Market Data" */
    source: string;
    state: FreshnessState;
    /** 相对时间或延迟,例如 "2s ago" / "120ms" / "last heartbeat 45s" */
    detail?: string;
    /** 行内紧凑模式(嵌入表格/Top Bar)或独立行 */
    inline?: boolean;
    /** 相对于请求窗口的覆盖不能冒充实时市场新鲜度。 */
    label?: string;
}

export function DataFreshness({source, state, detail, inline = false, label}: DataFreshnessProps) {
    const {t} = useTranslation();
    const s = {...STATE[state], label: label ?? t(`freshness.${state}`)};
    return (
        <span
            title={`${source}: ${s.label}${detail ? ` (${detail})` : ''}`}
            style={{
                // 小屏与长时点允许整组换行，避免新鲜度说明撑开页面或遮住抽屉。
                display: 'inline-flex',
                alignItems: 'center',
                gap: 8,
                flexWrap: 'wrap',
                fontSize: 12,
                color: 'var(--nq-text-secondary)',
                whiteSpace: 'normal',
                maxWidth: '100%',
                minWidth: 0,
                ...(inline ? {} : {justifyContent: 'space-between', width: '100%'}),
            }}
        >
            <span
                style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 6,
                    minWidth: 0,
                    overflowWrap: 'anywhere',
                }}
            >
                <span
                    style={{
                        width: 7,
                        height: 7,
                        borderRadius: '50%',
                        background: s.color,
                        flex: '0 0 auto',
                    }}
                />
                {source}
            </span>
            <span style={{color: s.color}}>{s.label}</span>
            {detail && (
                <span
                    className="nq-mono"
                    style={{
                        color: 'var(--nq-text-tertiary)',
                        fontFamily: 'var(--nq-font-mono)',
                        maxWidth: '100%',
                        overflowWrap: 'anywhere',
                    }}
                >
                    {detail}
                </span>
            )}
        </span>
    );
}
