package com.guidinglight.nexusquant.integration.dh;

/**
 * DhDryRunErrorCode 固定 NQ limited dry-run client 可记录的 fail-closed 错误分类。
 *
 * <p>Why: DH 侧 error envelope 和 NQ 本地 client 错误都必须收敛成稳定枚举，安全错误、解析错误、
 * timeout 和 response policy violation 均不得 fallback 成成功或交易信号。</p>
 */
public enum DhDryRunErrorCode {
    /** HMAC 签名无效。 */
    SIGNATURE_INVALID,
    /** timestamp 格式无效。 */
    TIMESTAMP_INVALID,
    /** timestamp 超出允许窗口。 */
    TIMESTAMP_OUT_OF_WINDOW,
    /** nonce 被重放。 */
    NONCE_REPLAY,
    /** tenant 与签名或 payload 绑定不一致。 */
    TENANT_MISMATCH,
    /** source 不允许。 */
    SOURCE_DENIED,
    /** payload 超过上限。 */
    PAYLOAD_TOO_LARGE,
    /** DH 或 policy rate limit。 */
    RATE_LIMITED,
    /** DH memory cap 拒绝。 */
    MEMORY_LIMIT_EXCEEDED,
    /** policy gate 拒绝。 */
    POLICY_DENIED,
    /** provider 明确关闭。 */
    PROVIDER_DISABLED,
    /** provider timeout。 */
    PROVIDER_TIMEOUT,
    /** 预算限制。 */
    BUDGET_EXCEEDED,
    /** 未知 DH 或本地错误，必须 fail-closed。 */
    UNKNOWN_ERROR,
    /** 本地 client 因 feature flag、kill switch、production gate 或配置缺失被禁用。 */
    CLIENT_DISABLED,
    /** 本地 fake/transport timeout，必须 fail-closed。 */
    CLIENT_TIMEOUT,
    /** response body 无法解析，必须 fail-closed。 */
    CLIENT_PARSE_ERROR,
    /** response 含交易动作、非 dry-run 或 schema mismatch 等 policy violation。 */
    RESPONSE_POLICY_VIOLATION;

    /**
     * 将 DH error envelope 的 code 映射为 NQ 本地稳定错误枚举。
     *
     * <p>Why: 未知 code 不能按成功处理，也不能升级成交易信号；必须 fail-closed 到 UNKNOWN_ERROR。</p>
     *
     * @param code DH 返回的 error code；可为空
     * @return 对应本地枚举；未知或空值返回 UNKNOWN_ERROR
     */
    public static DhDryRunErrorCode fromWireCode(String code) {
        if (code == null || code.isBlank()) {
            return UNKNOWN_ERROR;
        }
        try {
            return DhDryRunErrorCode.valueOf(code.trim());
        } catch (IllegalArgumentException ex) {
            return UNKNOWN_ERROR;
        }
    }
}
