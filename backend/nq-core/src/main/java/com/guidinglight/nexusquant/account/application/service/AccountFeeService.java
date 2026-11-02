package com.guidinglight.nexusquant.account.application.service;

import com.guidinglight.nexusquant.account.application.exception.AccountFeeSyncException;
import com.guidinglight.nexusquant.account.application.exception.ExchangeAccountNotFoundException;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.fee.AccountFeeObservation;
import com.guidinglight.nexusquant.account.domain.fee.AccountFeeSourceIdentity;
import com.guidinglight.nexusquant.account.domain.port.AccountFeeReadPort;
import com.guidinglight.nexusquant.account.domain.port.AccountFeeRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;

import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 同一同步身份只生成一份不可变证据；重放不重新访问供应方，读取不触发同步。 */
public class AccountFeeService {
    private final ExchangeAccountRepository accounts;
    private final ExchangeAccountCredentialRepository credentials;
    private final AccountFeeRepository repository;
    private final AccountFeeReadPort source;
    private final Clock clock;

    public AccountFeeService(
            ExchangeAccountRepository accounts,
            ExchangeAccountCredentialRepository credentials,
            AccountFeeRepository repository,
            AccountFeeReadPort source,
            Clock clock) {
        this.accounts = accounts;
        this.credentials = credentials;
        this.repository = repository;
        this.source = source;
        this.clock = clock;
    }

    /** 只读当前账户和标的的观察历史；最多50条，能力查询不联网，历史记录不代表过去区间的真实费率。 */
    @Transactional(readOnly = true, timeout = 5)
    public History history(long owner, long accountId, String symbol, UUID before, int limit) {
        validateSymbol(symbol);
        if (limit < 1 || limit > 50)
            throw new IllegalArgumentException("fee history limit must be 1..50");
        var account = account(owner, accountId);
        var rows = repository.history(owner, accountId, symbol, before, limit + 1);
        boolean more = rows.size() > limit;
        var page = rows.stream().limit(limit).toList();
        return new History(
                page,
                more,
                more ? page.getLast().observationId() : null,
                source.supports(account, symbol),
                clock.instant(),
                "CURRENT_OBSERVATION_NOT_HISTORICAL_FEE");
    }

    /** 查询原同步身份的已提交结果；空值包括仍在处理的情况，调用方不得据此自动发起新同步。 */
    @Transactional(readOnly = true, timeout = 5)
    public Optional<AccountFeeObservation> result(long owner, long accountId, UUID operationId) {
        account(owner, accountId);
        return repository
                .findOperation(owner, operationId)
                .filter(row -> row.exchangeAccountId() == accountId);
    }

    /** 在有界事务及身份锁内同步；相同输入返回原观察、不同输入冲突，来源失败或凭据变化时不写成功回执。 */
    @Transactional(timeout = 25)
    public AccountFeeObservation sync(
            long owner, long accountId, UUID operationId, long credentialId, String symbol) {
        validateSymbol(symbol);
        if (operationId == null || credentialId <= 0)
            throw new IllegalArgumentException("fee sync identity is invalid");
        account(owner, accountId);
        String input =
                sha256(
                        "NQ/ACCOUNT_FEE_SYNC/1|"
                                + owner
                                + "|"
                                + accountId
                                + "|"
                                + credentialId
                                + "|"
                                + symbol);
        repository.lock(owner, accountId, operationId);
        // 先查稳定身份再访问供应方；回执过期也应原样重放，不能把旧操作变成新查询。
        var prior = repository.findOperation(owner, operationId);
        if (prior.isPresent()) {
            if (!input.equals(prior.get().inputFingerprint()))
                throw new AccountFeeSyncException("FEE_SYNC_IDENTITY_CONFLICT");
            return prior.get();
        }
        var account = account(owner, accountId);
        if (!"ACTIVE".equals(account.status()) || !source.supports(account, symbol))
            throw new AccountFeeSyncException("PRIVATE_FEE_READ_DISABLED");
        var credential =
                credentials
                        .findByCredentialIdForOwner(owner, accountId, credentialId)
                        .filter(row -> row.isActive() && "ACTIVE".equals(row.credentialStatus()))
                        .orElseThrow(
                                () -> new AccountFeeSyncException("FEE_CREDENTIAL_NOT_ACTIVE"));
        var quote = source.read(account, credential, symbol);
        // 兼容旧生命周期入口；读取期间凭据被撤销或权限变化时不冻结已失效的来源。
        if (!credentials
                        .findByCredentialIdForOwner(owner, accountId, credentialId)
                        .filter(credential::equals)
                        .isPresent()
                || !account.equals(account(owner, accountId)))
            throw new AccountFeeSyncException("FEE_SOURCE_CONTEXT_CHANGED");
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (quote.observedAt().isAfter(now) || quote.observedAt().isBefore(now.minusSeconds(30)))
            throw new AccountFeeSyncException("FEE_SOURCE_OBSERVATION_STALE");
        // 摘要绑定精确费率、账户/凭据/环境和来源时点；五分钟有效期只描述本次观察的新鲜度。
        if (quote.commissionTerms() == null
                ? !"OKX".equals(account.exchangeCode())
                : !"BINANCE".equals(account.exchangeCode())
                        || !symbol.replace("-", "").equals(quote.commissionTerms().nativeSymbol()))
            throw new AccountFeeSyncException("FEE_SOURCE_SCOPE_MISMATCH");
        var unsigned =
                new AccountFeeObservation(
                        UUID.randomUUID(),
                        operationId,
                        owner,
                        accountId,
                        credentialId,
                        account.exchangeCode(),
                        account.tradeEnv(),
                        symbol,
                        input,
                        quote,
                        quote.observedAt().plusSeconds(300),
                        "");
        var observation =
                new AccountFeeObservation(
                        unsigned.observationId(),
                        operationId,
                        owner,
                        accountId,
                        credentialId,
                        account.exchangeCode(),
                        account.tradeEnv(),
                        symbol,
                        input,
                        quote,
                        unsigned.expiresAt(),
                        AccountFeeSourceIdentity.digest(unsigned));
        repository.save(observation);
        return observation;
    }

    private ExchangeAccountSummary account(long owner, long id) {
        return accounts.findByIdForOwner(owner, id)
                .orElseThrow(() -> new ExchangeAccountNotFoundException(id));
    }

    public static void validateSymbol(String symbol) {
        if (symbol == null || !symbol.matches("[A-Z0-9]{1,20}-[A-Z0-9]{1,20}"))
            throw new IllegalArgumentException("fee symbol is invalid");
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    /** 分页与来源装配状态分别返回；没有同步能力不抹掉已有证据，也不把空历史当作零费率。 */
    public record History(
            List<AccountFeeObservation> items,
            boolean hasMore,
            UUID nextBefore,
            boolean synchronizationAvailable,
            Instant generatedAt,
            String scope) {}
}
