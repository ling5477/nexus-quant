import {AuditOutlined, ExperimentOutlined, LineChartOutlined, LockOutlined, LoginOutlined, SafetyCertificateOutlined, UserOutlined} from '@ant-design/icons';
import {Alert, Button, Form, Input} from 'antd';
import {useMutation} from '@tanstack/react-query';
import {startTransition, useState} from 'react';
import {Navigate, useNavigate, useSearchParams} from 'react-router-dom';

import {authApi} from '@/api/auth';
import {AppLoadingScreen} from '@/components/app/AppLoadingScreen';
import {StandaloneSurface} from '@/components/standalone/StandaloneSurface';
import {selectIsAuthenticated, useAuthStore} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';
import {useTranslation} from 'react-i18next';
import {LanguageSelect} from '@/i18n/LanguageSelect';
import {ApiErrorNotice} from '@/errors/ApiErrorNotice';
import {readAuthError, clearAuthError} from '@/errors/auth-error';
import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import {BrandLockup} from '@/nq-design-system/brand/BrandLockup';

import './LoginPage.css';

interface LoginFormValues {
    username: string;
    password: string;
}

/**
 * 左区叙事只允许四类信息:系统是什么、能做什么、风控/审计边界、为什么可信。
 * 不出现 Gate 名称、里程碑、DEV/PAPER/LOCAL 等交付语义(降到 footer 极小号元信息)。
 */
const CAPABILITIES = [
    {key: 'auth.research', icon: <ExperimentOutlined/>},
    {key: 'auth.paper', icon: <LineChartOutlined/>},
    {key: 'auth.risk', icon: <SafetyCertificateOutlined/>},
    {key: 'auth.audit', icon: <AuditOutlined/>},
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
    const {t} = useTranslation();
    const [redirectError, setRedirectError] = useState(readAuthError);
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
            clearAuthError();
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
                message={t('auth.entering')}
                detail={t('auth.restored')}
            />
        );
    }

    if (isAuthenticated) {
        return <Navigate to={redirectTo} replace/>;
    }

    return (
        <StandaloneSurface className="nq-login" ariaLabel={t('auth.aria')}>
            <header className="nq-login__masthead">
                <h1 className="nq-login__product-title" aria-label="NexusQuant">
                    <BrandLockup caption={t('shell.subtitle')}/>
                </h1>
                <LanguageSelect/>
            </header>
            <div className="nq-login__inner">
                <ProductNarrative/>
                <LoginCard
                    loading={loginMutation.isPending}
                    error={loginMutation.error ?? redirectError}
                    onSubmit={(values) => {
                        clearAuthError();
                        setRedirectError(null);
                        loginMutation.mutate({
                        // 自动填充/复制可能带入尾随空格,提交前 trim 减少误判;密码只用于本次提交。
                        username: values.username.trim(),
                        password: values.password.trim(),
                        });
                    }}
                />
            </div>
            <footer className="nq-login__page-footer">
                <span>NexusQuant</span>
                <span>{t('auth.footer')}</span>
            </footer>
        </StandaloneSurface>
    );
}

/**
 * ProductNarrative — 左区产品叙事:系统是什么、能做什么、风控/审计边界、为什么可信。
 */
function ProductNarrative() {
    const {t} = useTranslation();
    return (
        <section className="nq-login__narrative" aria-labelledby="nq-login-title">
            <p className="nq-login__tagline">{t('auth.tagline')}</p>
            <h2 id="nq-login-title" className="nq-login__headline">
                {t('auth.heroLead')}<br/><span>{t('auth.heroAccent')}</span>
            </h2>
            <p className="nq-login__lede">
                {t('auth.lede')}
            </p>

            <ul className="nq-login__capabilities" aria-label={t('auth.capabilities')}>
                {CAPABILITIES.map(({key, icon}) => (
                    <li className="nq-login__capability" key={key}>
                        <span className="nq-login__capability-icon" aria-hidden="true">{icon}</span>
                        <span>{t(key)}</span>
                    </li>
                ))}
            </ul>

            <p className="nq-login__promise">
                <SafetyCertificateOutlined aria-hidden="true"/>
                <span>{t('auth.promise')}</span>
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
    const {t} = useTranslation();
    const [form] = useLocalizedForm<LoginFormValues>();
    return (
        <section className="nq-login__auth" aria-label={t('auth.title')}>
            <div className="nq-login__card">
                <div className="nq-login__card-brand"><BrandLockup caption={t('shell.console')}/></div>
                <h2 className="nq-login__card-title">{t('auth.title')}</h2>
                <p className="nq-login__card-caption">{t('auth.caption')}</p>

                {error ? <LoginErrorNotice error={error}/> : null}

                <Form<LoginFormValues> form={form} layout="vertical" requiredMark={false} onFinish={onSubmit}>
                    <Form.Item
                        label={t('auth.username')}
                        name="username"
                        rules={[{required: true, message: t('auth.usernameRequired')}]}
                    >
                        <Input
                            size="large"
                            prefix={<UserOutlined/>}
                            autoComplete="username"
                            placeholder={t('auth.usernameRequired')}
                        />
                    </Form.Item>
                    <Form.Item
                        label={t('auth.password')}
                        name="password"
                        rules={[{required: true, message: t('auth.passwordRequired')}]}
                    >
                        <Input.Password
                            size="large"
                            prefix={<LockOutlined/>}
                            autoComplete="current-password"
                            placeholder={t('auth.passwordRequired')}
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
                        {t('auth.submit')}
                    </Button>
                </Form>

                <Alert
                    className="nq-login__security"
                    type="info"
                    showIcon
                    icon={<SafetyCertificateOutlined/>}
                    message={t('auth.security')}
                    description={t('auth.securityDescription')}
                />

            </div>
        </section>
    );
}

/**
 * LoginErrorNotice — 登录错误脱敏展示。
 * 使用统一 catalog，不泄露内部 path 或后端消息；traceId 保留用于支持定位。
 */
function LoginErrorNotice({error}: {error: unknown}) {
    return <ApiErrorNotice className="nq-login__error" error={error as Partial<AppApiError>}
        presentation="AUTH_REDIRECT_OR_PROMPT"/>;
}
