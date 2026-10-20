import {expect, test, type BrowserContext} from 'playwright/test';

test.afterEach(async ({page}, info) => {
    if (info.status !== info.expectedStatus && !page.isClosed()) {
        await info.attach('visible-page', {
            body: await page.locator('body').innerText({timeout: 3000}),
            contentType: 'text/plain',
        });
        await page.screenshot({
            path: info.outputPath('failure-page.png'),
            fullPage: true,
            timeout: 5000,
            animations: 'disabled',
        });
    }
});

const runId = 'ptr-11111111-1111-4111-8111-111111111111';
const operationId = '22222222-2222-4222-8222-222222222222';
const publishId = 'pub-continuous-original';
const progress = (status = 'RUNNING') => ({
    paperRunId: runId,
    status,
    startedAt: '2026-10-15T00:00:00Z',
    lastPollAt: '2026-10-15T00:01:00Z',
    lastSuccessfulPollAt: null,
    lastProcessedBar: '2026-10-14T23:00:00Z',
    nextExpectedBar: '2026-10-15T00:00:00Z',
    lastObservedBar: '2026-10-15T01:00:00Z',
    gapStartBar: null,
    lastDecisionId: null,
    lastDecisionStatus: null,
    blockReason: status === 'STOPPED' ? 'STOPPED' : null,
    consecutivePollFailures: 0,
});

