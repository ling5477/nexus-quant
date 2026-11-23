package com.guidinglight.nexusquant.trading.api.dto;

import java.util.List;

/** 本地事实分页显式保留正式账户与交易账户身份，不表示实时外部交易所快照。 */
public record AccountFactPage<T>(Long exchangeAccountId, Long tradingAccountId, String venue,
        String tradeEnv, String source, List<T> items, int page, int size, long total) {}
