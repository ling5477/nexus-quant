package com.guidinglight.nexusquant.ledger.service.port;

import com.guidinglight.nexusquant.ledger.contracts.model.SimAssetFundingPlan;

import java.math.BigDecimal;
import java.util.Optional;

/** 新隔离 PAPER/SIM 的一次性多资产预算；不授予交易、转账或真实资产注入权限。 */
public interface SimAssetFundingPort {
    SimAssetFundingPlan fundOnce(
            long accountId, String paperRunId, SimAssetFundingPlan plan, String traceId);

    BigDecimal assetBalance(long accountId, String currency);

    Optional<SimAssetFundingPlan> findPlan(long accountId, String paperRunId);
}
