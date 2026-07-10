import {defineConfig, devices} from 'playwright/test';

const baseURL = process.env.E2E_BASE_URL ?? 'http://127.0.0.1:51888';
const useExternalDevServer = process.env.E2E_EXTERNAL_DEV_SERVER === 'true';

/**
 * Playwright 只负责启动前端并验证关键导航。
 * Why:
 * 后端仍由现有 Spring Boot 应用提供，冒烟用例通过真实登录接口确认
 * token、guard 和菜单跳转链路没有断裂。
 */
export default defineConfig({
    testDir: './tests/e2e',
    fullyParallel: false,
    // Why:
    // E2E 回归依赖单套 local 后端与 Vite dev server。
    // 并发 worker 会把登录链、前端热服务和本地样本数据竞争放大成非确定性失败，
    // 串行 worker 更符合当前项目的“关键链路可重复验收”目标。
    workers: 1,
    retries: 0,
    use: {
        baseURL,
        actionTimeout: 30_000,
        navigationTimeout: 30_000,
        trace: 'retain-on-failure',
    },
    webServer: useExternalDevServer
        ? undefined
        : {
            command: 'node ./node_modules/vite/bin/vite.js --host 127.0.0.1 --port 51888',
            url: baseURL,
            reuseExistingServer: true,
            timeout: 120000,
        },
    projects: [
        {
            name: 'chromium',
            use: {...devices['Desktop Chrome']},
        },
    ],
});
