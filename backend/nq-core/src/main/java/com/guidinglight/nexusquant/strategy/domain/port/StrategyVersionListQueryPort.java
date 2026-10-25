package com.guidinglight.nexusquant.strategy.domain.port;

import com.guidinglight.nexusquant.strategy.domain.StrategyVersionPage;

/** 分页读取与原版本写入端口分开，不要求已有内存仓储补造目录或快照。 */
public interface StrategyVersionListQueryPort {
    StrategyVersionPage readPage(StrategyVersionListQuery query);
}
