package com.guidinglight.nexusquant.account.infra.probe;

import com.guidinglight.nexusquant.account.domain.ExchangeCredentialPermissionProbeRequest;
import com.guidinglight.nexusquant.account.domain.ExchangeCredentialPermissionProbeResult;
import com.guidinglight.nexusquant.account.domain.CredentialPermissionExpectation;
import com.guidinglight.nexusquant.account.domain.port.ExchangeCredentialPermissionProbePort;
import com.guidinglight.nexusquant.account.infra.okx.readonly.JdbcOkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.account.infra.okx.readonly.OkxPrivateCredentialExecutor;
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
    private final CredentialPermissionExpectation permissionExpectation;
    private final Clock clock;

    public OkxRealReadonlyPermissionProbePort(
            OkxPrivateCredentialExecutor credentialExecutor,
            String expectedIp,
            Clock clock
    ) {
        this(credentialExecutor, expectedIp, CredentialPermissionExpectation.READ_ONLY_DIAGNOSTIC, clock);
    }

    public OkxRealReadonlyPermissionProbePort(
            OkxPrivateCredentialExecutor credentialExecutor,
            String expectedIp,
            CredentialPermissionExpectation permissionExpectation,
            Clock clock
    ) {
        this.credentialExecutor = Objects.requireNonNull(credentialExecutor, "credentialExecutor must not be null");
        this.expectedIp = OkxIpAddressNormalizer.normalizeLiteral(expectedIp);
        this.permissionExpectation = Objects.requireNonNull(
                permissionExpectation,
                "permissionExpectation must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public CredentialPermissionExpectation permissionExpectation() {
        return permissionExpectation;
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
            return failed(request, "RESPONSE_CONTRACT_MISMATCH", ipStatus(observed), false, requestId, startedAt);
        }
        Set<String> permissions = observed.normalizedPermissions();
        boolean read = permissions.contains("READ_ONLY");
        boolean trade = permissions.contains("TRADE");
        boolean withdraw = permissions.contains("WITHDRAW");
        String scope = trade ? "TRADE" : withdraw ? "FUNDING" : read ? "READ_ONLY" : null;
        if (!KNOWN_PERMISSIONS.containsAll(permissions)) {
            return failed(request, "RESPONSE_CONTRACT_MISMATCH", ipStatus(observed), read, trade, withdraw,
                    requestId, startedAt, scope);
        }
        String ipStatus = ipStatus(observed);
        if (withdraw) {
            return failed(request, "WITHDRAW_PERMISSION_ENABLED", ipStatus, read, trade, true,
                    requestId, startedAt, scope);
        }
        if (!read) {
            return failed(request, "READ_PERMISSION_MISSING", ipStatus, false, trade, false,
                    requestId, startedAt, scope);
        }
        if (permissionExpectation == CredentialPermissionExpectation.READ_ONLY_DIAGNOSTIC && trade) {
            return failed(request, "TRADE_PERMISSION_ENABLED", ipStatus, read, true, false,
                    requestId, startedAt, scope);
        }
        if (permissionExpectation == CredentialPermissionExpectation.GATEY_PILOT_READINESS && !trade) {
            return failed(request, "TRADE_PERMISSION_MISSING", ipStatus, read, false, false,
                    requestId, startedAt, scope);
        }
        if (observed.ipAllowlistStatus() != OkxIpAllowlistStatus.MATCHED) {
            return failed(request, ipFailure(observed.ipAllowlistStatus()), ipStatus, read, trade, false,
                    requestId, startedAt, scope);
        }
        return ExchangeCredentialPermissionProbeResult.succeeded(
                "OKX",
                request.credentialType(),
                scope,
                read,
                trade,
                false,
                permissionExpectation,
                trade,
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
        return failed(request, category, ipStatus, false, false, withdraw, requestId, startedAt, null);
    }

    private ExchangeCredentialPermissionProbeResult failed(
            ExchangeCredentialPermissionProbeRequest request,
            String category,
            String ipStatus,
            boolean read,
            boolean trade,
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
                read,
                trade,
                withdraw,
                request == null ? permissionExpectation : request.permissionExpectation(),
                trade,
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

    private boolean safeRequest(ExchangeCredentialPermissionProbeRequest request) {
        return request != null
                && request.ownerUserId() != null && request.ownerUserId() > 0
                && request.accountId() != null && request.accountId() > 0
                && request.credentialId() != null && request.credentialId() > 0
                && "OKX".equalsIgnoreCase(request.exchange())
                && "LIVE".equalsIgnoreCase(request.tradeEnv())
                && JdbcOkxPrivateCredentialExecutor.OKX_API_V5.equals(request.credentialType())
                && request.permissionExpectation() == permissionExpectation
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
            case HTTP_UNAUTHORIZED -> "HTTP_UNAUTHORIZED";
            case HTTP_FORBIDDEN -> "HTTP_FORBIDDEN";
            case HTTP_RATE_LIMITED, RATE_LIMITED -> "HTTP_RATE_LIMITED";
            case HTTP_SERVER_ERROR -> "HTTP_SERVER_ERROR";
            case HTTP_UNEXPECTED_STATUS, HTTP_ERROR -> "HTTP_UNEXPECTED_STATUS";
            case OKX_AUTHENTICATION_FAILED, AUTHENTICATION_FAILURE, INVALID_API_KEY -> "OKX_AUTHENTICATION_FAILED";
            case OKX_SIGNATURE_INVALID, SIGNATURE_FAILURE -> "OKX_SIGNATURE_INVALID";
            case OKX_TIMESTAMP_INVALID, CLOCK_SKEW -> "OKX_TIMESTAMP_INVALID";
            case OKX_IP_NOT_ALLOWED, IP_ALLOWLIST_FAILED -> "OKX_IP_NOT_ALLOWED";
            case OKX_PERMISSION_DENIED, PERMISSION_BLOCKED -> "OKX_PERMISSION_DENIED";
            case OKX_BUSINESS_REJECTED, OKX_PROVIDER_ERROR, ENVIRONMENT_MISMATCH -> "OKX_BUSINESS_REJECTED";
            case RESPONSE_PARSE_FAILED, MALFORMED_RESPONSE -> "RESPONSE_PARSE_FAILED";
            case RESPONSE_CONTRACT_MISMATCH, PARTIAL_RESPONSE -> "RESPONSE_CONTRACT_MISMATCH";
            case NETWORK_TIMEOUT, TIMEOUT -> "NETWORK_TIMEOUT";
            case NETWORK_IO_ERROR, NETWORK_FAILURE -> "NETWORK_IO_ERROR";
            case REDIRECT_REJECTED -> "REDIRECT_REJECTED";
            case RESPONSE_TOO_LARGE -> "RESPONSE_TOO_LARGE";
            case CREDENTIAL_UNAVAILABLE -> "CREDENTIAL_UNAVAILABLE";
            case CREDENTIAL_CONFLICT -> "CREDENTIAL_CONFLICT";
            case ACCOUNT_SCOPE_MISMATCH -> "ACCOUNT_SCOPE_MISMATCH";
        };
    }

    @Override
    public String toString() {
        return "OkxRealReadonlyPermissionProbePort[expectedIp=CONFIGURED,permissionExpectation="
                + permissionExpectation + "]";
    }
}
