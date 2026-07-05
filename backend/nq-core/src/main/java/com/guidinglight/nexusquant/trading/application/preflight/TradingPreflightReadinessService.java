package com.guidinglight.nexusquant.trading.application.preflight;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.marketdata.application.MarketdataQualityOverviewService;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityOverview;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityOverviewQuery;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TradingPreflightReadinessService 生成单交易所账户权限与风险前置只读基线。
 *
 * <p>Why: GateP Batch 4 的目标是解释“为什么当前不能进入真实交易”，而不是执行真实 precheck。
 * 本 service 只读取 exchange account、credential summary 和 Marketdata Data Quality overview；
 * 它不会读取 credential material，不会调用 permission probe port / adapter / RiskGate / OrderCommandService，
 * 也不会发起外部网络 IO 或数据库写入。
 */
@Service
public class TradingPreflightReadinessService {

    private static final String DEFAULT_EXCHANGE = "OKX";
    private static final String DEFAULT_MARKET_TYPE = "SPOT";

    private final ExchangeAccountRepository exchangeAccountRepository;
    private final ExchangeAccountCredentialRepository credentialRepository;
    private final MarketdataQualityOverviewService marketdataQualityOverviewService;
    private final Clock clock;

    /**
     * 生产构造器：注入现有只读 repository/service。
     *
     * @param exchangeAccountRepository exchange account metadata 端口
     * @param credentialRepository credential summary 端口；本 service 只调用 summary 方法
     * @param marketdataQualityOverviewService Data Quality 只读 overview service
     */
    @Autowired
    public TradingPreflightReadinessService(
            ExchangeAccountRepository exchangeAccountRepository,
            ExchangeAccountCredentialRepository credentialRepository,
            MarketdataQualityOverviewService marketdataQualityOverviewService
    ) {
        this(exchangeAccountRepository, credentialRepository, marketdataQualityOverviewService, Clock.systemUTC());
    }

