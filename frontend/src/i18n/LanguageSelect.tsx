import {Select} from 'antd';
import {useTranslation} from 'react-i18next';
import {setLocale, type AppLocale} from '@/i18n';

export function LanguageSelect() {
    const {t, i18n} = useTranslation();
    return <Select
        aria-label={t('language')}
        data-testid="language-select"
        value={i18n.resolvedLanguage === 'en-US' ? 'en-US' : 'zh-CN'}
        style={{minWidth: 116}}
        onChange={(value: AppLocale) => void setLocale(value)}
        options={[{value: 'zh-CN', label: '简体中文'}, {value: 'en-US', label: 'English'}]}
    />;
}
