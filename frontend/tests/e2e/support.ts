import {expect, type Page} from 'playwright/test';

const username = process.env.E2E_USERNAME ?? 'admin';
const password = process.env.E2E_PASSWORD ?? 'ChangeMe123!';

export async function loginToConsole(page: Page) {
    await page.goto('/login');
    await expect(page.getByRole('heading', {name: '登录控制台'})).toBeVisible();

    await page.getByLabel('用户名').fill(username);
    await page.getByLabel('密码').fill(password);
    await page.getByRole('button', {name: '登录并进入控制台'}).click();

    await expect(page).toHaveURL(/\/dashboard$/);
}
