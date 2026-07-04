package com.guidinglight.nexusquant.integration.dh;

import java.time.Duration;
import java.util.Objects;

/**
 * DhDryRunRuntimeProperties 固定 Integration-1 limited dry-run runtime client 的安全默认配置。
 *
 * <p>Why: NQ 侧 client 本轮只能用于 dev/test dry-run，默认必须同时被 feature flag、client flag、kill
 * switch、endpoint、签名 secret 和 production gate 约束。缺失配置不能 fallback 成真实调用，只能 fail-closed 并写入
 * dry-run failure record。</p>
 *
 * @param enabled           runtime 总开关；默认 false
 * @param clientEnabled     client 子开关；默认 false
 * @param source            source；当前只允许 NQ_DRYRUN，禁止匿名或 fallback
 * @param endpointUrl       DH dry-run endpoint；缺失时 fail-closed
 * @param productionEnabled production profile 是否允许；默认 false
 * @param killSwitch        kill switch；默认 true，优先阻断调用
 * @param timeoutMs         fake/transport timeout 毫秒数；非正数回退到 1500
 * @param signingSecret     HMAC secret；只能由受控配置注入，缺失时 fail-closed
 * @param schemaVersion     dry-run schema version；必须同时绑定 payload、header 和 response
 */
public record DhDryRunRuntimeProperties(
        boolean enabled,
        boolean clientEnabled,
        String source,
        String endpointUrl,
        boolean productionEnabled,
        boolean killSwitch,
        int timeoutMs,
        String signingSecret,
        String schemaVersion) {

    /** 当前唯一允许的 source，不能 fallback 到匿名或 legacy source。 */
    public static final String DEFAULT_SOURCE = "NQ_DRYRUN";
    /** 本轮 NQ client 内部 schema 标识；不 formalize contracts/OpenAPI/json-schema。 */
    public static final String DEFAULT_SCHEMA_VERSION = "nq-dh-i1-dryrun-v1";
    private static final int DEFAULT_TIMEOUT_MS = 1500;

    public DhDryRunRuntimeProperties {
        source = normalize(source);
        endpointUrl = normalize(endpointUrl);
        signingSecret = normalize(signingSecret);
        schemaVersion = normalizeOrDefault(schemaVersion, DEFAULT_SCHEMA_VERSION);
        timeoutMs = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
    }

    /**
     * 构造 disabled-by-default 配置。
     *
     * @return 所有 runtime/client 能力均关闭、kill switch 打开的安全默认值
     */
    public static DhDryRunRuntimeProperties disabledDefaults() {
        return new DhDryRunRuntimeProperties(
                false,
                false,
                DEFAULT_SOURCE,
                "",
                false,
                true,
                DEFAULT_TIMEOUT_MS,
                "",
                DEFAULT_SCHEMA_VERSION);
    }

    /**
     * 构造测试专用 enabled 配置。
     *
     * <p>Why: 单元测试需要验证 signing、headers 和 fake transport 行为，但不能读取环境变量或真实凭证。</p>
     *
     * @param endpointUrl fake endpoint；只传给测试 transport
     * @param signingSecret 测试固定 HMAC secret；不得来自生产凭证
     * @return dev/test only enabled 配置
     */
    public static DhDryRunRuntimeProperties enabledForTest(String endpointUrl, String signingSecret) {
        return new DhDryRunRuntimeProperties(
                true,
                true,
                DEFAULT_SOURCE,
                endpointUrl,
                false,
                false,
                DEFAULT_TIMEOUT_MS,
                signingSecret,
                DEFAULT_SCHEMA_VERSION);
    }

    /**
     * 判断 endpoint 是否已配置。
     *
     * @return true 表示 endpoint URL 非空；仍不代表允许真实 HTTP
     */
    public boolean hasEndpointUrl() {
        return !endpointUrl.isBlank();
    }

    /**
     * 判断 HMAC secret 是否已配置。
     *
     * @return true 表示签名 secret 非空；secret 本身禁止写入日志或 record
     */
    public boolean hasSigningSecret() {
        return !signingSecret.isBlank();
    }

    /**
     * 返回 timeout Duration。
     *
     * @return timeout 毫秒 Duration，用于 fake/transport gate
     */
    public Duration timeout() {
        return Duration.ofMillis(timeoutMs);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeOrDefault(String value, String fallback) {
        String normalized = normalize(value);
        return normalized.isBlank() ? Objects.requireNonNull(fallback, "fallback") : normalized;
    }
}
