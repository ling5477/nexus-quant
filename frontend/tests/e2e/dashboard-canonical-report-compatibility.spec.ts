import {expect, test as base, type Page} from 'playwright/test';
import type {PaperTradingRunItem} from '../../src/features/paper-trading/types/paper-trading';
import type {SimAnalysis} from '../../src/features/paper-trading/api/analysis';
import zh from '../../src/i18n/locales/zh-CN/pages.json' with {type: 'json'};
import en from '../../src/i18n/locales/en-US/pages.json' with {type: 'json'};

const timestamp = '2026-10-21T01:00:00Z';
const run = (paperRunId: string, canonicalAccountId?: number | null): PaperTradingRunItem => ({
    paperRunId, canonicalAccountId, publishId: `publish-${paperRunId}`,
    strategyVersionId: `version-${paperRunId}`, status: 'RUNNING', tradeEnv: 'SIM',
    exchangeCode: 'BINANCE', marketType: 'SPOT', symbol: 'BTC-USDT', intervalCode: '1h',
    startedAt: timestamp, stoppedAt: null, publishSnapshotJson: null,
    strategyVersionSnapshotJson: null, datasetSnapshotJson: null, paramSnapshotJson: null,
    configSnapshotJson: null, createdBy: 'fixture', createdAt: timestamp, updatedAt: timestamp,
});
const legacy = run('legacy-with-report', null);
const canonicalA = run('canonical-a', 101);
const canonicalB = run('canonical-b', 102);
const report = (paperRunId: string, reportDate = '2026-10-20') => ({
    reportId: `report-${paperRunId}`, paperRunId, reportDate, generatedAt: timestamp,
    totalEquity: 987654.32, dailyPnl: 12345.67, dailyReturn: 0.25, maxDrawdown: 0.5,
});
const failure = (traceId: string) => ({code: 'STATE_CONFLICT', message: 'fixture conflict', traceId});

function analysis(item: PaperTradingRunItem, filled: boolean): SimAnalysis {
    return {
        run: {...item, canonicalAccountId: item.canonicalAccountId!, strategyVersionId: item.strategyVersionId!,
            strategyCode: 'dashboard-fixture', versionName: 'fixture', version: 1,
            backtestConfigId: null, backtestRunId: null},
        facts: {
            paperRunId: item.paperRunId, canonicalAccountId: item.canonicalAccountId!,
            publishId: item.publishId, strategyVersionId: item.strategyVersionId!, inputSha256: 'fixture',
            initialBudget: '1000', cash: filled ? '800' : '1000', positionQuantity: filled ? '2' : '0',
            markPrice: '110', equity: filled ? '1020' : null, pnl: filled ? '20' : null,
            markAsOf: filled ? timestamp : null, ledgerAsOf: timestamp, positionAsOf: timestamp,
            orders: [], trades: filled ? [{trade_id: 'canonical-trade', quantity: '2'}] : [],
            positions: [], ledgerEntries: filled ? [{ledger_entry_id: 'canonical-ledger', delta: '-200'}] : [],
        },
        metrics: {orderStatusCounts: [], tradeCount: filled ? 1 : 0, fees: [], riskEventCount: 0,
            decisionCount: 0, decisionWindowLimited: false}, decisions: [],
    };
}

type Reply = {status?: number; body: unknown; wait?: Promise<void>};
type Fixture = {
    rows: PaperTradingRunItem[];
    replies: Map<string, Reply>;
    analyses: Map<string, SimAnalysis>;
    calls: string[];
    writes: string[];
    daily: string[];
    pageErrors: string[];
    heartbeatError: boolean;
    locale: 'zh-CN' | 'en-US';
};

