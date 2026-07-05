package com.guidinglight.nexusquant.integration.dh;

/**
 * DhDryRunTransportTimeoutException 表示 fake/transport 层超时。
 *
 * <p>Why: timeout 必须映射为 CLIENT_TIMEOUT 并 fail-closed，只写 dry-run failure record，
 * 不允许 fallback 为 NQ trading signal。</p>
 */
public final class DhDryRunTransportTimeoutException extends RuntimeException {

    /**
     * @param message 安全错误摘要；不得包含 URL query、credential 或 raw payload
     */
    public DhDryRunTransportTimeoutException(String message) {
        super(message);
    }
}
