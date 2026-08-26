package com.guidinglight.nexusquant.trading.application;

import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderType;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * PlaceOrderRequest 表示下单编排入口参数。
 * <p>
 * Why:
 * GateD 需要把 contracts 强契约向 core 收口，避免 controller / scheduler / strategy runner
 * 各自决定 requestId、idempotencyKey、source、quantity 的语义。
 *
 * @param requestId      本次执行请求 ID；用于区分同一幂等键上的不同触发
 * @param accountId      账户 ID
 * @param strategyRunId  策略运行 ID，可空
 * @param venue          交易场所
 * @param clientOrderId  客户端幂等键
 * @param idempotencyKey 幂等键；默认按 `accountId:clientOrderId` 生成
 * @param source         请求来源，如 strategy / manual / recovery / reconcile
 * @param symbol         交易对
 * @param side           买卖方向
 * @param type           订单类型
 * @param price          价格，市价可空
 * @param quantity       数量，必须大于 0
 * @param timeInForce    时效策略；未显式指定时按订单类型兜底
 * @param traceId        链路追踪 ID
 * @param tradeEnv       canonical交易环境，固定SIM/LIVE；既有调用默认SIM
 * @param executionScopeId 内部执行网关的一次性scope identity，可空；不得作为strategy run持久化
 */
public record PlaceOrderRequest(
        String requestId,
        Long accountId,
        String strategyRunId,
        String venue,
        String symbol,
        String clientOrderId,
        String idempotencyKey,
        String source,
        OrderSide side,
        OrderType type,
        BigDecimal price,
        BigDecimal quantity,
        String timeInForce,
        String traceId,
        String tradeEnv,
        String executionScopeId
) {

    /**
     * 兼容dedicated execution scope引入时的构造器；未显式声明环境时继续默认SIM。
     */
    public PlaceOrderRequest(
            String requestId,
            Long accountId,
            String strategyRunId,
            String venue,
            String symbol,
            String clientOrderId,
            String idempotencyKey,
            String source,
            OrderSide side,
            OrderType type,
            BigDecimal price,
            BigDecimal quantity,
            String timeInForce,
            String traceId,
            String executionScopeId
    ) {
        this(requestId, accountId, strategyRunId, venue, symbol, clientOrderId,
                idempotencyKey, source, side, type, price, quantity, timeInForce, traceId,
                "SIM", executionScopeId);
    }

    /**
     * 兼容既有完整构造器；普通/strategy调用没有独立执行scope。
     */
    public PlaceOrderRequest(
            String requestId,
            Long accountId,
            String strategyRunId,
            String venue,
            String symbol,
            String clientOrderId,
            String idempotencyKey,
            String source,
            OrderSide side,
            OrderType type,
            BigDecimal price,
            BigDecimal quantity,
            String timeInForce,
            String traceId
    ) {
        this(requestId, accountId, strategyRunId, venue, symbol, clientOrderId,
                idempotencyKey, source, side, type, price, quantity, timeInForce, traceId,
                "SIM", null);
    }

    /**
     * 兼容旧构造器，允许现有调用方在不感知新字段的前提下继续工作。
     */
    public PlaceOrderRequest(
            Long accountId,
            String strategyRunId,
            String venue,
            String clientOrderId,
            String symbol,
            OrderSide side,
            OrderType type,
            BigDecimal price,
            BigDecimal quantity,
            String traceId
    ) {
        this(
                traceId,
                accountId,
                strategyRunId,
                venue,
                symbol,
                clientOrderId,
                buildDefaultIdempotencyKey(accountId, clientOrderId),
                defaultSource(strategyRunId),
                side,
                type,
                price,
                quantity,
                defaultTimeInForce(type),
                traceId,
                "SIM",
                null
        );
    }

    public PlaceOrderRequest {
        traceId = requireText(traceId, "traceId");
        requestId = normalizeText(requestId, traceId);
        venue = requireText(venue, "venue");
        symbol = requireText(symbol, "symbol");
        clientOrderId = requireText(clientOrderId, "clientOrderId");
        idempotencyKey = normalizeText(idempotencyKey, buildDefaultIdempotencyKey(accountId, clientOrderId));
        source = normalizeText(source, defaultSource(strategyRunId));
        timeInForce = normalizeText(timeInForce, defaultTimeInForce(type));
        tradeEnv = normalizeText(tradeEnv, "SIM").toUpperCase(Locale.ROOT);
        if (!java.util.Set.of("SIM", "LIVE").contains(tradeEnv)) {
            throw new IllegalArgumentException("tradeEnv must be SIM or LIVE");
        }
        executionScopeId = normalizeText(executionScopeId, null);
    }

    private static String buildDefaultIdempotencyKey(Long accountId, String clientOrderId) {
        return accountId + ":" + clientOrderId;
    }

    private static String defaultSource(String strategyRunId) {
        return strategyRunId == null || strategyRunId.isBlank() ? "manual" : "strategy";
    }

    private static String defaultTimeInForce(OrderType type) {
        return type == OrderType.MARKET ? "IOC" : "GTC";
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


