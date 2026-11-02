package com.guidinglight.nexusquant.search.domain;

import java.time.Instant;

/** 只读定位摘要，不携带凭据、交易配置、账务金额或新的执行权限。 */
public record ObjectSearchHit(
        String kind,
        String id,
        String title,
        String status,
        String strategyCode,
        Long exchangeAccountId,
        String tradeEnv,
        String paperRunId,
        Instant updatedAt) {}
