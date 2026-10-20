package com.guidinglight.nexusquant.security.token.port;
import com.guidinglight.nexusquant.security.token.model.TokenClaims;
import java.util.List;
import java.util.Optional;
/** 持久化用户状态的校验端口；安全模块不拥有用户数据源。 */
@FunctionalInterface
public interface TokenUserValidator {
    Optional<UserState> validate(TokenClaims claims);
    record UserState(List<String> roles, boolean mustChangePassword) { }
}
