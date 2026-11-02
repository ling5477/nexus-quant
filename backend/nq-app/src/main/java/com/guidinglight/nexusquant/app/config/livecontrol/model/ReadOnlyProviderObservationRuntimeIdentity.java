package com.guidinglight.nexusquant.app.config.livecontrol.model;

/**
 * 只读 provider observation runtime 的不可变身份；启动时验证 release、capability、loopback 与 Java 绑定。
 */
public record ReadOnlyProviderObservationRuntimeIdentity(
        String releaseId,
        String sourceCommit,
        String releaseManifestSha256,
        String capability,
        String bindAddress,
        int javaMajor
) {
    public static final String CAPABILITY = "read-only-provider-observation";

    public ReadOnlyProviderObservationRuntimeIdentity {
        if (releaseId == null || !releaseId.matches("nq-[0-9a-f]{12}-[0-9a-f]{16}")) {
            throw new IllegalArgumentException("provider observation releaseId must be a canonical release identity");
        }
        if (sourceCommit == null || !sourceCommit.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("provider observation sourceCommit must be an exact lowercase commit");
        }
        // 这里只绑定制品与源码的结构身份；完整真实性仍由外部 admission 和 canonical installer 验证。
        if (!releaseId.substring(3, 15).equals(sourceCommit.substring(0, 12))) {
            throw new IllegalArgumentException("provider observation release source prefix mismatch");
        }
        if (releaseManifestSha256 == null || !releaseManifestSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("provider observation releaseManifestSha256 must be lowercase SHA-256");
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