const test = base.extend<{fixture: Fixture}>({
    fixture: async ({page}, use) => {
        const fixture: Fixture = {rows: [], replies: new Map(), analyses: new Map(),
            calls: [], writes: [], daily: [], pageErrors: [], heartbeatError: false, locale: 'zh-CN'};
        page.on('pageerror', error => fixture.pageErrors.push(error.message));
        page.on('request', request => {
            if (!new URL(request.url()).pathname.startsWith('/api/')) return;
            fixture.calls.push(new URL(request.url()).pathname);
            if (request.method() !== 'GET') fixture.writes.push(`${request.method()} ${request.url()}`);
        });
        await page.addInitScript(() => {
            localStorage.setItem('nexus-quant.console.auth', JSON.stringify({
                accessToken: 'dashboard-compatibility-fixture', tokenType: 'Bearer',
                expiresAt: '2999-01-01T00:00:00Z', username: 'fixture', roles: ['ADMIN'],
            }));
        });
        // 仅在测试响应中导出既有 client，定向刷新列表及注入缓存；不增加生产接口或替换组件。
        await page.route('**/src/app/providers/AppProviders.tsx', async route => {
            const response = await route.fetch();
            await route.fulfill({response, body: `${await response.text()}\nexport {queryClient as dashboardTestClient};`});
        });
        await page.route('**/api/**', async route => {
            const path = new URL(route.request().url()).pathname;
            if (!path.startsWith('/api/')) return route.fallback();
            const reply = (body: unknown, status = 200) => route.fulfill({status, json: body});
            if (route.request().method() !== 'GET') return reply({code: 'UNEXPECTED_MUTATION'}, 405);
            if (path === '/api/auth/me') return reply({userId: 1, username: 'fixture',
                authenticated: true, roles: ['ADMIN']});
            if (path === '/api/user/preferences') return reply({version: 0, locale: fixture.locale,
                hideLoginDisclosures: true, defaultMarket: null, watchlist: [], notificationsReadAt: null});
            if (path === '/api/user/notifications') return reply({items: [], hasMore: false, offset: 0});
            if (path === '/api/paper-trading/runs') return reply(fixture.rows);
            const daily = /^\/api\/paper-trading\/runs\/([^/]+)\/daily-reports$/.exec(path);
            if (daily) {
                const id = decodeURIComponent(daily[1]);
                fixture.daily.push(id);
                const response = fixture.replies.get(id) ?? {status: 409,
                    body: failure('STRATEGY_SIM_DAILY_REPORT_UNAVAILABLE_USE_CANONICAL_ANALYSIS')};
                await response.wait;
                return reply(response.body, response.status);
            }
            if (path.endsWith('/heartbeats')) return fixture.heartbeatError
                ? reply(failure('heartbeat-read-failed'), 409)
                : reply([{heartbeatId: 'heartbeat', status: 'HEALTHY', heartbeatTime: timestamp}]);
            if (path === '/api/paper-trading/strategy-sim/analysis/runs') return reply({
                items: [...fixture.analyses.values()].map(value => value.run), offset: 0,
                hasMore: false, windowLimited: false,
            });
            const selected = /^\/api\/paper-trading\/strategy-sim\/analysis\/runs\/([^/]+)$/.exec(path);
            if (selected) return reply(fixture.analyses.get(decodeURIComponent(selected[1])));
            if (path.endsWith('/performance')) {
                const id = decodeURIComponent(path.split('/').at(-2)!);
                return reply({paperRunId: id, status: 'NO_SAMPLES', timeBasis: 'REPLAY_EVENT_TIME',
                    quoteCurrency: 'USDT', coverageStart: null, coverageEnd: null, ledgerAsOf: timestamp,
                    initialEquity: null, finalEquity: null, pnl: null, returnRatio: null,
                    maxDrawdown: null, maxDrawdownRatio: null, samples: [], tradesAfterCoverage: 0,
                    sharpe: {value: null, status: 'INSUFFICIENT_SAMPLES', returnCount: 0,
                        samplingSeconds: 3600, method: 'fixture', riskFreeRate: '0'},
                    outcomes: {status: 'NO_CLOSED_TRADES', winRate: null, winningCount: 0,
                        losingCount: 0, flatCount: 0, netProfit: null, netLoss: null,
                        profitLossRatio: null, method: 'fixture', coverageStart: null, coverageEnd: null}});
            }
            return reply([]);
        });
        await use(fixture);
        expect(fixture.daily.filter(id => id.startsWith('canonical')), 'canonical legacy daily-report HTTP calls = 0').toEqual([]);
        expect(fixture.writes, 'Unexpected API mutations = 0').toEqual([]);
        expect(fixture.pageErrors, 'no browser runtime errors').toEqual([]);
    },
});

