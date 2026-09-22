import {t} from '@/i18n';
import {useTranslation} from 'react-i18next';
import {Button} from 'antd';
import {useNavigate, useSearchParams} from 'react-router-dom';

import {ExceptionView, type ExceptionTone} from '@/components/standalone/ExceptionView';
import {StandaloneSurface} from '@/components/standalone/StandaloneSurface';

type AuthFailureReason = 'session_expired' | 'identity_failed' | 'env_not_allowed';

interface AuthFailureCopy {
    tone: ExceptionTone;
    kicker: string;
    title: string;
    description: string;
    nextStep: string;
}

/**
 * 鉴权失败的三种情形各自给出原因与下一步,避免笼统的"登录失效"。
 * 由 query ?reason= 驱动展示,不读写鉴权状态、不改 RequireAuth 逻辑。
 */
const getReasonCopy = (): Record<AuthFailureReason, AuthFailureCopy> => ({
    session_expired: {
        tone: 'warning',
        kicker: t('exception.sessionKicker'),
        title: t('exception.sessionTitle'),
        description: t('exception.sessionDescription'),
        nextStep: t('exception.sessionNext'),
    },
    identity_failed: {
        tone: 'danger',
        kicker: t('exception.identityKicker'),
        title: t('exception.identityTitle'),
        description: t('exception.identityDescription'),
        nextStep: t('exception.identityNext'),
    },
    env_not_allowed: {
        tone: 'warning',
        kicker: t('exception.environmentKicker'),
        title: t('exception.environmentTitle'),
        description: t('exception.environmentDescription'),
        nextStep: t('exception.environmentNext'),
    },
});

function resolveReason(value: string | null): AuthFailureReason {
    if (value === 'identity_failed' || value === 'env_not_allowed') {
        return value;
    }

    return 'session_expired';
}

/**
 * AuthFailurePage — 鉴权失败异常页(v2)。
 * 区分会话过期 / 身份校验失败 / 环境不允许访问;只做展示与跳转,不改鉴权逻辑。
 */
export function AuthFailurePage() {
    useTranslation();
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const copy = getReasonCopy()[resolveReason(searchParams.get('reason'))];

    return (
        <StandaloneSurface ariaLabel={t('exception.authFailure')}>
            <ExceptionView
                tone={copy.tone}
                kicker={copy.kicker}
                title={copy.title}
                description={copy.description}
                nextSteps={<p style={{margin: 0}}>{copy.nextStep}</p>}
                actions={
                    <>
                        <Button type="primary" onClick={() => navigate('/login', {replace: true})}>{t('exception.signInAgain')}</Button>
                        <Button onClick={() => navigate('/login')}>{t('exception.backToLogin')}</Button>
                    </>
                }
            />
        </StandaloneSurface>
    );
}
