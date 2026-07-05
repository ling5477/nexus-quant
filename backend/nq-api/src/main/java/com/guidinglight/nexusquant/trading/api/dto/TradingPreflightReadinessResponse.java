package com.guidinglight.nexusquant.trading.api.dto;

import com.guidinglight.nexusquant.trading.application.preflight.TradingPreflightCredentialTypeSummary;
import com.guidinglight.nexusquant.trading.application.preflight.TradingPreflightReadiness;
import com.guidinglight.nexusquant.trading.application.preflight.TradingPreflightReason;
import com.guidinglight.nexusquant.trading.application.preflight.TradingPreflightScope;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * TradingPreflightReadinessResponse 是 GateP Batch 4 只读 preflight API 的安全 HTTP DTO。
 *
 * <p>Why: 响应只表达当前阻断/诊断状态，不返回 credential material、raw provider payload、
 * private endpoint 细节，也不提供 tradingReady / liveReady / authorizedForTrading 等授权字段。
 */
@Schema(name = "TradingPreflightReadinessResponse", description = "Read-only account permission and risk preflight baseline")
public record TradingPreflightReadinessResponse(
        Scope scope,
        String exchangeCode,
        Long accountId,
        String marketType,
        String symbol,
        String liveStatus,
        String realProviderStatus,
        String privateTradingStatus,
        String permissionProbeStatus,
        boolean credentialConfigured,
        String credentialStatus,
        List<CredentialTypeSummary> credentialTypeSummary,
        boolean accountConfigured,
        String accountStatus,
        String dataQualityStatus,
        String riskPreflightStatus,
        List<Reason> blockers,
        List<Reason> warnings,
        List<String> requiredNextSteps,
        Instant generatedAt
) {
    public static TradingPreflightReadinessResponse from(TradingPreflightReadiness readiness) {
        return new TradingPreflightReadinessResponse(
                Scope.from(readiness.scope()),
                readiness.exchangeCode(),
                readiness.accountId(),
                readiness.marketType(),
                readiness.symbol(),
                readiness.liveStatus(),
                readiness.realProviderStatus(),
                readiness.privateTradingStatus(),
                readiness.permissionProbeStatus(),
                readiness.credentialConfigured(),
                readiness.credentialStatus(),
                readiness.credentialTypeSummary().stream().map(CredentialTypeSummary::from).toList(),
                readiness.accountConfigured(),
                readiness.accountStatus(),
                readiness.dataQualityStatus(),
                readiness.riskPreflightStatus(),
                readiness.blockers().stream().map(Reason::from).toList(),
                readiness.warnings().stream().map(Reason::from).toList(),
                readiness.requiredNextSteps(),
                readiness.generatedAt()
        );
    }

    /** Scope 回显请求诊断范围；不表示交易授权。 */
    public record Scope(
            String exchangeCode,
            Long accountId,
            String marketType,
            String symbol,
            String strategyId
    ) {
        private static Scope from(TradingPreflightScope scope) {
            return new Scope(
                    scope.exchangeCode(),
                    scope.accountId(),
                    scope.marketType(),
                    scope.symbol(),
                    scope.strategyId()
            );
        }
    }

    /** CredentialTypeSummary 只暴露 credential metadata，不返回任何密钥或 payload。 */
    public record CredentialTypeSummary(
            Long credentialId,
            String credentialType,
            String credentialStatus,
            String verificationStatus,
            boolean active,
            String permissionProbeStatus,
            String permissionScope,
            String ipAllowlistProbeStatus,
            int failedAuthCount,
            Instant lastVerifiedAt,
            Instant lastPermissionProbeAt
    ) {
        private static CredentialTypeSummary from(TradingPreflightCredentialTypeSummary summary) {
            return new CredentialTypeSummary(
                    summary.credentialId(),
                    summary.credentialType(),
                    summary.credentialStatus(),
                    summary.verificationStatus(),
                    summary.active(),
                    summary.permissionProbeStatus(),
                    summary.permissionScope(),
                    summary.ipAllowlistProbeStatus(),
                    summary.failedAuthCount(),
                    summary.lastVerifiedAt(),
                    summary.lastPermissionProbeAt()
            );
        }
    }

    /** Reason 是 blocker / warning 的稳定 code，不承载敏感材料。 */
    public record Reason(String code, String severity, String message) {
        private static Reason from(TradingPreflightReason reason) {
            return new Reason(reason.code(), reason.severity(), reason.message());
        }
    }
}
