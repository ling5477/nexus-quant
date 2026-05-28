import {Navigate, Outlet, useLocation} from 'react-router-dom';

import {AppLoadingScreen} from '@/components/app/AppLoadingScreen';
import {selectIsAuthenticated, useAuthStore} from '@/store/auth-store';

/**
 * RequireAuth 把所有受保护路由统一收口到同一处守卫。
 * Why:
 * 登录态恢复必须集中在受保护路由入口，页面自身不能再重复做鉴权判断，
 * 否则会出现刷新恢复和权限跳转行为不一致。
 */
export function RequireAuth() {
    const location = useLocation();
    const isAuthenticated = useAuthStore(selectIsAuthenticated);
    const accessToken = useAuthStore((state) => state.accessToken);
    const bootstrapStatus = useAuthStore((state) => state.bootstrapStatus);

    if (accessToken && bootstrapStatus === 'loading') {
        return (
            <AppLoadingScreen
                message="正在恢复登录状态"
                detail="控制台正在校验当前登录状态，请稍候。"
            />
        );
    }

    if (!isAuthenticated) {
        const redirect = `${location.pathname}${location.search}`;
        return <Navigate to={`/login?redirect=${encodeURIComponent(redirect)}`} replace/>;
    }

    return <Outlet/>;
}
