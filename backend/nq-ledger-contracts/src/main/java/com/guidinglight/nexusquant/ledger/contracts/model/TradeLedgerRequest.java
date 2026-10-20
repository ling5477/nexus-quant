package com.guidinglight.nexusquant.ledger.contracts.model;

import com.guidinglight.nexusquant.contracts.model.OrderSide;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * TradeLedgerRequest 描述“成交驱动记账”的输入事实。
 * fee采用NQ成本符号：正数收费、负数返佣、零数无费用；feeCurrency保留原成交币种。
 * 供应方符号转换由正式适配边界负责，本合同不读取费率、不推导费用，也不提前确认返佣。
 *
 * Why:
 * scheduler 与其他运行时编排模块只能依赖 ledger contracts，
 * 不能继续直接依赖 `nq-ledger` 内部模型。
 */
public record TradeLedgerRequest(
        String tradeId,
        String orderId,
        Long accountId,
        String symbol,
        OrderSide side,
        BigDecimal price,
        BigDecimal qty,
        BigDecimal fee,
        String feeCurrency,
        String traceId,
        Instant ts,
        String tradeEnv) {}
