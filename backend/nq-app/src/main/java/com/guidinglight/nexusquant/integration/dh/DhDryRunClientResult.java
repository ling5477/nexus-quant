package com.guidinglight.nexusquant.integration.dh;

/**
 * DhDryRunClientResult 是 limited dry-run client 的同步执行结果。
 *
 * <p>Why: 调用方只能观察 accepted / fail-closed 状态和摘要 record，不能拿到 raw payload、signature material、
 * secret 或任何可执行交易指令。</p>
 *
 * @param accepted   response 是否通过 dry-run policy validation
 * @param failClosed 是否 fail-closed
 * @param record     已保存的 record 摘要
 */
public record DhDryRunClientResult(boolean accepted, boolean failClosed, DhDryRunRecord record) {
}
