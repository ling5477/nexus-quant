import {t} from '@/i18n';
import {useTranslation} from 'react-i18next';
import {Button} from 'antd';
import {useMemo} from 'react';
import {useNavigate, useSearchParams} from 'react-router-dom';

import {ExceptionView} from '@/components/standalone/ExceptionView';
import {StandaloneSurface} from '@/components/standalone/StandaloneSurface';

/**
 * SystemErrorPage — 系统错误异常页(v2)。
 * 包含 request id + 发生时间 + 返回入口,便于用户凭事实联系运维;不暴露异常栈/内部 path。
 */
export function SystemErrorPage() {
    useTranslation();
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();

    // request id 与时间在进入页面时固定一次,避免重渲染时跳动。
    const requestId = useMemo(
        () => searchParams.get('traceId')?.trim() || searchParams.get('requestId')?.trim() || t('exception.noTrace'),
        [searchParams, t],
    );
    const occurredAt = useMemo(() => new Date().toLocaleString('zh-CN', {hour12: false}), []);

    return (
        <StandaloneSurface ariaLabel={t('exception.system')}>
            <ExceptionView
                tone="danger"
                code="500"
                kicker={t('exception.system')}
                title={t('exception.systemTitle')}
                description={t('exception.systemDescription')}
                meta={[
                    {label: 'traceId', value: requestId},
                    {label: t('exception.occurred'), value: occurredAt},
                ]}
                nextSteps={<p style={{margin: 0}}>{t('exception.systemNext')}</p>}
                actions={
                    <>
                        <Button type="primary" onClick={() => navigate('/')}>{t('exception.console')}</Button>
                        <Button onClick={() => navigate(-1)}>{t('exception.previous')}</Button>
                    </>
                }
            />
        </StandaloneSurface>
    );
}
