import {expect, test, type BrowserContext} from 'playwright/test';

/** 仅验证前端交互和发行路由；真实注册、邮件、资金及隔离由独立 PostgreSQL/HTTP 测试证明。 */
async function anonymous(context: BrowserContext) {
    await context.route('**/api/distribution', (route) => route.fulfill({json: {distribution: 'NQ_PUBLIC_DEMO'}}));
    await context.addInitScript(() => {localStorage.clear(); localStorage.setItem('nq.locale', 'en-US');});
}

test('Demo 注册校验与通用回执不暴露账户存在，提交仅含允许字段', async ({page, context}) => {
    await anonymous(context);
    let submitted: Record<string, string> | undefined;
    await page.route('**/api/auth/register', async (route) => {
        submitted = route.request().postDataJSON();
        await route.fulfill({status: 202, json: {status: 'ACCEPTED'}});
    });
    await page.goto('/login');
    await page.getByRole('link', {name: 'Create demo account'}).click();
    await expect(page.getByRole('heading', {name: 'Create demo account'})).toBeVisible();
    await page.getByLabel('Username', {exact: true}).fill('fixture.demo');
    await page.getByLabel('Email', {exact: true}).fill('demo@example.test');
    await page.getByLabel('Password', {exact: true}).fill('LocalFixturePassword123');
    await page.getByLabel('Confirm password', {exact: true}).fill('DifferentPassword123');
    await page.getByRole('button', {name: 'Create demo account'}).click();
    await expect(page.getByText('Passwords do not match')).toBeVisible();
    expect(submitted).toBeUndefined();
    await page.getByLabel('Confirm password', {exact: true}).fill('LocalFixturePassword123');
    await expect(page.getByLabel('Username', {exact: true})).toHaveValue('fixture.demo');
    await page.getByRole('button', {name: 'Create demo account'}).click();
    await expect(page.getByText('Check your verification email')).toBeVisible();
    expect(Object.keys(submitted!).sort()).toEqual(['confirmPassword', 'email', 'password', 'registrationId', 'username']);
    expect(submitted!.registrationId).toMatch(/^[a-f\d-]{36}$/);
});

test('邮箱令牌不留在地址栏，失败与受控重发有明确反馈', async ({page, context}) => {
    await anonymous(context);
    let count = 0;
    await page.route('**/api/auth/verify-email', async (route) => {
        count++;
        expect(route.request().postDataJSON()).toEqual({token: 'local-fixture-token'});
        await route.fulfill({status: 410, json: {code: 'VERIFICATION_EXPIRED', message: 'Expired'}});
    });
    await page.route('**/api/auth/resend-verification', async (route) => route.fulfill({status: 202, json: {status: 'ACCEPTED'}}));
    await page.goto('/verify-email#token=local-fixture-token');
    await expect(page).toHaveURL(/\/verify-email$/);
    await expect(page.getByText('This verification link has expired. Request another email.')).toBeVisible();
    expect(count).toBe(1);
    await page.getByLabel('Email', {exact: true}).fill('demo@example.test');
    await page.getByRole('button', {name: 'Resend verification email'}).click();
    await expect(page.getByText('Check your verification email')).toBeVisible();
});

test('邮箱验证成功提供登录入口，小屏表单可读', async ({page, context}) => {
    await anonymous(context);
    await page.setViewportSize({width: 390, height: 844});
    let verified = 0;
    await page.route('**/api/auth/verify-email', async (route) => {verified++; await route.fulfill({status: 200, json: {status: 'READY'}});});
    await page.goto('/verify-email#token=local-fixture-token');
    await expect.poll(() => verified).toBe(1);
    await expect(page.getByText('Email verified. Your simulation account is ready.')).toBeVisible();
    await expect(page.getByRole('link', {name: 'Go to login'})).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.screenshot({path: test.info().outputPath('demo-verify-mobile.png'), fullPage: true});
});

test('Demo 首页使用唯一账户投影进入策略创建，未知状态不保留成功资金提示', async ({page, context}) => {
    await context.addInitScript(() => {
        localStorage.clear(); localStorage.setItem('nq.locale', 'en-US');
        localStorage.setItem('nexus-quant.console.auth', JSON.stringify({sessionId: 'local-ui-session', accessToken: 'synthetic-ui-token',
            tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600000).toISOString(), username: 'fixture.demo', roles: ['DEMO_USER'], mustChangePassword: false}));
    });
    let initialized = true;
    const unexpected: string[] = [];
    await page.route((url) => url.pathname.startsWith('/api/'), async (route) => {
        const url = new URL(route.request().url());
        let json: unknown;
        switch (url.pathname) {
            case '/api/distribution': json = {distribution: 'NQ_PUBLIC_DEMO'}; break;
            case '/api/auth/me': json = {userId: 17, username: 'fixture.demo', roles: ['DEMO_USER'], authenticated: true, mustChangePassword: false,
                defaultExchangeAccountId: 21, defaultExchangeCode: 'PAPER', defaultTradeEnv: 'SIM', defaultAccountAlias: 'Demo SIM'}; break;
            case '/api/demo/sim-account': json = {exchangeAccountId: 21, legacyAccountId: 31, exchangeCode: 'PAPER', tradeEnv: 'SIM', accountAlias: 'Demo SIM', externalAccountRef: null, isDefault: true, status: 'ACTIVE'}; break;
            case '/api/demo/registration-result': json = {status: initialized ? 'SIM_NOT_BOUND' : 'UNRECOGNIZED', tenantId: 'tenant-demo', exchangeAccountId: 21,
                accountId: 31, reservedPaperRunId: 'ptr-reserved', boundPaperRunId: null, initialCapital: '10000.00000000', currency: 'USDT'}; break;
            case '/api/strategies/page': json = {items: [], total: 0, limit: Number(url.searchParams.get('limit')), offset: Number(url.searchParams.get('offset'))}; break;
            default: unexpected.push(url.pathname); await route.fulfill({status: 403, json: {code: 'FORBIDDEN'}}); return;
        }
        await route.fulfill({status: 200, json});
    });
    await page.goto('/demo');
    await expect(page.getByRole('button', {name: 'Confirm read', exact: true})).toBeVisible();
    await page.getByRole('button', {name: 'Confirm read', exact: true}).click();
    await expect(page.getByRole('combobox', {name: 'Language', exact: true})).toBeEnabled();
    await expect(page.getByText('10,000 USDT', {exact: true})).toBeVisible();
    await expect(page.getByRole('button', {name: 'Demo SIM · PAPER · SIM', exact: true})).toBeVisible();
    initialized = false;
    await page.getByRole('button', {name: 'Refresh status'}).click();
    await expect(page.getByText('Account status unknown', {exact: true})).toBeVisible();
    await expect(page.getByText('10,000 USDT', {exact: true})).toHaveCount(0);
    initialized = true;
    await page.getByRole('button', {name: 'Refresh status'}).click();
    await page.getByRole('link', {name: 'Create / select strategy'}).click();
    await page.getByRole('button', {name: 'Create strategy definition', exact: true}).click();
    await expect(page.getByRole('dialog').getByRole('button', {name: 'Create strategy definition', exact: true})).toBeEnabled();
    expect(unexpected).toEqual([]);
});
