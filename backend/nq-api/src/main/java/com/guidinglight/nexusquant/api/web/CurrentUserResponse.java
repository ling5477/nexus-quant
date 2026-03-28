package com.guidinglight.nexusquant.api.web;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * CurrentUserResponse 描述当前登录用户信息。
 */
@Schema(name = "CurrentUserResponse", description = "当前用户信息")
public record CurrentUserResponse(
        @Schema(description = "用户 ID")
        Long userId,
        @Schema(description = "用户名")
        String username,
        @Schema(description = "角色列表")
        List<String> roles,
        @Schema(description = "是否已认证")
        boolean authenticated,
        @Schema(description = "默认 exchange account ID")
        Long defaultExchangeAccountId,
        @Schema(description = "默认交易所编码")
        String defaultExchangeCode,
        @Schema(description = "默认交易环境")
        String defaultTradeEnv,
        @Schema(description = "默认账户别名")
        String defaultAccountAlias
) {
}
