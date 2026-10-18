package com.guidinglight.nexusquant.marketdata.domain.port;

import com.guidinglight.nexusquant.marketdata.domain.PublicOrderBookSnapshot;

/** 仅公开现货盘口，不读取账户凭据，也没有下单或订阅私有流的能力。 */
public interface PublicOrderBookReadPort {
    String exchangeCode();

    PublicOrderBookSnapshot fetch(String symbol);
}
