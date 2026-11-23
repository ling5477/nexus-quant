package com.guidinglight.nexusquant.research.domain.paper.port;

import java.math.BigDecimal;

/** 复用策略 SIM 的只读估值口径，供旧详情摘要显示同一净盈亏。 */
public interface PaperRunCanonicalEconomicsQuery {
    BigDecimal pnl(String paperRunId);
}
