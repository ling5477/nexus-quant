import {NavLink, useLocation} from 'react-router-dom';
import {useTranslation} from 'react-i18next';

const groups = {
    research: [
        ['/strategies', 'strategies'], ['/research', 'research'], ['/backtests', 'backtests'],
        ['/experiments', 'pages:experiments.title'], ['/evaluations', 'evaluations'], ['/publishes', 'publishes'],
        ['/paper-trading/runs', 'sim'],
    ],
    market: [['/marketdata', 'market'], ['/instruments', 'instruments']],
    monitor: [
        ['/runs', 'runs'], ['/schedules', 'schedules'], ['/system/scheduler', 'scheduler'],
        ['/runtime/readiness', 'runtime'], ['/adapter-readiness', 'adapters'],
        ['/strategies/shadow-runs', 'shadow'], ['/strategies/validation', 'validation'],
    ],
} as const;

export function workspaceFamily(path: string): 'research' | 'market' | 'monitor' | 'sim' | 'other' {
    if (path.startsWith('/paper-trading') || path === '/trading') return 'sim';
    if (path === '/marketdata' || path === '/instruments') return 'market';
    if (groups.monitor.some(([route]) => path === route || path.startsWith(route + '/'))) return 'monitor';
    if (groups.research.some(([route]) => path === route || path.startsWith(route + '/'))) return 'research';
    return 'other';
}

/** 导航只表达业务顺序，不把访问过的步骤伪装成已完成，也不跨对象拼接内部身份。 */
export function WorkspaceNavigation() {
    const {pathname} = useLocation();
    const {t} = useTranslation('product');
    const family = workspaceFamily(pathname);
    const steps = family === 'sim' ? groups.research : family === 'other' ? null : groups[family];
    if (!steps) return null;
    return (
        <nav className="workspace-navigation" aria-label={t('workflow')}>
            <span className="workspace-navigation__label">{t(family === 'sim' ? 'researchFlow' : family + 'Flow')}</span>
            <ol>
                {steps.map(([to, label], index) => (
                    <li key={to}>
                        <NavLink to={to} end={to !== '/backtests'}>
                            <span className="workspace-navigation__index" aria-hidden="true">{String(index + 1).padStart(2, '0')}</span>
                            {t(label)}
                        </NavLink>
                    </li>
                ))}
            </ol>
        </nav>
    );
}
