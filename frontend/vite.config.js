import path from 'node:path';
import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv } from 'vite';
/**
 * Vite 配置统一收口 API base 与本地代理目标。
 * Why:
 * 本地开发服务器需要直接复用现有 `/api/**` 后端接口，
 * 避免页面内散落绝对地址或重新发明第二套联调方式。
 */
export default defineConfig(function (_a) {
    var mode = _a.mode;
    var env = loadEnv(mode, process.cwd(), '');
    var proxyTarget = env.VITE_API_PROXY_TARGET || 'http://127.0.0.1:18888';
    return {
        plugins: [react()],
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
        },
    };
});
