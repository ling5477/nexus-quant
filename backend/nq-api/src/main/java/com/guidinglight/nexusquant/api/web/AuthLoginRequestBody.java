package com.guidinglight.nexusquant.api.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * AuthLoginRequestBody 描述正式登录接口的输入边界。
 */
@Schema(name = "AuthLoginRequestBody", description = "正式登录请求体")
public record AuthLoginRequestBody(
        @NotBlank(message = "username must not be blank")
        @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED)
        String username,
        @NotBlank(message = "password must not be blank")
        @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED)
        String password
) {
}
