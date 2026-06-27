import {Space, Tag, Typography} from 'antd';
import type React from 'react';
import type {ReactNode} from 'react';

import type {PaperPortfolioRunRef} from '@/types/paper-trading';

/**
 * paperPortfolioShared —— Portfolio / Risk / Strategy Ranking 三个 dashboard 共享的小型展示件。
 *
 * K5-A2 行为保持型抽取：`renderRunRefTags`（Portfolio + Risk 共用）与 `ClickableMetricCard`
 * （Risk + Ranking 共用）来源于旧 all-in-one Paper Trading 页，删除旧页后继续按依赖集中在此局部 shared 文件。
 * 实现保留原语义，不改变任何渲染或交互行为。
 */

/** 把 run 引用清单渲染为紧凑 Tag 列表；空清单显示「无」，超过 12 条折叠为「等 N 个」。 */
export function renderRunRefTags(runs: PaperPortfolioRunRef[]): ReactNode {
    if (runs.length === 0) {
        return <Typography.Text type="secondary" style={{fontSize: 12}}>无</Typography.Text>;
    }
    return (
        <Space size={[6, 6]} wrap>
            {runs.slice(0, 12).map((run) => (
                <Tag key={run.paperRunId} className="nq-mono" style={{fontSize: 11}}>{run.paperRunId}</Tag>
            ))}
            {runs.length > 12 ? (
                <Typography.Text type="secondary" style={{fontSize: 12}}>等 {runs.length} 个</Typography.Text>
            ) : null}
        </Space>
    );
}

/**
 * 可点击筛选指标卡包装器（Loop-21 统一 affordance）。
 * - cursor pointer + 键盘可访问（Enter / Space）
 * - 激活态：2px primary outline，aria-pressed=true
 * - NqMetricCard 不支持 onClick，通过包装层实现，不修改共享组件
 */
export function ClickableMetricCard({
    children,
    onClick,
    ariaLabel,
    testId,
    isActive,
}: {
    children: ReactNode;
    onClick: () => void;
    ariaLabel: string;
    testId: string;
    isActive: boolean;
}) {
    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === 'Enter' || e.key === ' ') onClick();
    };
    return (
        <div
            role="button"
            tabIndex={0}
            aria-label={ariaLabel}
            aria-pressed={isActive}
            data-testid={testId}
            onClick={onClick}
            onKeyDown={handleKeyDown}
            style={{
                cursor: 'pointer',
                borderRadius: 'var(--nq-radius-lg)',
                outline: isActive
                    ? '2px solid var(--nq-color-primary)'
                    : '2px solid transparent',
                outlineOffset: '2px',
            }}
        >
            {children}
        </div>
    );
}
