import {fileURLToPath} from 'node:url';
import {defineConfig, mergeConfig} from 'vite';
import productionConfig from '../../../vite.config';

const root = fileURLToPath(new URL('../../../', import.meta.url));

// 夹具产物单独保存，正式 dist 及其 SBOM/发行身份继续来自原 production build。
export default defineConfig((env) => {
    const config = mergeConfig(productionConfig(env), {
    root,
    build: {
        outDir: 'dist-component-tests',
        rollupOptions: {input: {
            index: `${root}index.html`,
            designSystem: `${root}tests/e2e/fixtures/design-system.html`,
        }},
    },
    });
    // 深合并空对象不会移除既有代理；必须在合并后替换，防止测试访问本机业务服务。
    config.server = {...config.server, proxy: {}};
    config.preview = {...config.preview, proxy: {}};
    return config;
});
