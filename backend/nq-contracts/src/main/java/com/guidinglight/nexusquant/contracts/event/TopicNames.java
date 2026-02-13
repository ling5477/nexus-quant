package com.guidinglight.nexusquant.contracts.event;

/**
 * TopicNames 统一维护事件 topic 常量。
 *
 * Why:
 * docs/CONTRACTS.md 要求 topic 归口到 nq-contracts，避免字符串散落导致版本漂移。
 */
public final class TopicNames {

    public static final String ORDER_COMMAND_V1 = "order.command.v1";
    public static final String ORDER_EVENT_V1 = "order.event.v1";
    public static final String TRADE_EVENT_V1 = "trade.event.v1";
    public static final String POSITION_EVENT_V1 = "position.event.v1";
    public static final String LEDGER_EVENT_V1 = "ledger.event.v1";
    public static final String RISK_EVENT_V1 = "risk.event.v1";
    public static final String AUDIT_EVENT_V1 = "audit.event.v1";

    private TopicNames() {
        // 常量类不允许实例化。
    }
}
