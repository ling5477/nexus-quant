import {defineConfig, devices} from 'playwright/test';

const baseURL = process.env.E2E_BASE_URL ?? 'http://127.0.0.1:4173';

/**
 * Playwright 只负责启动前端并验证关键导航。
 * Why:
 * 后端仍由现有 Spring Boot 应用提供，冒烟用例通过真实登录接口确认
 * token、guard 和菜单跳转链路没有断裂。
 */
export default defineConfig({
    testDir: './tests/e2e',
    fullyParallel: false,
    retries: 0,
    use: {
        baseURL,
        trace: 'retain-on-failure',
    },
    webServer: {
        command: 'npm run dev',
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
