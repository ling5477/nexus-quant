package com.guidinglight.nexusquant.auth.domain.port;

import com.guidinglight.nexusquant.auth.domain.PasswordRotationTarget;

import java.time.Instant;
import java.util.Optional;

/** 现有用户密码维护的窄端口，不提供用户创建、启用、角色或归属修改能力。 */
public interface ExistingUserPasswordRotationRepository {
    Optional<PasswordRotationTarget> findTarget(long exactUserId);

    /** 以完整旧 hash 作原子比较；行数不为一时必须回滚，不可覆盖并发修改。 */
    void updatePasswordHash(long exactUserId, String expectedUsername, String expectedCurrentHash,
                            String newPasswordHash, Instant updatedAt);
}
