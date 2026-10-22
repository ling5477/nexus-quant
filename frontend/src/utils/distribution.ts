/** 发行身份在构建时冻结；运行时配置不能为 Self-Hosted 增加公共注册能力。 */
export const isPublicDemo = import.meta.env.VITE_DISTRIBUTION === 'PUBLIC_DEMO';

/** 仅供研究和受控 SIM 页面使用；真实交易与凭据操作不使用此能力。 */
export function canWriteResearch(roles: readonly string[] | undefined): boolean {
    return roles?.some((role) =>
        ['ADMIN', 'OPERATOR', 'ROLE_ADMIN', 'ROLE_OPERATOR'].includes(role)
        || (isPublicDemo && ['DEMO_USER', 'ROLE_DEMO_USER'].includes(role)),
    ) ?? false;
}
