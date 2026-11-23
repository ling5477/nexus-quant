import type {PropsWithChildren} from 'react';
import {Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {BrandLockup} from '@/nq-design-system/brand/BrandLockup';
import {StandaloneSurface} from '@/components/standalone/StandaloneSurface';
import {LanguageSelect} from '@/i18n/LanguageSelect';
import './i18n';
import './public-demo.css';

export function DemoAuthSurface({title, backLabel, children}: PropsWithChildren<{title: string; backLabel?: string}>) {
    const {t} = useTranslation('demo');
    return <StandaloneSurface className="nq-demo-auth" ariaLabel={title}>
        <header className="nq-demo-auth__header"><BrandLockup /><LanguageSelect /></header>
        <section className="nq-demo-auth__card">
            <h1>{title}</h1>
            {children}
            <Link to="/login">{backLabel ?? t('backToLogin')}</Link>
        </section>
    </StandaloneSurface>;
}