const card = (page: Page) => page.getByTestId('dashboard-focus-performance');
async function open(page: Page, fixture: Fixture) {
    await page.addInitScript(locale => localStorage.setItem('nq.locale', locale), fixture.locale);
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.getByRole('heading', {name: fixture.locale === 'zh-CN' ? zh.dashboard : en.dashboard})).toBeVisible();
    await expect(page.getByTestId('dashboard-runtime-readiness-card')).toContainText(
        fixture.locale === 'zh-CN' ? zh.runtimeGuardedLiveDisabled : en.runtimeGuardedLiveDisabled);
    await expect(page.locator('vite-error-overlay')).toHaveCount(0);
}

async function refreshRuns(page: Page) {
    await page.evaluate(async () => {
        const moduleUrl = '/src/app/providers/AppProviders.tsx';
        const {dashboardTestClient} = await import(/* @vite-ignore */ moduleUrl);
        await dashboardTestClient.invalidateQueries({queryKey: ['paper-trading-runs', 'list']});
    });
}

async function canonicalVisible(page: Page, item: PaperTradingRunItem, texts = zh) {
    await expect(card(page)).toContainText(item.paperRunId);
    await expect(card(page)).toContainText(texts.dashboardReporting.canonical);
    await expect(card(page)).toContainText(texts.dashboardReporting.canonicalDescription);
    await expect(card(page).locator('[data-error-presentation]')).toHaveCount(0);
    await expect(card(page).locator('.nq-metric-card')).toHaveCount(0);
    await expect(card(page).getByText(texts.legacyMonitor.daily)).toHaveCount(0);
    const href = await card(page).getByRole('link', {name: texts.dashboardReporting.viewSimAnalysis}).getAttribute('href');
    const target = new URL(href!, 'http://fixture');
    expect(target.pathname).toBe('/paper-trading/portfolio');
    expect([...target.searchParams]).toEqual([['analysisSource', 'sim'], ['paperRunId', item.paperRunId]]);
}

for (const filled of [false, true]) {
    test(`canonical ${filled ? 'filled' : 'empty'} run isolates daily reports and opens exact analysis`, async ({page, fixture}, testInfo) => {
        fixture.rows = [canonicalB, {...canonicalA, updatedAt: '2026-10-20T00:00:00Z'}];
        fixture.analyses.set(canonicalA.paperRunId, analysis(canonicalA, false));
        fixture.analyses.set(canonicalB.paperRunId, analysis(canonicalB, filled));
        await open(page, fixture);
        await canonicalVisible(page, canonicalB);
        expect(fixture.daily).toEqual([]);
        for (const endpoint of ['alerts', 'heartbeats', 'risk-results', 'recovery-events']) {
            await expect.poll(() => fixture.calls.some(path => path.endsWith(`/${endpoint}`)), endpoint).toBeTruthy();
        }
        if (filled) await card(page).screenshot({path: testInfo.outputPath('canonical-dashboard.png')});
        await card(page).getByRole('link', {name: zh.dashboardReporting.viewSimAnalysis}).click();
        await expect(page.getByTestId('sim-analysis-portfolio')).toBeVisible();
        const target = new URL(page.url());
        expect(target.searchParams.get('paperRunId')).toBe(canonicalB.paperRunId);
        await expect(page.getByRole('link', {name: zh.simAnalysis.manageRun})).toHaveAttribute('href',
            `/paper-trading/runs?paperRunId=${encodeURIComponent(canonicalB.paperRunId)}`);
        const analysisReads = fixture.calls.filter(path => /analysis\/runs\/[^/]+$/.test(path));
        // StrictMode 可取消首个带 signal 的读取并重挂载；所有请求仍须只指向指定实例。
        expect(analysisReads.length).toBeLessThanOrEqual(2);
        expect([...new Set(analysisReads)])
            .toEqual([`/api/paper-trading/strategy-sim/analysis/runs/${encodeURIComponent(canonicalB.paperRunId)}`]);
        await expect(page.getByTestId('sim-analysis-portfolio').locator('.sim-analysis-metrics').first())
            .toContainText(filled ? '1,020' : '1,000');
    });
}

