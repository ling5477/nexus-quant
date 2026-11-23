package com.guidinglight.nexusquant.app.config.auth;

import com.guidinglight.nexusquant.auth.domain.port.AuthUserRepository;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 本地配置用户由安装身份准备；受限运行账号只校验，不重写密码、角色或成员关系。
 */
@Configuration
@Profile({"local", "test"})
public class AuthSeedConfiguration {

    @Bean
    public ApplicationRunner authSeedRunner(SecurityRuntimeProperties properties, AuthUserRepository users) {
        return args -> {
            for(var configured:properties.getUsers()) {
                var current=users.findByUsername(configured.getUsername())
                        .orElseThrow(()->new IllegalStateException("LOCAL_IDENTITY_PROVISIONING_REQUIRED"));
                if(!java.util.Objects.equals(current.passwordHash(),configured.getPasswordHash())
                        || current.enabled()!=configured.isEnabled()
                        || !new java.util.HashSet<>(current.roles()).equals(new java.util.HashSet<>(configured.getRoles()))) {
                    throw new IllegalStateException("LOCAL_IDENTITY_PROVISIONING_REQUIRED");
                }
            }
        };
    }
}


