package com.guidinglight.nexusquant.app.integration0.support;

/**
 * Int0SideEffectTracker 是 test-only 副作用探针（INT0-T14）。
 *
 * <p>它模拟一个“交易副作用网关”：如果 contract validation 误触发任何下单 / 撤单 / Paper 启停 /
 * 策略·风控状态变更 / adapter / exchange / LIVE，计数会增加。Integration-0 contract validation
 * 必须永不调用这些方法，因此测试断言 {@link #total()} 恒为 0。
 *
 * <p>本类不连接任何真实交易网关、adapter 或交易所。
 */
public final class Int0SideEffectTracker {

    private int placeOrder;
    private int cancelOrder;
    private int mutatePaperRun;
    private int mutateStrategy;
    private int mutateRisk;
    private int tradingGatewayCall;
    private int adapterCall;
    private int exchangeCall;
    private int liveEnable;

    public void placeOrder() {
        placeOrder++;
    }

    public void cancelOrder() {
        cancelOrder++;
    }

    public void mutatePaperRun() {
        mutatePaperRun++;
    }

    public void mutateStrategy() {
        mutateStrategy++;
    }

    public void mutateRisk() {
        mutateRisk++;
    }

    public void tradingGatewayCall() {
        tradingGatewayCall++;
    }

    public void adapterCall() {
        adapterCall++;
    }

    public void exchangeCall() {
        exchangeCall++;
    }

    public void liveEnable() {
        liveEnable++;
    }

    public int total() {
        return placeOrder
                + cancelOrder
                + mutatePaperRun
                + mutateStrategy
                + mutateRisk
                + tradingGatewayCall
                + adapterCall
                + exchangeCall
                + liveEnable;
    }
}
