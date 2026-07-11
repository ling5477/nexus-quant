package com.guidinglight.nexusquant.scheduler.validationevidence;

import com.guidinglight.nexusquant.scheduler.lock.SchedulerLockKey;

import java.time.Duration;
import java.util.Objects;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Validation Evidence Scheduler 的 fail-closed 运行配置。
 *
 * <p>Why: scheduler 必须默认关闭，并在 Bean 启动前拒绝过短、过长或不稳定的锁配置，避免错误配置
 * 造成高频只读查询、无限执行或跨实例锁键漂移。配置不包含 exchange、credential、交易或 lifecycle 开关。
 */
@ConfigurationProperties(prefix = ValidationEvidenceSchedulerProperties.PREFIX)
public class ValidationEvidenceSchedulerProperties implements InitializingBean {

    public static final String PREFIX = "nq.validation-operations.scheduler";
    static final Duration MIN_FIXED_DELAY = Duration.ofSeconds(1);
    static final Duration MAX_FIXED_DELAY = Duration.ofHours(24);
    static final Duration MAX_INITIAL_DELAY = Duration.ofHours(24);
    static final Duration MIN_EXECUTION_TIMEOUT = Duration.ofSeconds(1);
    static final Duration MAX_EXECUTION_TIMEOUT = Duration.ofMinutes(5);

    private boolean enabled;
    private Duration fixedDelay = Duration.ofMinutes(5);
    private Duration initialDelay = Duration.ofSeconds(30);
    private String lockNamespace = "validation-operations";
    private String lockName = "runtime-evidence-refresh";
    private Duration executionTimeout = Duration.ofSeconds(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getFixedDelay() {
        return fixedDelay;
    }

    public void setFixedDelay(Duration fixedDelay) {
        this.fixedDelay = fixedDelay;
    }

    public Duration getInitialDelay() {
        return initialDelay;
    }

    public void setInitialDelay(Duration initialDelay) {
        this.initialDelay = initialDelay;
    }

    public String getLockNamespace() {
        return lockNamespace;
    }

    public void setLockNamespace(String lockNamespace) {
        this.lockNamespace = lockNamespace;
    }

    public String getLockName() {
        return lockName;
    }

    public void setLockName(String lockName) {
        this.lockName = lockName;
    }

    public Duration getExecutionTimeout() {
        return executionTimeout;
    }

    public void setExecutionTimeout(Duration executionTimeout) {
        this.executionTimeout = executionTimeout;
    }

    /**
     * 在 scheduler Bean 注册前验证全部边界。
     *
     * <p>execution timeout 上限与已接受的 GateV-3A primitive 五分钟上限保持一致；这里不修改或复制锁实现。
     */
    @Override
    public void afterPropertiesSet() {
        requireRange("fixedDelay", fixedDelay, MIN_FIXED_DELAY, MAX_FIXED_DELAY);
        Objects.requireNonNull(initialDelay, "initialDelay must not be null");
        if (initialDelay.isNegative() || initialDelay.compareTo(MAX_INITIAL_DELAY) > 0) {
            throw new IllegalArgumentException("initialDelay must be between PT0S and PT24H");
        }
        requireRange(
                "executionTimeout",
                executionTimeout,
                MIN_EXECUTION_TIMEOUT,
                MAX_EXECUTION_TIMEOUT
        );
        SchedulerLockKey validatedKey = new SchedulerLockKey(lockNamespace, lockName);
        lockNamespace = validatedKey.namespace();
        lockName = validatedKey.name();
    }

    private static void requireRange(String field, Duration value, Duration minimum, Duration maximum) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(field + " must be between " + minimum + " and " + maximum);
        }
    }
}
