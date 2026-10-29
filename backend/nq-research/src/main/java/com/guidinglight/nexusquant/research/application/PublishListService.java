package com.guidinglight.nexusquant.research.application;

import com.guidinglight.nexusquant.research.domain.BacktestPublishRecord;
import com.guidinglight.nexusquant.research.domain.ResearchResultPage;
import com.guidinglight.nexusquant.research.domain.port.BacktestPublishListQueryPort;
import com.guidinglight.nexusquant.research.domain.port.PublishListQuery;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 总数和发布记录共用只读快照，查询不会重放发布命令或改变冻结身份。 */
@Service
public class PublishListService {
    private final BacktestPublishListQueryPort queryPort;

    public PublishListService(BacktestPublishListQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public ResearchResultPage<BacktestPublishRecord> list(PublishListQuery query) {
        return queryPort.readPage(query);
    }
}
