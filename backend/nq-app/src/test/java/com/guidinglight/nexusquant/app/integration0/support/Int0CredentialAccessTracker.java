package com.guidinglight.nexusquant.app.integration0.support;

/**
 * Int0CredentialAccessTracker 是 test-only 凭证访问探针（INT0-T15）。
 *
 * <p>它模拟对 {@code .env} / credential store / 交易所凭证 / NQ credential 的访问点。
 * Integration-0 contract validation 必须永不访问凭证，因此测试断言 {@link #accessCount()} 恒为 0。
 *
 * <p>本类不读取任何真实凭证、不读取 {@code .env}、不读取真实 secret。
 */
public final class Int0CredentialAccessTracker {

    private int accessCount;

    /** 仅在被错误调用时计数；正常 contract validation 路径不应调用。 */
    public void recordCredentialAccess() {
        accessCount++;
    }

    public int accessCount() {
        return accessCount;
    }
}
