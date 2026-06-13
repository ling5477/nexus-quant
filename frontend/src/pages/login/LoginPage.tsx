import {
    AuditOutlined,
    BarChartOutlined,
    ControlOutlined,
    ExperimentOutlined,
    LockOutlined,
    LoginOutlined,
    SafetyCertificateOutlined,
    UserOutlined,
} from '@ant-design/icons';
import {Alert, Button, Card, Form, Input, Typography} from 'antd';
import {useMutation} from '@tanstack/react-query';
import {startTransition, type ReactNode} from 'react';
import {Navigate, useNavigate, useSearchParams} from 'react-router-dom';

import {authApi} from '@/api/auth';
import {AppLoadingScreen} from '@/components/app/AppLoadingScreen';
import {NqEnvironmentBadge} from '@/components/nq/NqEnvironmentBadge';
import {NqStatusTag} from '@/components/nq/NqStatusTag';
import {selectIsAuthenticated, useAuthStore} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';

interface LoginFormValues {
    username: string;
    password: string;
}

interface AuthShellProps {
    children: ReactNode;
}

interface ProductCapability {
    label: string;
    icon: ReactNode;
}

const PRODUCT_CAPABILITIES: ProductCapability[] = [
    {label: 'Strategy Research', icon: <ExperimentOutlined/>},
    {label: 'Backtest', icon: <BarChartOutlined/>},
    {label: 'Paper Trading', icon: <ControlOutlined/>},
    {label: 'Risk Control', icon: <SafetyCertificateOutlined/>},
    {label: 'Audit Trail', icon: <AuditOutlined/>},
];

const POSTURE_BADGES = [
    {status: 'PAPER ONLY', tone: 'info' as const},
    {status: 'LIVE DISABLED', tone: 'danger' as const},
    {status: 'GateJ completed', tone: 'success' as const},
    {status: 'Next: GateK-PLAN', tone: 'warning' as const},
    {status: 'Audit enabled', tone: 'success' as const},
    {status: 'Risk first', tone: 'warning' as const},
];

/**
 * LoginPage
 *
 * 职责：提供 NQ 控制台唯一登录入口，同时在登录前显式展示 Gate、PAPER/LIVE 隔离与审计边界。
 * Why：登录页是用户进入受保护控制台前的第一层风险提示，必须说明当前只允许受控 PAPER 场景；
 * How：只重组展示层和表单外壳，认证请求仍调用既有 `authApi.login`，成功后仍写入原 store 并按 redirect 跳转。
 * 边界：不展示默认凭证，不修改 token 存储，不新增认证协议，不开启 LIVE / AI / DH。
 */
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
        <AuthShell>
            <ProductIdentityPanel/>
            <section className="login-page__panel" aria-label="Console sign in">
                <LoginCard
                    loading={loginMutation.isPending}
                    error={loginMutation.error}
                    onSubmit={(values) => loginMutation.mutate({
                        // Why:
                        // 浏览器自动填充或复制粘贴可能带入尾随空格，登录前统一 trim 可以减少误判为
                        // 认证失败的输入噪音；密码仍只在本次提交中使用，不在页面或构建产物中提供默认值。
                        username: values.username.trim(),
                        password: values.password.trim(),
                    })}
                />
            </section>
        </AuthShell>
    );
}

/**
 * AuthShell — 登录页全屏布局外壳。
 *
 * Why：登录入口不应复用业务页的侧边栏/顶部栏，否则未认证用户会看到受保护控制台框架；
 * 这里用独立 `main` 承载左右两栏，并通过 CSS token 控制深色终端背景和响应式折叠。
 */
function AuthShell({children}: AuthShellProps) {
    return (
        <main className="login-page">
            {children}
        </main>
    );
}

/**
 * ProductIdentityPanel — 产品识别与能力边界。
 *
 * Why：左侧不只展示项目名，而要让用户在登录前理解 NQ 是量化基础设施控制台，
 * 并看到 Strategy / Backtest / Paper / Risk / Audit 的受控工作流范围。
 */
function ProductIdentityPanel() {
    return (
        <section className="login-page__identity" aria-labelledby="login-page-title">
            <div className="login-page__identity-main">
                <div className="login-page__brand-mark" aria-hidden="true">NQ</div>
                <Typography.Title id="login-page-title" className="login-page__title" level={1}>
                    NexusQuant
                </Typography.Title>
                <Typography.Text className="login-page__subtitle">
                    Quant Trading Infrastructure Console
                </Typography.Text>
                <Typography.Paragraph className="login-page__description">
                    Strategy research, backtest, paper trading, risk control and audit workflow in one controlled console.
                </Typography.Paragraph>
            </div>

            <nav className="login-page__capabilities" aria-label="NexusQuant console workflows">
                {PRODUCT_CAPABILITIES.map((item) => (
                    <span className="login-page__capability" key={item.label}>
                        <span className="login-page__capability-icon" aria-hidden="true">{item.icon}</span>
                        <span>{item.label}</span>
                    </span>
                ))}
            </nav>

            <SystemPosturePanel/>
        </section>
    );
}

