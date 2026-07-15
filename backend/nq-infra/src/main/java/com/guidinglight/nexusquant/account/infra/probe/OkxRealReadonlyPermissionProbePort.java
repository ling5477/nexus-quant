package com.guidinglight.nexusquant.account.infra.probe;

import com.guidinglight.nexusquant.account.domain.ExchangeCredentialPermissionProbeRequest;
import com.guidinglight.nexusquant.account.domain.ExchangeCredentialPermissionProbeResult;
import com.guidinglight.nexusquant.account.domain.port.ExchangeCredentialPermissionProbePort;
import com.guidinglight.nexusquant.account.infra.gatew.JdbcOkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.account.infra.gatew.OkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.adapter.okx.service.OkxIpAddressNormalizer;
import com.guidinglight.nexusquant.adapter.okx.service.OkxIpAllowlistStatus;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateEnvironment;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadError;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadException;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadOperation;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadRequest;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadResult;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 受控 OKX production account/config permission probe。
 *
 * <p>唯一网络能力由 typed request 固定为 GET /api/v5/account/config。凭证只在
 * {@link JdbcOkxPrivateCredentialExecutor} 的同步 callback 内解密和使用；本类不接收、记录或返回
 * raw credential/provider response，也不暴露任何交易、余额、撤单、转账或提现能力。</p>
 */
public final class OkxRealReadonlyPermissionProbePort implements ExchangeCredentialPermissionProbePort {

    private static final Set<String> KNOWN_PERMISSIONS = Set.of("READ_ONLY", "TRADE", "WITHDRAW");

    private final OkxPrivateCredentialExecutor credentialExecutor;
    private final String expectedIp;
    private final Clock clock;

