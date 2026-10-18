import {expect, type Page} from 'playwright/test';
import zhCommon from '@/i18n/locales/zh-CN/common.json' with {type: 'json'};
import enCommon from '@/i18n/locales/en-US/common.json' with {type: 'json'};

/** 只读验收只完成正式登录，不创建账户、不重设默认账户或改写用户偏好。 */
export async function loginForReadOnlyChecks(page: Page) {
    await page.goto('/login');
    await page.getByLabel('账号', {exact: true}).fill(process.env.E2E_USERNAME ?? 'admin');
    await page.getByLabel('密码', {exact: true}).fill(process.env.E2E_PASSWORD ?? 'ChangeMe123!');
    const preferencesResponse = page.waitForResponse(
        (response) =>
            response.url().endsWith('/api/user/preferences') &&
            response.request().method() === 'GET',
    );
    await page.getByRole('button', {name: /登\s*录/}).click();
    await expect(page).toHaveURL(/\/dashboard$/);
    const response = await preferencesResponse;
    const preferences = response.ok() ? await response.json() : null;
    if (!preferences?.hideLoginDisclosures) {
        const notice = page.getByRole('dialog', {name: /^(使用声明|Disclosures)$/});
        await notice.getByRole('button', {name: /^(确认阅读|Confirm read)$/}).click();
        await expect(notice).not.toBeVisible();
    }
    // 语言选择器会写用户偏好；只读验收随实际偏好选择断言，不为测试切换或保存语言。
    const texts = preferences?.locale === 'en-US' ? enCommon : zhCommon;
    await expect(
        page.getByRole('button', {
            name: preferences?.locale === 'en-US' ? 'Sign out' : '退出登录',
            exact: true,
        }),
    ).toBeVisible();
    return texts;
}

/** 按业务身份定位行，翻译名称或分页变化不能让断言误落到另一个交易所或能力。 */
export function capabilityRow(page: Page, venue: string, capability: string) {
    return page.locator(`tr[data-venue="${venue}"][data-capability="${capability}"]`);
}

/** 筛选不产生外部调用，九个能力行必须全部保留在当前对象下。 */
export async function selectCapabilityVenue(page: Page, venue: string): Promise<void> {
    // Ant Design非搜索Select的透明input被展示层覆盖，使用真实键盘路径开启选项。
    await page.getByRole('combobox', {name: /^(查看对象|View object)$/}).press('Enter');
    await page.getByText(`${venue} · 9`, {exact: true}).click();
    await expect(page.locator('tr[data-venue]')).toHaveCount(9);
    await expect(page.locator(`tr[data-venue="${venue}"]`)).toHaveCount(9);
}
