import path from 'node:path';

import react from '@vitejs/plugin-react';
import {defineConfig, loadEnv} from 'vite';

/**
 * Vite 配置统一收口 API base 与本地代理目标。
 * 本地开发服务器需要直接复用现有 `/api/**` 后端接口，
 * 避免页面内散落绝对地址或重新发明第二套联调方式。
 */
export default defineConfig(({mode}) => {
    const env = loadEnv(mode, process.cwd(), '');
    const proxyTarget = env.VITE_API_PROXY_TARGET || 'http://127.0.0.1:18888';
    // 生产包默认保留已有 SIM 入口；是否可执行仍由服务端能力、角色和风控决定。
    // 显式关闭用于受限部署，开发模式仍需开启；非法值直接终止构建，避免静默丢失功能。
    const simulationEntryFlags = Object.fromEntries(
        ['VITE_STRATEGY_SIM_ENABLED', 'VITE_CONTINUOUS_SIM_ENABLED'].map((key) => {
            const value = env[key] ?? (mode === 'production' ? 'true' : 'false');
            if (value !== 'true' && value !== 'false') {
                throw new Error(`${key} 必须为 true 或 false`);
            }
            return [`import.meta.env.${key}`, JSON.stringify(value)];
        }),
    );

    return {
        plugins: [react()],
        define: simulationEntryFlags,
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
            proxy: {'/api': {target: proxyTarget, changeOrigin: true, timeout: 15000, proxyTimeout: 15000}},
        },
    };
});
