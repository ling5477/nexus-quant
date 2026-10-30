import {readFileSync} from 'node:fs';
import {expect, test, type Page, type Request, type Response} from 'playwright/test';

interface Ready {
    username: string;
    password: string;
    dataSource: 'REAL_OKX_PUBLIC_CAPTURE_REPLAY' | 'SYNTHETIC_LONG_HISTORY';
    start: string;
    endExclusive: string;
    captureSha256?: string;
    strategies: Record<string, {strategyId: string; strategyCode: string; versionId: string}>;
}
const readyPath = process.env.NQ_LONG_HISTORY_E2E_READY;
if (!readyPath) throw new Error('NQ_LONG_HISTORY_E2E_READY must identify the task-owned backend fixture');
const fixture = JSON.parse(readFileSync(readyPath, 'utf8')) as Ready;
if (!['REAL_OKX_PUBLIC_CAPTURE_REPLAY', 'SYNTHETIC_LONG_HISTORY'].includes(fixture.dataSource))
    throw new Error('The fixture must disclose archived real capture replay or isolated synthetic market data');

interface Receipt {kind: string; objectId: string}
interface IngestionRun {status: string; fetchedBars: number; jobId: string; errorMessage?: string}
interface Dataset {datasetId: string; barCount: number; symbol: string}
interface FrozenInput {version: string; datasetVersionId: string; barCount: number; sha256: string; manifestSha256: string}
const consoleWarnings = new WeakMap<Page, string[]>();

/** 只观察由正式页面动作发出的响应，不用API代写或替换返回数据。 */
async function mutation<T>(page: Page, action: () => Promise<unknown>, matches: (response: Response) => boolean, timeout = 120_000): Promise<T> {
    const pending = page.waitForResponse(response =>
        ['POST', 'PATCH', 'PUT', 'DELETE'].includes(response.request().method()) && matches(response), {timeout});
    await action();
    const response = await pending;
    expect(response.ok(), await response.text()).toBeTruthy();
    return response.json() as Promise<T>;
}

async function createObject(page: Page, kind: string, action: () => Promise<unknown>) {
    const receipt = await mutation<Receipt>(page, action, response =>
        new URL(response.url()).pathname.startsWith('/api/research-creation-operations/')
        && response.request().postDataJSON()?.kind === kind);
    expect(receipt.kind).toBe(kind);
    expect(receipt.objectId).toBeTruthy();
    return receipt.objectId;
}

test.beforeEach(async ({page}) => {
    const warnings: string[] = [];
    consoleWarnings.set(page, warnings);
    page.on('console', message => {
        if (['warning', 'error'].includes(message.type()) && /antd|ant design|React 19|deprecated|destroyOnClose/i.test(message.text()))
            warnings.push(message.text());
    });
    test.info().annotations.push({type: 'market-data-source', description: fixture.dataSource
        + (fixture.captureSha256 ? ` / archive SHA256 ${fixture.captureSha256}` : '')});
    await page.goto('/login');
    await page.getByLabel('账号', {exact: true}).fill(fixture.username);
    await page.getByLabel('密码', {exact: true}).fill(fixture.password);
    await page.getByRole('button', {name: /登\s*录/}).click();
    await expect(page).toHaveURL(/dashboard$/, {timeout: 30_000});
    await page.getByRole('button', {name: /^(确认阅读|Confirm read)$/}).click();
    await page.getByTestId('language-select').click();
    await page.getByText('简体中文', {exact: true}).last().click();
});

test.afterEach(async ({page}, info) => {
    await info.attach('existing-antd-react-compatibility-warnings', {
        body: (consoleWarnings.get(page) ?? []).join('\n') || 'No matching console warnings', contentType: 'text/plain',
    });
});

