package com.guidinglight.nexusquant.adapter.okx.service;

import com.guidinglight.nexusquant.adapter.okx.model.OkxVenueRuleFactsSnapshot;

import java.util.Set;

/**
 * OkxVenueRuleFactsProvider 是 GateW-3 public-only venue-rule 读取窄端口。
 *
 * <p>端口只暴露 bounded public instrument snapshot，不暴露 raw endpoint、credential、private transport、
 * order/cancel 或其他交易能力。实现必须整批 fail-closed，不能返回部分 snapshot。</p>
 */
@FunctionalInterface
public interface OkxVenueRuleFactsProvider {

    /**
     * 读取 1..3 个 server-allowlisted OKX Spot symbols。
     *
     * @param allowlistedSymbols server-side allowlist 中本次选择的 symbols
     * @param traceId 脱敏追踪标识
     * @return 完整且已校验的 public snapshot
     */
    OkxVenueRuleFactsSnapshot fetch(Set<String> allowlistedSymbols, String traceId);
}
