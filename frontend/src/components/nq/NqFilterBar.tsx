import {Card} from 'antd';
import type {ReactNode} from 'react';

/**
 * NqFilterBar — 查询区统一容器。
 *
 * 关键约束：
 * 1) 标题默认“查询区”，保持各列表页查询入口口径一致（E2E 依赖按钮文案由调用方提供）；
 * 2) 只负责布局容器，不接管表单状态；表单仍由页面持有（AntD Form）。
 */
interface NqFilterBarProps {
    title?: string;
    /** 右上角操作区（查询/重置/新建按钮组）。 */
    actions?: ReactNode;
    children: ReactNode;
}

export function NqFilterBar({title = '查询区', actions, children}: NqFilterBarProps) {
    return (
        <Card className="page-section" bordered={false} title={title} extra={actions}>
            {children}
        </Card>
    );
}
