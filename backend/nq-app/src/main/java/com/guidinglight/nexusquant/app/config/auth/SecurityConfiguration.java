package com.guidinglight.nexusquant.app.config.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.auth.application.CurrentUserProfileService;
import com.guidinglight.nexusquant.auth.application.DbAuthService;
import com.guidinglight.nexusquant.auth.domain.port.AuthUserRepository;
import com.guidinglight.nexusquant.auth.application.AuthService;
import com.guidinglight.nexusquant.app.security.web.ApiSecurityErrorWriter;
import com.guidinglight.nexusquant.security.token.JwtTokenService;
import com.guidinglight.nexusquant.security.token.JwtTokenSettings;
import com.guidinglight.nexusquant.security.token.TokenService;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.gateway.infra.security.SecurityContextGatewayAuthFacade;
import com.guidinglight.nexusquant.security.web.JwtAuthenticationFilter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
        return new JwtTokenService(new JwtTokenSettings(
                properties.getIssuer(),
                properties.getSecret(),
                properties.getAccessTokenTtl()
        ));
    }

    @Bean
    public AuthService authService(
            TokenService tokenService,
            PasswordEncoder passwordEncoder,
            AuthUserRepository authUserRepository,
            SecurityRuntimeProperties properties
    ) {
        return new DbAuthService(
                tokenService,
                passwordEncoder,
                authUserRepository,
                properties.getIssuer(),
                properties.getAccessTokenTtl().toSeconds()
        );
    }

    @Bean
    public CurrentUserProfileService currentUserProfileService(AuthUserRepository authUserRepository) {
        return new CurrentUserProfileService(authUserRepository);
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
        return (request, response, authException) -> errorWriter.write(
                request,
                response,
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                "authentication required"
        );
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler(ApiSecurityErrorWriter errorWriter) {
        return (request, response, accessDeniedException) -> errorWriter.write(
                request,
                response,
                org.springframework.http.HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                "access denied"
        );
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(TokenService tokenService, AuthenticationEntryPoint entryPoint) {
        return new JwtAuthenticationFilter(tokenService, entryPoint);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/actuator/health",
                                "/actuator/readonlyproviderobservation"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/**").authenticated()
                        .requestMatchers("/api/**").hasAnyRole("ADMIN", "OPERATOR")
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}


