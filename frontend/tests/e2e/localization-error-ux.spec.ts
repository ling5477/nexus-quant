import {expect, test, type Page} from 'playwright/test';

/**
 * 前端协议及交互 E2E：所有 HTTP fixture 均由 page.route 隔离，不声称真实后端或交易资格证明。
 * 通过真实表单、语言选择器和错误展示验证本地化，不直接调用应用内部翻译或错误处理函数。
 */
const rawDiagnostic = 'JDBC SQL internal stack: private-diagnostic-must-not-render';
const account = {
    exchangeAccountId: 71, legacyAccountId: 1701, exchangeCode: 'OKX', tradeEnv: 'SIM',
    accountAlias: 'locale-fixture', externalAccountRef: null, isDefault: true, status: 'ENABLED',
};
const labels = {
    'zh-CN': {username: '账号', password: '密码', login: /登\s*录/, create: '新建账户', alias: '账户别名',
        save: /保\s*存/, cancel: /取\s*消/, cancelOrder: '撤单', submitCancellation: '执行撤单',
        orderId: '订单 ID', reason: '撤单原因', search: /查\s*询/, createBacktest: '新建回测配置', start: '开始时间'},
    'en-US': {username: 'Username', password: 'Password', login: /Sign in$/, create: 'Create account', alias: 'Account alias',
        save: /^Save$/, cancel: /^Cancel$/, cancelOrder: 'Cancel order', submitCancellation: 'Submit cancellation',
        orderId: 'Order ID', reason: 'Cancellation reason', search: /^Search$/, createBacktest: 'Create backtest configuration', start: 'Start time'},
} as const;
type Locale = keyof typeof labels;

async function changeLocale(page: Page, locale: Locale) {
    await page.getByTestId('language-select').click();
    // AntD 虚拟列表的 role=option 是辅助技术节点，点击实际可见选项。
    await page.locator('.ant-select-dropdown:visible').getByText(locale === 'en-US' ? 'English' : '简体中文', {exact: true}).click();
    await expect(page.locator('html')).toHaveAttribute('lang', locale);
}

async function installFixtures(page: Page) {
    const seen = {logins: [] as unknown[], cancellations: [] as Record<string, unknown>[], orderGets: [] as string[], creates: 0};
    await page.route('**/api/**', async (route) => {
        const request = route.request();
        const url = new URL(request.url());
        // Vite 的 /src/api/*.ts 是源码资源，不能被 HTTP 协议 fixture 截获。
        if (!url.pathname.startsWith('/api/')) return route.fallback();
        const reply = (body: unknown, status = 200) => route.fulfill({status, json: body});
        if (url.pathname === '/api/auth/login') {
            seen.logins.push(request.postDataJSON());
            return reply({accessToken: 'local-browser-fixture-token', tokenType: 'Bearer', expiresIn: 3600,
                expiresAt: new Date(Date.now() + 3600_000).toISOString(), username: 'fixture-operator', roles: ['ADMIN', 'OPERATOR', 'VIEWER']});
        }
        if (url.pathname === '/api/auth/me') return reply({userId: 17, username: 'fixture-operator',
            roles: ['ADMIN', 'OPERATOR', 'VIEWER'], authenticated: true, defaultExchangeAccountId: 71,
            defaultExchangeCode: 'OKX', defaultTradeEnv: 'SIM', defaultAccountAlias: 'locale-fixture'});
        if (url.pathname === '/api/exchange-accounts' && request.method() === 'GET') {
            return reply(Array.from({length: 12}, (_, index) => ({...account, exchangeAccountId: 71 + index,
                accountAlias: `locale-fixture-${index}`, isDefault: index === 0})));
        }
        if (url.pathname === '/api/exchange-accounts' && request.method() === 'POST') {
            seen.creates += 1;
            return reply({status: 500, code: 'INTERNAL_ERROR', message: rawDiagnostic, traceId: 'trace-global-500'}, 500);
        }
        if (url.pathname === '/api/trading/orders/cancel') {
            seen.cancellations.push(request.postDataJSON());
            return reply({status: 409, code: 'STATE_CONFLICT', errorKey: 'ORDER_VERSION_CONFLICT', errorId: 'NQ-TRD-1001',
                message: rawDiagnostic, traceId: 'trace-order-conflict', fieldErrors: []}, 409);
        }
        if (url.pathname === '/api/trading/orders' && request.method() === 'GET') {
            seen.orderGets.push(url.search);
            return reply({items: [], page: 0, size: 20, total: 0});
        }
        if (['/api/backtest-configs', '/api/paper-trading/runs'].includes(url.pathname)) return reply([]);
        return reply({status: 404, code: 'RESOURCE_NOT_FOUND', message: 'fixture route unavailable'}, 404);
    });
    return seen;
}

