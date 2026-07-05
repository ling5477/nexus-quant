package com.guidinglight.nexusquant.integration.dh;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * InMemoryDhDryRunRecorder 是测试隔离和默认禁用场景使用的内存 recorder。
 *
 * <p>Why: 本轮只需要证明 record-only 行为，不允许写 ledger、account、paper、live 或其他生产事实源。</p>
 */
public final class InMemoryDhDryRunRecorder implements DhDryRunRecorder {

    private final CopyOnWriteArrayList<DhDryRunRecord> records = new CopyOnWriteArrayList<>();

    /**
     * 保存 dry-run 摘要。
     *
     * @param record 已由 client 验证和脱敏的 record
     */
    @Override
    public void save(DhDryRunRecord record) {
        records.add(record);
    }

    /**
     * 返回当前记录快照。
     *
     * @return 不可变 record list；用于测试断言，不暴露可变状态
     */
    public List<DhDryRunRecord> records() {
        return List.copyOf(records);
    }
}
