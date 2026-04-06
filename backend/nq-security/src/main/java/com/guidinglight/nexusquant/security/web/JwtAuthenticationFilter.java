package com.guidinglight.nexusquant.security.web;

import com.guidinglight.nexusquant.security.token.TokenClaims;
import com.guidinglight.nexusquant.security.token.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
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
    private final AuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(TokenService tokenService, AuthenticationEntryPoint authenticationEntryPoint) {
        this.tokenService = Objects.requireNonNull(tokenService, "tokenService must not be null");
        this.authenticationEntryPoint = Objects.requireNonNull(authenticationEntryPoint, "authenticationEntryPoint must not be null");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
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
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                claims,
                token,
                claims.roles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}


