package com.guidinglight.nexusquant.auth.domain.port;

import com.guidinglight.nexusquant.auth.domain.WatchlistSymbol;

import java.util.List;
import java.util.Set;

/** 仅验证最多21个市场偏好；不会同步标的或访问交易所。 */
@FunctionalInterface
public interface UserMarketCatalog {
    /** 只返回请求中当前目录支持的市场；已有失效偏好仍允许读取和移除，不能被隐式替换。 */
    Set<WatchlistSymbol> supported(List<WatchlistSymbol> requested);
}
