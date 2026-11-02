package com.guidinglight.nexusquant.security.web;

import com.guidinglight.nexusquant.security.token.model.TokenClaims;
import com.guidinglight.nexusquant.security.token.port.TokenService;
import com.guidinglight.nexusquant.security.token.port.TokenUserValidator;
import com.guidinglight.nexusquant.common.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

/**
 * JwtAuthenticationFilter 负责从 `Authorization: Bearer` 中解析并建立认证上下文。
 * <p>
 * Why:
 * 该过滤器属于安全基础设施实现，应该挂在 `security.web` 而不是 `auth.api.web`，
 * 避免 `nq-api` 与 `nq-security` 共用 web API 包。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final TokenUserValidator userValidator;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(TokenService tokenService, AuthenticationEntryPoint authenticationEntryPoint, TokenUserValidator userValidator) {
        this.userValidator = Objects.requireNonNull(userValidator, "userValidator");
        this.tokenService = Objects.requireNonNull(tokenService, "tokenService must not be null");
        this.authenticationEntryPoint = Objects.requireNonNull(authenticationEntryPoint, "authenticationEntryPoint must not be null");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        // 容器线程复用和异常路径均不能留下上一请求的租户。
        TenantContext.clear();
        try {
            authenticateAndFilter(request, response, filterChain);
        } finally {
            TenantContext.clear();
        }
    }

    private void authenticateAndFilter(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isBlank()) {
            authenticationEntryPoint.commence(request, response, new BadCredentialsException("access token is blank"));
            return;
        }
        var parsedClaims = tokenService.parse(token);
        if (parsedClaims.isEmpty()) {
            authenticationEntryPoint.commence(request, response, new BadCredentialsException("access token is invalid or expired"));
            return;
        }
        TokenClaims claims = parsedClaims.get();
        Optional<TokenUserValidator.UserState> userState;
        try {
            userState = userValidator.validate(claims);
        } catch (AuthenticationException invalid) {
            authenticationEntryPoint.commence(request, response, invalid);
            return;
        }
        if (userState.isEmpty()) {
            authenticationEntryPoint.commence(request, response, new BadCredentialsException("user or token generation invalid"));
            return;
        }
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                claims,
                token,
                userState.get().roles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList()
        );
        authentication.setDetails(userState.get());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            if (userState.get().tenantIdentity() != null) {
                try (var scope = TenantContext.open(userState.get().tenantIdentity())) {
                    filterChain.doFilter(request, response);
                }
                return;
            }
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}


