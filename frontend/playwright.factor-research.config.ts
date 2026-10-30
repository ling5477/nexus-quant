import {defineConfig, devices} from 'playwright/test';
import base from './playwright.config';

/** 服务与数据库由任务自有夹具启动，浏览器不代替产品启动 Python。 */
export default defineConfig({...base, testDir: './tests/experiments', testMatch: 'factor-research.spec.ts',
    timeout: 180_000, workers: 1, webServer: undefined,
    use: {...base.use, baseURL: process.env.E2E_BASE_URL ?? 'http://127.0.0.1:51905', viewport: {width: 1440, height: 1000}},
    projects: [{name: 'chromium', use: {...devices['Desktop Chrome'], channel: 'chrome'}}]});
