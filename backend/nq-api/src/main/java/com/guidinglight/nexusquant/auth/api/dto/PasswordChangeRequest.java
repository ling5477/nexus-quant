package com.guidinglight.nexusquant.auth.api.dto;
import jakarta.validation.constraints.NotNull;
/** 密码仅用于请求处理，不作为用户资料或日志字段。 */
public record PasswordChangeRequest(
        @NotNull String currentPassword,
        @NotNull String newPassword
) {
    // 密码策略由事务服务验证，避免通用字段校验将 rejectedValue 回显为明文。
    @Override
    public String toString() { return "PasswordChangeRequest[REDACTED]"; }
}
