package com.guidinglight.nexusquant.trading.domain.port;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

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

    /** 由 PostgreSQL 的同 run UNIQUE 保证至多一行；不能用该查询代替唯一约束。 */
    default Optional<OrderRecord> findByStrategyRunId(String strategyRunId) {
        throw new UnsupportedOperationException("strategy order binding lookup is not implemented");
    }

    /**
     * 新建订单事实。
     *
     * @param order 订单快照
     * @param now 创建时间
     */
    void insert(OrderRecord order, Instant now);

    /**
     * 原子迁移指定状态代际；状态机合法性由调用方检查。
     *
     * @param orderId 系统订单 ID
     * @param expectedStatus 快照中的预期状态
     * @param expectedVersion 快照中的预期代际，不得为旧回执刷新后重试
     * @param status 迁移后的状态
     * @param reason 状态原因
     * @param now 更新时间
     * @return 实际影响行数：1 成功且 version 加一，0 冲突，其他值由调用方 fail closed
     */
    int compareAndSetStatus(String orderId, OrderStatus expectedStatus, long expectedVersion,
            OrderStatus status, String reason, Instant now);

    /** 对完整 durable Trade 集合按 venue fill 身份校验并累计；身份损坏、重复或非法数量必须拒绝。 */
    default BigDecimal durableExecutedQuantity(String orderId) {
        throw new UnsupportedOperationException("durable execution proof unavailable");
    }

    /** 仅供对账纠正撤单终态；同一 SQL 中重新验证完整成交证明及 expectedVersion，成功只加一代。 */
    default int compareAndSetCancelledToFilled(String orderId, long expectedVersion, String reason, Instant now) {
        throw new UnsupportedOperationException("proven terminal correction unavailable");
    }

    /**
     * 仅填充空的外部订单号，保持已有 identity 和 lifecycle version。
     * <p>
     * Why:
     * GateC-0 要求回执成功后立刻落库 external_order_id，供后续 reconcile/恢复/WS 关联使用。
     *
     * @param orderId 系统订单 ID
     * @param externalOrderId 外部订单号
     * @param now 更新时间
     * @return 1 填充成功，0 未填充（调用方须读取并确认已有 identity 相同），其他值 fail closed
     */
    int updateExternalOrderId(String orderId, String externalOrderId, Instant now);

    /**
     * 查询指定状态集合下的订单。
     *
     * @param statuses 目标状态集合
     * @param limit 最大返回条数
     * @return 订单列表
     */
    List<OrderRecord> findByStatuses(Collection<OrderStatus> statuses, int limit);

    /**
     * 为 venue 的 canonical 对账扫描预留一批候选，所有状态共享 limit。
     * 以持久化 (created_at, order_id) 游标循环选择；返回前独立短事务必须提交。
     * 预留只推进扫描进度，不修改订单；调用方失败或崩溃后候选在下一圈重新可达。
     * 同 venue 并发预留串行推进；不承诺处理期间的排他所有权或 exactly-once。
     *
     * @param venue 非空 venue，必须在数据库 LIMIT 前过滤
     * @param statuses 非空候选状态集合
     * @param limit 正的共享订单候选上限
     * @return 最多 limit 个订单快照；空集合不推进游标
     * @throws IllegalArgumentException 输入不满足预留约束
     */
    List<OrderRecord> reserveReconciliationCandidates(String venue, Collection<OrderStatus> statuses, int limit);
}


