package com.guidinglight.nexusquant.account.application.service;

import com.guidinglight.nexusquant.account.application.exception.ExchangeAccountNotFoundException;

import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCreateCommand;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountUpdateCommand;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.account.domain.port.SimAccountIdentityBridge;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * ExchangeAccountCommandService 提供账户写侧最小闭环。
 * <p>
 * Why:
 * 需要把创建、编辑、启停和默认账户切换统一收口到应用服务，
 * 防止 controller 或前端直接假设默认账户状态，破坏数据库里的唯一默认约束。
 */
public class ExchangeAccountCommandService {

    private final ExchangeAccountRepository exchangeAccountRepository;
    private final SimAccountIdentityBridge simIdentityBridge;
    private final Clock clock;

    public ExchangeAccountCommandService(ExchangeAccountRepository exchangeAccountRepository,
            SimAccountIdentityBridge simIdentityBridge) {
        this(exchangeAccountRepository, simIdentityBridge, Clock.systemUTC());
    }

    ExchangeAccountCommandService(ExchangeAccountRepository exchangeAccountRepository,
            SimAccountIdentityBridge simIdentityBridge, Clock clock) {
        this.exchangeAccountRepository = Objects.requireNonNull(
                exchangeAccountRepository,
                "exchangeAccountRepository must not be null"
        );
        this.simIdentityBridge = Objects.requireNonNull(simIdentityBridge, "simIdentityBridge must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public ExchangeAccountSummary create(Long ownerUserId, ExchangeAccountCreateCommand command) {
        Instant now = Instant.now(clock);
        ExchangeAccountSummary created;
        try {
            created = exchangeAccountRepository.create(
                    requirePositive(ownerUserId, "ownerUserId"),
                    normalizeText(command.exchangeCode(), "exchangeCode").toUpperCase(Locale.ROOT),
                    normalizeTradeEnv(command.tradeEnv()),
                    normalizeText(command.accountAlias(), "accountAlias"),
                    normalizeNullableText(command.externalAccountRef()),
                    now
            );
        } catch (DataIntegrityViolationException ex) {
            throw conflict(ex);
        }
        if (!"SIM".equals(created.tradeEnv())) {
            return created;
        }
        // 创建与兼容身份绑定共用事务，不能向调用方返回无法用于既有 Strategy FK 的半完成账户。
        simIdentityBridge.resolveOrCreateSim(created, TraceIdContext.getOrCreate(), now);
        return requireOwnedAccount(ownerUserId, created.exchangeAccountId());
    }

    /** 仅按当前 owner 定向补齐旧 SIM 账户，不扫描或改写其他账户。 */
    @Transactional
    public ExchangeAccountSummary resolveSimIdentity(Long ownerUserId, Long exchangeAccountId) {
        ExchangeAccountSummary account = requireOwnedAccount(ownerUserId, exchangeAccountId);
        simIdentityBridge.resolveOrCreateSim(account, TraceIdContext.getOrCreate(), Instant.now(clock));
        return requireOwnedAccount(ownerUserId, exchangeAccountId);
    }

    @Transactional
    public ExchangeAccountSummary updateProfile(Long ownerUserId, Long exchangeAccountId, ExchangeAccountUpdateCommand command) {
        requireOwnedAccount(ownerUserId, exchangeAccountId);
        Instant now = Instant.now(clock);
        try {
            boolean updated = exchangeAccountRepository.updateProfile(
                    ownerUserId,
                    exchangeAccountId,
                    normalizeText(command.accountAlias(), "accountAlias"),
                    normalizeNullableText(command.externalAccountRef()),
                    now
            );
            if (!updated) {
                throw new ExchangeAccountNotFoundException(exchangeAccountId);
            }
            return requireOwnedAccount(ownerUserId, exchangeAccountId);
        } catch (DataIntegrityViolationException ex) {
            throw conflict(ex);
        }
    }

    @Transactional
    public ExchangeAccountSummary enable(Long ownerUserId, Long exchangeAccountId) {
        requireOwnedAccount(ownerUserId, exchangeAccountId);
        boolean updated = exchangeAccountRepository.enable(ownerUserId, exchangeAccountId, Instant.now(clock));
        if (!updated) {
            throw new ExchangeAccountNotFoundException(exchangeAccountId);
        }
        return requireOwnedAccount(ownerUserId, exchangeAccountId);
    }

    @Transactional
    public ExchangeAccountSummary disable(Long ownerUserId, Long exchangeAccountId) {
        requireOwnedAccount(ownerUserId, exchangeAccountId);
        boolean updated = exchangeAccountRepository.disable(ownerUserId, exchangeAccountId, Instant.now(clock));
        if (!updated) {
            throw new ExchangeAccountNotFoundException(exchangeAccountId);
        }
        return requireOwnedAccount(ownerUserId, exchangeAccountId);
    }

    @Transactional
    public ExchangeAccountSummary setDefault(Long ownerUserId, Long exchangeAccountId) {
        ExchangeAccountSummary target = requireOwnedAccount(ownerUserId, exchangeAccountId);
        if (!"ACTIVE".equalsIgnoreCase(target.status())) {
            throw new IllegalStateException("disabled account cannot be set as default");
        }
        if (target.isDefault()) {
            return target;
        }
        Instant now = Instant.now(clock);
        exchangeAccountRepository.clearDefaultByScope(ownerUserId, target.exchangeCode(), target.tradeEnv(), now);
        boolean updated = exchangeAccountRepository.markDefault(ownerUserId, exchangeAccountId, now);
        if (!updated) {
            throw new ExchangeAccountNotFoundException(exchangeAccountId);
        }
        return requireOwnedAccount(ownerUserId, exchangeAccountId);
    }

    private ExchangeAccountSummary requireOwnedAccount(Long ownerUserId, Long exchangeAccountId) {
        return exchangeAccountRepository.findByIdForOwner(
                requirePositive(ownerUserId, "ownerUserId"),
                requirePositive(exchangeAccountId, "exchangeAccountId")
        ).orElseThrow(() -> new ExchangeAccountNotFoundException(exchangeAccountId));
    }

    private IllegalStateException conflict(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause() == null ? ex.getMessage() : ex.getMostSpecificCause().getMessage();
        if (message != null && message.contains("uq_exchange_accounts_exchange_env_external_ref_not_null")) {
            return new IllegalStateException("external account ref already exists for exchange/env");
        }
        return new IllegalStateException("exchange account alias already exists for owner/exchange/env");
    }

    private Long requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    private String normalizeTradeEnv(String tradeEnv) {
        String normalized = normalizeText(tradeEnv, "tradeEnv").toUpperCase(Locale.ROOT);
        if (!"SIM".equals(normalized) && !"LIVE".equals(normalized)) {
            throw new IllegalArgumentException("tradeEnv must be SIM or LIVE");
        }
        return normalized;
    }

    private String normalizeText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private String normalizeNullableText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
