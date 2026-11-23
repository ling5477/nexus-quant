package com.guidinglight.nexusquant.marketdata.domain;

/** 出站异常只传递固定错误码，不向HTTP调用方泄露供应方原始响应或请求头。 */
public class OrderBookSourceException extends RuntimeException {
    private final String code;

    public OrderBookSourceException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
