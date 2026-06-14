import {LockOutlined, LoginOutlined, SafetyCertificateOutlined, UserOutlined} from '@ant-design/icons';
import {Alert, Button, Form, Input} from 'antd';
import {useMutation} from '@tanstack/react-query';
import {startTransition} from 'react';
import {Navigate, useNavigate, useSearchParams} from 'react-router-dom';

import {authApi} from '@/api/auth';
import {AppLoadingScreen} from '@/components/app/AppLoadingScreen';
import {StandaloneSurface} from '@/components/standalone/StandaloneSurface';
import {selectIsAuthenticated, useAuthStore} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';

import './LoginPage.css';

interface LoginFormValues {
    username: string;
    password: string;
}

/**
 * 左区叙事只允许四类信息:系统是什么、能做什么、风控/审计边界、为什么可信。
 * 不出现 Gate 名称、里程碑、DEV/PAPER/LOCAL 等交付语义(降到 footer 极小号元信息)。
 */
const CAPABILITIES: string[] = [
    '策略研究与回测',
    '模拟交易(Paper)',
    '风控前置拦截',
    '全链路审计追踪',
];

/**
 * LoginPage — NQ 控制台唯一登录入口(v2)。
 *
 * 职责:在 AppShell 之外提供居中、平衡的双区登录;左区叙事产品定位与边界,右区认证卡片。
 * Why:登录页是进入受保护控制台前的第一层确认,需说明系统定位与风控/审计边界,而非堆交付语义。
 * How:只重做展示层与文案;认证仍调用既有 `authApi.login`,成功后写入既有 store 并按 redirect 跳转。
 * 边界:不展示任何默认凭证/明文,不新增认证协议,不改鉴权逻辑,不开启 LIVE/AI/DH。
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
                // 登录成功后写入既有 session;currentUser 真源仍由 /auth/me 查询补全,这里不塞临时对象。
                setSession(payload);
                navigate(redirectTo, {replace: true});
            });
        },
    });

    if (bootstrapStatus === 'loading' && isAuthenticated) {
        return (
            <AppLoadingScreen
                message="正在进入控制台"
                detail="登录态已恢复,正在跳转到受保护区域。"
            />
        );
    }

    if (isAuthenticated) {
        return <Navigate to={redirectTo} replace/>;
    }

    return (
        <StandaloneSurface className="nq-login" ariaLabel="登录 NexusQuant 控制台">
            <div className="nq-login__inner">
                <ProductNarrative/>
                <LoginCard
                    loading={loginMutation.isPending}
                    error={loginMutation.error}
                    onSubmit={(values) => loginMutation.mutate({
                        // 自动填充/复制可能带入尾随空格,提交前 trim 减少误判;密码只用于本次提交。
                        username: values.username.trim(),
                        password: values.password.trim(),
                    })}
                />
            </div>
        </StandaloneSurface>
    );
}

/**
 * ProductNarrative — 左区产品叙事:系统是什么、能做什么、风控/审计边界、为什么可信。
 */
function ProductNarrative() {
    return (
        <section className="nq-login__narrative" aria-labelledby="nq-login-title">
            <div className="nq-login__brand-mark" aria-hidden="true">NQ</div>
            <h1 id="nq-login-title" className="nq-login__brand">NexusQuant</h1>
            <p className="nq-login__tagline">量化交易基础设施控制台</p>
            <p className="nq-login__lede">
                在一个受控控制台内完成策略研究、回测、模拟交易、风控与审计的闭环。
            </p>

            <ul className="nq-login__capabilities" aria-label="NexusQuant 能力">
                {CAPABILITIES.map((item) => (
                    <li className="nq-login__capability" key={item}>
                        <span className="nq-login__capability-dot" aria-hidden="true"/>
                        {item}
                    </li>
                ))}
            </ul>

            <p className="nq-login__promise">
                <SafetyCertificateOutlined aria-hidden="true"/>
                <span>默认不启用 LIVE 交易;每一笔交易动作都先过风控,并保留完整审计追踪。</span>
            </p>
        </section>
    );
}

interface LoginCardProps {
    loading: boolean;
    error: unknown;
    onSubmit: (values: LoginFormValues) => void;
}

/**
 * LoginCard — 右区认证卡片。只收集账号/密码并交给既有登录接口,不承载任何环境/权限开关。
 */
function LoginCard({loading, error, onSubmit}: LoginCardProps) {
    return (
        <section className="nq-login__auth" aria-label="登录控制台">
            <div className="nq-login__card">
                <h2 className="nq-login__card-title">登录控制台</h2>
                <p className="nq-login__card-caption">输入账号凭证进入受保护控制台</p>

                {error ? <LoginErrorNotice error={error}/> : null}

                <Form<LoginFormValues> layout="vertical" requiredMark={false} onFinish={onSubmit}>
                    <Form.Item
                        label="账号"
                        name="username"
                        rules={[{required: true, message: '请输入账号'}]}
                    >
                        <Input
                            prefix={<UserOutlined/>}
                            autoComplete="username"
                            placeholder="请输入账号"
                        />
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
                        icon={<LoginOutlined/>}
                        loading={loading}
                        block
                    >
                        登录
                    </Button>
                </Form>

                <Alert
                    className="nq-login__security"
                    type="info"
                    showIcon
                    icon={<SafetyCertificateOutlined/>}
                    message="安全边界"
                    description="本控制台默认不启用 LIVE 交易;所有交易相关操作都需经过风控、审计与环境隔离。"
                />

                <p className="nq-login__footer">受控环境 · 默认 PAPER · LIVE 已禁用</p>
            </div>
        </section>
    );
}

/**
 * LoginErrorNotice — 登录错误脱敏展示。
 * 只按 HTTP 状态粗分,不在未认证页面暴露 traceId / 内部 path / 异常类名等后端细节。
 */
function LoginErrorNotice({error}: {error: unknown}) {
    const appError = error as Partial<AppApiError>;
    let description = '登录请求未完成。请检查网络与输入后重试。';

    if (appError.status === 401 || appError.status === 403) {
        description = '账号或密码不正确,或该账号不被允许访问控制台。';
    } else if (typeof appError.status === 'number' && appError.status >= 500) {
        description = '认证服务暂时不可用。请稍后重试,或联系运维。';
    }

    return (
        <Alert
            className="nq-login__error"
            type="error"
            showIcon
            message="登录失败"
            description={description}
        />
    );
}
