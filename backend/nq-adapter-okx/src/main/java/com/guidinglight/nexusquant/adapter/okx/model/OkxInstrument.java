package com.guidinglight.nexusquant.adapter.okx.model;

import java.math.BigDecimal;

/**
 * OkxInstrument 表示 OKX public instruments 的最小缓存条目。
 * <p>
 * Why:
 * GateC-1 要求下单前必须按 tickSz/lotSz/minSz 做 trim 与最小量校验，
 * 因此 adapter 需要一个稳定的元数据快照，而不是每次下单都临时拼 JSON 字段。
 *
 * @param instId OKX 交易对，例如 BTC-USDT
 * @param tickSize 价格步长，用于价格向下截断
 * @param lotSize 数量步长，用于数量向下截断
 * @param minSize 最小下单数量，小于该值必须拒单
 * @param state 产品状态，非 live 时必须拒绝下单
 */
public record OkxInstrument(
        String instId,
        BigDecimal tickSize,
        BigDecimal lotSize,
        BigDecimal minSize,
        String state
) {
}
