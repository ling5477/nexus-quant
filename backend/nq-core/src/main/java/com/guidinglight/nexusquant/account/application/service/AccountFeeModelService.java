package com.guidinglight.nexusquant.account.application.service;

import com.guidinglight.nexusquant.account.domain.fee.AccountFeeObservation;
import com.guidinglight.nexusquant.account.domain.fee.AccountFeeSourceIdentity;
import com.guidinglight.nexusquant.account.domain.fee.FrozenAccountFeeModel;
import com.guidinglight.nexusquant.account.domain.fee.FrozenDirectionalFeeModel;
import com.guidinglight.nexusquant.account.domain.port.AccountFeeRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.Policy;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.Precision;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** 从不可变观察解析模型；不解密、不同步或联网，冻结后过期只影响新选择，不替换历史运行。 */
public class AccountFeeModelService {
    private final AccountFeeRepository observations;
    private final ExchangeAccountRepository accounts;
    private final ExchangeAccountCredentialRepository credentials;

    public AccountFeeModelService(
            AccountFeeRepository observations,
            ExchangeAccountRepository accounts,
            ExchangeAccountCredentialRepository credentials) {
        this.observations = observations;
        this.accounts = accounts;
        this.credentials = credentials;
    }

    /** 新模型选择在一致读快照内检查当前账户、凭据、新鲜度和确切市场；市场单固定使用taker成本符号。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public FrozenAccountFeeModel select(
            long owner,
            UUID id,
            long account,
            String exchange,
            String env,
            String symbol,
            Instant at) {
        AccountFeeObservation row = scalarObservation(owner, id);
        context(row, account, exchange, env, symbol);
        var current =
                accounts.findByIdForOwner(owner, account)
                        .filter(
                                value ->
                                        "ACTIVE".equals(value.status())
                                                && exchange.equals(value.exchangeCode())
                                                && env.equals(value.tradeEnv()))
                        .orElseThrow(() -> invalid("FEE_MODEL_ACCOUNT_UNAVAILABLE"));
        credentials
                .findByCredentialIdForOwner(owner, current.exchangeAccountId(), row.credentialId())
                .filter(value -> value.isActive() && "ACTIVE".equals(value.credentialStatus()))
                .orElseThrow(() -> invalid("FEE_MODEL_CREDENTIAL_UNAVAILABLE"));
        Instant frozen = at.truncatedTo(ChronoUnit.MICROS);
        if (frozen.isBefore(row.quote().observedAt()) || !frozen.isBefore(row.expiresAt()))
            throw invalid("FEE_MODEL_OBSERVATION_EXPIRED");
        return new FrozenAccountFeeModel(row, frozen);
    }

    /** 历史复核仅绑定原观察与原冻结时点；后续轮换、停用或过期不改写已经采用的研究模型。 */
    @Transactional(readOnly = true, timeout = 5)
    public FrozenAccountFeeModel historical(
            long owner,
            UUID id,
            long account,
            String exchange,
            String env,
            String symbol,
            Instant frozen) {
        AccountFeeObservation row = scalarObservation(owner, id);
        context(row, account, exchange, env, symbol);
        if (frozen.isBefore(row.quote().observedAt()) || !frozen.isBefore(row.expiresAt()))
            throw invalid("FEE_MODEL_FREEZE_TIME_INVALID");
        return new FrozenAccountFeeModel(row, frozen);
    }

    /** 方向来源仍从持久观察解析；优惠开关只表示收费条件，执行端必须另外证明资金、预留和历史换算来源。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public FrozenDirectionalFeeModel selectDirectional(
            long owner,
            UUID id,
            long account,
            String exchange,
            String env,
            String symbol,
            Instant at,
            Policy policy) {
        AccountFeeObservation row = observation(owner, id);
        context(row, account, exchange, env, symbol);
        accounts.findByIdForOwner(owner, account)
                .filter(
                        value ->
                                "ACTIVE".equals(value.status())
                                        && exchange.equals(value.exchangeCode())
                                        && env.equals(value.tradeEnv()))
                .orElseThrow(() -> invalid("FEE_MODEL_ACCOUNT_UNAVAILABLE"));
        credentials
                .findByCredentialIdForOwner(owner, account, row.credentialId())
                .filter(
                        value ->
                                value.isActive()
                                        && "ACTIVE".equals(value.credentialStatus())
                                        && "VERIFIED".equals(value.verificationStatus())
                                        && value.revokedAt() == null
                                        && value.rotatedAt() == null)
                .orElseThrow(() -> invalid("FEE_MODEL_CREDENTIAL_UNAVAILABLE"));
        Instant frozen = at.truncatedTo(ChronoUnit.MICROS);
        if (frozen.isBefore(row.quote().observedAt()) || !frozen.isBefore(row.expiresAt()))
            throw invalid("FEE_MODEL_OBSERVATION_EXPIRED");
        return directional(row, frozen, policy);
    }

    /** 历史方向快照只复核原身份；账户停用、凭据撤销和观察过期不替换已采用的模型。 */
    @Transactional(readOnly = true, timeout = 5)
    public FrozenDirectionalFeeModel historicalDirectional(
            long owner,
            UUID id,
            long account,
            String exchange,
            String env,
            String symbol,
            Instant frozen,
            Policy policy) {
        AccountFeeObservation row = observation(owner, id);
        context(row, account, exchange, env, symbol);
        if (frozen.isBefore(row.quote().observedAt()) || !frozen.isBefore(row.expiresAt()))
            throw invalid("FEE_MODEL_FREEZE_TIME_INVALID");
        return directional(row, frozen, policy);
    }

    private static FrozenDirectionalFeeModel directional(
            AccountFeeObservation row, Instant frozen, Policy policy) {
        if (policy != Policy.RECEIVED_ASSET && policy != Policy.DISCOUNT_ASSET_IF_FUNDED)
            throw invalid("FEE_MODEL_PAYMENT_POLICY_INVALID");
        return new FrozenDirectionalFeeModel(row, frozen, policy, Precision.canonical());
    }

    private AccountFeeObservation observation(long owner, UUID id) {
        var row =
                observations
                        .findObservation(owner, id)
                        .orElseThrow(() -> invalid("FEE_MODEL_OBSERVATION_NOT_FOUND"));
        if (row.ownerUserId() != owner
                || !AccountFeeSourceIdentity.digest(row).equals(row.sourceSha256()))
            throw invalid("FEE_MODEL_SOURCE_IDENTITY_INVALID");
        return row;
    }

    private AccountFeeObservation scalarObservation(long owner, UUID id) {
        var row = observation(owner, id);
        // V1入口继续拒绝commission，方向来源只能经过独立V2校验，不得冒用旧标量合同。
        if (row.quote().commissionTerms() != null)
            throw invalid("FEE_MODEL_DIRECTIONAL_CONSUMPTION_REQUIRED");
        if (row.quote().takerCostRate().abs().compareTo(BigDecimal.ONE) >= 0)
            throw invalid("FEE_MODEL_RATE_INVALID");
        return row;
    }

    private static void context(
            AccountFeeObservation row, long account, String exchange, String env, String symbol) {
        if (row.exchangeAccountId() != account
                || !row.exchangeCode().equals(exchange)
                || !row.tradeEnv().equals(env)
                || !row.symbol().equals(symbol)) throw invalid("FEE_MODEL_SOURCE_SCOPE_MISMATCH");
    }

    private static IllegalArgumentException invalid(String reason) {
        return new IllegalArgumentException(reason);
    }
}
