import {readFileSync, writeFileSync} from 'node:fs';
import {expect, test, type Page, type Request, type Response} from 'playwright/test';
import type {ForwardEvaluation, ObservationDetail, ObservationReviewDetail} from '../../src/features/paper-trading/api/observations';

interface Ready {
    port: number; source: 'SYNTHETIC_CONTROLLED_PUBLIC_FEED'; syntheticClock: true;
    experimentId: string; validationId: string; selectedTrialId: string; strategyVersionId: string; publishId: string;
    paperRunId: string; canonicalAccountId: number; observationFrom: string; observationTo: string; observedAt: string;
    forwardFrom: string; forwardTo: string; owner: string; otherUsername: string;
}
const readyPath = process.env.NQ_FORWARD_SIM_E2E_READY;
if (!readyPath) throw new Error('NQ_FORWARD_SIM_E2E_READY must identify the task-owned backend fixture');
const fixture = JSON.parse(readFileSync(readyPath, 'utf8')) as Ready;
if (fixture.source !== 'SYNTHETIC_CONTROLLED_PUBLIC_FEED' || fixture.syntheticClock !== true)
    throw new Error('This test requires explicitly disclosed synthetic market data and accelerated observation time');
const uuid = /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i;
const analysisPath = `/api/paper-trading/strategy-sim/analysis/runs/${fixture.paperRunId}`;
const evaluationPath = analysisPath + '/forward-evaluation';
const params = (extra: Record<string, string> = {}) => new URLSearchParams({analysisSource: 'sim', paperRunId: fixture.paperRunId,
    experimentId: fixture.experimentId, validationId: fixture.validationId, selectedTrialId: fixture.selectedTrialId,
    strategyVersionId: fixture.strategyVersionId, publishId: fixture.publishId,
    simFrom: fixture.observationFrom, simTo: fixture.observationTo, simAsOf: fixture.observedAt, ...extra});
const reviewUrl = (extra: Record<string, string> = {}) => '/paper-trading/reviews?' + params(extra).toString();
const isWrite = (request: Request) => request.url().includes('/api/')
    && ['POST', 'PUT', 'PATCH', 'DELETE'].includes(request.method()) && !new URL(request.url()).pathname.startsWith('/api/auth/');
const responseAt = (response: Response, path: string) => new URL(response.url()).pathname === path;

async function login(page: Page, username = fixture.owner) {
    await page.goto('/login');
    await page.getByLabel(/^(账号|Username)$/).fill(username);
    await page.getByLabel(/^(密码|Password)$/).fill('test-password');
    await page.getByRole('button', {name: /登\s*录|Sign in/}).click();
    await expect(page).toHaveURL(/dashboard$/, {timeout: 30_000});
    await page.getByRole('button', {name: /^(确认阅读|Confirm read)$/}).click();
    await page.getByTestId('language-select').click();
    await page.getByText('简体中文', {exact: true}).last().click();
}

async function readEvaluation(page: Page, action: () => Promise<unknown>, from = fixture.observationFrom, to = fixture.observationTo) {
    const pending = page.waitForResponse(response => responseAt(response, evaluationPath)
        && response.request().method() === 'GET' && new URL(response.url()).searchParams.get('from') === from
        && new URL(response.url()).searchParams.get('to') === to
        && new URL(response.url()).searchParams.get('asOf') === fixture.observedAt);
    await action();
    const response = await pending;
    expect(response.ok(), await response.text()).toBeTruthy();
    return response.json() as Promise<ForwardEvaluation>;
}

/** 只观察页面显式写请求；任何执行控制、订单或替代业务endpoint均立即暴露。 */
async function recordMutation<T>(page: Page, action: () => Promise<unknown>, matches: (path: string) => boolean) {
    const pending = page.waitForResponse(response => isWrite(response.request()) && matches(new URL(response.url()).pathname));
    await action();
    const response = await pending;
    expect(response.ok(), await response.text()).toBeTruthy();
    const operationId = new URL(response.url()).pathname.split('/').at(-1)!;
    expect(operationId).toMatch(uuid);
    return {operationId, input: response.request().postDataJSON(), result: await response.json() as T};
}

test.beforeEach(async ({page}, info) => {
    // 与正式隔离夹具一致的合成观察时钟；不放宽生产的回溯未来时间校验。
    await page.clock.setFixedTime(new Date(fixture.observedAt));
    info.annotations.push({type: 'data-source', description: fixture.source},
        {type: 'observation-time', description: 'SYNTHETIC_CLOCK_ADVANCE_NOT_REAL_WORLD_LONG_RUNNING_OBSERVATION'});
    await login(page);
});

