package com.guidinglight.nexusquant.marketdata.application.exception;

/** 同一来源身份或K线版本已有不同记录时拒绝覆盖；恢复只能先读已保存事实。 */
public final class PriceEventSourceConflictException extends RuntimeException {
    public PriceEventSourceConflictException() {
        super("PRICE_EVENT_SOURCE_CONFLICT");
    }
}
