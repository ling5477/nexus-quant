import {expect, test} from 'playwright/test';
import enPages from '@/i18n/locales/en-US/pages.json' with {type: 'json'};
import zhPages from '@/i18n/locales/zh-CN/pages.json' with {type: 'json'};
import {loginForReadOnlyChecks} from './read-only-console-support';

// 正式只读页面不记录登录trace，也不修改任何系统任务控制。
test.use({trace: 'off'});

test('正式8项系统任务提供各自范围，持续SIM和Paper弹窗使用同一对象约束', async ({page}) => {
    const common = await loginForReadOnlyChecks(page);
    const labels = common.language === 'Language' ? enPages : zhPages;
    const responsePromise = page.waitForResponse(
        (response) =>
            response.url().endsWith('/api/scheduler/jobs') && response.request().method() === 'GET',
    );
    let writes = 0;
    page.on('request', (request) => {
        if (request.url().includes('/api/') && !['GET', 'HEAD'].includes(request.method()))
            writes++;
    });
    await page.goto('/system/scheduler');
    const response = await responsePromise;
    expect(response.status()).toBe(200);
    const rows: Array<{jobKey: string; minFixedDelayMs: number; maxFixedDelayMs: number}> =
        await response.json();
    expect(rows).toHaveLength(8);
    expect(new Set(rows.map((row) => row.jobKey)).size).toBe(8);
    expect(
        rows.every(
            (row) =>
                Number.isSafeInteger(row.minFixedDelayMs) &&
                row.minFixedDelayMs > 0 &&
                row.maxFixedDelayMs >= row.minFixedDelayMs,
        ),
    ).toBe(true);
    await expect(
        page.getByRole('heading', {name: labels.schedulerTitle, exact: true}),
    ).toBeVisible();
    for (const [key, min, max] of [
        ['CONTINUOUS_SIM_POLL', 30000, 86400000],
        ['PAPER_MATCHING', 1000, 60000],
    ] as const) {
        const expected = rows.find((row) => row.jobKey === key)!;
        expect(expected.minFixedDelayMs).toBe(min);
        expect(expected.maxFixedDelayMs).toBe(max);
        const row = page.getByRole('row').filter({hasText: key});
        await row.getByRole('button', {name: labels.schedulerEditDelay, exact: true}).click();
        const dialog = page.getByRole('dialog', {name: labels.schedulerEditDelay, exact: true});
        const input = dialog.getByRole('spinbutton', {
            name: labels.schedulerFixedDelay,
            exact: true,
        });
        await expect(input).toHaveAttribute('aria-valuemin', String(min));
        await expect(input).toHaveAttribute('aria-valuemax', String(max));
        await input.fill(String(min - 1));
        await expect(dialog.getByRole('button', {name: /^(保\s*存|Save)$/})).toBeDisabled();
        await dialog.getByRole('button', {name: /^(取\s*消|Cancel)$/}).click();
        await expect(dialog).not.toBeVisible();
    }
    // 运行边界链接只进入同一正式只读合同，页面跳转不能隐式发起诊断或交易写入。
    await page.getByRole('link', {name: labels.schedulerReadinessLink, exact: true}).click();
    await expect(page).toHaveURL(/\/runtime\/readiness$/);
    await expect(
        page.getByRole('heading', {name: labels.runtimeReadinessOverview, exact: true}),
    ).toBeVisible();
    expect(writes).toBe(0);
});