async function ingestAndCreateDataset(page: Page, symbol: 'BTC-USDT' | 'ETH-USDT', startDate: string, endDate: string, bars: number) {
    await page.goto('/marketdata?section=preparation&preparationTab=jobs');
    await page.getByRole('button', {name: '创建研究历史区间', exact: true}).click();
    const drawer = page.getByRole('dialog', {name: '创建研究历史区间', exact: true});
    if (symbol === 'ETH-USDT') {
        await drawer.getByTitle('BTC-USDT', {exact: true}).click();
        await page.getByTitle(symbol, {exact: true}).click();
    }
    await drawer.getByLabel('结束日期（UTC，不包含）', {exact: true}).fill(endDate);
    await drawer.getByLabel('结束日期（UTC，不包含）', {exact: true}).press('Tab');
    await drawer.getByLabel('开始日期（UTC，包含）', {exact: true}).fill(startDate);
    await drawer.getByLabel('开始日期（UTC，包含）', {exact: true}).press('Tab');
    await expect(drawer.getByText(new RegExp(`预计 ${bars / 24} 日 / ${bars} 根`))).toBeVisible();
    const job = await mutation<{jobId: string; symbol: string; startTime: string; endTime: string}>(page,
        () => drawer.getByRole('button', {name: '创建采集任务', exact: true}).click(),
        response => /^\/api\/marketdata\/ingestion-jobs\/[^/]+$/.test(new URL(response.url()).pathname));
    expect(job.symbol).toBe(symbol);
    expect((Date.parse(job.endTime) + 1 - Date.parse(job.startTime)) / 3_600_000).toBe(bars);
    const run = async () => {
        await page.getByRole('button', {name: '已查看，允许下一项操作', exact: true}).click();
        return mutation<IngestionRun>(page,
            () => page.getByRole('button', {name: '执行一次采集', exact: true}).last().click(),
            response => new URL(response.url()).pathname.startsWith(`/api/marketdata/ingestion-jobs/${job.jobId}/runs/`));
    };
    const first = await run();
    expect(first.jobId).toBe(job.jobId);
    expect(first.fetchedBars).toBe(Math.min(3000, bars));
    expect(first.status, first.errorMessage).toBe(bars > 3000 ? 'PARTIAL' : 'SUCCEEDED');
    if (bars > 3000) {
        const second = await run();
        expect(second.status, second.errorMessage).toBe('SUCCEEDED');
        expect(second.fetchedBars).toBe(bars - 3000);
    }
    await page.getByRole('link', {name: '使用此区间创建数据集并检查质量', exact: true}).click();
    const receipt = await mutation<{state: string; result: {dataset: Dataset; coverage: {actualBars: number; missingBars: number; qualityStatus: string}}}>(page,
        () => page.getByRole('button', {name: '创建 Dataset', exact: true}).click(),
        response => /^\/api\/marketdata\/datasets\/[^/]+\/creation$/.test(new URL(response.url()).pathname));
    expect(receipt.state).toBe('SUCCEEDED');
    expect(receipt.result.coverage).toMatchObject({actualBars: bars, missingBars: 0, qualityStatus: 'OK'});
    expect(receipt.result.dataset.barCount).toBe(bars);
    expect(receipt.result.dataset.symbol).toBe(symbol);
    return receipt.result.dataset;
}

