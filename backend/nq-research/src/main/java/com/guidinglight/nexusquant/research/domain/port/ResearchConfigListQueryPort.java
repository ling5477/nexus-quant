package com.guidinglight.nexusquant.research.domain.port;

import com.guidinglight.nexusquant.research.domain.ResearchConfigPage;

/** 正式列表专用读端口；复用研究配置事实源，不改变已有管理和历史追溯接口。 */
public interface ResearchConfigListQueryPort {
    ResearchConfigPage readPage(ResearchConfigListQuery query);
}
