import {Navigate} from 'react-router-dom';
import {renderApplication} from '@/app/render';
import {createAppRouter} from '@/router/routes';
import {useAuthStore} from '@/store/auth-store';
import {isPlatformIdentity} from '@/utils/distribution';
import '@/features/platform/i18n';

function Home() {
    const roles = useAuthStore((state) => state.currentUser?.roles);
    return <Navigate to={isPlatformIdentity(roles) ? '/platform' : '/demo'} replace />;
}
renderApplication(createAppRouter({
    home: <Home />,
    publicRoutes: [
        {path: '/register', lazy: () => import('@/features/public-demo/RegisterPage')},
        {path: '/verify-email', lazy: () => import('@/features/public-demo/VerifyEmailPage')},
    ],
    consoleRoutes: [
        {path: 'demo', lazy: () => import('@/features/public-demo/DemoAccountPage')},
        {path: 'platform', lazy: () => import('@/features/platform/PlatformPage')},
        {path: 'platform/tenants', lazy: () => import('@/features/platform/TenantsPage')},
        {path: 'platform/tenants/:tenantId', lazy: () => import('@/features/platform/TenantDetailPage')},
    ],
    simSetup: {lazy: () => import('@/features/public-demo/DemoSimSetupPage')},
}));
