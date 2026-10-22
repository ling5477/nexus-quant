import {expect, test, type Page} from 'playwright/test';
import {readFileSync} from 'node:fs';
interface PlatformFixture {port: number; platformUsername: string; usernameA: string; usernameB: string; tenantIdA: string; tenantIdB: string; userIdA: number; runIdA: string;}
const password = 'SyntheticDemoPassword123!';
async function signIn(page: Page, username: string) {
    await page.getByLabel('Username', {exact: true}).fill(username);
    await page.getByLabel('Password', {exact: true}).fill(password);
    await page.getByRole('button', {name: /Sign in$/}).click();
    await expect(page.getByRole('button', {name: 'Confirm read', exact: true})).toBeVisible();
    await page.getByRole('button', {name: 'Confirm read', exact: true}).click();
}
async function bearer(page: Page) {
    return page.evaluate(() => JSON.parse(localStorage.getItem('nexus-quant.console.auth')!).accessToken as string);
}

/** 仅连接本轮持有的本地三身份进程；经济事实由既有 canonical 夹具创建，浏览器不修改它们。 */
test('真实平台登录、只读canonical绩效、启停和三身份缓存隔离', async ({page, context}) => {
    test.setTimeout(180000);
    expect(process.env.NQ_PLATFORM_ADMIN_REAL_TEST).toBe('true');
    const file = process.env.NQ_PLATFORM_ADMIN_FIXTURE_FILE;
    if (!file) throw new Error('Task-owned platform fixture metadata is required.');
    const fixture = JSON.parse(readFileSync(file, 'utf8')) as PlatformFixture;
    expect(Number.isInteger(fixture.port) && fixture.port > 1024 && fixture.port < 65536).toBe(true);
    expect(process.env.VITE_API_PROXY_TARGET).toBe(`http://127.0.0.1:${fixture.port}`);
    await context.addInitScript(() => {
        if (sessionStorage.getItem('platform-real-initialized')) return;
        localStorage.clear(); sessionStorage.clear(); sessionStorage.setItem('platform-real-initialized', '1'); localStorage.setItem('nq.locale', 'en-US');
    });
    const aLogin = await page.request.post('/api/auth/login', {data: {username: fixture.usernameA, password}});
    expect(aLogin.ok()).toBe(true);
    const tokenA = (await aLogin.json()).accessToken as string;
    await page.goto('/login');
    await signIn(page, fixture.platformUsername);
    await expect(page).toHaveURL(/\/platform$/);
    await expect(page.getByRole('heading', {name: 'Platform overview'})).toBeVisible();
    const platformToken = await bearer(page);
    expect((await page.request.get('/api/strategies', {headers: {Authorization: `Bearer ${platformToken}`}})).status()).toBe(403);
    await page.getByRole('link', {name: 'View all users'}).click();
    await expect(page.getByRole('heading', {name: 'Tenant management'})).toBeVisible();
    await page.getByRole('searchbox', {name: 'Search username'}).fill(fixture.usernameA);
    const searchResponse = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/platform/tenants'
        && new URL(response.url()).searchParams.get('search') === fixture.usernameA);
    await page.getByRole('searchbox', {name: 'Search username'}).press('Enter');
    expect((await searchResponse).ok()).toBe(true);
    const row = page.getByRole('row').filter({hasText: fixture.usernameA});
    await expect(row).toHaveCount(1);
    await expect(page.getByRole('row').filter({hasText: fixture.usernameB})).toHaveCount(0);
    await page.getByText('All account states', {exact: true}).click();
    const filteredResponse = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/platform/tenants' && new URL(response.url()).searchParams.get('enabled') === 'true');
    await page.locator('.ant-select-dropdown:visible').getByText('Enabled', {exact: true}).click();
    expect((await filteredResponse).ok()).toBe(true);
    const performanceResponse = page.waitForResponse((response) => new URL(response.url()).pathname === `/api/platform/tenants/${fixture.tenantIdA}/sim-performance`);
    await row.getByRole('link', {name: 'View details'}).click();
    const performance = await (await performanceResponse).json();
    expect(['AVAILABLE', 'STALE']).toContain(performance.status);
    expect(performance.source).toBe('CANONICAL_SIM_PROJECTION');
    expect(performance.initialCapital).toMatch(/^10000(?:\.0+)?$/);
    expect(performance.equity).not.toBeNull(); expect(performance.netPnl).not.toBeNull();
    expect(performance.history.length).toBeGreaterThan(0); expect(performance.tradeCount).toBeGreaterThan(0);
    await expect(page.getByRole('heading', {name: 'Tenant details'})).toBeVisible();
    await expect(page.locator('.ant-table-tbody > tr[data-row-key]')).toHaveCount(performance.history.length);
    await page.screenshot({path: test.info().outputPath('platform-real-canonical-performance.png'), fullPage: true});
    await page.getByRole('button', {name: 'Disable user', exact: true}).click();
    await page.getByLabel('Operation reason').fill('Isolated browser verification of account suspension');
    const disabledResponse = page.waitForResponse((response) => new URL(response.url()).pathname === `/api/platform/users/${fixture.userIdA}/disable`);
    await page.getByRole('button', {name: 'Confirm operation', exact: true}).click();
    const disabled = await (await disabledResponse).json();
    expect(disabled.afterEnabled).toBe(false);
    expect(disabled.afterAuthVersion).toBeGreaterThan(disabled.beforeAuthVersion);
    expect(disabled.afterExecutionEpoch).toBeGreaterThan(disabled.beforeExecutionEpoch);
    await page.getByRole('button', {name: 'OK', exact: true}).click();
    await expect(page.getByRole('button', {name: 'Enable user', exact: true})).toBeVisible();
    expect((await page.request.get('/api/demo/sim-account', {headers: {Authorization: `Bearer ${tokenA}`}})).status()).toBe(401);
    expect((await page.request.post('/api/auth/renew', {headers: {Authorization: `Bearer ${tokenA}`}})).status()).toBe(401);
    expect((await page.request.post('/api/auth/login', {data: {username: fixture.usernameA, password}})).status()).toBe(401);
    await page.getByRole('button', {name: 'Enable user', exact: true}).click();
    await page.getByLabel('Operation reason').fill('Restore eligibility without restarting simulation');
    const enabledResponse = page.waitForResponse((response) => new URL(response.url()).pathname === `/api/platform/users/${fixture.userIdA}/enable`);
    await page.getByRole('button', {name: 'Confirm operation', exact: true}).click();
    const enabled = await (await enabledResponse).json();
    expect(enabled.afterEnabled).toBe(true);
    expect(enabled.afterExecutionEpoch).toBe(enabled.beforeExecutionEpoch);
    await page.getByRole('button', {name: 'OK', exact: true}).click();
    const summary = await (await page.request.get(`/api/platform/tenants/${fixture.tenantIdA}/summary`, {headers: {Authorization: `Bearer ${platformToken}`}})).json();
    expect(summary.requiresManualResume).toBe(true); expect(summary.suspended).toBe(true);
    const afterPerformance = await (await page.request.get(`/api/platform/tenants/${fixture.tenantIdA}/sim-performance`, {headers: {Authorization: `Bearer ${platformToken}`}})).json();
    for (const field of ['initialCapital', 'currentCash', 'equity', 'realizedPnl', 'unrealizedPnl', 'netPnl', 'tradeCount', 'orderCount', 'history'])
        expect(afterPerformance[field]).toEqual(performance[field]);
    await page.getByRole('button', {name: 'Sign out', exact: true}).click();
    await expect(page).toHaveURL(/\/login$/);
    await signIn(page, fixture.usernameA);
    await expect(page).toHaveURL(/\/demo$/);
    await expect(page.getByText(fixture.runIdA, {exact: true}).first()).toBeVisible();
    await expect(page.getByRole('menuitem', {name: 'Tenant management', exact: true})).toHaveCount(0);
    await expect(page.getByRole('heading', {name: 'Tenant details'})).toHaveCount(0);
    const aResult = await (await page.request.get('/api/demo/registration-result', {headers: {Authorization: `Bearer ${await bearer(page)}`}})).json();
    expect(aResult.tenantId).toBe(fixture.tenantIdA);
    await page.getByRole('button', {name: 'Sign out', exact: true}).click();
    await expect(page).toHaveURL(/\/login$/);
    await signIn(page, fixture.usernameB);
    await expect(page).toHaveURL(/\/demo$/);
    const bResult = await (await page.request.get('/api/demo/registration-result', {headers: {Authorization: `Bearer ${await bearer(page)}`}})).json();
    expect(bResult.tenantId).toBe(fixture.tenantIdB); expect(bResult.accountId).not.toBe(aResult.accountId);
    expect(bResult.boundPaperRunId).toBeNull();
    await expect(page.getByText(fixture.runIdA, {exact: true})).toHaveCount(0);
    await expect(page.getByRole('link', {name: 'Create / select strategy'})).toBeVisible();
    await page.screenshot({path: test.info().outputPath('platform-real-tenant-switch.png'), fullPage: true});
    await test.info().attach('real-platform-verification', {body: JSON.stringify({source: performance.source, status: performance.status,
        historySamples: performance.history.length, tradeCount: performance.tradeCount, initialCapital: performance.initialCapital,
        disableAuthVersionIncrement: disabled.afterAuthVersion - disabled.beforeAuthVersion,
        disableExecutionEpochIncrement: disabled.afterExecutionEpoch - disabled.beforeExecutionEpoch,
        manualResumeRequired: summary.requiresManualResume, economicProjectionUnchanged: true, tenantSwitchIsolated: true}), contentType: 'application/json'});
    await page.getByRole('button', {name: 'Sign out', exact: true}).click();
    await expect(page).toHaveURL(/\/login$/);
});
