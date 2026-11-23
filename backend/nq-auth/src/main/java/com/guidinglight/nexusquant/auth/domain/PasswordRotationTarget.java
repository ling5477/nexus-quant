package com.guidinglight.nexusquant.auth.domain;

/** 密码仅在认证维护边界内使用；禁止由自动生成的文本表示泄露 hash。 */
public record PasswordRotationTarget(long userId, String username, boolean enabled, String passwordHash) {
    @Override
    public String toString() {
        return "PasswordRotationTarget[userId=" + userId + ", credentials=REDACTED]";
    }
}
