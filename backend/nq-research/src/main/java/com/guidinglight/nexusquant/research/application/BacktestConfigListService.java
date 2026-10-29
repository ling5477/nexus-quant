package com.guidinglight.nexusquant.research.application;

import com.guidinglight.nexusquant.research.domain.BacktestConfigPage;
import com.guidinglight.nexusquant.research.domain.port.BacktestConfigListQuery;
import com.guidinglight.nexusquant.research.domain.port.BacktestConfigListQueryPort;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 保留父对象不存在时的404合同；总数和页面不能被并发归档拆成不同读取快照。 */
@Service
public class BacktestConfigListService {
    private final BacktestConfigListQueryPort queryPort;
    private final ResearchConfigService researchConfigs;

    public BacktestConfigListService(
            BacktestConfigListQueryPort queryPort, ResearchConfigService researchConfigs) {
        this.queryPort = queryPort;
        this.researchConfigs = researchConfigs;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public BacktestConfigPage list(BacktestConfigListQuery query) {
        if (query.researchConfigId() != null) {
            try {
                researchConfigs.getByResearchConfigId(query.researchConfigId());
            } catch (IllegalArgumentException missingParent) {
                // 只转换原父对象查找错误；数据库和分页结果损坏不能被吞成404或空列表。
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, missingParent.getMessage(), missingParent);
            }
        }
        return queryPort.readPage(query);
    }
}
