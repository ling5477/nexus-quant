import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {MemoryRouter} from 'react-router-dom';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';
import {setLocale} from '../../src/i18n';
import {paperRuleText} from '../../src/features/paper-trading/paper-rule-text';
import {PaperRuleSourceDetails} from '../../src/features/paper-trading/components/PaperRuleSourceDetails';
import {PaperAutoReviewDashboard} from '../../src/pages/paper-trading/components/PaperAutoReviewDashboard';
import {
    LegacyAnalysisObjectLink,
    LegacyAnalysisRelations,
} from '../../src/pages/paper-trading/components/LegacyAnalysisControls';
import type {PaperExecutionRunDiagnostic} from '../../src/features/paper-trading/types/paper-trading';
import type {PaperAutoReviewsResponse} from '../../src/features/paper-trading/types/paper-trading';
import zh from '../../src/i18n/locales/zh-CN/pages.json';
import en from '../../src/i18n/locales/en-US/pages.json';

beforeEach(async () => {
    await setLocale('en-US');
});
afterEach(async () => {
    await setLocale('zh-CN');
});

const summary = '多个 Paper run 处于 FAILED 终态。';
const action = '检查异常停机/告警/恢复事件并定位失败步骤。';
function review(): PaperAutoReviewsResponse {
    return {
        overview: {
            totalRuns: 1,
            reviewedRunCount: 1,
            issueRunCount: 1,
            healthyRunCount: 0,
            criticalIssueCount: 1,
            warningIssueCount: 0,
            strategyReviewedCount: 0,
            publishReviewedCount: 0,
            topIssueCause: 'FAILED_RUN',
            topWeakness: null,
            generatedAt: '2026-10-16T00:00:00Z',
        },
        portfolioReview: {
            headline: 'Paper 组合存在关键执行问题，需优先处理高风险 run。',
            summary:
                '共 1 个 bounded Paper run，其中 1 个存在问题、0 个健康；关键问题 run 1 个、警告 run 0 个；已评估策略 0 个、发布 0 个。结论为规则化 Paper 复盘，不构成投资建议。',
            keyFindings: [],
            riskHighlights: [],
            executionHighlights: [],
            strategyHighlights: [],
            backtestDeviationHighlights: [],
            suggestedNextActions: [],
            limitations: [],
        },
        runReviews: [],
        strategyReviews: [],
        publishReviews: [],
        issueClusters: [
            {
                clusterKey: 'FAILED_RUN',
                cause: 'FAILED_RUN',
                severity: 'CRITICAL',
                count: 1,
                affectedRunIds: ['run-do-not-translate'],
                affectedStrategyVersionIds: [],
                affectedPublishIds: [],
                summary,
                suggestedAction: action,
            },
        ],
        safety: {
            paperOnly: true,
            rulesBased: true,
            noInvestmentAdvice: true,
            noLiveTrading: true,
            noAiRuntime: true,
            message: 'Paper only',
        },
    };
}

