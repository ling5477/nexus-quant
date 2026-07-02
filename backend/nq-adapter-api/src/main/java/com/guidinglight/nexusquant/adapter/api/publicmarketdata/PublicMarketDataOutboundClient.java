package com.guidinglight.nexusquant.adapter.api.publicmarketdata;

/**
 * PublicMarketDataOutboundClient 是 GateO O-1 public marketdata outbound 的最小抽象。
 *
 * <p>Why: service 层不得直接散写 URL 或绕过 policy。实现必须先执行
 * {@link PublicMarketDataOutboundPolicy}，再决定是否访问 HTTP；测试可用 fake server/stub 验证，
 * 默认 profile 不构造真实 HTTP client。</p>
 */
public interface PublicMarketDataOutboundClient {

    /**
     * 执行一次 public marketdata outbound fetch。
     *
     * @param request 脱敏请求模型；不可为空
     * @return 不含 raw response/header/query/credential 的结果模型
     */
    PublicMarketDataOutboundResult fetch(PublicMarketDataOutboundRequest request);
}
