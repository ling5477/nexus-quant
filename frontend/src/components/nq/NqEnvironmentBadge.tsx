import {Tag} from 'antd';

import {nqColor} from '@/theme/tokens';

/**
 * NqEnvironmentBadge — 交易环境标识。
 *
 * 环境语义（Design System v1）：SIM/PAPER=蓝，DEMO=紫，LIVE=红色强警示。
 * 关键约束：
 * 1) 渲染文本保持原始环境值，便于审计与 E2E 断言；
 * 2) LIVE 仅是视觉强警示；当前阶段 LIVE 能力保持 disabled，本组件不承载任何开关行为。
 */
interface NqEnvironmentBadgeProps {
    env: string | null | undefined;
}

export function NqEnvironmentBadge({env}: NqEnvironmentBadgeProps) {
    if (!env) {
        return <Tag>-</Tag>;
    }

    const normalized = env.toUpperCase();

    if (normalized === 'LIVE') {
        return (
            <Tag color={nqColor.live} style={{fontWeight: 700}}>
                {env}
            </Tag>
        );
    }

    if (normalized === 'DEMO') {
        return <Tag color={nqColor.demo}>{env}</Tag>;
    }

    if (normalized === 'SIM' || normalized === 'PAPER') {
        return <Tag color={nqColor.paper}>{env}</Tag>;
    }

    return <Tag>{env}</Tag>;
}
