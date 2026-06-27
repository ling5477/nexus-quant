import {Alert, Button, Card, Space, Tag, Typography} from 'antd';
import {useNavigate} from 'react-router-dom';

interface PaperTradingPlaceholderPageProps {
    title: string;
}

/**
 * PaperTradingPlaceholderPage 是 K5-B 的静态占位页。
 *
 * Why:
 * Portfolio / Diagnostics / Reviews 的真实业务面板仍保留在 Runs 兼容页，避免本轮改变既有查询、缓存、
 * selectedRow / factTab / focusRunId 状态和 mutation 行为。placeholder 不调用任何 API/query，只提示 K5-C
 * 后续迁移路径，并重复展示 Paper-only 安全边界。
 */
export function PaperTradingPlaceholderPage({title}: PaperTradingPlaceholderPageProps) {
    const navigate = useNavigate();

    return (
        <Card className="page-section" variant="borderless">
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <Space direction="vertical" size={4}>
                    <Typography.Title level={4} style={{margin: 0}}>
                        {title}
                    </Typography.Title>
                    <Typography.Paragraph type="secondary" style={{margin: 0}}>
                        该模块将在 K5-C 迁移到当前子路由，当前完整视图仍在 Runs 兼容页可用。
                    </Typography.Paragraph>
                </Space>

                <Space size={6} wrap>
                    <Tag color="blue">SIM/Paper only</Tag>
                    <Tag color="red">LIVE 未开启</Tag>
                    <Tag color="default">不接真实交易所</Tag>
                    <Tag color="default">不构成投资建议</Tag>
                </Space>

                <Alert
                    type="info"
                    showIcon
                    message="Paper-only placeholder"
                    description="本页仅为 K5-B 子路由壳，不读取 credential、不访问真实交易所、不新增查询，也不构成投资建议。"
                />

                <div>
                    <Button type="primary" onClick={() => navigate('/paper-trading/runs')}>
                        返回 Runs
                    </Button>
                </div>
            </Space>
        </Card>
    );
}
