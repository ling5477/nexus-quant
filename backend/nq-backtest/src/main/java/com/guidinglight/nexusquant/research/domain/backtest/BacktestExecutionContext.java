package com.guidinglight.nexusquant.research.domain.backtest;

import com.guidinglight.nexusquant.marketdata.domain.DiscountAssetPriceSnapshot;
import com.guidinglight.nexusquant.marketdata.domain.DiscountAssetPriceSnapshot.Point;
import com.guidinglight.nexusquant.research.domain.backtest.ResearchRunFacts.ConversionFacts;
import com.guidinglight.nexusquant.research.domain.backtest.ResearchRunFacts.DiscountAssetFacts;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.DiscountFunding;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/**
 * BacktestExecutionContext 保存研究与回测契约单次 run的最小执行上下文。
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
    private DiscountAssetPriceSnapshot discountPrices;
    private BigDecimal discountBudgetFraction;
    private BigDecimal initialDiscountUnits;
    private BigDecimal initialDiscountValue;
    private BigDecimal initialQuoteCash;
    private BigDecimal discountBalance;
    private Point initialDiscountPrice;

    public BacktestExecutionContext(
            String backtestRunId, String symbol, BigDecimal initialCapital) {
        this.backtestRunId =
                Objects.requireNonNull(backtestRunId, "backtestRunId must not be null");
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

    /** 总预算在首次开盘可见价上拆分，不赠送第三币；floor单位后的尾差留在quote现金。 */
    public void initializeDiscountAssets(
            DiscountAssetPriceSnapshot prices, BigDecimal fraction, Instant at) {
        if (discountPrices != null || currentPosition != null || latestSnapshotTime != null)
            throw new IllegalStateException("BACKTEST_DISCOUNT_INITIALIZATION_NOT_EMPTY");
        Objects.requireNonNull(prices);
        Objects.requireNonNull(fraction);
        if (fraction.signum() <= 0
                || fraction.compareTo(BigDecimal.ONE) >= 0
                || fraction.scale() > 8
                || prices.assetCurrency().equals(symbol.substring(0, symbol.indexOf('-')))
                || !prices.quoteCurrency().equals(symbol.substring(symbol.indexOf('-') + 1))
                || prices.assetCurrency().equals(prices.quoteCurrency()))
            throw new IllegalArgumentException("BACKTEST_DISCOUNT_BUDGET_INVALID");
        Point point = prices.priceAt(at);
        BigDecimal units =
                initialCapital
                        .multiply(fraction)
                        .divide(point.quotePerAsset(), 8, RoundingMode.DOWN);
        if (units.signum() <= 0)
            throw new IllegalArgumentException("BACKTEST_DISCOUNT_BUDGET_DUST");
        BigDecimal assetValue =
                units.multiply(point.quotePerAsset()).setScale(8, RoundingMode.HALF_UP);
        if (assetValue.compareTo(initialCapital) > 0)
            throw new IllegalArgumentException("BACKTEST_DISCOUNT_BUDGET_EXCEEDS_CAPITAL");
        discountPrices = prices;
        discountBudgetFraction = fraction;
        initialDiscountPrice = point;
        initialDiscountUnits = units;
        discountBalance = units;
        initialDiscountValue = assetValue;
        initialQuoteCash = normalize(initialCapital.subtract(assetValue));
        cashBalance = initialQuoteCash;
    }

    public boolean hasDiscountAssets() {
        return discountPrices != null;
    }

    public DiscountAssetPriceSnapshot discountPrices() {
        return discountPrices;
    }

    public BigDecimal discountBudgetFraction() {
        return discountBudgetFraction;
    }

    public BigDecimal discountBalance() {
        return discountBalance;
    }

    public Point discountPriceAt(Instant at) {
        return hasDiscountAssets() ? discountPrices.priceAt(at) : null;
    }

    public DiscountFunding discountFundingAt(Instant at) {
        return hasDiscountAssets() ? discountPrices.funding(at, discountBalance) : null;
    }

    public BigDecimal discountQuoteValueAt(Instant at) {
        // 资金拆分和余额估值统一使用canonical八位quote；不能以十八位重估初始尾差凭空增减资本。
        return hasDiscountAssets()
                ? normalize(
                        discountBalance
                                .multiply(discountPrices.priceAt(at).quotePerAsset())
                                .setScale(8, RoundingMode.HALF_UP))
                : BigDecimal.ZERO;
    }

    public DiscountAssetFacts discountFactsAt(Instant at) {
        if (!hasDiscountAssets()) return null;
        Point last = discountPrices.priceAt(at);
        return new DiscountAssetFacts(
                discountPrices.assetCurrency(),
                discountPrices.quoteCurrency(),
                discountBudgetFraction,
                initialDiscountUnits,
                initialDiscountValue,
                initialQuoteCash,
                conversion(initialDiscountPrice),
                discountBalance,
                discountQuoteValueAt(at),
                conversion(last),
                discountPrices.sourceSha256(),
                discountPrices.sourceJson());
    }

    private static ConversionFacts conversion(Point point) {
        return new ConversionFacts(
                point.quotePerAsset(),
                point.observedAt().toString(),
                point.visibleAt().toString(),
                point.sourceSha256(),
                point.sourceJson());
    }

    /** 仅已成交原币费用改变资产余额；负费用在此入账，不提前进入下一笔的资金。 */
    private void settleDiscountFee(SimTrade trade) {
        if (!"DISCOUNT_ASSET_USED".equals(trade.feePaymentStatus())) return;
        if (!hasDiscountAssets() || !discountPrices.assetCurrency().equals(trade.feeCurrency()))
            throw new IllegalArgumentException("BACKTEST_DISCOUNT_SETTLEMENT_SCOPE_MISMATCH");
        BigDecimal next = discountBalance.subtract(trade.feeNativeAmount());
        if (next.signum() < 0)
            throw new IllegalArgumentException("BACKTEST_DISCOUNT_BALANCE_INSUFFICIENT");
        discountBalance = next;
    }

    public void applyBuy(SimTrade simTrade, SimPosition updatedPosition) {
        settleDiscountFee(simTrade);
        BigDecimal tradeNotional = simTrade.tradePrice().multiply(simTrade.quantity());
        cashBalance =
                normalize(
                        cashBalance
                                .subtract(tradeNotional)
                                .subtract(simTrade.cashFeeAmount())
                                .subtract(simTrade.slippageAmount()));
        totalFee = normalize(totalFee.add(simTrade.feeAmount()));
        totalSlippage = normalize(totalSlippage.add(simTrade.slippageAmount()));
        currentPosition = updatedPosition;
        latestSnapshotTime = simTrade.tradedAt();
    }

    public void applySell(SimTrade simTrade, SimPosition updatedPosition) {
        settleDiscountFee(simTrade);
        BigDecimal tradeNotional = simTrade.tradePrice().multiply(simTrade.quantity());
        cashBalance =
                normalize(
                        cashBalance
                                .add(tradeNotional)
                                .subtract(simTrade.cashFeeAmount())
                                .subtract(simTrade.slippageAmount()));
        totalFee = normalize(totalFee.add(simTrade.feeAmount()));
        totalSlippage = normalize(totalSlippage.add(simTrade.slippageAmount()));
        currentPosition = updatedPosition;
        latestSnapshotTime = simTrade.tradedAt();
    }

    /** 新版成交价已经包含滑点，现金只扣成交额和费用；滑点金额仅用于解释结果。 */
    public void applyAtSlippedFillPrice(SimTrade simTrade, SimPosition updatedPosition) {
        settleDiscountFee(simTrade);
        BigDecimal notional = simTrade.tradePrice().multiply(simTrade.quantity());
        cashBalance =
                "BUY".equals(simTrade.side())
                        ? normalize(
                                cashBalance.subtract(notional).subtract(simTrade.cashFeeAmount()))
                        : normalize(cashBalance.add(notional).subtract(simTrade.cashFeeAmount()));
        totalFee = normalize(totalFee.add(simTrade.feeAmount()));
        totalSlippage = normalize(totalSlippage.add(simTrade.slippageAmount()));
        currentPosition = updatedPosition;
        latestSnapshotTime = simTrade.tradedAt();
    }

    private BigDecimal normalize(BigDecimal value) {
        return value.setScale(18, RoundingMode.HALF_UP);
    }
}
