package com.guidinglight.nexusquant.trading.domain.port;

import com.guidinglight.nexusquant.trading.domain.OrderCancelFinality;

/** 事务 C 原子写入订单最终取消事实及对应 run 终态。 */
public interface OrderCancelFinalityRepository {
    boolean finish(OrderCancelFinality finality);
}
