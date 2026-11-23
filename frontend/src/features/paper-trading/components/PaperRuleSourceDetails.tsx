import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import './PaperRuleSourceDetails.css';

/** 翻译只负责呈现；原始回执按需展开，保留规则正文、机器码和精确数值，关闭时不序列化整个窗口。 */
export function PaperRuleSourceDetails({value}: {value: unknown}) {
    const {t} = useTranslation('pages');
    const [open, setOpen] = useState(false);
    return (
        <details
            className="paper-rule-source"
            onToggle={(event) => setOpen(event.currentTarget.open)}
        >
            <summary>{t('paperRules.originalOutput')}</summary>
            {open && (
                <>
                    <p>{t('paperRules.originalHelp')}</p>
                    <pre tabIndex={0} aria-label={t('paperRules.originalOutput')}>
                        {JSON.stringify(value, null, 2)}
                    </pre>
                </>
            )}
        </details>
    );
}
