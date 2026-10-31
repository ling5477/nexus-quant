package com.guidinglight.nexusquant.security.token.port;
import com.guidinglight.nexusquant.security.token.model.TokenClaims;
import com.guidinglight.nexusquant.common.tenant.TenantIdentity;
import java.util.List;
import java.util.Optional;
/** 持久化用户状态的校验端口；安全模块不拥有用户数据源。 */
@FunctionalInterface
public interface TokenUserValidator {
    Optional<UserState> validate(TokenClaims claims);
    record UserState(List<String> roles, boolean mustChangePassword, TenantIdentity tenantIdentity) {
        public UserState {
            roles = List.copyOf(roles);
        }

        /** 保留独立安全组件的原校验端口；正式装配必须提供已验证租户或专用平台角色。 */
        public UserState(List<String> roles, boolean mustChangePassword) {
            this(roles, mustChangePassword, null);
        }
    }
}