/** 这里只证明浏览器状态与真实Web Locks；隔离接口替身不代表正式命令业务验收。 */
async function fixture(
    context: BrowserContext,
    options: {pending?: 'CREATE' | 'PAUSE'; locale?: 'zh-CN' | 'en-US'; corrupt?: boolean} = {},
) {
    const state = {
        userId: 17,
        roles: ['OPERATOR', 'VIEWER'],
        mode: 'missing' as 'missing' | 'unavailable' | 'found',
        capability: true,
        parent: 'RUNNING',
        cursor: progress('RUNNING') as ReturnType<typeof progress> | null,
        cursorError: false,
        dropNextPost: false,
        holdPost: false,
        badNotifications: false,
    };
    const posts: Record<string, unknown>[] = [];
    const reads: string[] = [];
    const receipts = new Map<string, Record<string, unknown>>();
    let releasePost: (() => void) | undefined;
    const original = {
        operationId,
        type: options.pending ?? 'CREATE',
        ...(options.pending === 'PAUSE' ? {paperRunId: runId} : {publishId, budget: '100'}),
    };
    function receipt(request: Record<string, unknown>) {
        return {
            operationId: request.operationId,
            type: request.type,
            publishId: request.type === 'CREATE' ? request.publishId : null,
            budget: request.type === 'CREATE' ? '100' : null,
            paperRunId: runId,
            status: 'COMPLETED',
            result: progress(request.type === 'PAUSE' ? 'STOPPED' : 'RUNNING'),
            completedAt: '2026-10-15T00:02:00Z',
        };
    }
    if (options.pending) receipts.set(operationId, receipt(original));
    await context.addInitScript(
        ({pending, corrupt, original, locale}) => {
            localStorage.setItem(
                'nexus-quant.console.auth',
                JSON.stringify({
                    accessToken: 'continuous-browser-fixture',
                    tokenType: 'Bearer',
                    expiresAt: '2099-01-01T00:00:00Z',
                    username: 'continuous-fixture',
                    roles: ['OPERATOR', 'VIEWER'],
                }),
            );
            localStorage.setItem('nq.locale', locale);
            if (!localStorage.getItem('nq:e2e-continuous-seeded')) {
                if (pending || corrupt)
                    localStorage.setItem(
                        'nq:continuous-operation:v1:17',
                        corrupt
                            ? 'corrupt'
                            : JSON.stringify({
                                  ownerUserId: 17,
                                  request: original,
                                  phase: 'SUBMITTING',
                                  recordedAt: Date.now(),
                              }),
                    );
                localStorage.setItem('nq:e2e-continuous-seeded', 'yes');
            }
        },
        {
            pending: options.pending,
            corrupt: options.corrupt,
            original,
            locale: options.locale ?? 'zh-CN',
        },
    );
    const row = () => ({
        paperRunId: runId,
        publishId,
        strategyVersionId: 'sv-continuous',
        canonicalAccountId: 2,
        status: state.parent,
        tradeEnv: 'SIM',
        exchangeCode: 'OKX',
        marketType: 'SPOT',
        symbol: 'BTC-USDT',
        intervalCode: '1h',
        startedAt: '2026-10-15T00:00:00Z',
        stoppedAt: null,
        publishSnapshotJson: '{}',
        strategyVersionSnapshotJson: '{}',
        datasetSnapshotJson: '{}',
        paramSnapshotJson: '{}',
        configSnapshotJson: '{}',
        createdBy: 'continuous-fixture',
        createdAt: '2026-10-15T00:00:00Z',
        updatedAt: '2026-10-15T00:00:00Z',
    });
    await context.route('**/api/**', async (route) => {
        const path = new URL(route.request().url()).pathname;
        if (!path.startsWith('/api/')) return route.fallback();
        const reply = (json: unknown, status = 200) => route.fulfill({json, status});
        if (path === '/api/auth/me')
            return reply({
                userId: state.userId,
                username: 'continuous-fixture',
                authenticated: true,
                roles: state.roles,
            });
        if (path === '/api/user/preferences')
            return reply({
                version: 0,
                locale: options.locale ?? 'zh-CN',
                hideLoginDisclosures: true,
                defaultMarket: null,
                watchlist: [],
                notificationsReadAt: null,
            });
        if (path === '/api/user/notifications')
            return reply(
                state.badNotifications
                    ? []
                    : {
                          items: [],
                          offset: 0,
                          hasMore: false,
                          windowLimited: false,
                          checkedAt: '2026-10-15T00:00:00Z',
                          scope: 'OWNED_SIM_RUN_EVENTS',
                      },
            );
        if (path === '/api/publishes')
            return reply([
                {
                    publishRecordId: publishId,
                    publishName: 'Continuous SMA',
                    publishStatus: 'SUCCEEDED',
                    strategyVersionId: 'sv-continuous',
                },
            ]);
        if (path.endsWith('/continuous-capabilities'))
            return reply({commandsAvailable: state.capability});
        if (path === '/api/paper-trading/strategy-sim/continuous-operations') {
            const request = route.request().postDataJSON() as Record<string, unknown>;
            posts.push(request);
            const result = receipts.get(String(request.operationId)) ?? receipt(request);
            receipts.set(String(request.operationId), result);
            state.cursor = progress(request.type === 'PAUSE' ? 'STOPPED' : 'RUNNING');
            if (state.holdPost) {
                await new Promise<void>((resolve) => {
                    releasePost = resolve;
                });
                // 原标签被明确关闭后，只结束该已取消的网络响应；原回执仍由测试保存供第二标签查询。
                try {
                    await route.abort('failed');
                } catch {
                    /* 页面关闭会先取消此请求。 */
                }
                return;
            }
            if (state.dropNextPost) {
                state.dropNextPost = false;
                return route.abort('failed');
            }
            return reply(result);
        }
        if (path.includes('/continuous-operations/')) {
            reads.push(path);
            if (state.mode === 'unavailable')
                return reply({code: 'UNAVAILABLE', message: 'synthetic read unavailable'}, 503);
            if (state.mode === 'missing')
                return reply({code: 'NOT_FOUND', message: 'synthetic result missing'}, 404);
            return reply(receipts.get(path.split('/').at(-1)!) ?? {}, 200);
        }
        if (path === `/api/paper-trading/runs/${runId}`) return reply(row());
        if (path.endsWith(`/${runId}/continuous`))
            return state.cursorError ? reply({code: 'UNAVAILABLE'}, 503) : reply(state.cursor);
        if (path.endsWith(`/${runId}/facts`))
            return reply({
                ...row(),
                inputSha256: 'a'.repeat(64),
                budget: '100',
                cash: '100',
                positionQuantity: '0',
                markPrice: '100',
                equity: '100',
                pnl: '0',
                orders: [],
                trades: [],
                ledgerEntries: [],
            });
        if (path.endsWith(`/${runId}/decisions`)) return reply([]);
        return reply([]);
    });
    return {state, posts, reads, original, receipts, release: () => releasePost?.()};
}

