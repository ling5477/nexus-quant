package com.guidinglight.nexusquant.trading.api.web;

import java.util.List;

/**
 * OrderListResponse 是 GateH-1 交易工作台订单列表的分页响应。
 *
 * <p>Why: `/api/trading/orders` 需要成为正式列表查询入口，前端不能再把单订单查询当成工作台主模式。
 * 该 DTO 只表达读模型分页信息，不承载下单、撤单或状态推进语义。</p>
 */
public record OrderListResponse(
        List<OrderView> items,
        int page,
        int size,
        long total
) {
}
