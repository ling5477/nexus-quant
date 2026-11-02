import {Card, Space, Spin, Typography} from 'antd';

interface AppLoadingScreenProps {
    message: string;
    detail?: string;
}

export function AppLoadingScreen({message, detail}: AppLoadingScreenProps) {
    return (
        <div className="loading-screen">
            <Card className="loading-screen__panel page-card" variant="borderless">
                <Space direction="vertical" size={12}>
                    <Spin size="large"/>
                    <Typography.Title level={4} style={{margin: 0}}>
                        {message}
                    </Typography.Title>
                    {detail ? (
                        <Typography.Text type="secondary">{detail}</Typography.Text>
                    ) : null}
                </Space>
            </Card>
        </div>
    );
}
