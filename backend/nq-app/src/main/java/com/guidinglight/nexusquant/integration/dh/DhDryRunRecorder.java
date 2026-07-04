package com.guidinglight.nexusquant.integration.dh;

/**
 * DhDryRunRecorder 是 limited dry-run client 的 record-only 输出端口。
 *
 * <p>Why: 本轮禁止把 DH output 写入 order / execution / risk mutation / ledger / paper / live。client 只能把
 * 验证后的只读结果或 fail-closed 摘要交给隔离 recorder。</p>
 */
public interface DhDryRunRecorder {

    /**
     * 保存 dry-run 摘要记录。
     *
     * @param record 不含 secret、credential、raw payload 或 executable order 的摘要记录
     */
    void save(DhDryRunRecord record);
}
