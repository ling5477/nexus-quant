import {Tabs, Typography} from 'antd';
import {useTranslation} from 'react-i18next';
import financialDisclaimer from '../../../../DISCLAIMER.md?raw';
import thirdPartyNotices from '../../../../THIRD_PARTY_NOTICES.md?raw';

/** 原文直接来自仓库法律文件，显示层不改写许可或声明条件。 */
export function LegalDocuments() {
    const {t, i18n} = useTranslation();
    const chineseStart = financialDisclaimer.indexOf('## 中文');
    const englishStart = financialDisclaimer.indexOf('## English');
    const localizedRisk =
        chineseStart >= 0 && englishStart > chineseStart
            ? i18n.resolvedLanguage?.startsWith('zh')
                ? financialDisclaimer.slice(chineseStart, englishStart).trim()
                : financialDisclaimer.slice(englishStart).trim()
            : financialDisclaimer;
    return (
        <Tabs
            items={[
                {
                    key: 'financial',
                    label: t('preferences.financialDisclosure'),
                    children: (
                        <>
                            <pre
                                className="legal-document"
                                data-testid="localized-financial-disclaimer"
                            >
                                {localizedRisk}
                            </pre>
                            <details>
                                <summary>{t('preferences.fullOriginal')}</summary>
                                <pre
                                    className="legal-document"
                                    data-testid="financial-risk-disclaimer"
                                >
                                    {financialDisclaimer}
                                </pre>
                            </details>
                        </>
                    ),
                },
                {
                    key: 'third-party',
                    label: t('preferences.thirdPartyDisclosure'),
                    children: (
                        <>
                            <Typography.Paragraph>
                                {t('preferences.thirdPartySummary')}
                            </Typography.Paragraph>
                            <pre className="legal-document" data-testid="third-party-notices">
                                {thirdPartyNotices}
                            </pre>
                        </>
                    ),
                },
            ]}
        />
    );
}