test('恢复只GET原结果，原创建快照不覆盖当前已暂停进度', async ({page, context}) => {
    const h = await fixture(context, {pending: 'CREATE'});
    h.state.mode = 'found';
    h.state.cursor = progress('STOPPED');
    const errors: string[] = [];
    page.on('pageerror', (error) => errors.push(error.message));
    await page.setViewportSize({width: 1536, height: 1024});
    await page.goto(`/paper-trading/runs?paperRunId=${runId}`);
    await expect(page).toHaveTitle(/NexusQuant|NQ/i);
    await expect(page.getByText('创建持续模拟已确认完成', {exact: true})).toBeVisible();
    const panel = page.getByTestId('continuous-sim-status');
    await expect(panel.getByText('连续处理已暂停', {exact: true})).toBeVisible();
    await expect(page.getByTestId('continuous-operation-recovery')).toHaveCount(0);
    expect(
        await page.evaluate(() => localStorage.getItem('nq:continuous-operation:v1:17')),
    ).toBeNull();
    expect(h.posts).toHaveLength(0);
    expect(h.reads).toHaveLength(1);
    await panel.getByText('运行来源与原始状态', {exact: true}).click();
    await expect(panel.getByText(runId, {exact: true})).toBeVisible();
    await panel.screenshot({path: test.info().outputPath('continuous-current-paused-desktop.png')});
    await page.getByRole('button', {name: '查看该运行', exact: true}).click();
    await expect(page).toHaveURL(new RegExp(`paperRunId=${runId}`));
    expect(errors).toEqual([]);
});

test('404显式同编号继续，丢响应和503刷新保持未知且不重发', async ({page, context}) => {
    const h = await fixture(context, {pending: 'PAUSE'});
    await page.goto(`/paper-trading/runs?paperRunId=${runId}`);
    const recovery = page.getByTestId('continuous-operation-recovery');
    await expect.poll(() => h.reads.length).toBeGreaterThan(0);
    await expect(recovery.getByRole('button', {name: '继续同一操作', exact: true})).toBeEnabled();
    expect(h.posts).toHaveLength(0);
    await recovery.getByRole('button', {name: '继续同一操作', exact: true}).click();
    await page
        .getByRole('dialog')
        .getByRole('button', {name: /取\s*消/})
        .click();
    expect(h.posts).toHaveLength(0);
    h.state.dropNextPost = true;
    h.state.mode = 'unavailable';
    await recovery.getByRole('button', {name: '继续同一操作', exact: true}).click();
    await page.getByRole('dialog').getByRole('button', {name: '继续同一操作', exact: true}).click();
    await expect.poll(() => h.posts.length).toBe(1);
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await page.reload();
    await expect(recovery).toBeVisible();
    await expect(recovery.getByRole('button', {name: '继续同一操作', exact: true})).toBeDisabled();
    expect(h.posts).toEqual([h.original]);
    h.state.mode = 'found';
    await recovery.getByRole('button', {name: '查询原操作结果', exact: true}).click();
    await expect(recovery).toHaveCount(0);
    await expect(page.getByText('暂停连续处理已确认完成', {exact: true})).toBeVisible();
    expect(h.posts).toEqual([h.original]);
});

