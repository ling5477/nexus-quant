package com.guidinglight.nexusquant.strategy.strategyrelease.application;

import java.util.List;

/**
 * 从服务端 authentication/profile context 解析的 Shadow materialization actor。
 *
 * <p>客户端不能提交 actor 或 roles；application service 再次校验 ADMIN/OPERATOR，避免仅依赖
 * HTTP route 隐藏写按钮或单层 filter 配置。
 */
public record ShadowRunMaterializationActor(long actorId, List<String> roles) {

    public ShadowRunMaterializationActor {
        if (actorId <= 0) {
            throw new IllegalArgumentException("actorId must be positive");
        }
        roles = roles == null ? List.of() : roles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(String::trim)
                .toList();
    }

    public boolean canMaterialize() {
        return roles.stream()
                .map(role -> role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role)
                .anyMatch(role -> "ADMIN".equals(role) || "OPERATOR".equals(role));
    }
}
