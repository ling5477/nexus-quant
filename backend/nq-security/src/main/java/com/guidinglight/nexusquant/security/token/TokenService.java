package com.guidinglight.nexusquant.security.token;

import com.guidinglight.nexusquant.security.token.TokenClaims;
import java.util.Optional;

/**
 * TokenService 定义签发与解析令牌能力。
 */
public interface TokenService {

    /**
     * 签发访问令牌。
     *
     * @param claims 需要编码进令牌的声明
     * @return 访问令牌字符串
     */
    String issue(TokenClaims claims);

    /**
     * 解析令牌。
     *
     * @param token 访问令牌
     * @return 解析后的声明；解析失败返回 empty
     */
    Optional<TokenClaims> parse(String token);
}


