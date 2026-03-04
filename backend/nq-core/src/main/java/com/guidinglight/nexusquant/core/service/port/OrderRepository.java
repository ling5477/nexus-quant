package com.guidinglight.nexusquant.core.service.port;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.core.model.OrderRecord;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * OrderRepository 定义订单持久化端口。
 *
 * Why:
 * 通过端口隔离下单编排与 JDBC 细节，既能满足生产落库，也便于单测中注入内存替身验证幂等逻辑。
 */
public interface OrderRepository {

    /**
     * 按账户与 client_order_id 查询订单。
     *
     * @param accountId 账户 ID
     * @param clientOrderId 客户端幂等键
     * @return 命中返回订单快照
     */
    Optional<OrderRecord> findByAccountAndClientOrderId(Long accountId, String clientOrderId);

    /**
     * 按 order_id 查询订单。
     *
     * @param orderId 系统订单 ID
     * @return 命中返回订单快照
     */
    Optional<OrderRecord> findByOrderId(String orderId);

    /**
     * 新建订单事实。
     *
     * @param order 订单快照
     * @param now 创建时间
     */
    void insert(OrderRecord order, Instant now);

    /**
     * 更新订单状态与原因。
     *
     * @param orderId 系统订单 ID
     * @param status 迁移后的状态
     * @param reason 状态原因
     * @param now 更新时间
     */
    void updateStatus(String orderId, OrderStatus status, String reason, Instant now);

    /**
     * 更新订单外部订单号。
     * <p>
     * Why:
     * GateC-0 要求回执成功后立刻落库 external_order_id，供后续 reconcile/恢复/WS 关联使用。
     *
     * @param orderId 系统订单 ID
     * @param externalOrderId 外部订单号
     * @param now 更新时间
     */
    void updateExternalOrderId(String orderId, String externalOrderId, Instant now);

    /**
     * 查询指定状态集合下的订单。
     *
     * @param statuses 目标状态集合
     * @param limit 最大返回条数
     * @return 订单列表
     */
    List<OrderRecord> findByStatuses(Collection<OrderStatus> statuses, int limit);
}
