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
var baseURL = (_a = process.env.E2E_BASE_URL) !== null && _a !== void 0 ? _a : 'http://127.0.0.1:5179';
var useExternalDevServer = process.env.E2E_EXTERNAL_DEV_SERVER === 'true';
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
        baseURL: baseURL,
        actionTimeout: 30000,
        navigationTimeout: 30000,
        trace: 'retain-on-failure',
    },
    webServer: useExternalDevServer
        ? undefined
        : {
            command: 'node ./node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5179',
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
