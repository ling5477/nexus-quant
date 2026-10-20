import {Alert} from 'antd';
import type {ReactNode} from 'react';

/**
 * ApplicationRiskAlert — 应用业务页使用的 AntD 风险/安全状态横幅。
 *
 * 用于页面顶部回答“系统当前是否安全/为什么允许或禁止操作”。
 * 关键约束：风险、失败、拒绝、停机信息必须可见，不得为视觉效果隐藏；
 * level=danger 用于熔断/紧急停机/LIVE 强警示场景。
 * description 与 action 保留 AntD Alert 合同，且不提供关闭入口；设计系统的
 * RiskBanner 使用 severity/actions/onDismiss 与独立 ARIA 布局，服务于不同展示合同。
 */
interface ApplicationRiskAlertProps {
    level: 'info' | 'success' | 'warning' | 'danger';
    message: ReactNode;
    description?: ReactNode;
    action?: ReactNode;
}

export function ApplicationRiskAlert({level, message, description, action}: ApplicationRiskAlertProps) {
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
