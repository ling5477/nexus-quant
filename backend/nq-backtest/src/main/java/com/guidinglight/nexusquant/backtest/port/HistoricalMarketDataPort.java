package com.guidinglight.nexusquant.backtest.port;

import com.guidinglight.nexusquant.backtest.model.HistoricalBar;
import com.guidinglight.nexusquant.backtest.model.HistoricalMarketDataQuery;

import java.util.List;

/**
 * HistoricalMarketDataPort 定义 GateF-2 的历史行情输入端口。
 */
public interface HistoricalMarketDataPort {

    List<HistoricalBar> loadBars(HistoricalMarketDataQuery query);
}
