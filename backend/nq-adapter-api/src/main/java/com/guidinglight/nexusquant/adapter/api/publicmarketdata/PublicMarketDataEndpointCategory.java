package com.guidinglight.nexusquant.adapter.api.publicmarketdata;

/**
 * PublicMarketDataEndpointCategory 定义 GateO O-1 允许或拒绝的公开行情 endpoint 类别。
 *
 * <p>Why: public outbound 必须先经过类别级 allowlist/denylist，而不是让 service 直接拼 URL。
 * 当前 O-1 只允许 public REST 的 server time、instrument metadata、ticker 和 OHLCV/kline；
 * order book、recent trades、public WebSocket 虽然可能是公开数据，但默认后置。任何私有、签名、
 * credential、账户或交易相关类别都必须 fail-closed。</p>
 */
public enum PublicMarketDataEndpointCategory {

    SERVER_TIME(true, false),
    INSTRUMENTS(true, false),
    TICKER(true, false),
    OHLCV(true, false),
    ORDER_BOOK(false, false),
    RECENT_TRADES(false, false),
    PUBLIC_WEBSOCKET(false, false),
    ACCOUNT(false, true),
    BALANCE(false, true),
    ORDER(false, true),
    CANCEL(false, true),
    AMEND(false, true),
    POSITIONS(false, true),
    WALLET(false, true),
    TRANSFER(false, true),
    WITHDRAW(false, true),
    DEPOSIT(false, true),
    SUBACCOUNT(false, true),
    PRIVATE_WEBSOCKET(false, true),
    SIGNED_REQUEST(false, true),
    API_KEY_VALIDATION(false, true),
    REAL_PERMISSION_PROBE(false, true),
    AUTHENTICATED(false, true),
    UNKNOWN(false, true);

    private final boolean allowedByDefault;
    private final boolean privateOrSigned;

    PublicMarketDataEndpointCategory(boolean allowedByDefault, boolean privateOrSigned) {
        this.allowedByDefault = allowedByDefault;
        this.privateOrSigned = privateOrSigned;
    }

    /**
     * 判断该类别是否属于 O-1 public REST 最小 allowlist。
     *
     * @return true 表示 policy 可继续审查请求上下文；false 表示默认拒绝
     */
    public boolean allowedByDefault() {
        return allowedByDefault;
    }

    /**
     * 判断该类别是否需要私有鉴权、签名、credential 或 permission probe。
     *
     * @return true 表示必须直接拒绝，不能进入 HTTP client
     */
    public boolean privateOrSigned() {
        return privateOrSigned;
    }
}
