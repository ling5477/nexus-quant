package com.guidinglight.nexusquant.news.domain;

/** 固定错误码避免把不可信外部响应或网络诊断泄漏到产品界面。 */
public class NewsSourceException extends RuntimeException {
    private final String code;

    public NewsSourceException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
