import {defineConfig, devices} from 'playwright/test';
import base from './playwright.config';

/** 使用预先启动的任务专属Spring/PG与Vite，不隐式启动其他服务或交易运行。 */
export default defineConfig({...base,
    testDir: './tests/experiments',
    testMatch: 'forward-sim-evaluation.spec.ts',
    timeout: 180_000,
    workers: 1,
    webServer: undefined,
    use: {...base.use, baseURL: process.env.E2E_BASE_URL ?? 'http://127.0.0.1:51904', viewport: {width: 1440, height: 1000}},
    projects: [{name: 'chromium', use: {...devices['Desktop Chrome'],
        ...(process.env.E2E_BROWSER_CHANNEL ? {channel: process.env.E2E_BROWSER_CHANNEL} : {})}}],
});
