package com.guidinglight.nexusquant.research.application;

import com.guidinglight.nexusquant.research.domain.ResearchConfigPage;
import com.guidinglight.nexusquant.research.domain.port.ResearchConfigListQuery;
import com.guidinglight.nexusquant.research.domain.port.ResearchConfigListQueryPort;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 单次查询的总数和页面在同一快照读取，避免并发归档让两次SQL的口径不同。 */
@Service
public class ResearchConfigListService {
    private final ResearchConfigListQueryPort queryPort;

    public ResearchConfigListService(ResearchConfigListQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public ResearchConfigPage list(ResearchConfigListQuery query) {
        return queryPort.readPage(query);
    }
}
