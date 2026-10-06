package com.guidinglight.nexusquant.app.security.web;
import com.guidinglight.nexusquant.security.token.port.TokenUserValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
/** 鉴权完成后统一限制首次登录；方法与路径均精确匹配，避免前缀绕过。 */
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {
    private final ApiSecurityErrorWriter errorWriter;
    public PasswordChangeRequiredFilter(ApiSecurityErrorWriter errorWriter) { this.errorWriter = errorWriter; }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String path = request.getServletPath();
        if (path.isEmpty()) { path = request.getRequestURI().substring(request.getContextPath().length()); }
        boolean allowed = ("GET".equals(request.getMethod()) && "/api/auth/me".equals(path))
                || ("POST".equals(request.getMethod())
                && ("/api/auth/change-password".equals(path) || "/api/auth/logout".equals(path)));
        if (authentication != null && authentication.getDetails() instanceof TokenUserValidator.UserState user
                && user.mustChangePassword() && (path.equals("/api") || path.startsWith("/api/")) && !allowed) {
            errorWriter.write(request, response, HttpStatus.FORBIDDEN, "PASSWORD_CHANGE_REQUIRED",
                    "password change required before accessing business APIs");
            return;
        }
        chain.doFilter(request, response);
    }
}
