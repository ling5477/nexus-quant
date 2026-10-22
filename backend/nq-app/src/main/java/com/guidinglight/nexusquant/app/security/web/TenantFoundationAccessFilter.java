package com.guidinglight.nexusquant.app.security.web;

import com.guidinglight.nexusquant.auth.application.service.TenantIdentityResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 身份入口与逐项业务白名单独立判断；平台角色和未验证业务路径保持默认拒绝。 */
public final class TenantFoundationAccessFilter extends OncePerRequestFilter {
    private final ApiSecurityErrorWriter errors;
    private final boolean businessEnabled;

    public TenantFoundationAccessFilter(ApiSecurityErrorWriter errors) {
        this(errors, false);
    }

    public TenantFoundationAccessFilter(ApiSecurityErrorWriter errors, boolean businessEnabled) {
        this.errors = errors;
        this.businessEnabled = businessEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean restricted = authentication != null && authentication.getAuthorities().stream().anyMatch(
                authority -> authority.getAuthority().equals("ROLE_" + TenantIdentityResolver.DEMO_USER)
                        || authority.getAuthority().equals("ROLE_" + TenantIdentityResolver.PLATFORM_ADMIN));
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean identityRead = "GET".equals(request.getMethod())
                && ("/api/auth/me".equals(path) || "/api/auth/tenant".equals(path));
        boolean identityWrite = "POST".equals(request.getMethod())
                && ("/api/auth/renew".equals(path) || "/api/auth/logout".equals(path)
                    || "/api/auth/change-password".equals(path));
        boolean demo = authentication != null && authentication.getAuthorities().stream().anyMatch(
                authority -> authority.getAuthority().equals("ROLE_" + TenantIdentityResolver.DEMO_USER));
        boolean business = businessEnabled && demo && DemoTenantApiPolicy.allows(request);
        if (restricted && !identityRead && !identityWrite && !business) {
            errors.write(request, response, HttpStatus.FORBIDDEN, "DEMO_BUSINESS_ACCESS_DENIED",
                    "business access is not enabled for this identity");
            return;
        }
        if ((demo || "/api/auth/tenant".equals(path)) && (request.getHeader("X-Tenant-ID") != null
                || request.getParameterMap().containsKey("tenantId") || request.getParameterMap().containsKey("tenant_id"))) {
            errors.write(request, response, HttpStatus.FORBIDDEN, "TENANT_OVERRIDE_FORBIDDEN",
                    "tenant identity is resolved by the server");
            return;
        }
        chain.doFilter(request, response);
    }
}
