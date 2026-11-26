package com.guidinglight.nexusquant.marketdata.domain.port;

import com.guidinglight.nexusquant.marketdata.domain.PublicMarketSnapshot;

/** 只读取公开展示快照，形成中数据不会进入历史或策略输入类型。 */
public interface PublicMarketSnapshotReadPort {
    PublicMarketSnapshot read(String exchangeCode, String marketType, String symbol, String interval);

    final class UnsupportedSelection extends RuntimeException {}
}
