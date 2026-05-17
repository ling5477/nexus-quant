package com.guidinglight.nexusquant.marketdata.domain.port;

import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataIngestionJob;

import java.time.Instant;
import java.util.List;

/**
 * HistoricalKlineProvider 是 core 到交易所 adapter 的出站端口。
 * <p>
 * Why:
 * GateH-2 需要由 core 编排任务和幂等写库，但 OKX/Binance HTTP 细节必须留在 adapter 模块。
 * 该端口只返回平台统一的 HistoricalBar，避免 adapter payload 泄漏到 application service。
 */
public interface HistoricalKlineProvider {

    /**
     * 拉取一段历史 K 线。
     *
     * @param job 任务事实，包含交易所、市场、交易对和周期
     * @param startTime 本次请求开始时间，断点续拉时可能晚于任务 startTime
     * @param endTime 本次请求结束时间，不得晚于任务 endTime
     * @return 交易所返回并完成基础字段映射后的 bars；调用失败必须抛出带明确原因的异常
     */
    List<HistoricalBar> fetchBars(MarketdataIngestionJob job, Instant startTime, Instant endTime);
}
