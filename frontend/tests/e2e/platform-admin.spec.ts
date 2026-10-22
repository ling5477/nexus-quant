import {expect, test, type BrowserContext, type Page} from 'playwright/test';

const tenantId = 'cd42a427-3512-42d0-ae84-ef5c560df98a';
const asOf = '2026-10-22T06:00:00Z';
async function identity(context: BrowserContext, role = 'PLATFORM_ADMIN') {
    await context.addInitScript((role) => {
        if (sessionStorage.getItem('platform-ui-initialized')) return;
        localStorage.clear(); sessionStorage.clear(); sessionStorage.setItem('platform-ui-initialized', '1'); localStorage.setItem('nq.locale', 'en-US');
        localStorage.setItem('nexus-quant.console.auth', JSON.stringify({sessionId: 'platform-ui-session', accessToken: 'synthetic-ui-token',
            tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600000).toISOString(), username: 'platform.fixture', roles: [role], mustChangePassword: false}));
    }, role);
}
async function closeDisclosure(page: Page) {
    const confirm = page.getByRole('button', {name: 'Confirm read', exact: true});
    if (await confirm.isVisible()) await confirm.click();
}
function tenant(enabled = true) {return {tenantId, userId: 17, username: 'tenant.alpha', emailVerified: true,
    registeredAt: asOf, enabled, simAccountStatus: 'INITIALIZED', simStatus: enabled ? 'SIM_NOT_BOUND' : 'SUSPENDED'};}
function performance() {return {status: 'UNAVAILABLE', reason: 'VERIFIED_CANONICAL_VALUATION_UNAVAILABLE', source: 'CANONICAL_SIM_PROJECTION', asOf: null,
    initialCapital: '10000.00000000', currentCash: '10000.00000000', equity: null, realizedPnl: null, unrealizedPnl: null,
    netPnl: null, returnRate: null, drawdown: null, orderCount: 0, tradeCount: 0, simStatus: 'SIM_NOT_BOUND', history: [], coverageStart: asOf, coverageEnd: null};}