    public OkxRealReadonlyPermissionProbePort(
            OkxPrivateCredentialExecutor credentialExecutor,
            String expectedIp,
            Clock clock
    ) {
        this.credentialExecutor = Objects.requireNonNull(credentialExecutor, "credentialExecutor must not be null");
        this.expectedIp = OkxIpAddressNormalizer.normalizeLiteral(expectedIp);
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public boolean supportsControlledLiveReadOnlyProbe() {
        return true;
    }

    @Override
    public ExchangeCredentialPermissionProbeResult probe(ExchangeCredentialPermissionProbeRequest request) {
        Instant startedAt = clock.instant();
        String requestId = "okx-permission-" + UUID.randomUUID();
        if (!safeRequest(request)) {
            return failed(request, "REQUEST_SCOPE_BLOCKED", "UNKNOWN", false, requestId, startedAt);
        }
        try {
            OkxPrivateReadResult observed = credentialExecutor.withActiveCredential(
                    request.ownerUserId(),
                    request.accountId(),
                    request.credentialId(),
                    request.credentialType(),
                    session -> session.execute(
                            OkxPrivateReadRequest.accountConfiguration(expectedIp),
                            OkxPrivateEnvironment.PRODUCTION
                    )
            );
            return classify(request, observed, requestId, startedAt);
        } catch (OkxPrivateReadException ex) {
            return failed(request, errorCategory(ex.category()), "UNKNOWN", false, requestId, startedAt);
        } catch (RuntimeException ex) {
            return failed(request, "INTERNAL_PROBE_FAILURE", "UNKNOWN", false, requestId, startedAt);
        }
    }

    private ExchangeCredentialPermissionProbeResult classify(
            ExchangeCredentialPermissionProbeRequest request,
            OkxPrivateReadResult observed,
            String requestId,
            Instant startedAt
    ) {
        if (observed == null
                || observed.operation() != OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ
                || !observed.complete()
                || observed.normalizedPermissions().isEmpty()) {
            return failed(request, "RESPONSE_FIELDS_MISSING", ipStatus(observed), false, requestId, startedAt);
        }
        Set<String> permissions = observed.normalizedPermissions();
        if (!KNOWN_PERMISSIONS.containsAll(permissions)) {
            return failed(request, "UNKNOWN_PERMISSION_TOKEN", ipStatus(observed),
                    permissions.contains("WITHDRAW"), requestId, startedAt);
        }
        boolean read = permissions.contains("READ_ONLY");
        boolean trade = permissions.contains("TRADE");
        boolean withdraw = permissions.contains("WITHDRAW");
        String scope = trade ? "TRADE" : withdraw ? "FUNDING" : read ? "READ_ONLY" : null;
        String ipStatus = ipStatus(observed);
        if (!read) {
            return failed(request, "READ_PERMISSION_MISSING", ipStatus, withdraw, requestId, startedAt, scope);
        }
        if (trade) {
            return failed(request, "TRADE_PERMISSION_ENABLED", ipStatus, withdraw, requestId, startedAt, scope);
        }
        if (withdraw) {
            return failed(request, "WITHDRAW_PERMISSION_ENABLED", ipStatus, true, requestId, startedAt, scope);
        }
        if (observed.ipAllowlistStatus() != OkxIpAllowlistStatus.MATCHED) {
            return failed(request, ipFailure(observed.ipAllowlistStatus()), ipStatus, false, requestId, startedAt, scope);
        }
        return ExchangeCredentialPermissionProbeResult.succeeded(
                "OKX",
                request.credentialType(),
                "READ_ONLY",
                "PASSED",
                requestId,
                request.traceId(),
                startedAt,
                clock.instant()
        );
    }

    private ExchangeCredentialPermissionProbeResult failed(
            ExchangeCredentialPermissionProbeRequest request,
            String category,
            String ipStatus,
            boolean withdraw,
            String requestId,
            Instant startedAt
    ) {
        return failed(request, category, ipStatus, withdraw, requestId, startedAt, null);
    }

    private ExchangeCredentialPermissionProbeResult failed(
            ExchangeCredentialPermissionProbeRequest request,
            String category,
            String ipStatus,
            boolean withdraw,
            String requestId,
            Instant startedAt,
            String scope
    ) {
        return new ExchangeCredentialPermissionProbeResult(
                "OKX",
                request == null ? JdbcOkxPrivateCredentialExecutor.OKX_API_V5 : request.credentialType(),
                "FAILED",
                scope,
                withdraw,
                ipStatus,
                category,
                category,
                0,
                requestId,
                request == null ? null : request.traceId(),
                startedAt,
                clock.instant()
        );
    }

    private static boolean safeRequest(ExchangeCredentialPermissionProbeRequest request) {
        return request != null
                && request.ownerUserId() != null && request.ownerUserId() > 0
                && request.accountId() != null && request.accountId() > 0
                && request.credentialId() != null && request.credentialId() > 0
                && "OKX".equalsIgnoreCase(request.exchange())
                && "LIVE".equalsIgnoreCase(request.tradeEnv())
                && JdbcOkxPrivateCredentialExecutor.OKX_API_V5.equals(request.credentialType())
                && "PAPER".equalsIgnoreCase(request.requestedMode())
                && request.dryRun();
    }

    private static String ipStatus(OkxPrivateReadResult observed) {
        if (observed == null || observed.ipAllowlistStatus() == null) {
            return "UNKNOWN";
        }
        return switch (observed.ipAllowlistStatus()) {
            case MATCHED -> "PASSED";
            case MISSING, MISMATCHED -> "FAILED";
            case UNKNOWN, NOT_CHECKED -> "UNKNOWN";
        };
    }

    private static String ipFailure(OkxIpAllowlistStatus status) {
        return switch (status) {
            case MISSING -> "IP_ALLOWLIST_MISSING";
            case MISMATCHED -> "IP_ALLOWLIST_MISMATCH";
            case UNKNOWN, NOT_CHECKED -> "IP_ALLOWLIST_UNKNOWN";
            case MATCHED -> "IP_ALLOWLIST_FAILED";
        };
    }

    private static String errorCategory(OkxPrivateReadError error) {
        return switch (error) {
            case AUTHENTICATION_FAILURE -> "AUTH_FAILED";
            case INVALID_API_KEY -> "INVALID_API_KEY";
            case SIGNATURE_FAILURE -> "SIGNATURE_FAILED";
            case CLOCK_SKEW -> "CLOCK_SKEW";
            case IP_ALLOWLIST_FAILED -> "IP_ALLOWLIST_FAILED";
            case PERMISSION_BLOCKED -> "PERMISSION_BLOCKED";
            case RATE_LIMITED -> "RATE_LIMITED";
            case TIMEOUT -> "TIMEOUT";
            case REDIRECT_REJECTED -> "REDIRECT_REJECTED";
            case HTTP_ERROR -> "HTTP_ERROR";
            case OKX_PROVIDER_ERROR -> "PROVIDER_ERROR";
            case RESPONSE_TOO_LARGE -> "RESPONSE_TOO_LARGE";
            case MALFORMED_RESPONSE, PARTIAL_RESPONSE -> "MALFORMED_RESPONSE";
            case CREDENTIAL_UNAVAILABLE -> "CREDENTIAL_UNAVAILABLE";
            case CREDENTIAL_CONFLICT -> "CREDENTIAL_CONFLICT";
            case ENVIRONMENT_MISMATCH, ACCOUNT_SCOPE_MISMATCH -> "ACCOUNT_SCOPE_MISMATCH";
            case NETWORK_FAILURE -> "NETWORK_FAILURE";
        };
    }

    @Override
    public String toString() {
        return "OkxRealReadonlyPermissionProbePort[expectedIp=CONFIGURED]";
    }
}
