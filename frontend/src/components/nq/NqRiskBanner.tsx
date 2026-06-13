import {Alert} from 'antd';
import type {ReactNode} from 'react';

/**
 * NqRiskBanner — 风险/安全状态横幅。
 *
 * 用于页面顶部回答“系统当前是否安全/为什么允许或禁止操作”。
 * 关键约束：风险、失败、拒绝、停机信息必须可见，不得为视觉效果隐藏；
 * level=danger 用于熔断/紧急停机/LIVE 强警示场景。
 */
interface NqRiskBannerProps {
    level: 'info' | 'success' | 'warning' | 'danger';
    message: ReactNode;
    description?: ReactNode;
    action?: ReactNode;
}

export function NqRiskBanner({level, message, description, action}: NqRiskBannerProps) {
    return (
        <Alert
            type={level === 'danger' ? 'error' : level}
            showIcon
            message={message}
            description={description}
            action={action}
        />
    );
}