describe('Paper rule explanation presentation', () => {
    it('renders cluster prose and actions as meaningful text rather than unknown enum labels', async () => {
        for (const locale of ['en-US', 'zh-CN'] as const) {
            await setLocale(locale);
            const data = review();
            const before = JSON.stringify(data);
            const html = renderToStaticMarkup(
                createElement(
                    MemoryRouter,
                    {initialEntries: ['/?legacyDimension=cluster']},
                    createElement(PaperAutoReviewDashboard, {
                        query: {data, error: null, isFetching: false, refetch: () => {}},
                    }),
                ),
            );
            expect(html).toContain(
                locale === 'en-US'
                    ? 'Multiple Paper runs are in the FAILED terminal state.'
                    : summary,
            );
            expect(html).toContain(
                locale === 'en-US'
                    ? 'Inspect abnormal shutdowns, alerts and recovery events, and locate failed steps.'
                    : action,
            );
            expect(html).toContain('run-do-not-translate');
            expect(JSON.stringify(data)).toBe(before);
        }
    });
    it('translates every registered current-engine message in English and preserves original Chinese', async () => {
        const keys = Object.keys(zh.paperRules.messages) as Array<
            keyof typeof zh.paperRules.messages
        >;
        expect(keys.length).toBe(156);
        expect(keys.sort()).toEqual(Object.keys(en.paperRules.messages).sort());
        for (const key of keys)
            expect(paperRuleText(zh.paperRules.messages[key])).toBe(en.paperRules.messages[key]);
        await setLocale('zh-CN');
        for (const key of keys)
            expect(paperRuleText(zh.paperRules.messages[key])).toBe(zh.paperRules.messages[key]);
    });
    it('preserves exact signed ratio digits and explicitly names the ratio unit', () => {
        expect(paperRuleText('收益率=-0.000000000000000000001')).toBe(
            'Return (ratio)=-0.000000000000000000001',
        );
        expect(paperRuleText('最大回撤=-0.123456789012345678901')).toBe(
            'Maximum drawdown (ratio)=-0.123456789012345678901',
        );
        expect(paperRuleText('收益率=数据不足')).toBe('Return (ratio)=Insufficient data');
        expect(paperRuleText('订单数=0')).toBe('Orders=0');
        expect(paperRuleText('成交数=2147483647')).toBe('Fills=2147483647');
        expect(paperRuleText('风控拦截=是')).toBe('Risk blocked=Yes');
    });
    it('retains dynamic count identities and the rule-based advice boundary', () => {
        const source =
            '共 200 个 bounded Paper run，其中 150 个存在问题、50 个健康；关键问题 run 17 个、警告 run 133 个；已评估策略 4 个、发布 11 个。结论为规则化 Paper 复盘，不构成投资建议。';
        expect(paperRuleText(source)).toBe(
            'Paper runs in this window: 200; issues: 150; healthy: 50; critical: 17; warnings: 133. Evaluated strategies: 4; releases: 11. This rule-based Paper review is not investment advice.',
        );
        expect(paperRuleText('FAILED_RUN: 17 项 —— ' + summary)).toContain(
            '17 items — Multiple Paper runs are in the FAILED terminal state.',
        );
        expect(paperRuleText('关键问题 run 17 个，建议优先排查。')).toBe(
            'Critical runs: 17. Investigate these first.',
        );
        expect(paperRuleText('Paper 内部表现较强策略 4 个（非投资推荐）。')).toContain(
            'not an investment recommendation',
        );
    });
    it('matches score labels and values without accepting a mismatched dimension or changing thresholds', () => {
        expect(paperRuleText('风险评分较高（riskScore=70）')).toBe('High risk score (score 70)');
        expect(paperRuleText('风险评分较高（returnScore=70）')).toBe(
            '风险评分较高（returnScore=70）',
        );
        expect(paperRuleText('风险评分较高（riskScore=101）')).toBe(
            '风险评分较高（riskScore=101）',
        );
        expect(paperRuleText('风险评分偏低（得分 49）')).toBe('Low risk score (score 49)');
        expect(paperRuleText('主要短板: 风险评分偏低')).toBe('Primary weakness: Low risk score');
        expect(
            paperRuleText(
                'Paper 与 Backtest 收益偏差较大（首版阈值 > 15%），Paper 模拟与回测差异显著，需重点复盘。',
            ),
        ).toContain('above the current 15% threshold');
    });
    it('shows future rules, unrecognized causes and objects verbatim instead of hiding them', () => {
        for (const value of [
            'source-custom-id-01',
            '未来引擎的新解释。',
            'FUTURE_RULE: 3 项 —— 新规则正文。',
            '次要原因: FUTURE_CODE',
            '风险评分较高（riskScore=70） extra',
            '__proto__',
            '说明'.repeat(5000),
        ])
            expect(paperRuleText(value)).toBe(value);
        expect(paperRuleText('状态=FUTURE_STATE')).toBe('State=FUTURE_STATE');
        expect(paperRuleText(null)).toBe('—');
    });
    it('keeps known secondary causes and all dynamic summary families readable', () => {
        for (const source of [
            '状态=FAILED',
            '次要原因: RISK_BLOCKED, DATA_INSUFFICIENT',
            '最集中问题类型: FAILED_RUN。',
            '策略最常见短板: 风险评分偏低。',
            '共 4 个 Paper run：问题 3 个、健康 1 个。',
            '高风险策略 2 个，需复核风险来源。',
            '样本不足策略 3 个，需增加 Paper 样本。',
            '4 个策略版本 Paper 与 Backtest 偏差显著（HIGH），需重点复核策略稳定性。',
            'Paper 收益评分较高（returnScore=100）',
            '执行评分较高（executionScore=70）',
            '样本评分较高（sampleScore=90）',
            'Backtest 偏差评分较高（backtestDeviationScore=75）',
        ])
            expect(paperRuleText(source)).not.toMatch(/[\u4e00-\u9fff]/);
    });
    it('does not serialize the original response before explicit expansion', () => {
        let reads = 0;
        const source = {
            toJSON() {
                reads++;
                return {raw: summary, precise: '0.100000000000000000001'};
            },
        };
        const html = renderToStaticMarkup(createElement(PaperRuleSourceDetails, {value: source}));
        expect(reads).toBe(0);
        expect(html).toContain('Original rule output');
        expect(html).not.toContain(summary);
        expect(html).not.toContain('<pre');
    });
    it('localizes assembler placeholders while preserving actual and unknown object identities', () => {
        const missing = renderToStaticMarkup(
            createElement(
                MemoryRouter,
                null,
                createElement(LegacyAnalysisObjectLink, {kind: 'strategy', id: '(未绑定策略版本)'}),
            ),
        );
        expect(missing).toContain('No bound strategy version');
        expect(missing).toContain('title="(未绑定策略版本)"');
        const raw = renderToStaticMarkup(
            createElement(
                MemoryRouter,
                null,
                createElement(LegacyAnalysisObjectLink, {
                    kind: 'strategy',
                    id: 'future-version-id',
                }),
            ),
        );
        expect(raw).toContain('future-version-id');
        const run = {
            paperRunId: 'run',
            strategyVersionId: '(未绑定策略版本)',
            publishId: null,
        } as PaperExecutionRunDiagnostic;
        const actual = renderToStaticMarkup(
            createElement(
                MemoryRouter,
                null,
                createElement(LegacyAnalysisRelations, {
                    runs: [run],
                    children: createElement(LegacyAnalysisObjectLink, {
                        kind: 'strategy',
                        id: run.strategyVersionId!,
                    }),
                }),
            ),
        );
        expect(actual).toMatch(/<button\b[^>]*><span>\(未绑定策略版本\)<\/span><\/button>/);
        expect(actual).not.toContain('No bound strategy version');
    });
    it('renders an unknown explanation as escaped text without manufacturing a known conclusion', () => {
        const data = review();
        data.issueClusters[0].summary = '<script>future-rule()</script>';
        const html = renderToStaticMarkup(
            createElement(
                MemoryRouter,
                {initialEntries: ['/?legacyDimension=cluster']},
                createElement(PaperAutoReviewDashboard, {
                    query: {data, error: null, isFetching: false, refetch: () => {}},
                }),
            ),
        );
        expect(html).toContain('&lt;script&gt;future-rule()&lt;/script&gt;');
        expect(html).not.toContain('<script>future-rule()');
    });
});
