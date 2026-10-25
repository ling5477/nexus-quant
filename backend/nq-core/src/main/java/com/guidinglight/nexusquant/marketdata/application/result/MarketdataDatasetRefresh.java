package com.guidinglight.nexusquant.marketdata.application.result;

import com.guidinglight.nexusquant.marketdata.domain.MarketdataDataset;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataDatasetCoverage;

/** 同次事务内的数据集与覆盖记录；用于保留原次质量计算，而非以后读取的可变最新状态。 */
public record MarketdataDatasetRefresh(
        MarketdataDataset dataset, MarketdataDatasetCoverage coverage) {}
