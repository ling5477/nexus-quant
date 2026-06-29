package com.guidinglight.nexusquant.marketdata.domain.port;

import com.guidinglight.nexusquant.marketdata.domain.MarketdataReadinessBarFacts;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataReadinessIngestionFacts;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataReadinessQuery;

/**
 * MarketdataReadinessRepository exposes local DB aggregate facts for GateM-2E readiness.
 * <p>
 * Why: source health must remain a read-only local aggregation. This port intentionally has no
 * adapter/provider methods, no external network capability and no write operations.
 */
public interface MarketdataReadinessRepository {

    /**
     * Load bounded bar facts for the requested exchange/market/symbol/interval scope.
     *
     * @param query canonical local readiness scope
     * @return aggregate bar facts; empty facts when no bars exist
     */
    MarketdataReadinessBarFacts loadBarFacts(MarketdataReadinessQuery query);

    /**
     * Load sanitized ingestion facts for the requested exchange/market/symbol/interval scope.
     *
     * @param query canonical local readiness scope
     * @return latest success/failure timestamps and derived latency, never raw provider payloads
     */
    MarketdataReadinessIngestionFacts loadIngestionFacts(MarketdataReadinessQuery query);
}