test('原标签中断后，第二标签查询原编号；原生跨标签锁阻止第二次创建', async ({page, context}) => {
    const h = await fixture(context);
    h.state.holdPost = true;
    h.state.mode = 'found';
    await page.goto('/paper-trading/runs');
    await page.getByRole('combobox', {name: /已发布策略$/}).focus();
    await page.getByRole('combobox', {name: /已发布策略$/}).press('ArrowDown');
    await page.getByTitle('Continuous SMA · sv-continuous', {exact: true}).click();
    await page.getByRole('textbox', {name: /预算（USDT）$/}).fill('100');
    await page.getByRole('button', {name: '启动连续模拟', exact: true}).click();
    await expect.poll(() => h.posts.length).toBe(1);
    const second = await context.newPage();
    await second.goto(`/paper-trading/runs?paperRunId=${runId}`);
    const recovery = second.getByTestId('continuous-operation-recovery');
    await expect(recovery).toBeVisible();
    await expect(recovery.getByText(/另一页面正在提交/)).toBeVisible();
    await expect(second.getByRole('button', {name: '继续同一操作', exact: true})).toBeDisabled();
    await page.close();
    h.release();
    await expect(recovery).toHaveCount(0);
    await expect(second.getByText('创建持续模拟已确认完成', {exact: true})).toBeVisible();
    expect(h.posts).toHaveLength(1);
    await second.close();
});

test('切换用户不读取旧主体编号；权限降级只可查询，损坏存储关闭提交', async ({page, context}) => {
    const h = await fixture(context, {pending: 'CREATE'});
    await page.goto(`/paper-trading/runs?paperRunId=${runId}`);
    const recovery = page.getByTestId('continuous-operation-recovery');
    await expect(recovery.getByRole('button', {name: '继续同一操作', exact: true})).toBeEnabled();
    const originalReads = h.reads.length;
    h.state.userId = 18;
    await page.reload();
    await expect(page.getByTestId('continuous-sim-status')).toBeVisible();
    await expect(recovery).toHaveCount(0);
    expect(h.reads).toHaveLength(originalReads);
    expect(
        await page.evaluate(() => localStorage.getItem('nq:continuous-operation:v1:17')),
    ).not.toBeNull();
    h.state.userId = 17;
    h.state.roles = ['VIEWER'];
    await page.reload();
    await expect(recovery.getByRole('button', {name: '查询原操作结果', exact: true})).toBeEnabled();
    await expect(recovery.getByRole('button', {name: '继续同一操作', exact: true})).toBeDisabled();
    await page.evaluate(() => localStorage.setItem('nq:continuous-operation:v1:17', 'corrupt'));
    await page.reload();
    await expect(page.getByText(/无法可靠读取或保存待确认操作/)).toBeVisible();
    expect(h.posts).toHaveLength(0);
});

