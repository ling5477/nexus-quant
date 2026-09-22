import {expect, test} from 'playwright/test';

test.describe('design system live query smoke (B0.3)', () => {
    test('useLiveQuery polling / 手动刷新 / disabled / error 状态归一化', async ({page}) => {
        // Why: /dev/design-system 是公开自检路由,不依赖登录/后端;
        // 模拟源为本地 fake(无 WebSocket/SSE/真实后端),smoke 只验证 useLiveQuery 状态归一化。
        // 多次状态切换累计耗时较长,放宽单测超时。
        test.setTimeout(60_000);
        await page.goto('/dev/design-system');

        const status = page.getByLabel('live status');
        const freshness = page.getByTitle(/^模拟行情源:/);

        // 首载后应进入 fresh(本地模拟源约 150–400ms 返回)。
        await expect(status).toHaveText('fresh', {timeout: 15_000});
        await expect(freshness).toContainText('最新');

        // 暂停轮询后 machine status 保持 disabled，展示文案采用默认中文。
        await page.getByRole('button', {name: '暂停轮询'}).click();
        await expect(status).toHaveText('disabled');
        await expect(freshness).toContainText('已禁用');

        // 恢复轮询 + 手动刷新 → 回到 fresh(手动刷新强制一次拉取,避免 staleTime 缓存影响)。
        await page.getByRole('button', {name: '恢复轮询'}).click();
        await page.getByRole('button', {name: '立即刷新'}).click();
        await expect(status).toHaveText('fresh', {timeout: 15_000});

        // 模拟错误后 machine status 保持 error，展示明确错误文案，失败不静默。
        await page.getByRole('button', {name: '模拟错误'}).click();
        await expect(status).toHaveText('error', {timeout: 15_000});
        await expect(freshness).toContainText('错误');

        // 恢复正常 + 手动刷新 → 回到 fresh。
        await page.getByRole('button', {name: '恢复正常'}).click();
        await page.getByRole('button', {name: '立即刷新'}).click();
        await expect(status).toHaveText('fresh', {timeout: 15_000});
    });
});
