package com.guidinglight.nexusquant.account.domain.port;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.performance.AccountEquitySample;
import com.guidinglight.nexusquant.account.domain.performance.AccountValuationInput;
import com.guidinglight.nexusquant.account.domain.performance.ClosedTradeStatistics;

import java.time.Instant;
import java.util.List;

/** 派生账户测量端口；既有交易和账本仍拥有经济事实。 */
public interface AccountPerformanceRepository {
    /** 按账户身份顺序有界读取下一批采样对象；不合并独立SIM运行预算。 */
    List<ExchangeAccountSummary> samplingCandidates(long afterAccountId, int limit);

    /** 检查最近测量以限制采样频率；查询不创建历史或补造不存在的时点。 */
    boolean recentlySampled(long accountId, String currency, Instant now);

    /** 从既有余额、资金流和公开价格读取单账户估值来源；缺失或过期价格须保留不完整状态。 */
    AccountValuationInput readValuation(
            ExchangeAccountSummary account, String quoteCurrency, Instant now);

    /** 只保存派生权益测量及来源；不得写入订单、成交或账务，也不回填虚构历史。 */
    void save(AccountEquitySample sample);

    /** 读取指定账户、币种和时间范围内的有界历史；调用方先完成所有者授权检查。 */
    List<AccountEquitySample> findRange(
            long accountId, String currency, Instant from, Instant to, int limit);

    /** 从账户起点读取成本基础；跨环境、未知注资及超出有界窗口时明确不可计算，不截断后算胜率。 */
    TradeHistory readTradeHistory(
            ExchangeAccountSummary account, String quoteCurrency, Instant through);

    record TradeHistory(List<ClosedTradeStatistics.Trade> trades, boolean completeBasis) {}
}
