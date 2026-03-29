package com.guidinglight.nexusquant.marketdata.domain.port;

import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalMarketDataQuery;

import java.util.List;

/**
 * HistoricalMarketDataPort 定义 GateF-2 的历史行情输入端口。
 */
public interface HistoricalMarketDataPort {

    List<HistoricalBar> loadBars(HistoricalMarketDataQuery query);
}