test('legacy reports preserve metadata and unknown financial values', async ({page, fixture}) => {
    fixture.rows = [legacy];
    fixture.replies.set(legacy.paperRunId, {body: [report(legacy.paperRunId), report(legacy.paperRunId, '2026-10-19')]});
    await open(page, fixture);
    await expect(card(page)).toContainText('2026-10-20');
    await expect(card(page)).toContainText(zh.legacyMonitor.daily);
    await expect(card(page).getByText('2026-10-19')).toHaveCount(0);
    for (const label of [zh.totalEquity, zh.todaySPnl, zh.dailyReturn, zh.maximumDrawdown]) {
        await expect(card(page).locator('.nq-metric-card').filter({hasText: label})
            .locator('[class*="nq-metric-card__value"]')).toHaveText('-');
    }
    await expect(card(page)).not.toContainText('987,654');
    expect(fixture.daily).toEqual([legacy.paperRunId]);
});

test('legacy empty reports retain the empty state', async ({page, fixture}) => {
    fixture.rows = [legacy]; fixture.replies.set(legacy.paperRunId, {body: []});
    await open(page, fixture);
    await expect(card(page)).toContainText(zh.noDailyReportForTheFocusedRunGenerateOneFromPaperTradingDetails);
    expect(fixture.daily).toEqual([legacy.paperRunId]);
});

for (const status of [409, 500]) {
    test(`legacy HTTP ${status} stays visible as an error`, async ({page, fixture}) => {
        fixture.rows = [legacy];
        fixture.replies.set(legacy.paperRunId, {status, body: {
            ...failure('legacy-report-failed'), code: status === 500 ? 'UNKNOWN_ERROR' : 'STATE_CONFLICT',
        }});
        await open(page, fixture);
        await expect(card(page).locator('[data-error-presentation]')).toContainText('legacy-report-failed');
        await expect(card(page)).not.toContainText(zh.noDailyReportForTheFocusedRunGenerateOneFromPaperTradingDetails);
        expect(fixture.daily).toEqual(Array(status === 500 ? 2 : 1).fill(legacy.paperRunId));
    });
}

test('no focus then canonical does not request daily reports', async ({page, fixture}) => {
    await open(page, fixture);
    await expect(card(page)).toContainText(zh.createAndStartAPaperRunToSeeItsLatestDailyMetricsHere);
    expect(fixture.daily).toEqual([]);
    fixture.rows = [canonicalA]; await refreshRuns(page);
    await canonicalVisible(page, canonicalA);
    expect(fixture.daily).toEqual([]);
});

test('missing run identity is unknown until explicit legacy null arrives', async ({page, fixture}) => {
    fixture.rows = [run('unknown')];
    await open(page, fixture);
    await expect(card(page)).toContainText(zh.dashboardReporting.unknownIdentity);
    expect(fixture.daily).toEqual([]);
    fixture.rows = [run('unknown', null)];
    fixture.replies.set('unknown', {body: []}); await refreshRuns(page);
    await expect(card(page)).toContainText(zh.noDailyReportForTheFocusedRunGenerateOneFromPaperTradingDetails);
    expect(fixture.daily).toEqual(['unknown']);
});

