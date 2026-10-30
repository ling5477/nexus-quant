import {defineConfig, devices} from 'playwright/test';
import base from './playwright.config';

/** 后端与Vite由任务自有夹具预先启动；本配置不隐式启动或复用其他研究服务。 */
export default defineConfig({...base,
    testDir: './tests/experiments',
    testMatch: 'long-historical-research.spec.ts',
    timeout: 240_000,
    workers: 1,
    webServer: undefined,
    use: {...base.use, baseURL: process.env.E2E_BASE_URL ?? 'http://127.0.0.1:51903', viewport: {width: 1440, height: 1000}},
    projects: [{name: 'chromium', use: {...devices['Desktop Chrome'],
        ...(process.env.E2E_BROWSER_CHANNEL ? {channel: process.env.E2E_BROWSER_CHANNEL} : {})}}],
});
