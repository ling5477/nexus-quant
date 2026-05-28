import {LockOutlined, UserOutlined} from '@ant-design/icons';
import {Alert, Button, Card, Form, Input, Typography} from 'antd';
import {useMutation} from '@tanstack/react-query';
import {startTransition} from 'react';
import {Navigate, useNavigate, useSearchParams} from 'react-router-dom';

import {authApi} from '@/api/auth';
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
                // Why:
                // 当前用户接口是默认账户上下文的唯一真源，登录成功后不能再把一个缺少 default 账户字段的
                // 临时对象塞进 currentUser query cache，否则后续页面会被 stale cache 挡住，误判成“当前没有默认账户”。
                setSession(payload);
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
                <Typography.Title style={{margin: 0}}>
                    NexusQuant 控制台
                </Typography.Title>
            </section>
            <section className="login-page__panel">
                <Card className="login-page__card" bordered={false}>
                    <div className="login-page__form">
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
                            onFinish={(values) => loginMutation.mutate({
                                // Why:
                                // 浏览器自动填充或复制粘贴可能带入尾随空格，登录前统一 trim 可以减少误判为
                                // 认证失败的输入噪音；密码仍只在本次提交中使用，不在页面或构建产物中提供默认值。
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
                                登录
                            </Button>
                        </Form>
                    </div>
                </Card>
            </section>
        </div>
    );
}
