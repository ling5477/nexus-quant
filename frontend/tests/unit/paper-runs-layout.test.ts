import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {MemoryRouter} from 'react-router-dom';
import {afterEach, describe, expect, it, vi} from 'vitest';
import i18n, {t} from '@/i18n';
import {localizedPaperLabel} from '@/pages/paper-trading/components/paperAnalysisOptions';

const fixture = vi.hoisted(() => ({
    paperRunId: 'ptr-layout-test', publishId: 'pub-layout-test', strategyVersionId: 'sv-layout-test',
    canonicalAccountId: null, status: 'RUNNING', tradeEnv: 'SIM', exchangeCode: 'OKX', marketType: 'SPOT',
    symbol: 'BTC-USDT', intervalCode: '1m', createdAt: null, updatedAt: null, startedAt: null, stoppedAt: null,
    createdBy: 'test-user', publishSnapshotJson: 'SNAPSHOT_MUST_NOT_OBSCURE_OPERATIONS',
}));

vi.mock('@/features/paper-trading/hooks/usePaperTradingQuery', async (importOriginal) => {
    const actual = await importOriginal<Record<string, unknown>>();
    return Object.fromEntries(Object.keys(actual).map((name) => [name, () => ({
        data: name === 'usePaperTradingDetailQuery' ? fixture : undefined,
        isFetching: false, isPending: false, error: null, mutate: vi.fn(), refetch: vi.fn(),
    })]));
});

// 面板自身的功能开关由既有路径验证，这里只隔离运行页的操作顺序与详情层级。
vi.mock('@/features/paper-trading/components/StrategySimPanel', () => ({StrategySimPanel: () => null}));

import {PaperTradingRunsPage} from '@/pages/paper-trading/PaperTradingRunsPage';

afterEach(() => vi.unstubAllEnvs());

describe('Paper run layout', () => {
    it('keeps query and lifecycle actions ahead of detail facts and collapsed diagnostics', async () => {
        vi.stubEnv('VITE_STRATEGY_SIM_ENABLED', 'false');
        await i18n.changeLanguage('en-US');
        const html = renderToStaticMarkup(createElement(MemoryRouter,
            {initialEntries: ['/paper-trading/runs?paperRunId=ptr-layout-test']}, createElement(PaperTradingRunsPage)));
        expect(html.indexOf('paper-runs-query')).toBeLessThan(html.indexOf(t('pages:startPaperRun')));
        expect(html.indexOf(t('pages:startPaperRun'))).toBeLessThan(html.indexOf(t('pages:runFacts')));
        expect(html.indexOf(t('pages:emergencyStop'))).toBeLessThan(html.indexOf(t('pages:runFacts')));
        expect(html).toContain('ptr-layout-test');
        expect(html).not.toContain('SNAPSHOT_MUST_NOT_OBSCURE_OPERATIONS');
        expect(html).not.toContain('paper-runs-create_publishId');
    });

    it('uses bilingual enum labels and preserves unsupported values as unknown', async () => {
        i18n.addResource('zh-CN', 'pages', 'paperRunRunningStatus', '运行中');
        i18n.addResource('en-US', 'pages', 'paperRunRunningStatus', 'Running');
        i18n.addResource('zh-CN', 'pages', 'paperAnalysisUnknownValue', '未识别的状态或类型');
        await i18n.changeLanguage('en-US');
        expect(localizedPaperLabel('RUNNING')).toBe('Running');
        await i18n.changeLanguage('zh-CN');
        expect(localizedPaperLabel('RUNNING')).toBe('运行中');
        expect(localizedPaperLabel('UNSUPPORTED_NEW_STATUS')).toBe('未识别的状态或类型');
        expect(localizedPaperLabel(null)).toBe('—');
    });
});
