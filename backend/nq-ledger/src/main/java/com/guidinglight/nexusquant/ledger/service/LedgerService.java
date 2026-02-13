package com.guidinglight.nexusquant.ledger.service;

import com.guidinglight.nexusquant.ledger.model.LedgerEntry;
import java.math.BigDecimal;
import java.util.List;

/**
 * LedgerService 定义账本写入与重算能力。
 */
public interface LedgerService {

    /**
     * 追加账本流水。
     *
     * @param entry 不可变流水
     */
    void append(LedgerEntry entry);

    /**
     * 按账户和币种重算余额。
     *
     * @param accountId 账户 ID
     * @param currency 币种
     * @return 聚合余额
     */
    BigDecimal rebuildBalance(Long accountId, String currency);

    /**
     * 拉取某账户账本流水（占位）。
     */
    List<LedgerEntry> queryEntries(Long accountId, String currency);
}
