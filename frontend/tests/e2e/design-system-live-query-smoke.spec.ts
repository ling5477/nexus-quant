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
        await expect(freshness).toContainText('Fresh');

        // 暂停轮询 → disabled(归一化为 DataFreshness 的 Disabled)。
        await page.getByRole('button', {name: '暂停轮询'}).click();
        await expect(status).toHaveText('disabled');
        await expect(freshness).toContainText('Disabled');

        // 恢复轮询 + 手动刷新 → 回到 fresh(手动刷新强制一次拉取,避免 staleTime 缓存影响)。
        await page.getByRole('button', {name: '恢复轮询'}).click();
        await page.getByRole('button', {name: '立即刷新'}).click();
        await expect(status).toHaveText('fresh', {timeout: 15_000});

        // 模拟错误 → error,且 DataFreshness 显示 Error(失败不静默)。
        await page.getByRole('button', {name: '模拟错误'}).click();
        await expect(status).toHaveText('error', {timeout: 15_000});
        await expect(freshness).toContainText('Error');

        // 恢复正常 + 手动刷新 → 回到 fresh。
        await page.getByRole('button', {name: '恢复正常'}).click();
        await page.getByRole('button', {name: '立即刷新'}).click();
        await expect(status).toHaveText('fresh', {timeout: 15_000});
    });
});
