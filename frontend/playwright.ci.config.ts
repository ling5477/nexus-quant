import {defineConfig, devices} from 'playwright/test';

/**
 * Playwright CI 配置 —— Batch 5A no-backend E2E（仅 GitHub Actions 使用）。
 *
 * 职责:在 production build 产物上,用 loopback `vite preview` 运行四个已批准的
 * 纯 loopback / no-backend Playwright spec。该配置**不接** backend / PostgreSQL /
 * Flyway / auth / token / 账户写入 / 外网业务调用,也不调用 `loginToConsole()`。
 *
 * 关键约束(fail-closed):
 * 1) testDir + testMatch 仅匹配 5A allowlist 四个 spec,绝不用宽泛 glob 自动发现全部 27 个 spec;
 *    CI 命令仍会显式再列出四个 spec 作为第二道过滤。
 * 2) trace / screenshot / video 全部显式 `off`;reporter 仅 `line`(console),不产 HTML report。
 * 3) preview 只绑定 127.0.0.1,不绑定 0.0.0.0;`reuseExistingServer: false`,显式 server timeout。
 * 4) 不使用 storageState;不上传任何 artifact(trace/video/screenshot/HAR/storageState/
 *    browser profile/test-results/HTML report)。临时 output 由 CI job 在结束前删除。
 *
 * 与 `playwright.config.ts`(本地 dev server 全量回归)分离,互不影响。
 */

const HOST = '127.0.0.1';
const PORT = 5179;
const baseURL = `http://${HOST}:${PORT}`;

export default defineConfig({
    testDir: './tests/e2e',
    // Fail-closed allowlist:只发现 5A 四个 no-backend spec。设置 testMatch 会覆盖默认 `**/*.spec.ts`,
    // 因此其余 23 个 spec 不会被自动发现。
    testMatch: [
        '**/login-page-smoke.spec.ts',
        '**/design-system-table-smoke.spec.ts',
        '**/design-system-live-query-smoke.spec.ts',
        '**/design-system-backtest-chart-smoke.spec.ts',
    ],
    fullyParallel: false,
    // CI 禁止 test.only 误入基线。
    forbidOnly: true,
    workers: 1,
    retries: 0,
    // 仅 console line reporter:不生成 HTML report,不写 results JSON,不产 artifact。
    reporter: [['line']],
    // 即便全部 capture 关闭,Playwright 仍可能创建该目录;CI job 在结束前显式删除,且绝不上传。
    outputDir: './test-results-ci',
    use: {
        baseURL,
        actionTimeout: 30_000,
        navigationTimeout: 30_000,
        // 二进制证据全部禁用:本轮不上传也不生成 trace/screenshot/video。
        trace: 'off',
        screenshot: 'off',
        video: 'off',
        // 不注入 storageState:5A 无认证态、无 token。
    },
    webServer: {
        // 仅在 production build 产物上启动 loopback preview;只绑定 127.0.0.1,不绑定 0.0.0.0。
        // 不经 dev server,preview 无 `/api` proxy —— 正符合 5A 不接 backend 的边界。
        command: 'node ./node_modules/vite/bin/vite.js preview --host 127.0.0.1 --port 5179',
        url: baseURL,
        reuseExistingServer: false,
        timeout: 120_000,
    },
    projects: [
        {
            name: 'chromium',
            use: {...devices['Desktop Chrome']},
        },
    ],
});
