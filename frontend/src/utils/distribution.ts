/** 发行身份在构建时冻结；运行时配置不能为 Self-Hosted 增加公共注册能力。 */
export const isPublicDemo = import.meta.env.VITE_DISTRIBUTION === 'PUBLIC_DEMO';

/** 仅供研究和受控 SIM 页面使用；真实交易与凭据操作不使用此能力。 */
export function canWriteResearch(roles: readonly string[] | undefined): boolean {
    return roles?.some((role) =>
        ['ADMIN', 'OPERATOR', 'ROLE_ADMIN', 'ROLE_OPERATOR'].includes(role)
        || (isPublicDemo && ['DEMO_USER', 'ROLE_DEMO_USER'].includes(role)),
    ) ?? false;
}

/** 平台角色独占身份，不继承普通业务权限。 */
export function isPlatformIdentity(roles: readonly string[] | undefined): boolean {
    return roles?.some((role) => role === 'PLATFORM_ADMIN' || role === 'ROLE_PLATFORM_ADMIN') ?? false;
}

export function sessionHome(roles: readonly string[] | undefined): string {
    return isPublicDemo ? isPlatformIdentity(roles) ? '/platform' : '/demo' : '/dashboard';
}

/** 登录后的返回路径保持当前身份边界，旧账号书签不能把新账号带入另一权限域。 */
export function loginDestination(redirect: string | null, roles: readonly string[] | undefined): string {
    if (!redirect || !redirect.startsWith('/') || redirect.startsWith('//')) return sessionHome(roles);
    if (isPublicDemo) {
        const path = redirect.split(/[?#]/, 1)[0];
        const platformPath = path === '/platform' || path.startsWith('/platform/');
        if (isPlatformIdentity(roles) !== platformPath) return sessionHome(roles);
    }
    return redirect;
}
