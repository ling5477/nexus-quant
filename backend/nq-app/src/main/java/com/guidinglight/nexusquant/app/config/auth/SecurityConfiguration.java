package com.guidinglight.nexusquant.app.config.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.app.security.web.ApiSecurityErrorWriter;
import com.guidinglight.nexusquant.app.security.web.PasswordChangeRequiredFilter;
import com.guidinglight.nexusquant.app.security.web.TenantFoundationAccessFilter;
import com.guidinglight.nexusquant.app.security.web.DemoTenantApiPolicy;
import com.guidinglight.nexusquant.auth.application.service.TenantIdentityResolver;
import com.guidinglight.nexusquant.auth.application.port.AuthService;
import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.auth.application.service.DbAuthService;
import com.guidinglight.nexusquant.auth.application.service.PasswordChangeService;
import com.guidinglight.nexusquant.auth.domain.port.AuthUserRepository;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.gateway.infra.security.SecurityContextGatewayAuthFacade;
import com.guidinglight.nexusquant.security.token.config.JwtTokenSettings;
import com.guidinglight.nexusquant.security.token.port.TokenService;
import com.guidinglight.nexusquant.security.token.port.TokenUserValidator;
import com.guidinglight.nexusquant.security.token.service.JwtTokenService;
import com.guidinglight.nexusquant.security.web.JwtAuthenticationFilter;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * SecurityConfiguration 负责正式认证鉴权链的最终装配。
 */