    TradingPreflightReadinessService(
            ExchangeAccountRepository exchangeAccountRepository,
            ExchangeAccountCredentialRepository credentialRepository,
            MarketdataQualityOverviewService marketdataQualityOverviewService,
            Clock clock
    ) {
        this.exchangeAccountRepository = Objects.requireNonNull(
                exchangeAccountRepository,
                "exchangeAccountRepository must not be null"
        );
        this.credentialRepository = Objects.requireNonNull(
                credentialRepository,
                "credentialRepository must not be null"
        );
        this.marketdataQualityOverviewService = Objects.requireNonNull(
                marketdataQualityOverviewService,
                "marketdataQualityOverviewService must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 生成 preflight readiness baseline。
     *
     * <p>幂等/副作用：该方法是 read-only diagnostic。它只读本地 DB metadata 与 data quality facts；
     * 不会调用 credential material、permission probe、adapter、真实交易所、RiskGate 或订单链路。
     *
     * @param query 当前用户与可选筛选条件；ownerUserId 必须为正数
     * @return fail-closed readiness baseline；当前总是返回 `RISK_PREFLIGHT_BLOCKED`
     */
    @Transactional(readOnly = true)
    public TradingPreflightReadiness readiness(TradingPreflightReadinessQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        Long ownerUserId = requirePositiveOwnerUserId(query.ownerUserId());
        String exchangeCode = normalizeOrDefault(query.exchangeCode(), DEFAULT_EXCHANGE);
        String marketType = normalizeOrDefault(query.marketType(), DEFAULT_MARKET_TYPE);
        String symbol = normalizeNullable(query.symbol());
        String strategyId = normalizeNullable(query.strategyId());
        Optional<ExchangeAccountSummary> selectedAccount = selectAccount(ownerUserId, exchangeCode, query.accountId());
        Long accountId = selectedAccount.map(ExchangeAccountSummary::exchangeAccountId).orElse(null);

        List<ExchangeAccountCredentialSummary> activeCredentials = selectedAccount
                .map(account -> credentialRepository.listActiveSummaries(ownerUserId, account.exchangeAccountId()))
                .orElseGet(List::of);
        List<TradingPreflightCredentialTypeSummary> credentialTypes = activeCredentials.stream()
                .sorted(Comparator.comparing(ExchangeAccountCredentialSummary::credentialType))
                .map(this::toCredentialTypeSummary)
                .toList();
        MarketdataQualityOverview dataQualityOverview = marketdataQualityOverviewService.summarize(
                new MarketdataQualityOverviewQuery(
                        exchangeCode,
                        marketType,
                        symbol,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );

        List<TradingPreflightReason> blockers = blockers(
                selectedAccount.orElse(null),
                exchangeCode,
                activeCredentials,
                dataQualityOverview
        );
        List<TradingPreflightReason> warnings = warnings(dataQualityOverview);
        return new TradingPreflightReadiness(
                new TradingPreflightScope(exchangeCode, accountId, marketType, symbol, strategyId),
                exchangeCode,
                accountId,
                marketType,
                symbol,
                "LIVE_DISABLED",
                "REAL_PROVIDER_NOT_IMPLEMENTED",
                "PRIVATE_TRADING_NOT_IMPLEMENTED",
                permissionProbeStatus(activeCredentials),
                !activeCredentials.isEmpty(),
                credentialStatus(activeCredentials),
                credentialTypes,
                selectedAccount.isPresent(),
                selectedAccount.map(ExchangeAccountSummary::status).orElse("ACCOUNT_UNCONFIGURED"),
                dataQualityOverview.qualityStatus().name(),
                "RISK_PREFLIGHT_BLOCKED",
                blockers,
                warnings,
                requiredNextSteps(selectedAccount.isPresent(), activeCredentials.isEmpty()),
                Instant.now(clock)
        );
    }

    private Optional<ExchangeAccountSummary> selectAccount(Long ownerUserId, String exchangeCode, Long accountId) {
        if (accountId != null) {
            if (accountId <= 0) {
                return Optional.empty();
            }
            return exchangeAccountRepository.findByIdForOwner(ownerUserId, accountId);
        }
        List<ExchangeAccountSummary> accounts = exchangeAccountRepository.listByOwnerUserId(ownerUserId);
        Optional<ExchangeAccountSummary> defaultMatched = accounts.stream()
                .filter(account -> account.isDefault() && exchangeCode.equalsIgnoreCase(account.exchangeCode()))
                .findFirst();
        if (defaultMatched.isPresent()) {
            return defaultMatched;
        }
        return accounts.stream()
                .filter(account -> exchangeCode.equalsIgnoreCase(account.exchangeCode()))
                .findFirst();
    }

    private TradingPreflightCredentialTypeSummary toCredentialTypeSummary(ExchangeAccountCredentialSummary summary) {
        return new TradingPreflightCredentialTypeSummary(
                summary.credentialId(),
                summary.credentialType(),
                summary.credentialStatus(),
                summary.verificationStatus(),
                summary.isActive(),
                summary.permissionProbeStatus(),
                summary.permissionScope(),
                summary.ipAllowlistProbeStatus(),
                summary.failedAuthCount(),
                summary.lastVerifiedAt(),
                summary.lastPermissionProbeAt()
        );
    }

    private List<TradingPreflightReason> blockers(
            ExchangeAccountSummary account,
            String requestedExchangeCode,
            List<ExchangeAccountCredentialSummary> activeCredentials,
            MarketdataQualityOverview dataQualityOverview
    ) {
        List<TradingPreflightReason> blockers = new ArrayList<>();
        blockers.add(blocker("LIVE_DISABLED", "LIVE is disabled; real trading cannot be authorized."));
        blockers.add(blocker(
                "REAL_PROVIDER_NOT_IMPLEMENTED",
                "Real provider / RealClient is not implemented for current GateP Batch 4 baseline."
        ));
        blockers.add(blocker(
                "PRIVATE_TRADING_NOT_IMPLEMENTED",
                "Private trading adapter is not implemented; order/cancel/transfer/withdraw remain forbidden."
        ));
        if (account == null) {
            blockers.add(blocker(
                    "ACCOUNT_UNCONFIGURED",
                    "No owned exchange account metadata is configured for the requested venue."
            ));
        } else {
            if (!requestedExchangeCode.equalsIgnoreCase(account.exchangeCode())) {
                blockers.add(blocker(
                        "ACCOUNT_EXCHANGE_MISMATCH",
                        "Requested exchange does not match the selected account exchange."
                ));
            }
            if (!"ACTIVE".equalsIgnoreCase(account.status())) {
                blockers.add(blocker("ACCOUNT_DISABLED", "Selected exchange account is not ACTIVE."));
            }
            if ("LIVE".equalsIgnoreCase(account.tradeEnv())) {
                blockers.add(blocker(
                        "ACCOUNT_LIVE_ENV_BLOCKED",
                        "Selected account is LIVE, but LIVE trading remains disabled."
                ));
            }
        }
        if (activeCredentials.isEmpty()) {
            blockers.add(blocker(
                    "CREDENTIAL_UNCONFIGURED",
                    "No ACTIVE credential metadata is configured for the selected account."
            ));
        }
        blockers.add(blocker(
                "PERMISSION_PROBE_NOT_IMPLEMENTED",
                "Real permission probe is not implemented; no credential permission can be treated as verified."
        ));
        if (!"OK".equals(dataQualityOverview.qualityStatus().name())) {
            blockers.add(blocker(
                    "DATA_QUALITY_NOT_OK",
                    "Marketdata data quality is diagnostic-only and currently not OK for the requested scope."
            ));
        }
        return blockers;
    }

    private List<TradingPreflightReason> warnings(MarketdataQualityOverview dataQualityOverview) {
        List<TradingPreflightReason> warnings = new ArrayList<>();
        warnings.add(warning(
                "DATA_QUALITY_DIAGNOSTIC_ONLY",
                "Data quality status is diagnostic-only and never represents trading authorization."
        ));
        warnings.add(warning(
                "RISK_PREFLIGHT_READONLY",
                "Risk preflight is a read-only baseline; it does not approve or simulate any trade."
        ));
        warnings.add(warning(
                "DATA_QUALITY_STATUS_" + dataQualityOverview.qualityStatus().name(),
                "Current data quality status is " + dataQualityOverview.qualityStatus().name() + "."
        ));
        return warnings;
    }

    private List<String> requiredNextSteps(boolean accountConfigured, boolean credentialMissing) {
        List<String> steps = new ArrayList<>();
        if (!accountConfigured) {
            steps.add("Configure a scoped exchange account metadata record for the single-venue candidate.");
        }
        if (credentialMissing) {
            steps.add("Configure ACTIVE credential metadata without exposing credential material.");
        }
        steps.add("Design and review a credential-material-free real permission probe in a separate gated task.");
        steps.add("Implement real provider / private trading only after separate Gate authorization.");
        steps.add("Keep LIVE disabled until explicit LIVE authorization, rollout and rollback review are complete.");
        return steps;
    }

    private String permissionProbeStatus(List<ExchangeAccountCredentialSummary> activeCredentials) {
        if (activeCredentials.isEmpty()) {
            return "PERMISSION_PROBE_NOT_AVAILABLE";
        }
        boolean anySkipped = activeCredentials.stream()
                .anyMatch(summary -> "SKIPPED".equalsIgnoreCase(summary.permissionProbeStatus()));
        return anySkipped ? "PERMISSION_PROBE_SKIPPED" : "PERMISSION_PROBE_NOT_IMPLEMENTED";
    }

    private String credentialStatus(List<ExchangeAccountCredentialSummary> activeCredentials) {
        if (activeCredentials.isEmpty()) {
            return "CREDENTIAL_UNCONFIGURED";
        }
        if (activeCredentials.size() > 1) {
            return "CREDENTIAL_STATUS_UNKNOWN";
        }
        return emptyToUnknownCredentialStatus(activeCredentials.getFirst().credentialStatus());
    }

    private Long requirePositiveOwnerUserId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("ownerUserId must be positive");
        }
        return value;
    }

    private String normalizeOrDefault(String value, String defaultValue) {
        String normalized = normalizeNullable(value);
        return normalized == null ? defaultValue : normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String emptyToUnknownCredentialStatus(String value) {
        return value == null || value.isBlank() ? "CREDENTIAL_STATUS_UNKNOWN" : value;
    }

    private TradingPreflightReason blocker(String code, String message) {
        return new TradingPreflightReason(code, "BLOCKER", message);
    }

    private TradingPreflightReason warning(String code, String message) {
        return new TradingPreflightReason(code, "WARNING", message);
    }
}
