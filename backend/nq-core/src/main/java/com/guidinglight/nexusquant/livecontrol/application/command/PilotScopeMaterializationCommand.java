package com.guidinglight.nexusquant.livecontrol.application.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 受控实盘执行 typed command。所有引用都必须是 exact identity/revision/digest；禁止 latest/current/HEAD。
 */
public record PilotScopeMaterializationCommand(
        UUID sessionId,
        UUID pilotScopeId,
        long exchangeAccountId,
        long credentialReference,
        String strategyReleaseId,
        String releaseDigest,
        long releaseAdmissionRevision,
        RiskSelection risk,
        List<String> symbolAllowlist,
        BigDecimal capitalCap,
        Instant executionWindowStart,
        Instant executionWindowEnd,
        String expectedPilotScopeHash,
        String idempotencyKey,
        String requestId,
        String traceId
) {

    public record RiskSelection(
            UUID riskLimitSetId,
            String riskLimitSetDigest,
            int version,
            BigDecimal capitalCap,
            BigDecimal maxOrderNotional,
            BigDecimal maxSymbolPositionNotional,
            BigDecimal maxDailyRealizedLoss,
            BigDecimal maxDailyTotalLoss,
            int maxOpenOrders,
            int maxIntradayOrders,
            List<String> symbolAllowlist,
            int maxSessionDurationSeconds,
            BigDecimal spreadLimitBps,
            BigDecimal slippageLimitBps,
            int maxMarketDataAgeMs,
            int minDataCoverageBps
    ) {
    }
}