@Configuration
@EnableConfigurationProperties(SecurityRuntimeProperties.class)
public class SecurityConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public TokenService tokenService(SecurityRuntimeProperties properties) {
        return new JwtTokenService(
                new JwtTokenSettings(
                        properties.getIssuer(),
                        properties.getSecret(),
                        properties.getAccessTokenTtl()));
    }

    @Bean
    public AuthService authService(
            TokenService tokenService,
            PasswordEncoder passwordEncoder,
            AuthUserRepository authUserRepository,
            SecurityRuntimeProperties properties,
            org.springframework.beans.factory.ObjectProvider<com.guidinglight.nexusquant.auth.domain.port.PublicDemoDistribution> demoDistribution) {
        return new DbAuthService(
                tokenService,
                passwordEncoder,
                authUserRepository,
                properties.getIssuer(),
                properties.getAccessTokenTtl().toSeconds(),
                properties.getSessionIdleTimeout().toSeconds(),
                properties.getSessionMaxLifetime().toSeconds(),
                Clock.systemUTC(),demoDistribution.getIfAvailable());
    }

    @Bean
    public TenantIdentityResolver tenantIdentityResolver(AuthUserRepository users) {
        return new TenantIdentityResolver(users);
    }

    @Bean
    public CurrentUserProfileService currentUserProfileService(
            AuthUserRepository authUserRepository) {
        return new CurrentUserProfileService(authUserRepository);
    }

    @Bean
    public PasswordChangeService passwordChangeService(
            AuthUserRepository repository, PasswordEncoder encoder) {
        return new PasswordChangeService(repository, encoder);
    }

    @Bean
    public GatewayAuthFacade gatewayAuthFacade() {
        return new SecurityContextGatewayAuthFacade();
    }

    @Bean
    public ApiSecurityErrorWriter apiSecurityErrorWriter(ObjectMapper objectMapper) {
        return new ApiSecurityErrorWriter(objectMapper);
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(ApiSecurityErrorWriter errorWriter) {
        return (request, response, authException) ->
                errorWriter.write(
                        request,
                        response,
                        HttpStatus.UNAUTHORIZED,
                        "UNAUTHORIZED",
                        "authentication required");
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler(ApiSecurityErrorWriter errorWriter) {
        return (request, response, accessDeniedException) ->
                errorWriter.write(
                        request, response, HttpStatus.FORBIDDEN, "FORBIDDEN", "access denied");
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            TokenService tokenService,
            AuthenticationEntryPoint entryPoint,
            AuthUserRepository repository,
            TenantIdentityResolver tenantIdentities,
            org.springframework.beans.factory.ObjectProvider<com.guidinglight.nexusquant.auth.domain.port.PublicDemoDistribution> demoDistribution,
            SecurityRuntimeProperties properties) {
        return new JwtAuthenticationFilter(
                tokenService,
                entryPoint,
                claims ->
                        repository
                                .findByUsername(claims.subject())
                                .filter(
                                        user ->
                                                user.enabled()
                                                        && user.authVersion()
                                                                == claims.authVersion()
                                                        && user.username()
                                                                .equals(claims.username())
                                                        && (claims.sessionId() == null || repository.isSessionActive(
                                                                claims.sessionId(), claims.username(), claims.authVersion(),
                                                                Instant.now(), Instant.now().minus(properties.getSessionIdleTimeout()))))
                                .flatMap(user -> {
                                    var identity = tenantIdentities.resolve(user);
                                    if (demoDistribution.getIfAvailable()!=null && !demoDistribution.getObject().permitsUser(user.userId()))
                                        return Optional.empty();
                                    if (demoDistribution.getIfAvailable()!=null && identity.tenant()!=null
                                            && identity.tenant().kind()!=com.guidinglight.nexusquant.common.tenant.TenantKind.DEMO)
                                        return Optional.empty();
                                    // 旧本地令牌仍按原合同有效；新角色必须使用可撤销的数据库会话。
                                    if (claims.sessionId() == null && (identity.roles().contains(TenantIdentityResolver.DEMO_USER)
                                            || identity.roles().contains(TenantIdentityResolver.PLATFORM_ADMIN))) return Optional.empty();
                                    return Optional.of(new TokenUserValidator.UserState(
                                            identity.roles(), user.mustChangePassword(), identity.tenant()));
                                }));
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler,
            ApiSecurityErrorWriter errorWriter,
            org.springframework.beans.factory.ObjectProvider<com.guidinglight.nexusquant.auth.domain.port.PublicDemoDistribution> demoDistribution,
            @org.springframework.beans.factory.annotation.Value("${nq.demo.tenant-business-enabled:false}") boolean demoBusinessEnabled)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(
                        exception ->
                                exception
                                        .authenticationEntryPoint(authenticationEntryPoint)
                                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(request -> demoDistribution.getIfAvailable()!=null
                                                && "POST".equals(request.getMethod())
                                                && java.util.Set.of("/api/auth/register","/api/auth/verify-email","/api/auth/resend-verification")
                                                    .contains(request.getRequestURI().substring(request.getContextPath().length())))
                                        .permitAll()
                                        .requestMatchers(HttpMethod.POST, "/api/auth/login")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/auth/change-password",
                                                "/api/auth/renew",
                                                "/api/auth/logout")
                                        .authenticated()
                                        // 仅写认证主体自己的界面偏好，不扩大任何业务命令或交易权限。
                                        .requestMatchers(HttpMethod.PUT, "/api/user/preferences")
                                        .authenticated()
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/actuator/health",
                                                "/actuator/readonlyproviderobservation")
                                        .permitAll()
                                        // 稳定操作回执含运维输入与结果，读取和提交都保持ADMIN并由接口继续校验owner。
                                        .requestMatchers(
                                                "/api/scheduler/operations",
                                                "/api/scheduler/operations/**")
                                        .hasRole("ADMIN")
                                        // 创建恢复与原命令保持同一角色边界，操作人归属由只读查询继续校验。
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/strategy-releases/*/shadow-run-creation-result")
                                        .hasAnyRole("ADMIN", "OPERATOR")
                                        // 维护回执与原写入保持同一角色边界，接口再按提交人过滤。
                                        .requestMatchers("/api/trading/maintenance/operations/**")
                                        .hasAnyRole("ADMIN", "OPERATOR")
                                        .requestMatchers(request -> "GET".equals(request.getMethod()) && DemoTenantApiPolicy.allows(request))
                                        .authenticated()
                                        .requestMatchers(DemoTenantApiPolicy::allows)
                                        .hasAnyRole("ADMIN", "OPERATOR", "DEMO_USER")
                                        .requestMatchers(HttpMethod.GET, "/api/**")
                                        .authenticated()
                                        .requestMatchers(HttpMethod.PATCH, "/api/scheduler/jobs/*")
                                        .hasRole("ADMIN")
                                        .requestMatchers(
                                                HttpMethod.POST, "/api/scheduler/jobs/*/run-once")
                                        .hasRole("ADMIN")
                                        .requestMatchers("/api/**")
                                        .hasAnyRole("ADMIN", "OPERATOR")
                                        .anyRequest()
                                        .permitAll())
                .addFilterBefore(
                        jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(
                        new PasswordChangeRequiredFilter(errorWriter),
                        JwtAuthenticationFilter.class)
                .addFilterAfter(new TenantFoundationAccessFilter(errorWriter, demoBusinessEnabled), PasswordChangeRequiredFilter.class);
        return http.build();
    }
}