async function login(page: Page, locale: Locale = 'zh-CN', redirect = '/accounts') {
    await page.goto(`/login?redirect=${encodeURIComponent(redirect)}`);
    if (locale === 'en-US') await changeLocale(page, locale);
    const copy = labels[locale];
    await page.getByLabel(copy.username, {exact: true}).fill('fixture-operator');
    await page.getByLabel(copy.password, {exact: true}).fill('fixture-password');
    await page.getByRole('button', {name: copy.login}).click();
    await expect(page).toHaveURL(new RegExp(`${redirect}$`));
    await expect(page.getByTestId('language-select')).toBeVisible();
}

test.describe('localization and error UX with isolated HTTP fixtures', () => {
    test('default Chinese, bilingual required fields, English login and reload persistence', async ({page}) => {
        const seen = await installFixtures(page);
        await page.goto('/login?redirect=%2Faccounts');
        await expect(page.locator('html')).toHaveAttribute('lang', 'zh-CN');
        await expect(page.getByRole('heading', {name: '登录控制台'})).toBeVisible();
        await page.getByRole('button', {name: labels['zh-CN'].login}).click();
        await expect(page.getByText('请输入账号', {exact: true})).toBeVisible();
        await expect(page.getByText('请输入密码', {exact: true})).toBeVisible();
        expect(seen.logins).toHaveLength(0);
        await changeLocale(page, 'en-US');
        await expect(page.getByText('Enter your username', {exact: true})).toBeVisible();
        await expect(page.getByText('Enter your password', {exact: true})).toBeVisible();
        await expect(page.getByText('请输入账号', {exact: true})).not.toBeVisible();
        await page.getByLabel('Username', {exact: true}).fill('preserved-input');
        await changeLocale(page, 'zh-CN');
        await expect(page.getByLabel('账号', {exact: true})).toHaveValue('preserved-input');
        await changeLocale(page, 'en-US');
        await expect(page.getByLabel('Username', {exact: true})).toHaveValue('preserved-input');
        await page.reload();
        await expect(page.getByRole('heading', {name: 'Sign in to the console'})).toBeVisible();
        expect(await page.evaluate(() => localStorage.getItem('nq.locale'))).toBe('en-US');
        await page.getByLabel('Username', {exact: true}).fill('fixture-operator');
        await page.getByLabel('Password', {exact: true}).fill('fixture-password');
        await page.getByRole('button', {name: /Sign in$/}).click();
        await expect(page.getByRole('heading', {name: 'Accounts and credentials'})).toBeVisible();
        expect(seen.logins).toEqual([{username: 'fixture-operator', password: 'fixture-password'}]);
        await changeLocale(page, 'zh-CN');
        await expect(page.getByRole('heading', {name: '账户与凭证管理'})).toBeVisible();
        await page.reload();
        await expect(page.locator('html')).toHaveAttribute('lang', 'zh-CN');
        await expect(page.getByRole('heading', {name: '账户与凭证管理'})).toBeVisible();
    });

    for (const locale of ['zh-CN', 'en-US'] as const) {
        test(`${locale}: global 500 notification is localized and retains traceId`, async ({page}) => {
            const seen = await installFixtures(page);
            await login(page, locale);
            const copy = labels[locale];
            await page.getByRole('button', {name: copy.create, exact: true}).click();
            const drawer = page.getByRole('dialog', {name: copy.create, exact: true});
            await drawer.getByLabel(copy.alias, {exact: true}).fill('failed-create-fixture');
            await drawer.getByRole('button', {name: copy.save}).click();
            const notice = page.locator('.ant-notification-notice');
            await expect(notice).toContainText(locale === 'zh-CN' ? '服务异常' : 'Service unavailable');
            await expect(notice).toContainText('trace-global-500');
            await expect(notice).toContainText(locale === 'zh-CN'
                ? '服务暂时无法完成请求，请保留追踪编号以便排查。'
                : 'The service could not complete the request. Keep the trace ID for support.');
            await expect(page.locator('body')).not.toContainText(rawDiagnostic);
            expect(seen.creates).toBe(1);
        });
    }

    test('canonical conflict requires user refresh and preserves the exact cancellation payload across locales', async ({page}) => {
        const seen = await installFixtures(page);
        await login(page, 'zh-CN', '/trading');
        await expect.poll(() => seen.orderGets.length).toBe(1);
        for (const [index, locale] of (['zh-CN', 'en-US'] as const).entries()) {
            if (locale === 'en-US') await changeLocale(page, locale);
            const copy = labels[locale];
            await page.getByRole('button', {name: locale === 'zh-CN' ? /撤\s*单/ : copy.cancelOrder, exact: true}).click();
            const drawer = page.getByRole('dialog', {name: copy.cancelOrder, exact: true});
            // 页面查询表单与抽屉都有 orderId；限定到协议字段避免重复 DOM id 的 label 关联歧义。
            await drawer.locator('input[id="orderId"]').fill('ord-locale-71');
            await drawer.getByLabel(locale === 'zh-CN' ? '客户端订单 ID' : 'Client order ID', {exact: true}).fill('client-locale-identity');
            await drawer.getByLabel(copy.reason).fill('operator-reviewed-reason');
            await drawer.getByRole('button', {name: copy.submitCancellation, exact: true}).click();
            const feedback = page.locator('.ant-message-notice').last();
            await expect(feedback).toContainText(locale === 'zh-CN'
                ? '订单已被其他操作更新，请刷新最新状态后重试。'
                : 'The order was updated by another operation. Refresh the latest state before trying again.');
            await expect(feedback).toContainText('NQ-TRD-1001');
            await expect(feedback).toContainText('ORDER_VERSION_CONFLICT');
            await expect(feedback).toContainText('trace-order-conflict');
            await expect(page.locator('body')).not.toContainText(rawDiagnostic);
            // 有界观察超过默认首轮重试延迟，断言既没有重放写请求，也没有偷偷刷新读取。
            await page.waitForTimeout(1500);
            expect(seen.cancellations).toHaveLength(index + 1);
            expect(seen.orderGets).toHaveLength(1);
            await drawer.getByRole('button', {name: copy.cancel}).click();
        }
        expect(seen.cancellations[0]).toEqual({orderId: 'ord-locale-71', accountId: 71,
            clientOrderId: 'client-locale-identity', reason: 'operator-reviewed-reason'});
        expect(seen.cancellations[1]).toEqual(seen.cancellations[0]);
        // 当前正式撤单协议没有 expectedVersion；语言切换不能凭空添加字段或转换 machine identity。
        expect(seen.cancellations[0]).not.toHaveProperty('expectedVersion');
        await page.getByRole('button', {name: 'Search', exact: true}).click();
        await expect.poll(() => seen.orderGets.length).toBe(2);
        // 对比同类手动查询；首次自动查询省略空字段，不能与表单提交直接比较。
        await changeLocale(page, 'zh-CN');
        expect(seen.orderGets).toHaveLength(2);
        await page.getByRole('button', {name: /查\s*询/, exact: true}).click();
        await expect.poll(() => seen.orderGets.length).toBe(3);
        expect(seen.orderGets[2]).toBe(seen.orderGets[1]);
        expect(seen.cancellations).toHaveLength(2);
    });

    test('unknown code and field validation stay safe and localized while retaining technical identity', async ({page}) => {
        await installFixtures(page);
        await page.route('**/api/auth/login', (route) => route.fulfill({status: 418, json: {
            code: 'FUTURE_BACKEND_CODE', message: rawDiagnostic, traceId: 'trace-unknown-safe',
            fieldErrors: [{field: 'username', rejectedValue: 'fixture-operator', reason: rawDiagnostic}],
        }}));
        await page.goto('/login');
        await page.getByLabel('账号', {exact: true}).fill('fixture-operator');
        await page.getByLabel('密码', {exact: true}).fill('fixture-password');
        await page.getByRole('button', {name: labels['zh-CN'].login}).click();
        const notice = page.locator('[data-error-presentation="AUTH_REDIRECT_OR_PROMPT"]');
        await expect(notice).toContainText('FUTURE_BACKEND_CODE');
        await expect(notice).toContainText('trace-unknown-safe');
        await expect(notice).toContainText('暂时无法完成请求');
        await expect(notice).toContainText('username');
        await expect(notice).not.toContainText(rawDiagnostic);
        await changeLocale(page, 'en-US');
        await expect(notice).toContainText('The request could not be completed.');
        await expect(notice).toContainText('username: check this value.');
        await expect(notice).toContainText('trace-unknown-safe');
        await expect(page.locator('body')).not.toContainText(rawDiagnostic);
    });

    test('live-query errors use the same localized catalog and retain traces after language switching', async ({page}) => {
        await installFixtures(page);
        await page.route('**/api/backtest-configs/locale-config', (route) => route.fulfill({json: {
            backtestConfigId: 'locale-config', name: 'locale-fixture', researchConfigId: 'research-fixture',
            initialCapital: 1000, startTime: '2026-01-01T00:00:00Z', endTime: '2026-01-02T00:00:00Z',
            executionSpec: '{}', evaluationSpec: '{}', configSnapshot: '{}',
        }}));
        await page.route('**/api/evaluations?*', (route) => route.fulfill({json: [{
            evalReportId: 'locale-evaluation', backtestRunId: 'locale-run', evaluationStatus: 'SUCCEEDED',
        }]}));
        for (const path of ['/api/evaluations/locale-evaluation', '/api/backtest-runs/locale-run/pnl-snapshots']) {
            await page.route(`**${path}`, (route) => route.fulfill({status: 500, json: {
                code: 'INTERNAL_ERROR', message: rawDiagnostic, traceId: 'trace-live-query',
            }}));
        }
        await login(page, 'zh-CN', '/backtests/locale-config');
        const content = page.locator('.app-shell__content');
        await expect(content).toContainText('trace-live-query');
        await expect(content).toContainText('服务暂时无法完成请求');
        await changeLocale(page, 'en-US');
        await expect(content).toContainText('The service could not complete the request.');
        await expect(content).toContainText('trace-live-query');
        await expect(content).not.toContainText('服务暂时无法完成请求');
        await expect(page.locator('body')).not.toContainText(rawDiagnostic);
    });

    test('Ant Design pagination, date picker and modal follow the selected locale', async ({page}) => {
        test.setTimeout(60_000);
        await installFixtures(page);
        await login(page);
        for (const locale of ['zh-CN', 'en-US'] as const) {
            if (locale === 'en-US') await changeLocale(page, locale);
            await page.goto('/accounts');
            await expect(page.locator('.ant-pagination-next')).toHaveAttribute('title', locale === 'zh-CN' ? '下一页' : 'Next Page');
            await expect(page.getByRole('columnheader', {name: locale === 'zh-CN' ? '账户别名' : 'Account alias', exact: true})).toBeVisible();
            await page.goto('/backtests');
            const copy = labels[locale];
            await page.getByRole('button', {name: copy.createBacktest, exact: true}).click();
            const drawer = page.getByRole('dialog', {name: copy.createBacktest, exact: true});
            await expect(drawer.getByLabel(copy.start, {exact: true})).toHaveAttribute('placeholder', locale === 'zh-CN' ? '请选择日期' : 'Select date');
            await drawer.getByLabel(copy.start, {exact: true}).click();
            await expect(page.locator('.ant-picker-now-btn')).toHaveText(locale === 'zh-CN' ? '此刻' : 'Now');
            // Escape 同时属于 Drawer 关闭动作；点击标题只关闭日期弹层，保持待测表单打开。
            await drawer.getByText(copy.createBacktest, {exact: true}).click();
            await drawer.getByRole('button', {name: copy.cancel}).click();
            await page.goto('/paper-trading/runs');
            await expect(page.getByText(locale === 'zh-CN' ? '查询区' : 'Query', {exact: true})).toBeVisible();
            await page.getByRole('button', {name: /(?:创建|Create) Paper Run/i}).click();
            const modal = page.locator('.ant-modal');
            await expect(modal.getByRole('button', {name: locale === 'zh-CN' ? /确\s*定/ : /^OK$/})).toBeVisible();
            await modal.getByRole('button', {name: copy.cancel}).click();
        }
    });
});
