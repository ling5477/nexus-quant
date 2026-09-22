import i18n from 'i18next';
import {initReactI18next} from 'react-i18next';
import zhCommon from './locales/zh-CN/common.json';
import enCommon from './locales/en-US/common.json';
import zhErrors from './locales/zh-CN/errors.json';
import enErrors from './locales/en-US/errors.json';
import zhPages from './locales/zh-CN/pages.json';
import enPages from './locales/en-US/pages.json';

export const LOCALE_STORAGE_KEY = 'nq.locale';
export type AppLocale = 'zh-CN' | 'en-US';
export function readLocale(): AppLocale {
    try {
        return localStorage.getItem(LOCALE_STORAGE_KEY) === 'en-US' ? 'en-US' : 'zh-CN';
    } catch {
        return 'zh-CN';
    }
}

export const resources = {
    'zh-CN': {common: zhCommon, errors: zhErrors, pages: zhPages},
    'en-US': {common: enCommon, errors: enErrors, pages: enPages},
};

void i18n.use(initReactI18next).init({
    resources,
    lng: readLocale(),
    fallbackLng: 'zh-CN',
    supportedLngs: ['zh-CN', 'en-US'],
    defaultNS: 'common',
    interpolation: {escapeValue: false},
    initAsync: false,
});

export async function setLocale(locale: AppLocale): Promise<void> {
    await i18n.changeLanguage(locale);
    try {
        localStorage.setItem(LOCALE_STORAGE_KEY, locale);
    } catch {
        // 浏览器禁用存储时仍允许本次会话切换语言。
    }
}

export const t = i18n.t.bind(i18n);
export default i18n;
