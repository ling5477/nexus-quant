package com.guidinglight.nexusquant.backtest.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/**
 * BacktestExecutionContext 保存 GateF-3 单次 run 的最小执行上下文。
 * <p>
 * Why:
 * 模拟订单、成交、持仓、PnL 更新需要共享同一组可变运行状态；
 * 显式上下文对象比把现金、费用、持仓散落在多处局部变量里更容易验证与复盘。
 */
public final class BacktestExecutionContext {

    private final String backtestRunId;
    private final String symbol;
    private final BigDecimal initialCapital;
    private BigDecimal cashBalance;
    private BigDecimal totalFee;
    private BigDecimal totalSlippage;
    private SimPosition currentPosition;
    private Instant latestSnapshotTime;

    public BacktestExecutionContext(String backtestRunId, String symbol, BigDecimal initialCapital) {
        this.backtestRunId = Objects.requireNonNull(backtestRunId, "backtestRunId must not be null");
        this.symbol = Objects.requireNonNull(symbol, "symbol must not be null");
        this.initialCapital = normalize(initialCapital);
        this.cashBalance = normalize(initialCapital);
        this.totalFee = BigDecimal.ZERO;
        this.totalSlippage = BigDecimal.ZERO;
        this.currentPosition = null;
        this.latestSnapshotTime = null;
    }

    public String backtestRunId() {
        return backtestRunId;
    }

    public String symbol() {
        return symbol;
    }

    public BigDecimal initialCapital() {
        return initialCapital;
    }

    public BigDecimal cashBalance() {
        return cashBalance;
    }

    public BigDecimal totalFee() {
        return totalFee;
    }

    public BigDecimal totalSlippage() {
        return totalSlippage;
    }

    public SimPosition currentPosition() {
        return currentPosition;
    }

    public Instant latestSnapshotTime() {
        return latestSnapshotTime;
    }

    public void applyBuy(SimTrade simTrade, SimPosition updatedPosition) {
        BigDecimal tradeNotional = simTrade.tradePrice().multiply(simTrade.quantity());
        cashBalance = normalize(cashBalance.subtract(tradeNotional)
                .subtract(simTrade.feeAmount())
                .subtract(simTrade.slippageAmount()));
        totalFee = normalize(totalFee.add(simTrade.feeAmount()));
        totalSlippage = normalize(totalSlippage.add(simTrade.slippageAmount()));
        currentPosition = updatedPosition;
        latestSnapshotTime = simTrade.tradedAt();
    }

    public void applySell(SimTrade simTrade, SimPosition updatedPosition) {
        BigDecimal tradeNotional = simTrade.tradePrice().multiply(simTrade.quantity());
        cashBalance = normalize(cashBalance.add(tradeNotional)
                .subtract(simTrade.feeAmount())
                .subtract(simTrade.slippageAmount()));
        totalFee = normalize(totalFee.add(simTrade.feeAmount()));
        totalSlippage = normalize(totalSlippage.add(simTrade.slippageAmount()));
        currentPosition = updatedPosition;
        latestSnapshotTime = simTrade.tradedAt();
    }

    private BigDecimal normalize(BigDecimal value) {
        return value.setScale(18, RoundingMode.HALF_UP);
    }
}
