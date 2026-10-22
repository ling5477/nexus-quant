import {expect, test} from 'playwright/test';
import {readFileSync} from 'node:fs';

interface LocalDemoFixture {
    port: number;
    datasetId: string;
    boundUsername: string;
    boundRunId: string;
    mailApi: string;
}

/** 必须显式指向本任务的隔离进程和本地 Mailpit；不向共享环境或外部邮件服务执行。 */
test('真实注册邮件验证登录与账户投影、策略创建及 canonical SIM 分析', async ({page, context, request}) => {
    test.setTimeout(180000);
    expect(process.env.NQ_PUBLIC_DEMO_REAL_TEST).toBe('true');
    const fixtureFile = process.env.NQ_PUBLIC_DEMO_FIXTURE_FILE;
    if (!fixtureFile) throw new Error('Task-owned fixture metadata is required.');
    const fixture = JSON.parse(readFileSync(fixtureFile, 'utf8')) as LocalDemoFixture;
    expect(Number.isInteger(fixture.port) && fixture.port > 1024 && fixture.port < 65536).toBe(true);
    const backend = `http://127.0.0.1:${fixture.port}`;
    expect(process.env.VITE_API_PROXY_TARGET).toBe(backend);
    const mailbox = new URL(fixture.mailApi);
    expect(mailbox.protocol).toBe('http:'); expect(mailbox.hostname).toBe('127.0.0.1');
    const suffix = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 7)}`;
    const username = `ui-demo-${suffix}`;
    const email = `${username}@demo.test`;
    const password = 'SyntheticDemoPassword123!';
    const failures: Array<{path: string; status: number}> = [];
    const analysisReads = new Set<string>();
    page.on('response', (response) => {
        const path = new URL(response.url()).pathname;
        if (path.startsWith('/api/') && response.status() >= 400)
            failures.push({path, status: response.status()});
        if (path.startsWith('/api/paper-trading/strategy-sim/analysis/runs/') && response.ok())
            analysisReads.add(path);
    });
    await context.addInitScript(() => {localStorage.clear(); localStorage.setItem('nq.locale', 'en-US');});
    await page.goto('/register');
    await page.getByLabel('Username', {exact: true}).fill(username);
    await page.getByLabel('Email', {exact: true}).fill(email);
    await page.getByLabel('Password', {exact: true}).fill(password);
    await page.getByLabel('Confirm password', {exact: true}).fill(password);
    await page.getByRole('button', {name: 'Create demo account'}).click();
    await expect(page.getByText('Check your verification email')).toBeVisible();

    let messageId: string | undefined;
    await expect.poll(async () => {
        const response = await request.get(new URL('/api/v1/messages?limit=100', mailbox).href);
        if (!response.ok()) return false;
        const body = await response.json() as {messages?: Array<{ID: string; To?: Array<{Address: string}>}>};
        messageId = body.messages?.find((message) => message.To?.some((to) => to.Address === email))?.ID;
        return Boolean(messageId);
    }, {timeout: 45000, intervals: [250, 500, 1000]}).toBe(true);
    const mailResponse = await request.get(new URL(`/api/v1/message/${messageId}`, mailbox).href);
    if (!mailResponse.ok()) throw new Error('Local verification email could not be read.');
    const mail = await mailResponse.json() as {Text?: string};
    const token = mail.Text?.match(/#token=([A-Za-z0-9._~-]+)/)?.[1];
    if (!token) throw new Error('Verification token missing from local email.');
    await page.goto(`/verify-email#token=${token}`);
    await expect(page).toHaveURL(/\/verify-email$/);
    await expect(page.getByText('Email verified. Your simulation account is ready.')).toBeVisible();
    await page.getByRole('link', {name: 'Go to login'}).click();
    await page.getByLabel('Username', {exact: true}).fill(username);
    await page.getByLabel('Password', {exact: true}).fill(password);
    await page.getByRole('button', {name: /Sign in$/}).click();
    await expect(page).toHaveURL(/\/demo$/);
    await page.getByRole('button', {name: 'Confirm read', exact: true}).click();
    await expect(page.getByText('10,000 USDT', {exact: true})).toBeVisible();
    await expect(page.getByRole('button', {name: 'Demo SIM · PAPER · SIM', exact: true})).toBeVisible();
    await page.getByRole('link', {name: 'Create / select strategy'}).click();
    await page.getByRole('button', {name: 'Create strategy definition', exact: true}).click();
    const drawer = page.getByRole('dialog', {name: 'Create strategy definition', exact: true});
    await drawer.getByLabel('Strategy code', {exact: true}).fill(`UI_${suffix}`);
    await drawer.getByLabel('Strategy name', {exact: true}).fill('Isolated UI demo strategy');
    await expect(drawer.getByRole('button', {name: 'Create strategy definition', exact: true})).toBeEnabled();
    const created = page.waitForResponse((response) => response.request().method() === 'POST'
        && new URL(response.url()).pathname.startsWith('/api/research-creation-operations/'));
    await drawer.getByRole('button', {name: 'Create strategy definition', exact: true}).click();
    expect((await created).ok()).toBe(true);
    await expect(drawer).toBeHidden();
    const detail = page.getByRole('dialog', {name: 'Strategy details', exact: true});
    await expect(detail).toBeVisible();
    await detail.getByRole('button', {name: 'Close', exact: true}).click();
    await expect(detail).toBeHidden();

    await page.getByRole('button', {name: 'Sign out', exact: true}).click();
    await expect(page).toHaveURL(/\/login$/);
    await page.getByLabel('Username', {exact: true}).fill(fixture.boundUsername);
    await page.getByLabel('Password', {exact: true}).fill(password);
    await page.getByRole('button', {name: /Sign in$/}).click();
    await expect(page).toHaveURL(/\/demo$/);
    await page.getByRole('button', {name: 'Confirm read', exact: true}).click();
    await expect(page.getByText(fixture.boundRunId, {exact: true}).first()).toBeVisible();
    await page.getByRole('link', {name: 'View SIM analysis'}).click();
    await expect(page).toHaveURL(new RegExp(`/paper-trading/portfolio\\?paperRunId=${fixture.boundRunId}`));
    await expect.poll(() => analysisReads.has(`/api/paper-trading/strategy-sim/analysis/runs/${fixture.boundRunId}`)).toBe(true);
    await expect(page.getByTestId('sim-analysis-portfolio')).toBeVisible();
    await page.screenshot({path: test.info().outputPath('demo-real-canonical-analysis.png'), fullPage: true});
    expect(failures).toEqual([]);
});