test('保存进度的空、失败、未知、修订与父运行边界不伪装成功', async ({page, context}) => {
    const h = await fixture(context);
    h.state.capability = false;
    await page.goto(`/paper-trading/runs?paperRunId=${runId}`);
    const panel = page.getByTestId('continuous-sim-status');
    await expect(panel.getByRole('button', {name: '暂停连续处理', exact: true})).toBeDisabled();
    h.state.cursor = {
        ...progress('STALLED'),
        blockReason: 'EXPECTED_BAR_MISSING',
        gapStartBar: '2026-10-15T00:00:00Z',
        consecutivePollFailures: 3,
    };
    await panel.getByRole('button', {name: '刷新持续状态', exact: true}).click();
    await expect(panel.getByText('持续处理受阻', {exact: true})).toBeVisible();
    h.state.cursorError = true;
    await panel.getByRole('button', {name: '刷新持续状态', exact: true}).click();
    await expect(panel.getByText('上次读取的状态，当前进度待确认', {exact: true})).toBeVisible();
    await expect(panel.getByRole('button', {name: '暂停连续处理', exact: true})).toBeDisabled();
    h.state.cursorError = false;
    h.state.cursor = progress('FUTURE_STATE');
    await panel.getByRole('button', {name: '刷新持续状态', exact: true}).click();
    await expect(panel.getByText('状态待核查', {exact: true})).toBeVisible();
    await expect(panel.getByRole('button', {name: /暂停连续处理|恢复连续处理/})).toHaveCount(0);
    h.state.cursor = {...progress('STOPPED'), blockReason: 'DATA_REVISION_DETECTED'};
    await panel.getByRole('button', {name: '刷新持续状态', exact: true}).click();
    await expect(panel.getByText('已观察数据发生修订，不能直接恢复', {exact: true})).toBeVisible();
    await expect(panel.getByRole('button', {name: '恢复连续处理', exact: true})).toHaveCount(0);
    h.state.parent = 'STOPPED';
    h.state.cursor = progress('STOPPED');
    await page.reload();
    await expect(
        panel.getByText('父 SIM 未运行，不能从此处恢复处理。', {exact: true}),
    ).toBeVisible();
    h.state.parent = 'RUNNING';
    h.state.cursor = null;
    await page.reload();
    await expect(panel.getByText('此运行尚未建立持续处理游标', {exact: true})).toBeVisible();
    h.state.cursorError = true;
    await page.reload();
    await expect(
        panel.getByText('持续状态读取失败，无法确认当前进度', {exact: true}),
    ).toBeVisible();
    await expect(panel.getByText('此运行尚未建立持续处理游标', {exact: true})).toHaveCount(0);
    expect(h.posts).toHaveLength(0);
});

test('英文390宽度下原编号和完整来源可读，恢复确认仍保留同一操作', async ({page, context}) => {
    const h = await fixture(context, {pending: 'CREATE', locale: 'en-US'});
    await page.setViewportSize({width: 390, height: 950});
    await page.goto(`/paper-trading/runs?paperRunId=${runId}`);
    const recovery = page.getByTestId('continuous-operation-recovery');
    await expect(
        recovery.getByRole('button', {name: 'Continue same operation', exact: true}),
    ).toBeEnabled();
    await expect(recovery.getByText(operationId, {exact: true})).toBeVisible();
    const panel = page.getByTestId('continuous-sim-status');
    await panel.getByText('Run source and raw status', {exact: true}).click();
    await expect(panel.getByText(runId, {exact: true})).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
        390,
    );
    expect(
        await panel.locator('time').evaluateAll((nodes) => {
            const container = nodes[0]?.closest('section')?.getBoundingClientRect();
            return (
                Boolean(container) &&
                nodes.every((node) => {
                    const box = node.getBoundingClientRect();
                    return box.left >= container!.left && box.right <= container!.right;
                })
            );
        }),
    ).toBe(true);
    await recovery.screenshot({path: test.info().outputPath('continuous-recovery-en-390.png')});
    await panel
        .locator('.ant-collapse')
        .screenshot({path: test.info().outputPath('continuous-source-en-390.png')});
    expect(h.posts).toHaveLength(0);
});

test('损坏的通知响应只显示读取失败，不让运行工作区崩溃或伪装为空通知', async ({page, context}) => {
    const h = await fixture(context);
    h.state.badNotifications = true;
    await page.goto(`/paper-trading/runs?paperRunId=${runId}`);
    await expect(page.getByTestId('continuous-sim-status')).toBeVisible();
    await page.getByRole('button', {name: '打开通知', exact: true}).click();
    const drawer = page.getByRole('dialog', {name: '通知', exact: true});
    await expect(drawer.getByText(/NOTIFICATION_RESPONSE_INVALID/)).toBeVisible();
    await expect(page.getByRole('heading', {name: '控制台遇到一个错误'})).toHaveCount(0);
    expect(h.posts).toHaveLength(0);
});
