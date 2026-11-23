package com.guidinglight.nexusquant.research.domain.port;

import com.guidinglight.nexusquant.research.domain.BacktestConfigPage;

/** 有界查询必须复用正式费用来源读取范围，不能先全表读取再在客户端过滤。 */
public interface BacktestConfigListQueryPort {
    BacktestConfigPage readPage(BacktestConfigListQuery query);
}
