package com.guidinglight.nexusquant.marketdata.application.service;

import com.guidinglight.nexusquant.marketdata.domain.MarketdataDatasetPage;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataDatasetListQuery;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataDatasetListQueryPort;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 并发质量刷新时COUNT与本页保持同一快照，读取超时不吞成空结果。 */
@Service
public class MarketdataDatasetListService {
    private final MarketdataDatasetListQueryPort queryPort;

    public MarketdataDatasetListService(MarketdataDatasetListQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public MarketdataDatasetPage list(MarketdataDatasetListQuery query) {
        return queryPort.readPage(query);
    }
}
