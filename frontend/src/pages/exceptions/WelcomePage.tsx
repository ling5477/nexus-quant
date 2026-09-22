import {t} from '@/i18n';
import {useTranslation} from 'react-i18next';
import {Button} from 'antd';
import {useNavigate} from 'react-router-dom';

import {ExceptionView} from '@/components/standalone/ExceptionView';
import {StandaloneSurface} from '@/components/standalone/StandaloneSurface';

/**
 * WelcomePage — 空系统初始化引导页(v2)。
 * 当控制台还没有可用数据时,给出明确的第一步动作,而不是空白页。
 */
export function WelcomePage() {
    useTranslation();
    const navigate = useNavigate();

    return (
        <StandaloneSurface ariaLabel={t('exception.welcome')}>
            <ExceptionView
                tone="info"
                kicker={t('exception.welcome')}
                title={t('exception.welcomeTitle')}
                description={t('exception.welcomeDescription')}
                nextSteps={
                    <>
                        <div className="nq-exception__next-title">{t('exception.firstStep')}</div>
                        <ol className="nq-exception__steps">
                            <li>{t('exception.createAccountDetail')}</li>
                            <li>{t('exception.importStrategyDetail')}</li>
                            <li>{t('exception.marketDataDetail')}</li>
                        </ol>
                    </>
                }
                actions={
                    <>
                        <Button type="primary" onClick={() => navigate('/accounts')}>{t('exception.createAccount')}</Button>
                        <Button onClick={() => navigate('/strategies')}>{t('exception.importStrategy')}</Button>
                        <Button onClick={() => navigate('/marketdata')}>{t('exception.marketData')}</Button>
                    </>
                }
            />
        </StandaloneSurface>
    );
}
