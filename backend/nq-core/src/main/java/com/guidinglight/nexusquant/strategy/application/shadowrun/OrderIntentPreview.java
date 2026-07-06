package com.guidinglight.nexusquant.strategy.application.shadowrun;

/**
 * GateR-4 Shadow Run order intent preview 模型。
 *
 * <p>职责：保存拟议订单意图的只读 preview。Why：后续一致性分析需要看到本地策略会产生怎样的
 * order intent，但 GateR-4 不允许提交订单、撤单、转账、提现、调用 private endpoint 或修改真实订单。
 *
 * @param previewOnly 必须为 true；false 表示调用方试图提交真实交易语义，必须拒绝
 * @param side        预览方向；只读字段，不得触发 BUY / SELL 执行
 * @param symbol      标的代码
 * @param quantity    数量字符串；保留调用方精度，不做真实下单数量解释
 * @param orderType   预览订单类型
 * @param limitPrice  限价字符串；可为空表示非限价预览
 * @param timeInForce 预览有效期策略
 * @param reasonCode  生成该预览的原因码
 * @param riskRef     风险预检引用
 * @param traceId     全链路 trace id
 */
public record OrderIntentPreview(
        boolean previewOnly,
        String side,
        String symbol,
        String quantity,
        String orderType,
        String limitPrice,
        String timeInForce,
        String reasonCode,
        String riskRef,
        String traceId
) {

    public OrderIntentPreview {
        if (!previewOnly) {
            throw new IllegalArgumentException("order intent preview must set previewOnly to true");
        }
        side = StrategyDecisionTrace.requireText(side, "side");
        symbol = StrategyDecisionTrace.requireText(symbol, "symbol");
        quantity = StrategyDecisionTrace.requireText(quantity, "quantity");
        orderType = StrategyDecisionTrace.requireText(orderType, "orderType");
        limitPrice = limitPrice == null || limitPrice.isBlank() ? null : limitPrice.trim();
        timeInForce = StrategyDecisionTrace.requireText(timeInForce, "timeInForce");
        reasonCode = StrategyDecisionTrace.requireText(reasonCode, "reasonCode");
        riskRef = StrategyDecisionTrace.requireText(riskRef, "riskRef");
        traceId = StrategyDecisionTrace.requireText(traceId, "traceId");
    }
}
