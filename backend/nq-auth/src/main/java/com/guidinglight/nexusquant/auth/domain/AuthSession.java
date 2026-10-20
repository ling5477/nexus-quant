package com.guidinglight.nexusquant.auth.domain;

import java.time.Instant;

/** 会话期限由服务端维护；续期只移动活跃时间，不能延长首次登录确定的截止时间。 */
public record AuthSession(String sessionId, long userId, long authVersion,
                          Instant createdAt, Instant lastActiveAt, Instant expiresAt) {
}
