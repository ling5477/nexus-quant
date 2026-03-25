package com.guidinglight.nexusquant.contracts.event;

/**
 * EventPublisherPort 定义领域事件向事实链追加的最小共享端口。
 * <p>
 * Why:
 * `nq-core`、`nq-ledger` 与 `nq-scheduler` 都需要把命令/事件落到事实链，
 * 但这些模块不应该直接依赖 `nq-infra` 的 JDBC 实现。
 * 因此把“按 topic 追加 EventEnvelope”抽成共享 port，由 infra 提供最终持久化实现。
 */
public interface EventPublisherPort {

    /**
     * 追加一条事件到事实链。
     *
     * @param topic    事实链 topic，必须使用 `TopicNames` 常量
     * @param envelope 统一事件外壳，必须包含 eventId/type/version/traceId/key
     */
    void append(String topic, EventEnvelope<?> envelope);
}
