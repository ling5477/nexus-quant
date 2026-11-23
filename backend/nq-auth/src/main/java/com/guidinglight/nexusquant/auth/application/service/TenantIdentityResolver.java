package com.guidinglight.nexusquant.auth.application.service;

import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.auth.domain.port.AuthUserRepository;
import com.guidinglight.nexusquant.auth.domain.tenant.Tenant;
import com.guidinglight.nexusquant.auth.domain.tenant.TenantMembership;
import com.guidinglight.nexusquant.common.tenant.TenantIdentity;
import com.guidinglight.nexusquant.common.tenant.TenantKind;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.authentication.BadCredentialsException;

/** 每次认证重新读取成员状态；无归属、失效或歧义时不退回本地租户。 */
public final class TenantIdentityResolver {
    public static final String DEMO_USER = "DEMO_USER";
    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";
    private static final Set<String> LOCAL_ROLES = Set.of("ADMIN", "OPERATOR", "VIEWER");
    private final AuthUserRepository users;

    public TenantIdentityResolver(AuthUserRepository users) {
        this.users = Objects.requireNonNull(users);
    }

    /** 后台调用也须从数据库重新取得用户，不能把保存的 JWT 或旧角色当执行资格。 */
    public Identity resolve(String username) {
        return resolve(users.findByUsername(username).orElseThrow(TenantIdentityResolver::invalid));
    }

    /** 参数只能来自服务器的 AuthUserRepository，不能由请求 DTO 构造。 */
    public Identity resolve(AuthUserProfile user) {
        if (user == null || user.userId() == null || user.userId() <= 0 || !user.enabled()) throw invalid();
        var memberships = users.findTenantMemberships(user.userId());
        var roles = Set.copyOf(user.roles());
        if (roles.contains(PLATFORM_ADMIN)) {
            if (roles.size() != 1 || !memberships.isEmpty()) throw invalid();
            return new Identity(null, List.of(PLATFORM_ADMIN));
        }
        if (memberships.size() != 1) throw invalid();
        var membership = memberships.getFirst();
        var tenant = membership.tenant();
        if (membership.userId() != user.userId() || membership.status() != TenantMembership.Status.ACTIVE
                || tenant.status() != Tenant.Status.ACTIVE) throw invalid();
        List<String> effectiveRoles;
        if (tenant.kind() == TenantKind.DEMO) {
            if (!roles.isEmpty() && !roles.equals(Set.of(DEMO_USER))) throw invalid();
            effectiveRoles = List.of(DEMO_USER);
        } else {
            if (!LOCAL_ROLES.containsAll(roles)) throw invalid();
            effectiveRoles = List.copyOf(user.roles());
        }
        return new Identity(new TenantIdentity(tenant.id(), tenant.code(), tenant.kind(), user.userId()), effectiveRoles);
    }

    private static BadCredentialsException invalid() {
        return new BadCredentialsException("active unambiguous tenant identity required");
    }

    /** 平台身份刻意没有业务 Tenant；只能使用专用平台权限入口。 */
    public record Identity(TenantIdentity tenant, List<String> roles) {
        public Identity {
            roles = List.copyOf(roles);
        }
    }
}
