package com.guidinglight.nexusquant.ledger.service;

import com.guidinglight.nexusquant.ledger.model.LedgerEntry;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * NoopLedgerService 提供无副作用占位实现。
 *
 * Why:
 * Gate A 只冻结账本接口，不执行真实记账。
 */
public class NoopLedgerService implements LedgerService {

    @Override
    public void append(LedgerEntry entry) {
        // Gate A 占位：不执行持久化。
    }

    @Override
    public BigDecimal rebuildBalance(Long accountId, String currency) {
        return BigDecimal.ZERO;
    }

    @Override
    public List<LedgerEntry> queryEntries(Long accountId, String currency) {
        return Collections.emptyList();
    }
}
