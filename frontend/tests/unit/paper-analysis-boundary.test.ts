import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {MemoryRouter} from 'react-router-dom';
import {describe, expect, it} from 'vitest';
import i18n, {t} from '@/i18n';
import {PaperAnalysisBoundary, paperRunFactsPath, type PaperAnalysisKind} from '@/pages/paper-trading/components/PaperAnalysisBoundary';
import {localizedPaperOptions} from '@/pages/paper-trading/components/paperAnalysisOptions';

describe('Paper analysis source boundary', () => {
    it('opens the existing fact route with one exact, escaped run identity', () => {
        expect(paperRunFactsPath(' ptr-one ')).toBe('/paper-trading/runs?paperRunId=ptr-one');
        expect(paperRunFactsPath('ptr-a&tradeEnv=LIVE')).toBe('/paper-trading/runs?paperRunId=ptr-a%26tradeEnv%3DLIVE');
    });

    it.each<PaperAnalysisKind>(['portfolio', 'diagnostics', 'reviews'])('does not read or render mixed aggregate results on %s', (kind) => {
        // 不提供 QueryClient；边界页如果误挂聚合查询会直接失败，而不是显示错误的零统计。
        const html = renderToStaticMarkup(createElement(MemoryRouter, {initialEntries: [`/paper-trading/${kind}?paperRunId=ptr-existing`]},
            createElement(PaperAnalysisBoundary, {kind})));
        expect(html).toContain(`paper-analysis-boundary-${kind}`);
        expect(html).toContain('value="ptr-existing"');
        expect(html).toContain('href="/paper-trading/runs"');
        expect(html).not.toContain('EXECUTION_NO_ORDER');
        expect(html).not.toContain('nq-metric-card');
    });

    it('rebuilds translated option snapshots while retaining machine filter values', async () => {
        const source = [{get label() {return t('pages:allRatings');}, value: 'all'}];
        await i18n.changeLanguage('en-US');
        const english = localizedPaperOptions(source);
        await i18n.changeLanguage('zh-CN');
        const chinese = localizedPaperOptions(source);
        expect(chinese).not.toBe(english);
        expect(chinese[0]).not.toBe(english[0]);
        expect(english[0].label).toBe('All ratings');
        expect(chinese[0].label).toBe('全部评级');
        expect(chinese[0].value).toBe(english[0].value);
        expect(english[0].label).toBe('All ratings');
    });
});
