import {expect, test} from 'playwright/test';

// 只对明确提供的独立 fresh installer 地址运行，避免误用既有开发或长跑环境。
test('fresh admin is forced to change password and reauthenticate', async ({page, request}) => {
    expect(process.env.NQ_FRESH_INSTALL_TEST).toBe('true');
    const password = 'InstallerChangedPassword123';
    await page.goto('/login');
    await page.getByLabel('账号').fill('admin');
    await page.getByLabel('密码', {exact: true}).fill('123456');
    await page.getByRole('button', {name: /登\s*录/}).click();
    await expect(page).toHaveURL(/\/change-password$/);
    await expect(page.getByRole('heading', {name: '修改初始密码'})).toBeVisible();
    const oldSession = await page.evaluate(() => JSON.parse(localStorage.getItem('nexus-quant.console.auth')!));
    await page.goto('/trading');
    await expect(page).toHaveURL(/\/change-password$/);
    await page.reload();
    await expect(page.getByRole('heading', {name: '修改初始密码'})).toBeVisible();
    const headers = {Authorization: `Bearer ${oldSession.accessToken}`};
    for (const path of ['/api/trading', '/api/strategy', '/api/scheduler/jobs']) {
        const response = await request.get(path, {headers});
        expect(response.status()).toBe(403); expect((await response.json()).code).toBe('PASSWORD_CHANGE_REQUIRED');
    }
    await page.getByLabel('当前密码', {exact: true}).fill('123456');
    await page.getByLabel('新密码', {exact: true}).fill(password);
    await page.getByLabel('确认新密码', {exact: true}).fill(password);
    await page.getByRole('button', {name: '修改密码并重新登录'}).click();
    await expect(page).toHaveURL(/\/login\?passwordChanged=true$/);
    expect(await page.evaluate(() => localStorage.getItem('nexus-quant.console.auth'))).toBeNull();
    expect((await request.get('/api/auth/me', {headers})).status()).toBe(401);
    expect((await request.post('/api/auth/login', {data: {username: 'admin', password: '123456'}})).status()).toBe(401);
    await page.getByLabel('账号').fill('admin');
    await page.getByLabel('密码', {exact: true}).fill(password);
    await page.getByRole('button', {name: /登\s*录/}).click();
    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.getByRole('heading', {name: '控制台总览'})).toBeVisible();
    await page.screenshot({path: test.info().outputPath('installer-dashboard.png'), fullPage: true});
});
