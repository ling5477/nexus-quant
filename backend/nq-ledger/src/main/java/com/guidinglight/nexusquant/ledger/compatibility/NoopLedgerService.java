package com.guidinglight.nexusquant.ledger.compatibility;

import com.guidinglight.nexusquant.ledger.model.LedgerEntry;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * NoopLedgerService 提供无副作用占位实现。
 *
 * Why:
 * 该类仅供本地和测试环境兜底使用。
 * 它保留是为了兼容本地/验证环境，不代表正式账本主链能力，后续搜索或规划不得把它当作生产实现。
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
