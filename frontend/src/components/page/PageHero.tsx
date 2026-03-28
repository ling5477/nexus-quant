import {Alert, Space, Tag, Typography} from 'antd';

interface PageHeroProps {
    title: string;
    description: string;
    badge?: string;
    tip?: string;
}

export function PageHero({title, description, badge = 'RC1', tip}: PageHeroProps) {
    return (
        <div className="page-hero">
            <Space align="start" size={12} wrap>
                <div>
                    <Typography.Title level={2} style={{margin: 0}}>
                        {title}
                    </Typography.Title>
                    <Typography.Paragraph type="secondary" style={{margin: '8px 0 0'}}>
                        {description}
                    </Typography.Paragraph>
                </div>
                <Tag color="blue">{badge}</Tag>
            </Space>
            {tip ? (
                <Alert
                    type="info"
                    showIcon
                    message={tip}
                />
            ) : null}
        </div>
    );
}
