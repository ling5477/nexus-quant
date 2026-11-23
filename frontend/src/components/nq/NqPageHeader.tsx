import {Space, Tag, Typography} from 'antd';
import type {ReactNode} from 'react';

/**
 * NqPageHeader — 页面统一头部。
 *
 * 关键约束：
 * 1) 标题必须渲染为语义化 heading（Typography.Title），E2E 通过
 *    getByRole('heading', {name}) 定位页面，重构时不得改成普通文本；
 * 2) 高密度排版：标题用 level=3，描述为 secondary 小字号，不做营销风大标题。
 */
interface NqPageHeaderProps {
    title: string;
    description?: string;
    /** 字符串渲染为 Tag，复杂内容（如环境徽标）传 ReactNode。 */
    badge?: ReactNode;
    /** 右侧操作区（按钮组等）。 */
    extra?: ReactNode;
    /** 头部下方的提示条（如风险提示），由调用方传入完整组件。 */
    tip?: ReactNode;
}

export function NqPageHeader({title, description, badge, extra, tip}: NqPageHeaderProps) {
    return (
        <div className="nq-page-header">
            <div className="nq-page-header__row">
                <Space align="center" size={12} wrap>
                    <Typography.Title level={3} style={{margin: 0}}>
                        {title}
                    </Typography.Title>
                    {typeof badge === 'string' ? <Tag color="processing">{badge}</Tag> : badge}
                </Space>
                {extra}
            </div>
            {description ? (
                <Typography.Paragraph type="secondary" style={{margin: 0}}>
                    {description}
                </Typography.Paragraph>
            ) : null}
            {tip}
        </div>
    );
}
