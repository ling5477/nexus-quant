package com.guidinglight.nexusquant.adapter.binance.ws;

/**
 * BinanceWsMetricsSnapshot 表示 Binance 私有 WS 治理层的可观测快照。
 * <p>
 * Why:
 * PR-BW1 只做连接治理，不接业务映射，因此这里把验收关注的状态统一封装成快照，
 * 既可供 local smoke runner 打日志，也可供后续接 Micrometer 时作为桥接数据源。
 *
 * @param wsConnected                  当前连接状态（0/1）
 * @param reconnectCount               已触发的重连次数
 * @param listenKeyRefreshSuccessCount listenKey keepalive 成功次数
 * @param listenKeyRefreshFailCount    listenKey keepalive 失败次数
 * @param lastMsgAgeMs                 最近一条消息距今毫秒数
 * @param lastReconnectEpochMs         最近一次重连调度时间（epoch millis），未发生时为 0
 */
public record BinanceWsMetricsSnapshot(
        int wsConnected,
        long reconnectCount,
        long listenKeyRefreshSuccessCount,
        long listenKeyRefreshFailCount,
        long lastMsgAgeMs,
        long lastReconnectEpochMs
) {
}
