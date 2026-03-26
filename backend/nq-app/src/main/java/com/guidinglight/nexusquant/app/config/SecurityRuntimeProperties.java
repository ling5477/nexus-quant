package com.guidinglight.nexusquant.app.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * SecurityRuntimeProperties 承接本地账户与 JWT 最小运行时配置。
 */
@Validated
@ConfigurationProperties(prefix = "nq.security")
public class SecurityRuntimeProperties {

    @NotBlank
    private String issuer;

    @NotBlank
    private String secret;

    @NotNull
    private Duration accessTokenTtl = Duration.ofHours(1);

    @Valid
    @NotEmpty
    private List<LocalUserProperties> users = new ArrayList<>();

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public List<LocalUserProperties> getUsers() {
        return users;
    }

    public void setUsers(List<LocalUserProperties> users) {
        this.users = users;
    }

    public static class LocalUserProperties {
        @NotBlank
        private String username;

        @NotBlank
        private String passwordHash;

        @NotEmpty
        private List<String> roles = new ArrayList<>();

        private boolean enabled = true;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPasswordHash() {
            return passwordHash;
        }

        public void setPasswordHash(String passwordHash) {
            this.passwordHash = passwordHash;
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
