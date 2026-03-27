import {LockOutlined, UserOutlined} from '@ant-design/icons';
import {Alert, Button, Card, Form, Input, Space, Typography} from 'antd';
import {useMutation, useQueryClient} from '@tanstack/react-query';
import {startTransition} from 'react';
import {Navigate, useNavigate, useSearchParams} from 'react-router-dom';

import {authApi} from '@/api/auth';
import {authQueryKeys} from '@/api/query-keys';
import {formatApiError} from '@/api/errors';
import {AppLoadingScreen} from '@/components/app/AppLoadingScreen';
import {selectIsAuthenticated, useAuthStore} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';

interface LoginFormValues {
    username: string;
    password: string;
}

export function LoginPage() {
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const [searchParams] = useSearchParams();
    const isAuthenticated = useAuthStore(selectIsAuthenticated);
    const bootstrapStatus = useAuthStore((state) => state.bootstrapStatus);
    const setSession = useAuthStore((state) => state.setSession);

    const redirect = searchParams.get('redirect');
    const redirectTo = redirect && redirect.startsWith('/') ? redirect : '/dashboard';

    const loginMutation = useMutation({
        mutationFn: authApi.login,
        onSuccess: (payload) => {
            startTransition(() => {
                setSession(payload);
                queryClient.setQueryData(authQueryKeys.currentUser(payload.accessToken), {
                    username: payload.username,
                    roles: payload.roles,
                    authenticated: true,
                });
                navigate(redirectTo, {replace: true});
            });
        },
    });

    if (bootstrapStatus === 'loading' && isAuthenticated) {
        return (
            <AppLoadingScreen
                message="正在跳转控制台"
                detail="登录态已经恢复，页面即将进入受保护区域。"
            />
        );
    }

    if (isAuthenticated) {
        return <Navigate to={redirectTo} replace/>;
    }

    return (
        <div className="login-page">
            <section className="login-page__hero">
                <Space direction="vertical" size={18}>
                    <Typography.Text strong style={{color: '#1f5fb8', letterSpacing: 1.2}}>
                        NEXUSQUANT GATEG
                    </Typography.Text>
                    <Typography.Title style={{margin: 0}}>
                        正式控制台骨架已启动
                    </Typography.Title>
                    <Typography.Paragraph style={{margin: 0, maxWidth: 560}}>
                        本批建立登录鉴权、基础布局、路由、统一请求封装与 Playwright 冒烟。
                        后续 GateG-3/4/5 将在此骨架上继续接入策略、研究、回测和交易验证页面。
                    </Typography.Paragraph>
                </Space>
                <Card className="page-card" bordered={false}>
                    <Space direction="vertical" size={8}>
                        <Typography.Title level={4} style={{margin: 0}}>
                            本地联调默认口径
                        </Typography.Title>
                        <Typography.Paragraph style={{margin: 0}}>
                            后端端口：
                            {' '}
                            <Typography.Text code>18888</Typography.Text>
                            {' '}
                            （
                            <Typography.Text
                                code>backend/nq-app/src/main/resources/application-local.yml</Typography.Text>
                            ）
                        </Typography.Paragraph>
                        <Typography.Paragraph style={{margin: 0}}>
                            默认账号：
                            {' '}
                            <Typography.Text code>admin / ChangeMe123!</Typography.Text>
                        </Typography.Paragraph>
                        <Typography.Paragraph style={{margin: 0}}>
                            认证协议：
                            {' '}
                            <Typography.Text code>POST /api/auth/login</Typography.Text>
                            {' + '}
                            <Typography.Text code>GET /api/auth/me</Typography.Text>
                            {' + '}
                            <Typography.Text code>Authorization: Bearer &lt;token&gt;</Typography.Text>
                        </Typography.Paragraph>
                    </Space>
                </Card>
            </section>
            <section className="login-page__panel">
                <Card className="login-page__card" bordered={false}>
                    <Space direction="vertical" size={20} style={{width: '100%'}}>
                        <div>
                            <Typography.Title level={3} style={{marginBottom: 8}}>
                                登录控制台
                            </Typography.Title>
                            <Typography.Paragraph type="secondary" style={{marginBottom: 0}}>
                                使用现有本地账户完成认证并进入正式控制台。
                            </Typography.Paragraph>
                        </div>
                        {loginMutation.error ? (
                            <Alert
                                type="error"
                                showIcon
                                message="登录失败"
                                description={formatApiError(loginMutation.error as AppApiError)}
                            />
                        ) : null}
                        <Form<LoginFormValues>
                            layout="vertical"
                            initialValues={{
                                username: 'admin',
                                password: 'ChangeMe123!',
                            }}
                            onFinish={(values) => loginMutation.mutate({
                                // Why:
                                // Playwright 和部分浏览器自动填充链路会把尾随空格一并带进提交体，
                                // 本地默认账号是固定值时会被放大成稳定 401。登录表单在提交前统一 trim，
                                // 既能消除这类输入噪音，也不会改变正式认证协议。
                                username: values.username.trim(),
                                password: values.password.trim(),
                            })}
                        >
                            <Form.Item
                                label="用户名"
                                name="username"
                                rules={[{required: true, message: '请输入用户名'}]}
                            >
                                <Input prefix={<UserOutlined/>} autoComplete="username" placeholder="请输入用户名"/>
                            </Form.Item>
                            <Form.Item
                                label="密码"
                                name="password"
                                rules={[{required: true, message: '请输入密码'}]}
                            >
                                <Input.Password
                                    prefix={<LockOutlined/>}
                                    autoComplete="current-password"
                                    placeholder="请输入密码"
                                />
                            </Form.Item>
                            <Button
                                type="primary"
                                htmlType="submit"
                                size="large"
                                loading={loginMutation.isPending}
                                block
                            >
                                登录并进入控制台
                            </Button>
                        </Form>
                    </Space>
                </Card>
            </section>
        </div>
    );
}
