import {t} from '@/i18n';
import {useTranslation} from 'react-i18next';
import {Button} from 'antd';
import {useNavigate} from 'react-router-dom';

import {ExceptionView} from '@/components/standalone/ExceptionView';
import {StandaloneSurface} from '@/components/standalone/StandaloneSurface';

/**
 * NotFoundPage — 404 异常页(v2)。
 * 用统一异常表现层替代 AntD 默认 404 模板,与其余异常页同源。
 */
export function NotFoundPage() {
    useTranslation();
    const navigate = useNavigate();

    return (
        <StandaloneSurface ariaLabel={t('exception.notFound')}>
            <ExceptionView
                tone="neutral"
                code="404"
                kicker={t('exception.notFound')}
                title={t('exception.notFoundTitle')}
                description={t('exception.notFoundDescription')}
                actions={
                    <Button type="primary" onClick={() => navigate('/dashboard')}>{t('exception.console')}</Button>
                }
            />
        </StandaloneSurface>
    );
}
