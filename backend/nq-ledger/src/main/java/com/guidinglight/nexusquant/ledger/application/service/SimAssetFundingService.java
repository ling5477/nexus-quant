package com.guidinglight.nexusquant.ledger.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.contracts.model.LedgerDirection;
import com.guidinglight.nexusquant.ledger.contracts.model.AccountBalanceBasis;
import com.guidinglight.nexusquant.ledger.contracts.model.AccountSnapshotProjection;
import com.guidinglight.nexusquant.ledger.contracts.model.LedgerPostingEntry;
import com.guidinglight.nexusquant.ledger.contracts.model.SimAssetFundingPlan;
import com.guidinglight.nexusquant.ledger.service.port.LedgerPostingRepository;
import com.guidinglight.nexusquant.ledger.service.port.SimAssetFundingPort;
import com.guidinglight.nexusquant.ledger.service.port.SimAssetFundingRepository;
import com.guidinglight.nexusquant.ledger.service.port.SimAssetFundingRepository.Receipt;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** 总 USDT 预算按冻结换算拆分；分录、事件、快照和身份在同一事务中成功或回滚。 */
@Service
public class SimAssetFundingService implements SimAssetFundingPort {
    private final LedgerPostingRepository ledger;
    private final SimAssetFundingRepository funding;
    private final ObjectMapper mapper;

    public SimAssetFundingService(
            LedgerPostingRepository ledger,
            SimAssetFundingRepository funding,
            ObjectMapper mapper) {
        this.ledger = Objects.requireNonNull(ledger);
        this.funding = Objects.requireNonNull(funding);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    @Transactional(timeout = 10)
    public SimAssetFundingPlan fundOnce(
            long accountId, String paperRunId, SimAssetFundingPlan plan, String traceId) {
        validateIdentity(accountId, paperRunId);
        if (plan == null || traceId == null || traceId.isBlank() || traceId.length() > 64) {
            throw new IllegalArgumentException("INVALID_SIM_ASSET_FUNDING_REQUEST");
        }
        validateSourceObject(plan.sourceJson());
        // 沿用 canonical 账户锁及币种锁，覆盖空账户首次初始化和并发成交，不使用 JVM 互斥。
        ledger.lockSnapshotCurrencies(
                accountId, List.of(plan.quoteCurrency(), plan.assetCurrency()));
        ledger.assertAccountEnvironment(accountId, "SIM");
        Optional<Receipt> existing = funding.findReceipt(accountId, paperRunId);
        funding.assertBoundSimAccount(accountId, paperRunId, existing.isEmpty());
        if (existing.isPresent()) {
            Receipt receipt = existing.get();
            if (!receipt.plan().equals(plan))
                throw new IllegalStateException("SIM_ASSET_FUNDING_IDENTITY_DRIFT");
            // 重放核对原分录完整性，不能把已成交余额重置成初始预算或补造部分分录。
            funding.assertCompletePosting(receipt);
            return receipt.plan();
        }
        funding.assertEmptyAccount(accountId);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Receipt receipt =
                new Receipt(
                        accountId,
                        paperRunId,
                        plan,
                        now,
                        traceId,
                        entryId(),
                        entryId(),
                        entryId(),
                        entryId());
        postCurrency(
                receipt,
                plan.quoteCurrency(),
                plan.cashBudget(),
                receipt.cashEntryId(),
                receipt.cashContraEntryId());
        postCurrency(
                receipt,
                plan.assetCurrency(),
                plan.assetQuantity(),
                receipt.assetEntryId(),
                receipt.assetContraEntryId());
        funding.insertReceipt(receipt);
        funding.assertCompletePosting(receipt);
        return plan;
    }

    @Override
    public BigDecimal assetBalance(long accountId, String currency) {
        if (accountId <= 0 || currency == null || !currency.matches("[A-Z0-9]{2,20}")) {
            throw new IllegalArgumentException("INVALID_SIM_ASSET_BALANCE_REQUEST");
        }
        ledger.assertAccountEnvironment(accountId, "SIM");
        return ledger.currentBalance(accountId, currency, "SIM");
    }

    @Override
    public Optional<SimAssetFundingPlan> findPlan(long accountId, String paperRunId) {
        validateIdentity(accountId, paperRunId);
        return funding.findReceipt(accountId, paperRunId).map(Receipt::plan);
    }

    private void postCurrency(
            Receipt receipt, String currency, BigDecimal amount, String cashId, String contraId) {
        postEntry(receipt, currency, amount, cashId, "CASH", "SIM_FUNDING_CASH", amount);
        postEntry(
                receipt,
                currency,
                amount.negate(),
                contraId,
                "CONTRA",
                "SIM_FUNDING_CONTRA",
                amount);
        ledger.insertAccountSnapshot(
                new AccountSnapshotProjection(
                        receipt.accountId(),
                        currency,
                        amount,
                        amount,
                        BigDecimal.ZERO,
                        receipt.fundedAt(),
                        receipt.traceId(),
                        "SIM",
                        AccountBalanceBasis.LEDGER_CASH_PROJECTION));
    }

    private void postEntry(
            Receipt receipt,
            String currency,
            BigDecimal delta,
            String id,
            String role,
            String refType,
            BigDecimal balance) {
        ledger.insertEntry(
                new LedgerPostingEntry(
                        id,
                        receipt.accountId(),
                        currency,
                        delta,
                        balance,
                        delta.signum() >= 0 ? LedgerDirection.CREDIT : LedgerDirection.DEBIT,
                        refType,
                        receipt.paperRunId(),
                        receipt.paperRunId() + ":SIM_FUND:" + currency + ":" + role,
                        receipt.traceId(),
                        receipt.fundedAt()));
        String payload =
                mapper.createObjectNode()
                        .put("source", "STRATEGY_SIM_ASSET_FUNDING")
                        .put("sourceSha256", receipt.plan().sourceSha256())
                        .put("paperRunId", receipt.paperRunId())
                        .put("currency", currency)
                        .put("role", role)
                        .toString();
        ledger.insertLedgerEvent(id, "POSTED", payload, receipt.traceId());
    }

    private void validateSourceObject(String json) {
        try {
            JsonNode source = mapper.readTree(json);
            if (source == null || !source.isObject() || source.isEmpty()) {
                throw new IllegalArgumentException("SIM_ASSET_FUNDING_SOURCE_OBJECT_REQUIRED");
            }
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("SIM_ASSET_FUNDING_SOURCE_OBJECT_REQUIRED", failure);
        }
    }

    private static void validateIdentity(long accountId, String run) {
        if (accountId <= 0 || run == null || run.isBlank() || run.length() > 64) {
            throw new IllegalArgumentException("INVALID_SIM_ASSET_FUNDING_IDENTITY");
        }
    }

    private static String entryId() {
        return "le-" + UUID.randomUUID();
    }
}
