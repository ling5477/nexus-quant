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
const REASON_COPY: Record<AuthFailureReason, AuthFailureCopy> = {
    session_expired: {
        tone: 'warning',
        kicker: '会话已过期',
        title: '登录态已过期',
        description: '登录会话已超时,出于安全控制台已退出当前身份。',
        nextStep: '重新登录后会回到你刚才访问的页面。',
    },
    identity_failed: {
        tone: 'danger',
        kicker: '身份校验失败',
        title: '身份校验未通过',
        description: '无法确认当前身份,可能是登录凭证损坏或已在别处注销。',
        nextStep: '请重新登录以重建受信任的登录态。',
    },
    env_not_allowed: {
        tone: 'warning',
        kicker: '环境不允许访问',
        title: '当前环境不允许访问',
        description: '当前账号在此环境下没有访问权限(控制台默认仅开放受控 PAPER 环境)。',
        nextStep: '请联系运维确认账号可用的环境与权限后再试。',
    },
};

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
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const copy = REASON_COPY[resolveReason(searchParams.get('reason'))];

    return (
        <StandaloneSurface ariaLabel="鉴权失败">
            <ExceptionView
                tone={copy.tone}
                kicker={copy.kicker}
                title={copy.title}
                description={copy.description}
                nextSteps={<p style={{margin: 0}}>{copy.nextStep}</p>}
                actions={
                    <>
                        <Button type="primary" onClick={() => navigate('/login', {replace: true})}>
                            重新登录
                        </Button>
                        <Button onClick={() => navigate('/login')}>返回登录页</Button>
                    </>
                }
            />
        </StandaloneSurface>
    );
}
