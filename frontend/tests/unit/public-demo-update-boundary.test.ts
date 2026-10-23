import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {describe, expect, it, vi} from 'vitest';
import '@/i18n';

vi.mock('@/utils/distribution', () => ({isPublicDemo: true}));
import {SystemVersionPanel} from '../../src/features/system/components/SystemVersionPanel';
import {WhatsNew} from '../../src/features/system/components/VersionHistory';

describe('Public Demo 更新操作边界', () => {
    it('不渲染宿主安装入口、交接表单或Self-Hosted登录提示', () => {
        const client = new QueryClient();
        const markup = renderToStaticMarkup(createElement(QueryClientProvider, {client},
            createElement(SystemVersionPanel), createElement(WhatsNew)));
        expect(markup).toContain('版本历史');
        expect(markup).not.toContain('查看检查与更新步骤');
        expect(markup).not.toContain('检查与更新交接');
        expect(markup).not.toContain('目标安装环境');
        expect(markup).not.toContain('nexusquant.ps1');
        expect(markup).not.toContain('本次更新');
    });
});
