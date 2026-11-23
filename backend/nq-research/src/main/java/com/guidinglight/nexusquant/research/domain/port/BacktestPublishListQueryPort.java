package com.guidinglight.nexusquant.research.domain.port;

import com.guidinglight.nexusquant.research.domain.BacktestPublishRecord;
import com.guidinglight.nexusquant.research.domain.ResearchResultPage;

/** 发布列表仍从正式发布事实及原运行读取权限，不建立独立来源或新的发布状态。 */
public interface BacktestPublishListQueryPort {
    ResearchResultPage<BacktestPublishRecord> readPage(PublishListQuery query);
}
