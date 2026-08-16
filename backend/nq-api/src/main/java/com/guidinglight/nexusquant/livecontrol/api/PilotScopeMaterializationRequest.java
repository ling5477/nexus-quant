package com.guidinglight.nexusquant.livecontrol.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.guidinglight.nexusquant.livecontrol.application.PilotScopeMaterializationCommand;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** GateY-6D exact typed materialization body；不接受 creator、credential material 或 generic JSON。 */
public record PilotScopeMaterializationRequest(
        @NotNull UUID sessionId,
        @NotNull UUID pilotScopeId,
        @Positive long exchangeAccountId,
        @Positive long credentialReference,
        @NotBlank @Size(max = 128) String strategyReleaseId,
        @NotBlank @Size(min = 64, max = 64) String releaseDigest,
        @Positive long releaseAdmissionRevision,
        @NotNull @Valid RiskSelection risk,
        @NotEmpty @Size(max = 2) List<@NotBlank String> symbolAllowlist,
        @NotNull BigDecimal capitalCap,
        @NotNull Instant executionWindowStart,
        @NotNull Instant executionWindowEnd,
        @NotBlank @Size(min = 64, max = 64) String expectedPilotScopeHash
) {

    public PilotScopeMaterializationCommand toCommand(String idempotencyKey, String requestId, String traceId) {
        return new PilotScopeMaterializationCommand(
                sessionId, pilotScopeId, exchangeAccountId, credentialReference,
                strategyReleaseId, releaseDigest, releaseAdmissionRevision, risk.toCommand(), symbolAllowlist,
                capitalCap, executionWindowStart, executionWindowEnd, expectedPilotScopeHash,
                idempotencyKey, requestId, traceId
        );
    }

    /** 即使全局 Jackson 配置忽略 unknown field，也拒绝旧 observation authority payload。 */
    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object ignoredValue) {
        throw new IllegalArgumentException("unsupported pilot materialization field: " + fieldName);
    }

    public record RiskSelection(
            @NotNull UUID riskLimitSetId,
            @NotBlank @Size(min = 64, max = 64) String riskLimitSetDigest,
            @Positive int version,
            @NotNull BigDecimal capitalCap,
            @NotNull BigDecimal maxOrderNotional,
            @NotNull BigDecimal maxSymbolPositionNotional,
            @NotNull BigDecimal maxDailyRealizedLoss,
            @NotNull BigDecimal maxDailyTotalLoss,
            @Positive int maxOpenOrders,
            @Positive int maxIntradayOrders,
            @NotEmpty @Size(max = 2) List<@NotBlank String> symbolAllowlist,
            @Positive int maxSessionDurationSeconds,
            @NotNull BigDecimal spreadLimitBps,
            @NotNull BigDecimal slippageLimitBps,
            @Positive int maxMarketDataAgeMs,
            @Positive int minDataCoverageBps
    ) {
        private PilotScopeMaterializationCommand.RiskSelection toCommand() {
            return new PilotScopeMaterializationCommand.RiskSelection(
                    riskLimitSetId, riskLimitSetDigest, version, capitalCap, maxOrderNotional,
                    maxSymbolPositionNotional, maxDailyRealizedLoss, maxDailyTotalLoss, maxOpenOrders,
                    maxIntradayOrders, symbolAllowlist, maxSessionDurationSeconds, spreadLimitBps,
                    slippageLimitBps, maxMarketDataAgeMs, minDataCoverageBps);
        }
    }

}
