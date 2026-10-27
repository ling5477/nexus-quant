import {defineConfig, devices} from 'playwright/test';
import base from './playwright.config';

export default defineConfig({...base, testDir: './tests/experiments', timeout: 90_000,
    projects: [{name: 'chromium', use: {...devices['Desktop Chrome'], ...(process.env.E2E_BROWSER_CHANNEL ? {channel: process.env.E2E_BROWSER_CHANNEL} : {})}}],
});
