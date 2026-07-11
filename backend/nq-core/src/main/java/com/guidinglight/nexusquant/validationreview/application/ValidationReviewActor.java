package com.guidinglight.nexusquant.validationreview.application;

import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewCase;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 由 API authentication context 解析出的可信 review actor。
 *
 * <p>客户端不能构造本对象；roles 使用现有 ADMIN / OPERATOR，不创建第二套权限体系。
 *
 * @param userId 当前 DB-backed user id
 * @param roles 当前用户角色快照
 */
public record ValidationReviewActor(long userId, Set<String> roles) {

    public ValidationReviewActor {
        ValidationReviewCase.requirePositive(userId, "userId");
        if (roles == null) {
            roles = Set.of();
        } else {
            roles = roles.stream()
                    .filter(role -> role != null && !role.isBlank())
                    .map(role -> role.trim().toUpperCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    /** @return 是否具有同 tenant 管理权限 */
    public boolean admin() {
        return roles.contains("ADMIN");
    }

    /** @return 是否具有 owner-scoped 人工复核权限 */
    public boolean operator() {
        return roles.contains("OPERATOR");
    }
}
