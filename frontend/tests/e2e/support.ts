import {expect, type Page} from 'playwright/test';

const username = process.env.E2E_USERNAME ?? 'admin';
const password = process.env.E2E_PASSWORD ?? 'ChangeMe123!';
const defaultAccountAlias = 'rc1-admin-default';
const altAccountAlias = 'rc1-admin-alt';

export interface E2EExchangeAccountFixture {
    exchangeAccountId: number;
    legacyAccountId?: number | null;
    exchangeCode: string;
    tradeEnv: string;
    accountAlias: string;
    isDefault: boolean;
    status: string;
}

async function resetDefaultAccountFixture(page: Page) {
    const loginResponse = await page.request.post('/api/auth/login', {
        data: {username, password},
        timeout: 30_000,
    });
    expect(loginResponse.ok()).toBeTruthy();

    const loginPayload = await loginResponse.json();
    const accessToken = loginPayload.accessToken;
    expect(accessToken, 'E2E fixture reset requires an accessToken from /api/auth/login').toBeTruthy();
    const authHeaders = {
        Authorization: `Bearer ${accessToken}`,
    };

    const accounts = await listAccounts(page, authHeaders);
    const defaultAccount = await ensureAccount(page, authHeaders, accounts, defaultAccountAlias);
    await ensureAccount(page, authHeaders, accounts, altAccountAlias);

    // Why: 账户写侧 smoke 会把默认账户切到 rc1-admin-alt；每条 E2E 登录前先固定回
    // rc1-admin-default。这里按 alias 解析真实 exchangeAccountId，避免本地库自增 ID 漂移导致
    // 所有页面 smoke 在登录前置阶段失败。
    const resetResponse = await page.request.post(`/api/exchange-accounts/${defaultAccount.exchangeAccountId}/set-default`, {
        headers: authHeaders,
        timeout: 30_000,
    });
    expect(resetResponse.ok(), await resetResponse.text()).toBeTruthy();

    return {...defaultAccount, isDefault: true};
}

export async function loginToConsole(page: Page): Promise<E2EExchangeAccountFixture> {
    const defaultAccount = await resetDefaultAccountFixture(page);

    // Why: 登录页文案在 fix(freeze) 288c28f8 中改为 "NexusQuant 控制台" / "登录"，
    // 这里同步选择器，否则所有 E2E 在登录前置阶段直接失败。
    await page.goto('/login');
    await expect(page.getByRole('heading', {name: 'NexusQuant 控制台'})).toBeVisible();

    // Why: fix(freeze) 288c28f8 移除了登录表单的默认凭证预填（登录泄露清理），
    // E2E 必须始终显式填写用户名密码，不能再依赖页面预填默认值。
    await page.getByLabel('用户名').fill(username);
    await page.getByLabel('密码').fill(password);
    // Why: AntD 会在两个汉字的按钮文本中自动插入空格（"登 录"），按既有规范用 \s* 正则匹配。
    await page.getByRole('button', {name: /^登\s*录$/}).click();

    // Why: 本地验收会先拉起后端再并发启动 Playwright worker，首页路由恢复偶发慢于默认 5 秒，
    // 这里显式放宽等待窗口，避免把真实登录成功误判成路由失败。
    await expect(page).toHaveURL(/\/dashboard$/, {timeout: 15_000});
    await expect(page.getByRole('heading', {name: '控制台总览'})).toBeVisible({timeout: 15_000});

    return defaultAccount;
}

async function listAccounts(page: Page, authHeaders: {Authorization: string}): Promise<E2EExchangeAccountFixture[]> {
    const response = await page.request.get('/api/exchange-accounts', {
        headers: authHeaders,
        timeout: 30_000,
    });
    expect(response.ok(), await response.text()).toBeTruthy();
    return await response.json() as E2EExchangeAccountFixture[];
}

async function ensureAccount(
    page: Page,
    authHeaders: {Authorization: string},
    knownAccounts: E2EExchangeAccountFixture[],
    accountAlias: string,
): Promise<E2EExchangeAccountFixture> {
    const existing = knownAccounts.find((account) => (
        account.exchangeCode === 'OKX'
        && account.tradeEnv === 'SIM'
        && account.accountAlias === accountAlias
    ));
    if (existing) {
        if (existing.status !== 'ACTIVE') {
            const enableResponse = await page.request.post(`/api/exchange-accounts/${existing.exchangeAccountId}/enable`, {
                headers: authHeaders,
                timeout: 30_000,
            });
            expect(enableResponse.ok(), await enableResponse.text()).toBeTruthy();
            return {...existing, status: 'ACTIVE'};
        }
        return existing;
    }

    const createResponse = await page.request.post('/api/exchange-accounts', {
        headers: authHeaders,
        data: {
            exchangeCode: 'OKX',
            tradeEnv: 'SIM',
            accountAlias,
            externalAccountRef: null,
        },
        timeout: 30_000,
    });
    expect(createResponse.ok(), await createResponse.text()).toBeTruthy();
    const created = await createResponse.json() as E2EExchangeAccountFixture;
    knownAccounts.push(created);
    return created;
}
