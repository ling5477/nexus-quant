import {t} from '@/i18n';
import {useTranslation} from 'react-i18next';
import {Button} from 'antd';
import {useNavigate, useSearchParams} from 'react-router-dom';

import {ExceptionView} from '@/components/standalone/ExceptionView';
import {StandaloneSurface} from '@/components/standalone/StandaloneSurface';

/**
 * ForbiddenPage — 无权限异常页(v2)。
 * 说明缺少哪个角色、如何申请;只做展示与跳转,不在前端绕过任何后端权限校验。
 */
export function ForbiddenPage() {
    useTranslation();
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    // 缺失角色由调用方通过 ?role= 注入(后端权限错误的可读映射),缺省给通用文案。
    const missingRole = searchParams.get('role')?.trim() || t('exception.unspecified');

    return (
        <StandaloneSurface ariaLabel={t('exception.forbidden')}>
            <ExceptionView
                tone="warning"
                code="403"
                kicker={t('exception.forbidden')}
                title={t('exception.forbiddenTitle')}
                description={t('exception.forbiddenDescription')}
                meta={[{label: t('exception.missingRole'), value: missingRole}]}
                nextSteps={
                    <p style={{margin: 0}}>{t('exception.requestRole')}</p>
                }
                actions={
                    <>
                        <Button type="primary" onClick={() => navigate('/')}>{t('exception.console')}</Button>
                        <Button onClick={() => navigate('/login')}>{t('exception.switchAccount')}</Button>
                    </>
                }
            />
        </StandaloneSurface>
    );
}
