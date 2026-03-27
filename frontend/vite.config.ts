import path from 'node:path';

import react from '@vitejs/plugin-react';
import {defineConfig, loadEnv} from 'vite';

/**
 * Vite 配置统一收口 API base 与本地代理目标。
 * Why:
 * GateG-1 需要让前端开发服务器直接复用现有 `/api/**` 后端接口，
 * 避免页面内散落绝对地址或重新发明第二套联调方式。
 */
export default defineConfig(({mode}) => {
    const env = loadEnv(mode, process.cwd(), '');
    const proxyTarget = env.VITE_API_PROXY_TARGET || 'http://127.0.0.1:18888';

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
                port: 4173,
                proxy: {
                    '/api': {
                        target: proxyTarget,
                        changeOrigin: true,
                    },
                },
            }
            : {
                host: '127.0.0.1',
                port: 4173,
            },
        preview: {
            host: '127.0.0.1',
            port: 4173,
        },
    };
});
