package com.guidinglight.nexusquant.contracts.command;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * PlaceOrderCommand 冻结下单命令契约。
 * <p>
 * Why:
 * GateD 要求下单命令在 contracts 层显式携带 requestId、idempotencyKey、venue、accountId、symbol、
 * quantity、price 等核心语义，避免 core、risk、scheduler 各自补字段导致口径漂移。
 *
 * @param orderId        系统订单 ID
 * @param requestId      本次下单请求 ID；用于区分“同一幂等键下的不同触发尝试”
 * @param accountId      账户 ID
 * @param venue          交易场所
 * @param symbol         交易对
 * @param clientOrderId  客户端幂等键
 * @param idempotencyKey 执行闭环幂等键；默认按 `accountId:clientOrderId` 衍生
 * @param side           买卖方向
 * @param type           订单类型
 * @param price          价格（市价时可空）
 * @param quantity       数量
 * @param timeInForce    时效策略
 * @param source         请求来源，如 strategy / manual / recovery / reconcile
 * @param strategyId     触发该命令的策略 ID（占位）
 * @param traceId        链路追踪 ID
 */
public record PlaceOrderCommand(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("request_id") String requestId,
        @JsonProperty("account_id") Long accountId,
        @JsonProperty("venue") String venue,
        @JsonProperty("symbol") String symbol,
        @JsonProperty("client_order_id") String clientOrderId,
        @JsonProperty("idempotency_key") String idempotencyKey,
        @JsonProperty("side") String side,
        @JsonProperty("type") String type,
        @JsonProperty("price") BigDecimal price,
        @JsonProperty("quantity") BigDecimal quantity,
        @JsonProperty("time_in_force") String timeInForce,
        @JsonProperty("source") String source,
        @JsonProperty("strategy_id") String strategyId,
        @JsonProperty("trace_id") String traceId
) {

    /**
     * 兼容 GateD 第二批改造前的旧构造器，避免一次性改爆所有调用点。
     * <p>
     * Why:
     * 当前仓库仍存在旧签名的测试、回归脚本与事件序列化校验；先在 contracts 层提供兼容入口，
     * 再逐步把调用侧升级到显式 requestId / idempotencyKey / source。
     */
    public PlaceOrderCommand(
            String orderId,
            Long accountId,
            String venue,
            String symbol,
            String clientOrderId,
            String side,
            String type,
            BigDecimal price,
            BigDecimal quantity,
            String timeInForce,
            String strategyId,
            String traceId
    ) {
        this(
                orderId,
                traceId,
                accountId,
                venue,
                symbol,
                clientOrderId,
                buildDefaultIdempotencyKey(accountId, clientOrderId),
                side,
                type,
                price,
                quantity,
                timeInForce,
                defaultSource(strategyId),
                strategyId,
                traceId
        );
    }

    public PlaceOrderCommand {
        traceId = requireText(traceId, "traceId");
        requestId = normalizeText(requestId, traceId);
        venue = requireText(venue, "venue");
        symbol = requireText(symbol, "symbol");
        clientOrderId = requireText(clientOrderId, "clientOrderId");
        idempotencyKey = normalizeText(idempotencyKey, buildDefaultIdempotencyKey(accountId, clientOrderId));
        timeInForce = normalizeText(timeInForce, "GTC");
        source = normalizeText(source, defaultSource(strategyId));
    }

    private static String buildDefaultIdempotencyKey(Long accountId, String clientOrderId) {
        return accountId + ":" + clientOrderId;
    }

    private static String defaultSource(String strategyId) {
        return strategyId == null || strategyId.isBlank() ? "manual" : "strategy";
    }

    private static String requireText(String value, String fieldName) {
        String normalized = normalizeText(value, null);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private static String normalizeText(String value, String fallback) {
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }
}
