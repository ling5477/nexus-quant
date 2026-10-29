package com.guidinglight.nexusquant.trading.domain.port;

import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import java.time.Instant;

/** 与 Order 绑定的不可逆发送决定；实现必须使用调用方的短事务，不拥有网络能力。 */
public interface OrdinaryPlaceAuthorityRepository {
    /** 原子创建新 Order 与 NOT_ARMED；已有订单不得补建可发送资格。 */
    void insertOrder(OrderRecord order, Instant now);

    /** 仅当前状态代际、Kill 允许且 NOT_ARMED 时获得一次决定；提交未知不授予发送机会。 */
    boolean arm(OrderRecord expected);

    /** 锁定同一订单/authority，撤销仅在同事务完成 Order 无订单终态时才可提交。 */
    boolean revoke(OrderRecord expected);
}
