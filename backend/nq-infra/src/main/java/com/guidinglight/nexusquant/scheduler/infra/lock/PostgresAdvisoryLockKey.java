package com.guidinglight.nexusquant.scheduler.infra.lock;

/** PostgreSQL two-int advisory lock protocol 的确定性映射结果。 */
public record PostgresAdvisoryLockKey(int namespaceKey, int lockKey) {
}
