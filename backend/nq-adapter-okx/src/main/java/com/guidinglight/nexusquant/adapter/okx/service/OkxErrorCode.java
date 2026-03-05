package com.guidinglight.nexusquant.adapter.okx.service;

/**
 * OkxErrorCode 统一维护 OKX 业务错误码的规范化语义。
 * <p>
 * Why:
 * GateC 恢复流程需要对特定错误码（如 51603 订单不存在）做可审计降级；
 * 如果只传递原始字符串，调用方很难稳定识别可恢复错误并做分支处理。
 */
public enum OkxErrorCode {

    ORDER_NOT_FOUND("51603"),
    UNKNOWN("");

    private final String rawCode;

    OkxErrorCode(String rawCode) {
        this.rawCode = rawCode;
    }

    public String rawCode() {
        return rawCode;
    }

    /**
     * 按原始 OKX code 映射错误语义。
     *
     * @param rawCode OKX 返回的原始错误码
     * @return 规范化错误语义；无法识别时返回 UNKNOWN
     */
    public static OkxErrorCode fromRawCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return UNKNOWN;
        }
        for (OkxErrorCode candidate : values()) {
            if (candidate.rawCode.equals(rawCode)) {
                return candidate;
            }
        }
        return UNKNOWN;
    }
}
