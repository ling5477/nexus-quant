package com.guidinglight.nexusquant.strategy.domain;

import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyExecutionIntent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** 同一 run 的不可变有效请求；恢复只解释已保存的版本，不重新读取策略配置。 */
public record StrategyDispatchWork(
        String strategyRunId,
        int workSchemaVersion,
        int definitionVersion,
        Long accountId,
        String clientOrderId,
        String symbol,
        OrderSide side,
        OrderType orderType,
        BigDecimal quantity,
        BigDecimal price,
        String timeInForce
) {
    public StrategyDispatchWork {
        requireText(strategyRunId, 64, "strategyRunId");
        if (workSchemaVersion != 1 || definitionVersion < 1 || accountId == null || accountId <= 0) {
            throw new IllegalArgumentException("unsupported strategy work version or scope");
        }
        requireText(clientOrderId, 128, "clientOrderId");
        symbol = requireText(symbol, 64, "symbol");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(orderType, "orderType");
        quantity = exactDecimal(Objects.requireNonNull(quantity, "quantity"));
        if (quantity.signum() <= 0) throw new IllegalArgumentException("quantity must be positive");
        price = price == null ? null : exactDecimal(price);
        if (orderType == OrderType.LIMIT && (price == null || price.signum() <= 0)) {
            throw new IllegalArgumentException("price must be positive for LIMIT order");
        }
        String requiredTif = orderType == OrderType.MARKET ? "IOC" : "GTC";
        if (!requiredTif.equals(timeInForce)) throw new IllegalArgumentException("unsupported strategy work TIF");
    }

    public StrategyExecutionIntent intent(StrategyRun run) {
        if (!strategyRunId.equals(run.strategyRunId()) || !accountId.equals(run.accountId())
                || !clientOrderId.equals("coid-" + run.requestId())) {
            throw new IllegalStateException("STRATEGY_WORK_IDENTITY_MISMATCH");
        }
        requireText(run.requestId(), 64, "requestId");
        requireText(run.traceId(), 64, "traceId");
        return new StrategyExecutionIntent(run.requestId(), accountId, strategyRunId, run.exchangeCode(),
                symbol, clientOrderId, accountId + ":" + clientOrderId, "strategy_manual", side, orderType,
                price, quantity, timeInForce, run.traceId(), run.tradeEnv());
    }

    private static BigDecimal exactDecimal(BigDecimal value) {
        BigDecimal scaled = value.setScale(8, RoundingMode.UNNECESSARY);
        if (scaled.precision() > 38) throw new IllegalArgumentException("strategy work decimal exceeds NUMERIC(38,8)");
        return scaled;
    }

    private static String requireText(String value, int limit, String field) {
        if (value == null || value.isBlank() || value.trim().length() > limit) {
            throw new IllegalArgumentException("invalid " + field);
        }
        return value.trim();
    }
}