test('BTC180日：真实接入、版本冻结、正式回测与评价、只读刷新及窄屏中英', async ({page}, info) => {
    const dataset = await ingestAndCreateDataset(page, 'BTC-USDT', fixture.start.slice(0, 10), fixture.endExclusive.slice(0, 10), 4320);
    await page.getByTestId('dataset-operation-recovery').getByRole('link', {name: '使用此数据集创建研究', exact: true}).click();
    const researchDrawer = page.getByRole('dialog', {name: '新建研究配置', exact: true});
    await researchDrawer.getByRole('button', {name: '选择策略', exact: true}).click();
    await page.getByRole('dialog', {name: '选择源策略', exact: true}).getByRole('row')
        .filter({hasText: fixture.strategies.SPOT_SMA_TARGET_V1.strategyCode}).getByRole('button', {name: '选用', exact: true}).click();
    await researchDrawer.getByLabel('名称', {exact: true}).fill(`BTC180 UI ${Date.now()}`);
    const researchId = await createObject(page, 'RESEARCH', () => researchDrawer.getByRole('button', {name: '提交创建', exact: true}).click());
    await expect(page).toHaveURL(new RegExp(researchId));
    await page.getByRole('link', {name: '创建回测配置', exact: true}).click();
    const backtestDrawer = page.getByRole('dialog', {name: '新建回测配置', exact: true});
    await backtestDrawer.getByRole('button', {name: '选择数据集', exact: true}).click();
    await page.getByRole('dialog', {name: '选择行情数据集', exact: true}).getByRole('row')
        .filter({hasText: dataset.datasetId}).getByRole('button', {name: '选用', exact: true}).click();
    await backtestDrawer.getByLabel('名称', {exact: true}).fill(`BTC180 backtest UI ${Date.now()}`);
    await backtestDrawer.getByLabel('初始资金', {exact: true}).fill('10000');
    for (const [label, value] of Object.entries({'运行采用费率（小数）': '0.001', '运行采用滑点（基点）': '10',
        '数量步长': '0.00000001', '价格步长': '0.1', '最小数量': '0.00001', '最小名义金额': '5',
        '费率来源版本': 'EXPLICIT_RESEARCH_FEE_V1', '滑点来源版本': 'EXPLICIT_RESEARCH_SLIPPAGE_V1'}))
        await backtestDrawer.getByLabel(label, {exact: true}).fill(value);
    const configId = await createObject(page, 'BACKTEST', () => backtestDrawer.getByRole('button', {name: '提交创建', exact: true}).click());
    const detail = page.getByRole('dialog', {name: '回测配置详情', exact: true});
    await detail.getByRole('button', {name: '选择版本', exact: true}).click();
    await page.getByRole('dialog', {name: '选择策略版本', exact: true}).getByRole('row')
        .filter({hasText: fixture.strategies.SPOT_SMA_TARGET_V1.versionId}).getByRole('button', {name: '选用', exact: true}).click();
    await mutation(page, () => detail.getByRole('button', {name: '绑定策略版本', exact: true}).click(),
        response => new URL(response.url()).pathname === `/api/backtest-configs/${configId}/strategy-version`);
    await detail.getByRole('button', {name: '选择数据集', exact: true}).click();
    await page.getByRole('dialog', {name: '选择行情数据集', exact: true}).getByRole('row')
        .filter({hasText: dataset.datasetId}).getByRole('button', {name: '选用', exact: true}).click();
    const bound = await mutation<{datasetSnapshotJson: string}>(page,
        () => detail.getByRole('button', {name: '绑定数据集', exact: true}).click(),
        response => new URL(response.url()).pathname === `/api/backtest-configs/${configId}/dataset`);
    const frozen = (JSON.parse(bound.datasetSnapshotJson) as {frozenInput: FrozenInput}).frozenInput;
    expect(frozen).toMatchObject({version: 'HISTORICAL_RESEARCH_INPUT_V2', barCount: 4320});
    expect(frozen.sha256).toMatch(/^[a-f0-9]{64}$/);
    expect(frozen.manifestSha256).toMatch(/^[a-f0-9]{64}$/);
    const runId = await createObject(page, 'BACKTEST_RUN', () => detail.getByRole('button', {name: '创建回测运行', exact: true}).click());
    const actions = page.getByTestId('backtest-run-actions');
    const started = Date.now();
    const run = await mutation<{status: string; backtestRunId: string}>(page,
        () => actions.getByRole('button', {name: '启动回测', exact: true}).click(),
        response => new URL(response.url()).pathname === `/api/backtest-runs/${runId}/start`);
    expect(run).toMatchObject({status: 'SUCCEEDED', backtestRunId: runId});
    const evaluation = await mutation<{state: string; result: {evaluationStatus: string; backtestRunId: string; evalReportId: string; netPnl: string | number}}>(page,
        () => actions.getByRole('button', {name: '评估此运行', exact: true}).click(),
        response => new URL(response.url()).pathname.startsWith('/api/backtest-evaluation-requests/'));
    expect(evaluation.state).toBe('SUCCEEDED');
    expect(evaluation.result).toMatchObject({evaluationStatus: 'SUCCEEDED', backtestRunId: runId});
    expect(evaluation.result.evalReportId).toBeTruthy();
    expect(evaluation.result.netPnl).not.toBeNull();
    const snapshots = page.waitForResponse(response => new URL(response.url()).pathname === `/api/backtest-runs/${runId}/pnl-snapshots`);
    await actions.getByRole('link', {name: '可视化', exact: true}).click();
    expect(await (await snapshots).json()).toHaveLength(4320);
    await expect(page.getByRole('heading', {name: '回测详情可视化', exact: true})).toBeVisible();
    const identity = page.getByTestId('frozen-research-input');
    await expect(identity.getByText(frozen.datasetVersionId, {exact: true})).toBeVisible();
    await expect(identity.getByText('4320', {exact: true})).toBeVisible();
    await expect(identity.getByText(frozen.sha256, {exact: true})).toBeVisible();
    await info.attach('long-history-ui-facts', {body: JSON.stringify({dataSource: fixture.dataSource,
        dataset, frozen, configId, runId, evaluationId: evaluation.result.evalReportId, executeAndEvaluateMillis: Date.now() - started}), contentType: 'application/json'});
    await info.attach('long-history-desktop', {body: await page.screenshot({fullPage: true}), contentType: 'image/png'});

    const writes: string[] = [];
    const observeWrite = (request: Request) => {
        if (['POST', 'PATCH', 'PUT', 'DELETE'].includes(request.method()) && request.url().includes('/api/') && !request.url().includes('/auth/'))
            writes.push(`${request.method()} ${new URL(request.url()).pathname}`);
    };
    page.on('request', observeWrite);
    await page.reload();
    await expect(page.getByTestId('frozen-research-input').getByText(frozen.datasetVersionId, {exact: true})).toBeVisible();
    expect(writes).toEqual([]);
    page.off('request', observeWrite);
    await page.setViewportSize({width: 390, height: 844});
    await expect(page.getByTestId('frozen-research-input').getByText('4320', {exact: true})).toBeVisible();
    await info.attach('long-history-mobile', {body: await page.screenshot({fullPage: true}), contentType: 'image/png'});
    await page.getByTestId('language-select').click();
    await page.getByText('English', {exact: true}).last().click();
    await expect(page.getByTestId('frozen-research-input').getByText('Frozen research input', {exact: true})).toBeVisible();
    await expect(page.getByTestId('frozen-research-input').getByText(frozen.datasetVersionId, {exact: true})).toBeVisible();
    await info.attach('long-history-English', {body: await page.screenshot({fullPage: true}), contentType: 'image/png'});
    writes.length = 0;
    page.on('request', observeWrite);
    await page.reload();
    await expect(page.getByTestId('frozen-research-input').getByText('Frozen research input', {exact: true})).toBeVisible();
    expect(writes).toEqual([]);
    page.off('request', observeWrite);
});

test('ETH90日：真实Adapter分页、完整Dataset与零缺口', async ({page}) => {
    const endExclusive = new Date(Date.parse(fixture.start) + 90 * 86_400_000).toISOString().slice(0, 10);
    await ingestAndCreateDataset(page, 'ETH-USDT', fixture.start.slice(0, 10), endExclusive, 2160);
});
