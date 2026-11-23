package com.guidinglight.nexusquant.app.security.web;

import jakarta.servlet.http.HttpServletRequest;

/** 平台只有登记的方法和路径，不继承本地管理员或租户业务权限。 */
public final class PlatformApiPolicy {
    private PlatformApiPolicy() {}

    public static boolean allows(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return ("GET".equals(request.getMethod()) && (path.equals("/api/platform/tenants")
                || path.matches("/api/platform/tenants/[0-9a-fA-F-]{36}/(?:summary|sim-performance)")))
                || ("POST".equals(request.getMethod())
                && path.matches("/api/platform/users/[1-9][0-9]*/(?:disable|enable)"));
    }
}
