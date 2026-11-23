package com.guidinglight.nexusquant.account.infra.okx.readonly;

import com.guidinglight.nexusquant.account.application.exception.AccountFeeSyncException;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.fee.AccountFeeQuote;
import com.guidinglight.nexusquant.account.domain.port.AccountFeeReadPort;
import com.guidinglight.nexusquant.adapter.okx.auth.OkxIpAddressNormalizer;
import com.guidinglight.nexusquant.adapter.okx.auth.OkxIpAllowlistStatus;
import com.guidinglight.nexusquant.adapter.okx.auth.OkxPrivateEnvironment;
import com.guidinglight.nexusquant.adapter.okx.privateread.error.OkxPrivateReadException;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateReadOperation;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateReadRequest;
import com.guidinglight.nexusquant.livecontrol.deployment.model.ScopedCredentialCapability;
import com.guidinglight.nexusquant.livecontrol.deployment.model.ScopedCredentialReference;
import com.guidinglight.nexusquant.livecontrol.deployment.policy.ScopedCredentialCapabilityPolicy;
import com.guidinglight.nexusquant.risk.domain.model.KillSwitchSnapshot;
import com.guidinglight.nexusquant.risk.domain.model.KillSwitchStatus;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;

import java.time.Clock;
import java.util.Objects;
import java.util.Set;

/** 只调用固定账户配置GET与费率GET；配置限定owner/账户/凭据/标的，禁止隐式扩大读取范围。 */
public final class OkxAccountFeeReadPort implements AccountFeeReadPort {
    private final OkxPrivateCredentialExecutor executor;
    private final KillSwitchService kill;
    private final ScopedCredentialCapabilityPolicy policy;
    private final ScopedCredentialCapabilityPolicy.PermissionScope permission;
    private final Clock clock;
    private final String expectedIp;
    private final long ownerId, accountId, credentialId;
    private final Set<String> symbols;

    public OkxAccountFeeReadPort(
            OkxPrivateCredentialExecutor executor,
            KillSwitchService kill,
            ScopedCredentialCapabilityPolicy policy,
            ScopedCredentialCapabilityPolicy.PermissionScope permission,
            Clock clock,
            String expectedIp,
            long ownerId,
            long accountId,
            long credentialId,
            Set<String> symbols) {
        this.executor = Objects.requireNonNull(executor);
        this.kill = Objects.requireNonNull(kill);
        this.policy = Objects.requireNonNull(policy);
        this.permission = Objects.requireNonNull(permission);
        this.clock = Objects.requireNonNull(clock);
        this.expectedIp = OkxIpAddressNormalizer.normalizeLiteral(expectedIp);
        this.ownerId = ownerId;
        this.accountId = accountId;
        this.credentialId = credentialId;
        this.symbols = Set.copyOf(symbols);
        if (expectedIp == null
                || expectedIp.isBlank()
                || ownerId <= 0
                || accountId <= 0
                || credentialId <= 0
                || symbols.isEmpty()
                || symbols.size() > 20
                || symbols.stream()
                        .anyMatch(symbol -> !symbol.matches("[A-Z0-9]{1,20}-[A-Z0-9]{1,20}")))
            throw new IllegalArgumentException("fee private read scope is invalid");
    }

    /** 只校验显式配置范围；不解密、不请求供应方，真正读取时还要核验凭据、远端权限及IP。 */
    @Override
    public boolean supports(ExchangeAccountSummary account, String symbol) {
        return account != null
                && Objects.equals(account.ownerUserId(), ownerId)
                && Objects.equals(account.exchangeAccountId(), accountId)
                && "OKX".equals(account.exchangeCode())
                && "ACTIVE".equals(account.status())
                && Set.of("SIM", "LIVE").contains(account.tradeEnv())
                && symbols.contains(symbol);
    }

