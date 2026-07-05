package com.guidinglight.nexusquant.integration.dh;

import java.util.UUID;

/**
 * DhDryRunNonceGenerator 生成每次 dry-run request 唯一的 nonce。
 *
 * <p>Why: nonce 必须参与 HMAC material 和 replay protection 语义。当前 NQ 侧只负责生成与绑定，真正的 replay
 * 判定仍由 DH policy gate 在未来受控 runtime 中执行。</p>
 */
public final class DhDryRunNonceGenerator {

    /**
     * 生成新的 nonce。
     *
     * @return UUID 字符串；不携带时间戳、账号或 credential 信息
     */
    public String newNonce() {
        return UUID.randomUUID().toString();
    }
}
