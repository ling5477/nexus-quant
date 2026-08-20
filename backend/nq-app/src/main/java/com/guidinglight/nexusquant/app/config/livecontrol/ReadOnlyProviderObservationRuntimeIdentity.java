package com.guidinglight.nexusquant.app.config.livecontrol;

/**
 * 只读 provider observation runtime 的不可变身份；启动时验证 release、capability、loopback 与 Java 绑定。
 */
public record ReadOnlyProviderObservationRuntimeIdentity(
        String releaseId,
        String sourceCommit,
        String capability,
        String bindAddress,
        int javaMajor
) {
    public static final String CAPABILITY = "read-only-provider-observation";

    public ReadOnlyProviderObservationRuntimeIdentity {
        if (releaseId == null || !releaseId.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("provider observation releaseId must be an exact commit");
        }
        if (!releaseId.equals(sourceCommit)) {
            throw new IllegalArgumentException("provider observation sourceCommit must equal releaseId");
        }
        if (!CAPABILITY.equals(capability)) {
            throw new IllegalArgumentException("provider observation capability identity mismatch");
        }
        if (!"127.0.0.1".equals(bindAddress)) {
            throw new IllegalArgumentException("provider observation runtime must bind loopback");
        }
        if (javaMajor != 21) {
            throw new IllegalArgumentException("provider observation runtime requires Java 21");
        }
    }
}
