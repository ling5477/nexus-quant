package com.guidinglight.nexusquant.trading.domain;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import java.math.BigDecimal;
import java.util.Set;

/**
 * OrderRecord 表示订单在持久化层的完整快照。
 *
 * Why:
 * Gate B 需要在下单、撮合、记账阶段共享同一份订单事实，
 * 通过统一模型避免各服务重复组装字段导致状态与 trace_id 丢失。
 *
 * @param orderId 系统订单 ID
 * @param accountId 账户 ID
 * @param strategyRunId 策略运行 ID，可空
 * @param venue 交易场所
 * @param symbol 交易对
 * @param clientOrderId 客户端幂等键
 * @param side 买卖方向
 * @param type 订单类型
 * @param price 价格，市价可空
 * @param qty 下单数量
 * @param externalOrderId 外部订单号，可空；仅在回执确认后落库
 * @param status 订单状态
 * @param reason 状态原因
 * @param traceId 链路追踪 ID
 * @param tradeEnv canonical交易环境，固定SIM/LIVE
 * @param version durable 状态迁移代际；identity-only enrichment 不推进代际
 */
public record OrderRecord(
        String orderId,
        Long accountId,
        String strategyRunId,
        String venue,
        String symbol,
        String clientOrderId,
        String side,
        String type,
        BigDecimal price,
        BigDecimal qty,
        String externalOrderId,
        OrderStatus status,
        String reason,
        String traceId,
        String tradeEnv,
        long version
) {

    /** 保留历史原始持久值用于事实校验，所有交易分支使用解析后的身份，未知值拒绝。 */
    public TradingVenue canonicalVenue() {
        return TradingVenue.parse(venue);
    }

    /** 新订单及既有测试构造入口从代际零开始；持久化读取必须使用完整构造器。 */
    public OrderRecord(String orderId, Long accountId, String strategyRunId, String venue, String symbol,
            String clientOrderId, String side, String type, BigDecimal price, BigDecimal qty,
            String externalOrderId, OrderStatus status, String reason, String traceId, String tradeEnv) {
        this(orderId, accountId, strategyRunId, venue, symbol, clientOrderId, side, type, price, qty,
                externalOrderId, status, reason, traceId, tradeEnv, 0L);
    }

    /** 兼容既有订单构造器；未显式声明时保持历史SIM默认值。 */
    public OrderRecord(
            String orderId,
            Long accountId,
            String strategyRunId,
            String venue,
            String symbol,
            String clientOrderId,
            String side,
            String type,
            BigDecimal price,
            BigDecimal qty,
            String externalOrderId,
            OrderStatus status,
            String reason,
            String traceId
    ) {
        this(orderId, accountId, strategyRunId, venue, symbol, clientOrderId, side, type,
                price, qty, externalOrderId, status, reason, traceId, "SIM");
    }

    public OrderRecord {
        if (version < 0) {
            throw new IllegalArgumentException("order version must be nonnegative");
        }
        if (!Set.of("SIM", "LIVE").contains(tradeEnv)) {
            throw new IllegalArgumentException("tradeEnv must be SIM or LIVE");
        }
    }

    /**
     * 为成功迁移构造下一代快照；调用方必须先取得数据库 CAS 的迁移所有权。
     *
     * @param nextStatus 迁移后的状态
     * @param nextReason 迁移原因
     * @return 新的订单快照
     */
    public OrderRecord withStatus(OrderStatus nextStatus, String nextReason) {
        return new OrderRecord(
                orderId,
                accountId,
                strategyRunId,
                venue,
                symbol,
                clientOrderId,
                side,
                type,
                price,
                qty,
                externalOrderId,
                nextStatus,
                nextReason,
                traceId,
                tradeEnv,
                Math.incrementExact(version)
        );
    }

    /**
     * 基于当前订单构造带外部订单号的新快照。
     * <p>
     * Why:
     * GateC-0 要求 placeOrder 成功回执后立刻把 external_order_id 落库，
     * 这里用不可变快照避免调用方在内存里“半更新”导致状态与外部单号不一致。
     *
     * @param nextExternalOrderId 新的外部订单号
     * @return 带外部订单号的新快照
     */
    public OrderRecord withExternalOrderId(String nextExternalOrderId) {
        return new OrderRecord(
                orderId,
                accountId,
                strategyRunId,
                venue,
                symbol,
                clientOrderId,
                side,
                type,
                price,
                qty,
                nextExternalOrderId,
                status,
                reason,
                traceId,
                tradeEnv,
                version
        );
    }
}

