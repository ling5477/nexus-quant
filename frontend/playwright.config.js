var __assign = (this && this.__assign) || function () {
    __assign = Object.assign || function(t) {
        for (var s, i = 1, n = arguments.length; i < n; i++) {
            s = arguments[i];
            for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p))
                t[p] = s[p];
        }
        return t;
    };
    return __assign.apply(this, arguments);
};
var _a;
import { defineConfig, devices } from 'playwright/test';
var baseURL = (_a = process.env.E2E_BASE_URL) !== null && _a !== void 0 ? _a : 'http://127.0.0.1:4173';
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
        baseURL: baseURL,
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
            use: __assign({}, devices['Desktop Chrome']),
        },
    ],
});
