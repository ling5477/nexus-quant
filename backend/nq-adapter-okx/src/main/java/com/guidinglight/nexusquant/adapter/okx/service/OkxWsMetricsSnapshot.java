package com.guidinglight.nexusquant.adapter.okx.service;

/**
 * OkxWsMetricsSnapshot 表示 WS 治理层指标快照。
 * <p>
 * Why:
 * PR-W1 要求连接治理可观测，这里提供统一快照，既可用于日志，也可在后续接入 Micrometer 时作为桥接数据源。
 *
 * @param wsConnected           当前连接状态（0/1）
 * @param reconnectCount        重连次数
 * @param subscribeSuccessCount 订阅成功次数
 * @param subscribeFailCount    订阅失败次数
 * @param lastMsgAgeMs          最近消息距今毫秒
 */
public record OkxWsMetricsSnapshot(
        int wsConnected,
        long reconnectCount,
        long subscribeSuccessCount,
        long subscribeFailCount,
        long lastMsgAgeMs
) {
}