for (const failed of [false, true]) {
    test(`legacy ${failed ? 'error' : 'report'} to canonical A to B to legacy isolates cache`, async ({page, fixture}) => {
        fixture.rows = [legacy];
        fixture.replies.set(legacy.paperRunId, failed
            ? {status: 409, body: failure('old-legacy-error')} : {body: [report(legacy.paperRunId)]});
        await open(page, fixture);
        await expect(card(page)).toContainText(failed ? 'old-legacy-error' : '2026-10-20');
        fixture.rows = [canonicalA]; await refreshRuns(page);
        await canonicalVisible(page, canonicalA);
        await expect(card(page)).not.toContainText('old-legacy-error');
        await expect(card(page)).not.toContainText('2026-10-20');
        fixture.rows = [canonicalB]; await refreshRuns(page);
        await canonicalVisible(page, canonicalB);
        const otherLegacy = run('legacy-other', null);
        fixture.rows = [otherLegacy];
        fixture.replies.set(otherLegacy.paperRunId, {body: []}); await refreshRuns(page);
        await expect(card(page)).toContainText(otherLegacy.paperRunId);
        await expect(card(page)).toContainText(zh.noDailyReportForTheFocusedRunGenerateOneFromPaperTradingDetails);
        await expect(card(page)).not.toContainText('old-legacy-error');
        await expect(card(page)).not.toContainText('2026-10-20');
        expect(fixture.daily).toEqual([legacy.paperRunId, otherLegacy.paperRunId]);
        if (!failed) {
            fixture.rows = [legacy]; await refreshRuns(page);
            await expect(card(page)).toContainText('2026-10-20');
            expect(fixture.daily, 'fresh legacy cache is reused').toEqual([legacy.paperRunId, otherLegacy.paperRunId]);
        }
    });
}

test('late legacy response cannot appear after switching to canonical', async ({page, fixture}) => {
    let release!: () => void;
    const wait = new Promise<void>(resolve => {release = resolve;});
    fixture.rows = [legacy]; fixture.replies.set(legacy.paperRunId, {body: [report(legacy.paperRunId)], wait});
    await open(page, fixture);
    await expect.poll(() => fixture.daily).toEqual([legacy.paperRunId]);
    try {
        fixture.rows = [canonicalA]; await refreshRuns(page);
        await canonicalVisible(page, canonicalA);
    } finally {release();}
    await expect.poll(() => page.evaluate(async () => {
        const moduleUrl = '/src/app/providers/AppProviders.tsx';
        const {dashboardTestClient} = await import(/* @vite-ignore */ moduleUrl);
        return dashboardTestClient.getQueryState(['paper-trading-runs', 'daily-reports', 'legacy-with-report'])?.status;
    })).toBe('success');
    await canonicalVisible(page, canonicalA);
    expect(fixture.daily).toEqual([legacy.paperRunId]);
});

test('disabled canonical queries hide same-key cached data and errors', async ({page, fixture}) => {
    fixture.rows = [canonicalA]; await open(page, fixture);
    await canonicalVisible(page, canonicalA);
    await page.evaluate(async ({id, cachedReport}) => {
        const moduleUrl = '/src/app/providers/AppProviders.tsx';
        const {dashboardTestClient} = await import(/* @vite-ignore */ moduleUrl);
        const key = ['paper-trading-runs', 'daily-reports', id];
        dashboardTestClient.setQueryData(key, [cachedReport]);
        dashboardTestClient.getQueryCache().find({queryKey: key, exact: true}).setState({
            status: 'error', error: Object.assign(new Error('old cached error'), {code: 'STATE_CONFLICT'}),
        });
    }, {id: canonicalA.paperRunId, cachedReport: report(canonicalA.paperRunId)});
    await canonicalVisible(page, canonicalA);
    await expect(card(page)).not.toContainText('2026-10-20');
    expect(fixture.daily).toEqual([]);
});

test('canonical guidance keeps other real read errors visible', async ({page, fixture}) => {
    fixture.rows = [canonicalA]; fixture.heartbeatError = true;
    await open(page, fixture); await canonicalVisible(page, canonicalA);
    await expect(page.locator('.nq-dashboard__heading [data-error-presentation]')).toContainText('heartbeat-read-failed');
    expect(fixture.daily).toEqual([]);
});

test('English canonical and unknown-identity guidance is translated', async ({page, fixture}) => {
    fixture.locale = 'en-US'; fixture.rows = [canonicalA];
    await open(page, fixture); await canonicalVisible(page, canonicalA, en);
    fixture.rows = [run('unknown')]; await refreshRuns(page);
    await expect(card(page)).toContainText(en.dashboardReporting.unknownIdentity);
    expect(fixture.daily).toEqual([]);
});

test('canonical navigation safely encodes the exact URL identity', async ({page, fixture}) => {
    const encoded = run('canonical identity&=中文', 103);
    fixture.rows = [encoded]; await open(page, fixture);
    await canonicalVisible(page, encoded);
    expect(fixture.daily).toEqual([]);
});
