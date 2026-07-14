package com.guidinglight.nexusquant.trading.application.reconciliation;

import java.util.Objects;

/** 安全诊断 finding；detail 只允许固定、无 provider raw data 的内部说明。 */
public record ReconciliationFinding(
        ReconciliationTaxonomy taxonomy,
        String localOrderReference,
        String exchangeOrderId,
        String clientOrderId,
        String detail
) {
    public ReconciliationFinding {
        Objects.requireNonNull(taxonomy, "taxonomy must not be null");
        detail = detail == null ? "" : detail;
    }
}
