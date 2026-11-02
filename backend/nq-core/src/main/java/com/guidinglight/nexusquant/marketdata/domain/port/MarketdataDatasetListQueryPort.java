package com.guidinglight.nexusquant.marketdata.domain.port;

import com.guidinglight.nexusquant.marketdata.domain.MarketdataDatasetPage;

/** 独立读取端口保留原数据集写入合同，不要求已有内存仓储伪造分页能力。 */
public interface MarketdataDatasetListQueryPort {
    MarketdataDatasetPage readPage(MarketdataDatasetListQuery query);
}
