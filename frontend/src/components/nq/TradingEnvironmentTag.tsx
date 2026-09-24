import {Tag} from 'antd';

import {nqColor} from '@/theme/tokens';

/**
 * TradingEnvironmentTag — 应用业务表格与详情使用的原始交易环境标识。
 *
 * 环境语义（Design System v1）：SIM/PAPER=蓝，DEMO=紫，LIVE=红色强警示。
 * 关键约束：
 * 1) 渲染文本保持原始环境值，便于审计与 E2E 断言；
 * 2) LIVE 仅是视觉强警示；当前阶段 LIVE 能力保持 disabled，本组件不承载任何开关行为。
 * 3) 保留 SIM、未知值与空值的 AntD Tag 回退合同；设计系统的 EnvironmentBadge
 *    只接受受控枚举，服务于外壳和样例展示。
 */
interface TradingEnvironmentTagProps {
    env: string | null | undefined;
}

export function TradingEnvironmentTag({env}: TradingEnvironmentTagProps) {
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