    /** 沿用即时凭据执行器，只执行配置与费率GET；逐次核验同一ENGAGED状态，任一护栏变化即拒绝冻结。 */
    @Override
    public AccountFeeQuote read(
            ExchangeAccountSummary account,
            ExchangeAccountCredentialSummary credential,
            String symbol) {
        if (!supports(account, symbol)
                || credential == null
                || !Objects.equals(credential.credentialId(), credentialId)
                || !Objects.equals(credential.exchangeAccountId(), accountId)
                || !"OKX_API_V5".equals(credential.credentialType()))
            throw denied("FEE_PRIVATE_SCOPE_MISMATCH");
        var start = clock.instant();
        var initial = kill.snapshot();
        requireKill(initial);
        var reference =
                ScopedCredentialReference.fromSummary(
                        ownerId,
                        "OKX_SPOT",
                        ScopedCredentialCapability.PRIVATE_READONLY_DIAGNOSTIC,
                        credential);
        var decision = policy.evaluate(reference, start, permission);
        if (decision.status() != ScopedCredentialCapabilityPolicy.Status.ALLOWED)
            throw denied("FEE_CREDENTIAL_POLICY_DENIED");
        // 交易环境仍只有SIM/LIVE；供应方DEMO只映射SIM，不形成新交易权限或额外环境。
        var environment =
                "SIM".equals(account.tradeEnv())
                        ? OkxPrivateEnvironment.DEMO
                        : OkxPrivateEnvironment.PRODUCTION;
        try {
            return executor.withActiveCredential(
                    ownerId,
                    accountId,
                    credentialId,
                    "OKX_API_V5",
                    session -> {
                        requireKill(initial);
                        var configuration =
                                session.execute(
                                        OkxPrivateReadRequest.accountConfiguration(expectedIp),
                                        environment);
                        // 远端权限集合必须与授权范围精确一致；不能因是只读GET而接受额外权限或缺失IP限制。
                        Set<String> required =
                                permission == ScopedCredentialCapabilityPolicy.PermissionScope.TRADE
                                        ? Set.of("READ_ONLY", "TRADE")
                                        : Set.of("READ_ONLY");
                        if (configuration.operation()
                                        != OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ
                                || !configuration.complete()
                                || !required.equals(configuration.normalizedPermissions())
                                || !configuration.ipAllowlistConfigured()
                                || configuration.ipAllowlistStatus() != OkxIpAllowlistStatus.MATCHED
                                || configuration.observedAt().isAfter(clock.instant())
                                || configuration.observedAt().isBefore(start.minusSeconds(5))) {
                            throw denied("FEE_REMOTE_PERMISSION_OR_IP_DENIED");
                        }
                        requireKill(initial);
                        var result =
                                session.execute(
                                        OkxPrivateReadRequest.spotAccountFee(symbol), environment);
                        requireKill(initial);
                        if (result.operation() != OkxPrivateReadOperation.OKX_SPOT_ACCOUNT_FEE_READ
                                || !result.complete()
                                || result.fee() == null
                                || !symbol.equals(result.fee().instrumentId()))
                            throw denied("FEE_SOURCE_INCOMPLETE");
                        return new AccountFeeQuote(
                                result.fee().makerRate(),
                                result.fee().takerRate(),
                                result.fee().tier(),
                                result.fee().providerTimestamp(),
                                result.observedAt());
                    });
        } catch (AccountFeeSyncException ex) {
            throw ex;
        } catch (OkxPrivateReadException ex) {
            throw denied("FEE_PRIVATE_READ_" + ex.category().name());
        } catch (RuntimeException ex) {
            throw denied("FEE_PRIVATE_READ_FAILED");
        }
    }

    /** 比较状态、版本和来源身份，防止读取途中解除或重新设置kill switch后被误认仍是同一安全状态。 */
    private void requireKill(KillSwitchSnapshot initial) {
        var current = kill.snapshot();
        if (initial == null
                || current == null
                || initial.status() != KillSwitchStatus.ENGAGED
                || current.status() != KillSwitchStatus.ENGAGED
                || initial.version() != current.version()
                || initial.scope() != current.scope()
                || !Objects.equals(initial.updatedAt(), current.updatedAt())
                || !Objects.equals(initial.source(), current.source()))
            throw denied("FEE_KILL_STATE_CHANGED");
    }

    private static AccountFeeSyncException denied(String reason) {
        return new AccountFeeSyncException(reason);
    }
}