test('真实Forward评价：创建观察、继续/停止研究复盘、冻结历史、只读恢复与访问隔离', async ({page}, info) => {
    const writes: string[] = [];
    const listener = (request: Request) => {if (isWrite(request)) writes.push(`${request.method()} ${new URL(request.url()).pathname}`);};
    page.on('request', listener);
    const evaluation = await readEvaluation(page, () => page.goto(reviewUrl()));
    expect(evaluation).toMatchObject({paperRunId: fixture.paperRunId, canonicalAccountId: fixture.canonicalAccountId,
        strategyVersionId: fixture.strategyVersionId, publishId: fixture.publishId, timeBasis: 'ACTUAL_EVENT_TIME'});
    expect(Date.parse(evaluation.observationFrom)).toBe(Date.parse(fixture.observationFrom));
    expect(Date.parse(evaluation.observationTo)).toBe(Date.parse(fixture.observationTo));
    expect(Date.parse(evaluation.observedAt)).toBe(Date.parse(fixture.observedAt));
    expect(evaluation.samples.expected).toBe(24);
    expect(evaluation.samples.observed + evaluation.samples.missing + evaluation.samples.unknown).toBe(24);
    expect(evaluation.checksum).toMatch(/^[a-f0-9]{64}$/);
    expect(evaluation.watermark).toMatch(/^[a-f0-9]{64}$/);
    const panel = page.getByTestId('forward-sim-observation');
    await expect(panel).toBeVisible();
    await expect(panel.getByText('实际估值覆盖', {exact: true})).toBeVisible();
    await expect(panel.getByText('accountPerformance.pnl', {exact: true})).toHaveCount(0);
    await expect(panel.getByText('短期盈利不证明长期统计优势。', {exact: false})).toBeVisible();
    await panel.getByText('冻结 SIM 成本假设', {exact: true}).click();
    await expect(panel.getByText('不是交易所实付费用', {exact: false})).toBeVisible();
    expect(evaluation.costAssumptions).toMatchObject({label: 'FROZEN_SIM_COST_ASSUMPTIONS', source: 'FROZEN_SIM_RUN_CONFIG',
        feeDisclosure: 'SIM_RECORDED_MODEL_CHARGE_NOT_REAL_EXCHANGE_FEE'});
    await panel.getByText('身份、方法与来源摘要', {exact: true}).click();
    await expect(panel.getByText(evaluation.checksum, {exact: true})).toBeVisible();
    const repeated = await readEvaluation(page, () => panel.getByRole('button', {name: '重读固定截止', exact: true}).click());
    expect(repeated).toEqual(evaluation);
    expect(writes).toEqual([]);
    await page.screenshot({path: info.outputPath('forward-evaluation-desktop.png'), fullPage: true});

    await panel.getByText('显式保存观察记录', {exact: true}).first().click();
    const created = await recordMutation<ObservationDetail>(page,
        () => panel.getByRole('button', {name: '显式保存观察记录', exact: true}).click(),
        path => /^\/api\/strategy-observations\/operations\/[^/]+$/.test(path));
    const observation = created.result.observation;
    expect(observation).toMatchObject({paperRunId: fixture.paperRunId, kind: 'RETROSPECTIVE', status: 'OPEN', revision: 0,
        validationId: fixture.validationId, selectedTrialId: fixture.selectedTrialId, strategyVersionId: fixture.strategyVersionId});
    expect(created.input.kind).toBe('RETROSPECTIVE');
    await expect(page).toHaveURL(new RegExp(`observationId=${observation.observationId}`));
    await panel.getByLabel('理由与说明', {exact: true}).fill('合成夹具：继续研究观察，不改变SIM执行。');
    const continued = await recordMutation<ObservationReviewDetail>(page,
        () => panel.getByRole('button', {name: '冻结评价并保存人工决定', exact: true}).click(),
        path => new RegExp(`^/api/strategy-observations/${observation.observationId}/reviews/operations/[^/]+$`).test(path));
    expect(continued.result.review).toMatchObject({observationId: observation.observationId, decision: 'CONTINUE_OBSERVATION', revision: 1,
        evaluationChecksum: evaluation.checksum});
    expect(continued.result.evaluation).toEqual(evaluation);
    await expect(panel.getByText('正在读取原人工复盘冻结的评价', {exact: false})).toBeVisible();
    expect(writes).toHaveLength(2);

    writes.length = 0;
    const frozenPath = `/api/strategy-observations/${observation.observationId}/reviews/${continued.result.review.reviewId}`;
    const frozenRead = page.waitForResponse(response => responseAt(response, frozenPath) && response.request().method() === 'GET');
    await page.reload();
    const frozenResponse = await frozenRead;
    expect(frozenResponse.ok()).toBeTruthy();
    expect((await frozenResponse.json()).evaluation).toEqual(evaluation);
    await expect(panel.getByText('正在读取原人工复盘冻结的评价', {exact: false})).toBeVisible();
    expect(writes).toEqual([]);

    await readEvaluation(page, () => page.goto(reviewUrl({observationId: observation.observationId})));
    await expect(panel.getByRole('button', {name: '冻结评价并保存人工决定', exact: true})).toBeEnabled();
    await panel.getByLabel('人工研究决定', {exact: true}).press('ArrowDown');
    await page.getByText('停止研究观察（不停止 SIM）', {exact: true}).last().click();
    await panel.getByLabel('理由与说明', {exact: true}).fill('仅关闭研究记录；不暂停SIM、撤单或平仓。');
    const stopped = await recordMutation<ObservationReviewDetail>(page,
        () => panel.getByRole('button', {name: '冻结评价并保存人工决定', exact: true}).click(),
        path => new RegExp(`^/api/strategy-observations/${observation.observationId}/reviews/operations/[^/]+$`).test(path));
    expect(stopped.operationId).not.toBe(continued.operationId);
    expect(stopped.result.review).toMatchObject({decision: 'STOP_OBSERVATION', revision: 2, observationId: observation.observationId});
    await expect(panel.getByText('2 · CLOSED · RETROSPECTIVE', {exact: true})).toBeVisible();
    expect(writes).toHaveLength(1);
    expect(writes.every(path => /^POST \/api\/strategy-observations\/[^/]+\/reviews\/operations\//.test(path))).toBe(true);
    const stoppedUrl = page.url();

    writes.length = 0;
    await page.setViewportSize({width: 390, height: 844});
    await page.reload();
    await expect(panel.getByText('2 · CLOSED · RETROSPECTIVE', {exact: true})).toBeVisible();
    await expect(panel.getByRole('button', {name: '冻结评价并保存人工决定', exact: true})).toBeDisabled();
    await page.screenshot({path: info.outputPath('forward-review-mobile-zh.png'), fullPage: true});
    expect(writes).toEqual([]);
    // 语言偏好是用户显式设置；其请求不计入下面独立的纯读取窗口。
    page.off('request', listener);
    await page.getByTestId('language-select').click();
    await page.getByText('English', {exact: true}).last().click();
    await expect(panel.getByText('Forward SIM evaluation and manual review', {exact: true})).toBeVisible();
    writes.length = 0; page.on('request', listener);
    await page.reload();
    await expect(panel.getByText('Viewing the evaluation frozen by this manual review.', {exact: false})).toBeVisible();
    expect(new URL(page.url()).searchParams.get('simAsOf')).toBe(fixture.observedAt);
    expect(writes).toEqual([]);
    await page.screenshot({path: info.outputPath('forward-review-mobile-en.png'), fullPage: true});

    const sourceLink = panel.getByRole('link', {name: 'Return to original experiment', exact: true});
    expect(await sourceLink.getAttribute('href')).toContain(fixture.experimentId);
    await sourceLink.click();
    await expect(page).toHaveURL(new RegExp(`/experiments\\?experimentId=${fixture.experimentId}`));
    expect(writes).toEqual([]);
    page.off('request', listener);
    await page.getByRole('button', {name: /^(退出登录|Sign out)$/}).click();
    await login(page, fixture.otherUsername);
    writes.length = 0; page.on('request', listener);
    const deniedRead = page.waitForResponse(response => responseAt(response, analysisPath) && response.request().method() === 'GET');
    await page.goto(stoppedUrl);
    const denied = await deniedRead;
    expect([403, 404]).toContain(denied.status());
    await expect(page.getByTestId('forward-sim-observation')).toHaveCount(0);
    await expect(page.getByText(evaluation.checksum, {exact: true})).toHaveCount(0);
    expect(writes).toEqual([]);
    await page.screenshot({path: info.outputPath('forward-review-other-owner-denied.png'), fullPage: true});
    writeFileSync(info.outputPath('forward-review-facts.json'), JSON.stringify({source: fixture.source, syntheticClock: true,
        realWorldLongRunningObservation: false, paperRunId: fixture.paperRunId, canonicalAccountId: fixture.canonicalAccountId,
        evaluation, observation, operations: [created.operationId, continued.operationId, stopped.operationId],
        reviews: [continued.result.review, stopped.result.review], wrongOwnerStatus: denied.status(), unexpectedBusinessWrites: writes}, null, 2));
});

test('未到期窗口只读显示样本不足，不创建观察或执行事实', async ({page}, info) => {
    const writes: string[] = [];
    page.on('request', request => {if (isWrite(request)) writes.push(`${request.method()} ${new URL(request.url()).pathname}`);});
    const result = await readEvaluation(page, () => page.goto(reviewUrl({simFrom: fixture.forwardFrom, simTo: fixture.forwardTo})), fixture.forwardFrom, fixture.forwardTo);
    expect(result.status).toBe('INSUFFICIENT_SAMPLES');
    expect(result.samples.expected).toBe(0);
    expect(result.performance.pnl).toBeNull();
    expect(result.findings.some(finding => finding.code === 'OBSERVATION_PENDING' && finding.reason === 'OBSERVATION_NOT_STARTED')).toBe(true);
    const panel = page.getByTestId('forward-sim-observation');
    await expect(panel.getByText('样本不足', {exact: true}).first()).toBeVisible();
    await expect(panel.getByText('观察尚未开始', {exact: true}).first()).toBeVisible();
    expect(writes).toEqual([]);
    await page.screenshot({path: info.outputPath('forward-not-due-insufficient.png'), fullPage: true});
    writeFileSync(info.outputPath('forward-insufficient-facts.json'), JSON.stringify({source: fixture.source, syntheticClock: true, result, writes}, null, 2));
});