test('平台页面仅访问平台统计，详情保留未知估值且小屏可读', async ({page, context}) => {
    await identity(context);
    await page.setViewportSize({width: 390, height: 844});
    let valued = false;
    const unexpected: string[] = [];
    await page.route((url) => url.pathname.startsWith('/api/'), async (route) => {
        const path = new URL(route.request().url()).pathname;
        let json: unknown;
        if (path === '/api/distribution') json = {distribution: 'NQ_PUBLIC_DEMO'};
        else if (path === '/api/auth/me') json = {userId: 1, username: 'platform.fixture', roles: ['PLATFORM_ADMIN'], authenticated: true, mustChangePassword: false};
        else if (path === '/api/platform/tenants') json = {items: [tenant()], total: 1, page: 0, size: 20,
            overview: {registeredUsers: 1, enabledUsers: 1, disabledUsers: 0, runningSimCount: 0, capacity: 8, asOf, source: 'DEMO_REGISTRATIONS'}};
        else if (path.endsWith('/summary')) json = {...tenant(), suspended: false, requiresManualResume: false, executionEpoch: 1};
        else if (path.endsWith('/sim-performance')) json = valued ? {...performance(), status: 'STALE', reason: 'LATEST_VERIFIED_SNAPSHOT_IS_HISTORICAL', asOf, equity: '11250.00000000', netPnl: '1250.00000000', returnRate: '0.125', drawdown: '0.0005', coverageEnd: asOf} : performance();
        else {unexpected.push(path); await route.fulfill({status: 403, json: {code: 'FORBIDDEN'}}); return;}
        await route.fulfill({json});
    });
    await page.goto('/platform');
    await expect(page.getByRole('heading', {name: 'Platform overview'})).toBeVisible();
    await closeDisclosure(page);
    await expect(page.getByRole('button', {name: 'Choose account', exact: true})).toHaveCount(0);
    await page.getByRole('link', {name: 'View details'}).click();
    await expect(page.getByRole('heading', {name: 'Tenant details'})).toBeVisible();
    await expect(page.getByText('10,000', {exact: true})).toHaveCount(2);
    await expect(page.getByText('No verified canonical valuation is available')).toBeVisible();
    await expect(page.getByText('Unknown / unavailable', {exact: true}).first()).toBeVisible();
    valued = true;
    await page.getByRole('button', {name: 'Refresh', exact: true}).click();
    await expect(page.getByText('12.5%', {exact: true})).toBeVisible();
    await expect(page.getByText('These values are historical and do not represent a current valuation')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.screenshot({path: test.info().outputPath('platform-tenant-mobile.png'), fullPage: true});
    expect(unexpected).toEqual([]);
});

test('启停有确认，网络结果未知后使用同一个 operationId 重试并清除过期列表', async ({page, context}) => {
    await identity(context);
    let enabled = true; let attempts = 0;
    const bodies: unknown[] = [];
    await page.route((url) => url.pathname.startsWith('/api/'), async (route) => {
        const path = new URL(route.request().url()).pathname;
        if (path === '/api/distribution') {await route.fulfill({json: {distribution: 'NQ_PUBLIC_DEMO'}}); return;}
        if (path === '/api/auth/me') {await route.fulfill({json: {userId: 1, username: 'platform.fixture', roles: ['PLATFORM_ADMIN'], authenticated: true, mustChangePassword: false}}); return;}
        if (path === '/api/platform/tenants') {await route.fulfill({json: {items: [tenant(enabled)], total: 1, page: 0, size: 20,
            overview: {registeredUsers: 1, enabledUsers: enabled ? 1 : 0, disabledUsers: enabled ? 0 : 1, runningSimCount: 0, capacity: 8, asOf, source: 'DEMO_REGISTRATIONS'}}}); return;}
        if (path === '/api/platform/users/17/disable' || path === '/api/platform/users/17/enable') {
            bodies.push(route.request().postDataJSON()); attempts++;
            if (attempts === 1) {await route.abort('failed'); return;}
            enabled = path.endsWith('/enable');
            await route.fulfill({json: {operationId: route.request().postDataJSON().operationId, targetUserId: 17, targetTenantId: tenantId,
                action: enabled ? 'ENABLE' : 'DISABLE', beforeEnabled: !enabled, afterEnabled: enabled,
                beforeAuthVersion: 1, afterAuthVersion: 2, beforeExecutionEpoch: 1, afterExecutionEpoch: 2, createdAt: asOf}}); return;
        }
        await route.fulfill({status: 403, json: {code: 'FORBIDDEN'}});
    });
    await page.goto('/platform/tenants');
    await expect(page.getByRole('heading', {name: 'Tenant management'})).toBeVisible();
    await closeDisclosure(page);
    await page.getByRole('button', {name: 'Disable user', exact: true}).click();
    await page.getByLabel('Operation reason').fill('Suspend fixture user');
    expect(attempts).toBe(0);
    await page.getByRole('button', {name: 'Confirm operation', exact: true}).click();
    await expect(page.getByText(/The outcome is not confirmed/)).toBeVisible();
    await page.getByRole('button', {name: 'Cancel', exact: true}).click();
    await page.reload();
    await expect(page.getByRole('heading', {name: 'Tenant management'})).toBeVisible();
    await closeDisclosure(page);
    await page.getByRole('button', {name: 'Disable user', exact: true}).click();
    await expect(page.getByLabel('Operation reason')).toHaveValue('Suspend fixture user');
    await expect(page.getByLabel('Operation reason')).toBeDisabled();
    await page.getByRole('button', {name: 'Retry same operation', exact: true}).click();
    await page.getByRole('button', {name: 'OK', exact: true}).click();
    expect(bodies[1]).toEqual(bodies[0]);
    await expect(page.getByRole('button', {name: 'Enable user', exact: true})).toBeVisible();
    await page.getByRole('button', {name: 'Enable user', exact: true}).click();
    await expect(page.getByText(/Restore login eligibility/)).toBeVisible();
    await page.getByLabel('Operation reason').fill('Restore fixture eligibility');
    await page.getByRole('button', {name: 'Confirm operation', exact: true}).click();
    await page.getByRole('button', {name: 'OK', exact: true}).click();
    await expect(page.getByRole('button', {name: 'Disable user', exact: true})).toBeVisible();
    expect((bodies[2] as {operationId: string}).operationId).not.toBe((bodies[0] as {operationId: string}).operationId);
});

test('发行身份不匹配时拒绝恢复认证与平台页面', async ({page, context}) => {
    await identity(context); let me = 0;
    await page.route('**/api/distribution', (route) => route.fulfill({json: {distribution: 'NQ_SELF_HOSTED'}}));
    await page.route('**/api/auth/me', (route) => {me++; return route.fulfill({status: 500});});
    await page.goto('/platform');
    await expect(page.getByText('Distribution identity could not be verified')).toBeVisible();
    await expect(page.getByRole('heading', {name: 'Platform overview'})).toHaveCount(0);
    expect(me).toBe(0);
});

for (const [role, path] of [['PLATFORM_ADMIN', '/strategies'], ['DEMO_USER', '/platform/tenants']] as const) {
    test(`公共发行身份拒绝跨权限域路由 ${role}`, async ({page, context}) => {
        await identity(context, role); const unexpected: string[] = [];
        await page.route((url) => url.pathname.startsWith('/api/'), async (route) => {
            const path = new URL(route.request().url()).pathname;
            if (path === '/api/distribution') {await route.fulfill({json: {distribution: 'NQ_PUBLIC_DEMO'}}); return;}
            if (path === '/api/auth/me') {await route.fulfill({json: {userId: role === 'PLATFORM_ADMIN' ? 1 : 17, username: 'platform.fixture', roles: [role], authenticated: true, mustChangePassword: false}}); return;}
            unexpected.push(path); await route.fulfill({status: 403, json: {code: 'FORBIDDEN'}});
        });
        await page.goto(path);
        await expect(page.getByText('403', {exact: true})).toBeVisible();
        expect(unexpected).toEqual([]);
    });
}
