import {App, Select} from 'antd';
import {useTranslation} from 'react-i18next';
import {setLocale, type AppLocale} from '@/i18n';
import {useUserPreferences} from '@/features/preferences/hooks/useUserPreferences';
import {useAuthStore} from '@/store/auth-store';
import {showApiError} from '@/api/errors';
import {isPublicDemo} from '@/utils/distribution';

export function LanguageSelect() {
    const {t, i18n} = useTranslation();
    const {message} = App.useApp();
    const {query, save} = useUserPreferences();
    const user = useAuthStore((state) => state.currentUser);
    const persist = !isPublicDemo && Boolean(user?.userId && !user.mustChangePassword);
    return (
        <Select
            aria-label={t('language')}
            data-testid="language-select"
            value={i18n.resolvedLanguage === 'en-US' ? 'en-US' : 'zh-CN'}
            style={{minWidth: 116}}
            disabled={save.isPending || (persist && !query.data)}
            onChange={(value: AppLocale) => {
                if (persist)
                    save.mutate(
                        {locale: value},
                        {
                            onSuccess: () => {
                                void setLocale(value);
                            },
                            onError: (error) => showApiError(error, message),
                        },
                    );
                else void setLocale(value);
            }}
            options={[
                {value: 'zh-CN', label: '简体中文'},
                {value: 'en-US', label: 'English'},
            ]}
        />
    );
}
