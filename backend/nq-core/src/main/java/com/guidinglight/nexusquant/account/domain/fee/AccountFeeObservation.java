package com.guidinglight.nexusquant.account.domain.fee;

import java.time.Instant;
import java.util.UUID;

/** 当前同步时实际适用的账户费率；不是任意历史区间费率，也不是已经发生的成交费用。 */
public record AccountFeeObservation(
        UUID observationId,
        UUID operationId,
        long ownerUserId,
        long exchangeAccountId,
        long credentialId,
        String exchangeCode,
        String tradeEnv,
        String symbol,
        String inputFingerprint,
        AccountFeeQuote quote,
        Instant expiresAt,
        String sourceSha256) {
    public static final String SOURCE = "OKX_PRIVATE_SPOT_FEE_V5";
    public static final String CONVENTION = "OKX_NEGATIVE_COST_V1";

    @Override
    public String toString() {
        return "AccountFeeObservation[REDACTED]";
    }
}