/**
 * SystemPosturePanel — 当前 Gate 与交易环境姿态。
 *
 * Why：NQ 当前是 GateJ completed / GateK-PLAN 前置阶段，LIVE、AI、DH 均未启动；
 * 登录页必须把这些边界显式化，避免用户把控制台入口理解为真实交易或 AI 自动交易入口。
 */
function SystemPosturePanel() {
    return (
        <aside className="login-page__posture" aria-label="System posture">
            <div className="login-page__posture-heading">
                <SafetyCertificateOutlined aria-hidden="true"/>
                <span>Controlled console posture</span>
            </div>
            <div className="login-page__posture-badges">
                {POSTURE_BADGES.map((item) => (
                    <NqStatusTag key={item.status} status={item.status} tone={item.tone}/>
                ))}
            </div>
        </aside>
    );
}

interface LoginCardProps {
    loading: boolean;
    error: unknown;
    onSubmit: (values: LoginFormValues) => void;
}

/**
 * LoginCard — 认证表单外壳。
 *
 * Why：表单只负责收集用户名和密码并交给既有登录接口；环境 Badge 与安全提示用于登录前确认
 * DEV/PAPER/LOCAL 与 LIVE disabled 边界，不承担任何开关或权限变更。
 */
function LoginCard({loading, error, onSubmit}: LoginCardProps) {
    return (
        <Card className="login-page__card" variant="borderless">
            <div className="login-page__card-header">
                <div>
                    <Typography.Title className="login-page__card-title" level={2}>
                        Sign in to Console
                    </Typography.Title>
                    <Typography.Text className="login-page__card-caption">
                        DEV / PAPER / LOCAL controlled access
                    </Typography.Text>
                </div>
                <div className="login-page__environment-badges" aria-label="Environment badges">
                    <NqStatusTag status="DEV" tone="neutral"/>
                    <NqEnvironmentBadge env="PAPER"/>
                    <NqStatusTag status="LOCAL" tone="neutral"/>
                </div>
            </div>

            <div className="login-page__form">
                {error ? <LoginErrorNotice error={error}/> : null}
                <Form<LoginFormValues> layout="vertical" requiredMark={false} onFinish={onSubmit}>
                    <Form.Item
                        label="Username"
                        name="username"
                        rules={[{required: true, message: 'Enter username'}]}
                    >
                        <Input
                            prefix={<UserOutlined/>}
                            autoComplete="username"
                            placeholder="Enter username"
                        />
                    </Form.Item>
                    <Form.Item
                        label="Password"
                        name="password"
                        rules={[{required: true, message: 'Enter password'}]}
                    >
                        <Input.Password
                            prefix={<LockOutlined/>}
                            autoComplete="current-password"
                            placeholder="Enter password"
                        />
                    </Form.Item>
                    <Button
                        className="login-page__submit"
                        type="primary"
                        htmlType="submit"
                        size="large"
                        icon={<LoginOutlined/>}
                        loading={loading}
                        block
                    >
                        Sign in
                    </Button>
                </Form>
                <SecurityNotice/>
            </div>
        </Card>
    );
}

interface LoginErrorNoticeProps {
    error: unknown;
}

/**
 * LoginErrorNotice — 登录错误脱敏展示。
 *
 * Why：登录失败需要清楚告诉用户下一步，但不能把 traceId、内部 path、异常类名或后端细节暴露在未认证页面；
 * 因此仅按 HTTP 状态粗分为凭证/权限问题、认证服务问题和网络问题。
 */
function LoginErrorNotice({error}: LoginErrorNoticeProps) {
    const appError = error as Partial<AppApiError>;
    let description = 'Sign-in request did not complete. Check the input and network, then retry.';

    if (appError.status === 401 || appError.status === 403) {
        description = 'Username or password is invalid, or this account is not allowed to access the console.';
    } else if (typeof appError.status === 'number' && appError.status >= 500) {
        description = 'Authentication service is temporarily unavailable. Retry later or contact the operator.';
    }

    return (
        <Alert
            className="login-page__error"
            type="error"
            showIcon
            message="Sign-in failed"
            description={description}
        />
    );
}

/**
 * SecurityNotice — 登录前安全边界提示。
 *
 * Why：NQ 当前不默认启用 LIVE trading；任何交易相关动作必须经过风控、审计与环境隔离，
 * 登录页保留这条提示可以防止用户把入口误解为真实交易直连。
 */
function SecurityNotice() {
    return (
        <Alert
            className="login-page__security"
            type="info"
            showIcon
            icon={<SafetyCertificateOutlined/>}
            message="Security boundary"
            description="This console does not enable LIVE trading by default. All trading-related actions require risk control, audit and explicit environment isolation."
        />
    );
}
