package com.guidinglight.nexusquant.demo.platform;

import com.guidinglight.nexusquant.auth.application.service.TenantIdentityResolver;
import com.guidinglight.nexusquant.auth.domain.port.AuthUserRepository;
import com.guidinglight.nexusquant.common.tenant.TenantContext;
import com.guidinglight.nexusquant.security.token.model.TokenClaims;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** 服务入口重新校验平台主体，直接调用服务也不能靠目标 TenantId 建立业务上下文。 */
@Component
public final class PlatformAccess {
    private final AuthUserRepository users;
    private final Duration idleTimeout;

    public PlatformAccess(AuthUserRepository users, Environment environment) {
        this.users = users;
        this.idleTimeout = environment.getProperty("nq.security.session-idle-timeout", Duration.class, Duration.ofMinutes(30));
    }

    public Actor require() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (TenantContext.current().isPresent() || authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof TokenClaims claims) || claims.sessionId() == null
                || authentication.getAuthorities().size() != 1
                || !authentication.getAuthorities().iterator().next().getAuthority().equals("ROLE_PLATFORM_ADMIN")) throw denied();
        var user = users.findByUsername(claims.username()).orElseThrow(PlatformAccess::denied);
        if (!user.enabled() || user.mustChangePassword() || user.authVersion() != claims.authVersion()
                || !claims.expiresAt().isAfter(Instant.now())
                || !new TenantIdentityResolver(users).resolve(user).roles().equals(List.of("PLATFORM_ADMIN"))
                || !users.isSessionActive(claims.sessionId(), user.username(), user.authVersion(),
                    Instant.now(), Instant.now().minus(idleTimeout))) throw denied();
        return new Actor(user.userId(), claims.sessionId(), user.authVersion());
    }

    private static AccessDeniedException denied() { return new AccessDeniedException("PLATFORM_ACCESS_DENIED"); }
    public record Actor(long userId, String sessionId, long authVersion) {}
}
