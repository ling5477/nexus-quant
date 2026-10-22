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
import path from 'node:path';
import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv } from 'vite';
/**
 * Vite 配置统一收口 API base 与本地代理目标。
 * 本地开发服务器需要直接复用现有 `/api/**` 后端接口，
 * 避免页面内散落绝对地址或重新发明第二套联调方式。
 */
export default defineConfig(function (_a) {
    var _b;
    var mode = _a.mode;
    var env = loadEnv(mode, process.cwd(), '');
    var proxyTarget = env.VITE_API_PROXY_TARGET || 'http://127.0.0.1:18888';
    var distribution = (_b = env.VITE_DISTRIBUTION) !== null && _b !== void 0 ? _b : 'SELF_HOSTED';
    if (!['SELF_HOSTED', 'PUBLIC_DEMO'].includes(distribution)) {
        throw new Error('VITE_DISTRIBUTION 必须为 SELF_HOSTED 或 PUBLIC_DEMO');
    }
    // 生产包默认保留已有 SIM 入口；是否可执行仍由服务端能力、角色和风控决定。
    // 显式关闭用于受限部署，开发模式仍需开启；非法值直接终止构建，避免静默丢失功能。
    var simulationEntryFlags = Object.fromEntries(['VITE_STRATEGY_SIM_ENABLED', 'VITE_CONTINUOUS_SIM_ENABLED'].map(function (key) {
        var _a;
        var value = (_a = env[key]) !== null && _a !== void 0 ? _a : (mode === 'production' ? 'true' : 'false');
        if (value !== 'true' && value !== 'false') {
            throw new Error("".concat(key, " \u5FC5\u987B\u4E3A true \u6216 false"));
        }
        return ["import.meta.env.".concat(key), JSON.stringify(value)];
    }));
    return {
        plugins: [react()],
        define: __assign(__assign({}, simulationEntryFlags), { 'import.meta.env.VITE_DISTRIBUTION': JSON.stringify(distribution) }),
        resolve: {
            alias: {
                '@': path.resolve(__dirname, './src'),
            },
        },
        server: proxyTarget
            ? {
                host: '127.0.0.1',
                port: 51888,
                proxy: {
                    '/api': {
                        target: proxyTarget,
                        changeOrigin: true,
                    },
                },
            }
            : {
                host: '127.0.0.1',
                port: 51888,
            },
        preview: {
            host: '127.0.0.1',
            port: 51888,
            proxy: { '/api': { target: proxyTarget, changeOrigin: true, timeout: 15000, proxyTimeout: 15000 } },
        },
    };
});
