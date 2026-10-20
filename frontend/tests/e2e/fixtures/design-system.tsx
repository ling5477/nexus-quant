import {createRoot} from 'react-dom/client';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {DesignSystemDemoPage} from '@/pages/dev/DesignSystemDemoPage';
import '@/i18n';
import '@/styles/index.css';
import '@/styles/workspace.css';

// 仅用于组件合同测试；不注册正式产品路由，也不注入业务 API 或认证状态。
const root = document.getElementById('root');
if (!root) throw new Error('Component test root is missing');
const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});
createRoot(root).render(
    <QueryClientProvider client={queryClient}><DesignSystemDemoPage /></QueryClientProvider>,
);
