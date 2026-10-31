import {Link} from 'react-router-dom';
import {Typography} from 'antd';
import {useTranslation} from 'react-i18next';
import {demoRegistrationApi} from '@/features/public-demo/api';
export const readSessionAccounts = demoRegistrationApi.simAccount;
export function LoginExtras() {
    const {t} = useTranslation();
    return <Typography.Paragraph style={{marginTop: 16}}>
        <Link to="/register">{t('auth.registerDemo')}</Link>
        {' · '}<Link to="/verify-email">{t('auth.verifyEmail')}</Link>
    </Typography.Paragraph>;
}
