package com.guidinglight.nexusquant.marketdata.domain.port;

import com.guidinglight.nexusquant.marketdata.domain.BarProvenance;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 对已返回的最多500根bar读取来源；不采集、不读取凭据、不创建行情事实。 */
@FunctionalInterface
public interface BarProvenanceReadPort {
    Map<Instant, BarProvenance> read(
            String exchange,
            String market,
            String symbol,
            String interval,
            List<Instant> openTimes);
}
