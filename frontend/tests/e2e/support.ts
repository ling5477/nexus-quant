import {expect, type Page} from 'playwright/test';

const username = process.env.E2E_USERNAME ?? 'admin';
const password = process.env.E2E_PASSWORD ?? 'ChangeMe123!';
const defaultUsername = 'admin';
const defaultPassword = 'ChangeMe123!';

export async function loginToConsole(page: Page) {
    await page.goto('/login');
    await expect(page.getByRole('heading', {name: '登录控制台'})).toBeVisible();

    if (username !== defaultUsername) {
        await page.getByLabel('用户名').fill(username);
    }
    if (password !== defaultPassword) {
        await page.getByLabel('密码').fill(password);
    }
    await page.getByRole('button', {name: '登录并进入控制台'}).click();

    // Why: 本地验收会先拉起后端再并发启动 Playwright worker，首页路由恢复偶发慢于默认 5 秒，
    // 这里显式放宽等待窗口，避免把真实登录成功误判成路由失败。
    await expect(page).toHaveURL(/\/dashboard$/, {timeout: 15_000});
    await expect(page.getByRole('heading', {name: '控制台总览'})).toBeVisible({timeout: 15_000});
}
