package com.guidinglight.nexusquant.ledger.application.service;

import com.guidinglight.nexusquant.contracts.model.LedgerDirection;
import com.guidinglight.nexusquant.ledger.contracts.model.AccountSnapshotProjection;
import com.guidinglight.nexusquant.ledger.contracts.model.LedgerPostingEntry;
import com.guidinglight.nexusquant.ledger.service.port.LedgerPostingRepository;
import com.guidinglight.nexusquant.ledger.service.port.SimCashFundingPort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 仅给新建、隔离的 PAPER 账户记入一次合成 USDT 预算及对手分录。 */
@Service
public class SimCashFundingService implements SimCashFundingPort {
    private final LedgerPostingRepository repository;

    public SimCashFundingService(LedgerPostingRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    @Transactional(timeout = 5)
    public BigDecimal fundOnce(long accountId, String paperRunId, BigDecimal budget, String traceId) {
        if (accountId <= 0 || paperRunId == null || paperRunId.isBlank()
                || budget == null || budget.signum() <= 0 || budget.scale() > 8
                || traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("invalid isolated SIM funding request");
        }
        repository.lockSnapshotCurrencies(accountId, List.of("USDT"));
        String cashKey = paperRunId + ":SIM_FUND:CASH";
        String contraKey = paperRunId + ":SIM_FUND:CONTRA";
        boolean cashExists = repository.existsByIdempotencyKey(cashKey);
        boolean contraExists = repository.existsByIdempotencyKey(contraKey);
        if (cashExists != contraExists) {
            throw new IllegalStateException("INCOMPLETE_SIM_FUNDING");
        }
        if (cashExists) {
            BigDecimal current = repository.currentBalance(accountId, "USDT");
            if (current.signum() < 0) throw new IllegalStateException("NEGATIVE_SIM_CASH");
            return current;
        }
        if (repository.currentBalance(accountId, "USDT").signum() != 0) {
            throw new IllegalStateException("SIM_FUNDING_REQUIRES_EMPTY_ACCOUNT");
        }
        Instant now = Instant.now();
        insert(accountId, budget, "SIM_FUNDING_CASH", cashKey, paperRunId, traceId, now);
        insert(accountId, budget.negate(), "SIM_FUNDING_CONTRA", contraKey, paperRunId, traceId, now);
        repository.insertAccountSnapshot(new AccountSnapshotProjection(accountId, "USDT", budget,
                budget, BigDecimal.ZERO, now, traceId));
        return budget;
    }

    @Override
    public BigDecimal cashBalance(long accountId) {
        return repository.currentBalance(accountId, "USDT");
    }

    private void insert(long accountId, BigDecimal delta, String refType, String key,
                        String paperRunId, String traceId, Instant now) {
        String entryId = "le-" + UUID.randomUUID();
        BigDecimal balanceAfter = "SIM_FUNDING_CONTRA".equals(refType)
                ? repository.currentBalance(accountId, "USDT")
                : repository.currentBalance(accountId, "USDT").add(delta);
        repository.insertEntry(new LedgerPostingEntry(entryId, accountId, "USDT", delta,
                balanceAfter,
                delta.signum() >= 0 ? LedgerDirection.CREDIT : LedgerDirection.DEBIT,
                refType, paperRunId, key, traceId, now));
        repository.insertLedgerEvent(entryId, "POSTED", "{\"source\":\"STRATEGY_SIM_SIM_FUNDING\"}", traceId);
    }
}
