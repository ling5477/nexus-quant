import {Alert, Button, Empty, Spin, Space, Typography} from 'antd';
import type {ReactNode} from 'react';

import {formatApiError} from '@/api/errors';
import type {AppApiError} from '@/types/api';

/**
 * Nq 状态组件族 — 空态 / 错误态 / 加载态统一表达。
 *
 * 关键约束：
 * 1) 空态文案由调用方传入并保持业务口径（E2E 依赖空态原文）；
 * 2) 错误态必须展示可解释的错误信息（含 traceId，由 formatApiError 统一格式化）；
 * 3) 不允许用空白或骨架屏掩盖失败。
 */

interface NqEmptyStateProps {
    description: ReactNode;
    children?: ReactNode;
}

export function NqEmptyState({description, children}: NqEmptyStateProps) {
    return (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={description}>
            {children}
        </Empty>
    );
}

interface NqErrorStateProps {
    title?: ReactNode;
    error?: AppApiError | null;
    description?: ReactNode;
    onRetry?: () => void;
}

export function NqErrorState({title = '查询失败', error, description, onRetry}: NqErrorStateProps) {
    return (
        <Alert
            type="error"
            showIcon
            message={title}
            description={description ?? (error ? formatApiError(error) : undefined)}
            action={onRetry ? (
                <Button size="small" onClick={onRetry}>
                    重试
                </Button>
            ) : undefined}
        />
    );
}

interface NqLoadingStateProps {
    message?: string;
}

export function NqLoadingState({message = '加载中...'}: NqLoadingStateProps) {
    return (
        <Space size={8} style={{padding: '8px 0'}}>
            <Spin size="small"/>
            <Typography.Text type="secondary">{message}</Typography.Text>
        </Space>
    );
}
