import {
    ArrowRightOutlined,
    SafetyCertificateOutlined,
    ShareAltOutlined,
} from '@ant-design/icons';
import {Button, Card, Col, Descriptions, List, Row, Space, Tag, Typography} from 'antd';
import {useNavigate} from 'react-router-dom';

import {PageHero} from '@/components/page/PageHero';
import {appNavItems} from '@/router/navigation';
import {useAuthStore} from '@/store/auth-store';
import {appEnv} from '@/utils/env';

export function DashboardPage() {
    const navigate = useNavigate();
    const currentUser = useAuthStore((state) => state.currentUser);

    return (
        <Space direction="vertical" size={16} style={{display: 'flex'}}>
            <Card className="page-card" bordered={false}>
                <PageHero
                    title="控制台总览"
                    description="当前批次已经建立登录闭环、统一请求封装、鉴权守卫和页面路由。Dashboard 作为受保护入口，展示用户上下文与主要导航。"
                    badge="GateG-1 Ready"
                />
            </Card>
            <Row gutter={[16, 16]}>
                <Col xs={24} xl={12}>
                    <Card className="page-section" bordered={false} title="当前用户">
                        <Descriptions column={1} colon={false}>
                            <Descriptions.Item label="用户名">{currentUser?.username ?? '-'}</Descriptions.Item>
                            <Descriptions.Item label="角色">
                                <Space wrap>
                                    {currentUser?.roles.map((role) => (
                                        <Tag color="blue" key={role}>
                                            {role}
                                        </Tag>
                                    ))}
                                </Space>
                            </Descriptions.Item>
                            <Descriptions.Item label="环境">{appEnv.envLabel}</Descriptions.Item>
                            <Descriptions.Item label="Trace Header">X-Trace-Id</Descriptions.Item>
                        </Descriptions>
                    </Card>
                </Col>
                <Col xs={24} xl={12}>
                    <Card
                        className="page-section"
                        bordered={false}
                        title="控制台约束"
                        extra={<SafetyCertificateOutlined/>}
                    >
                        <List
                            dataSource={[
                                '统一认证：POST /api/auth/login + Bearer token + GET /api/auth/me',
                                '统一请求封装：所有 API 调用走 Axios instance',
                                '统一错误口径：401 回登录、403/500 统一通知',
                                '统一状态边界：服务端数据走 TanStack Query，鉴权态走 Zustand',
                            ]}
                            renderItem={(item) => <List.Item>{item}</List.Item>}
                        />
                    </Card>
                </Col>
                <Col span={24}>
                    <Card
                        className="page-section"
                        bordered={false}
                        title="业务入口"
                        extra={<ShareAltOutlined/>}
                    >
                        <Row gutter={[16, 16]}>
                            {appNavItems
                                .filter((item) => item.key !== 'dashboard')
                                .map((item) => (
                                    <Col xs={24} md={12} xl={8} key={item.key}>
                                        <Card hoverable onClick={() => navigate(item.path)}>
                                            <Space direction="vertical" size={10} style={{width: '100%'}}>
                                                <Space align="center" size={12}>
                                                    <span style={{fontSize: 18}}>{item.icon}</span>
                                                    <Typography.Title level={4} style={{margin: 0}}>
                                                        {item.label}
                                                    </Typography.Title>
                                                </Space>
                                                <Typography.Paragraph type="secondary" style={{margin: 0}}>
                                                    {item.description}
                                                </Typography.Paragraph>
                                                <Button type="link" icon={<ArrowRightOutlined/>}
                                                        style={{paddingInline: 0}}>
                                                    进入页面
                                                </Button>
                                            </Space>
                                        </Card>
                                    </Col>
                                ))}
                        </Row>
                    </Card>
                </Col>
            </Row>
        </Space>
    );
}
