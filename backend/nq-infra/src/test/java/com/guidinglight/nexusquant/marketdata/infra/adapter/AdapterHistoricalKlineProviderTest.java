package com.guidinglight.nexusquant.marketdata.infra.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.adapter.api.model.HistoricalKlineRequest;
import com.guidinglight.nexusquant.adapter.api.service.port.HistoricalKlineAdapter;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataIngestionJob;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataIngestionStatus;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 用不会联网的适配器证明正式分发之前的关闭边界，而非只检查启动参数文本。 */
class AdapterHistoricalKlineProviderTest {
    private static final Instant AT = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    void disabledOutboundCiOrMissingManualProfileNeverDispatchesToAnAdapter() {
        for (var configuration :
                List.of(
                        new Settings(new String[] {"prod"}, true, false, false),
                        new Settings(
                                new String[] {"prod", "public-marketdata-manual"},
                                false,
                                false,
                                false),
                        new Settings(
                                new String[] {"prod", "public-marketdata-manual"},
                                true,
                                true,
                                false),
                        new Settings(
                                new String[] {"prod", "public-marketdata-manual"},
                                true,
                                false,
                                true),
                        new Settings(
                                new String[] {"paper", "public-marketdata-manual"},
                                true,
                                false,
                                false))) {
            var adapter = mock(HistoricalKlineAdapter.class);
            when(adapter.exchangeCode()).thenReturn("OKX");
            when(adapter.fetchHistoricalKlines(
                            new HistoricalKlineRequest(
                                    "OKX", "SPOT", "BTC-USDT", "1m", AT, AT.plusSeconds(180), 500)))
                    .thenReturn(List.of());
            try (var context = context(adapter, configuration)) {
                clearInvocations(adapter);
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                context.getBean(AdapterHistoricalKlineProvider.class)
                                        .fetchBars(job(), AT, AT.plusSeconds(180)));
                verifyNoInteractions(adapter);
            }
        }
    }

    @Test
    void explicitManualPublicReadKeepsTheExistingBoundedAdapterRequest() {
        var adapter = mock(HistoricalKlineAdapter.class);
        when(adapter.exchangeCode()).thenReturn("OKX");
        var request =
                new HistoricalKlineRequest(
                        "OKX", "SPOT", "BTC-USDT", "1m", AT, AT.plusSeconds(180), 500);
        when(adapter.fetchHistoricalKlines(request)).thenReturn(List.of());
        try (var context =
                context(
                        adapter,
                        new Settings(
                                new String[] {"prod", "public-marketdata-manual"},
                                true,
                                false,
                                false))) {
            clearInvocations(adapter);
            assertEquals(
                    List.of(),
                    context.getBean(AdapterHistoricalKlineProvider.class)
                            .fetchBars(job(), AT, AT.plusSeconds(180)));
            verify(adapter).fetchHistoricalKlines(request);
        }
    }

    private static AnnotationConfigApplicationContext context(
            HistoricalKlineAdapter adapter, Settings settings) {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles(settings.profiles());
        context.getEnvironment()
                .getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "explicit-test-boundary",
                                Map.of(
                                        "nq.public-marketdata.outbound.enabled", settings.enabled(),
                                        "nq.env-safety.no-outbound", settings.noOutbound(),
                                        "nq.env-safety.ci", settings.ci())));
        context.registerBean(HistoricalKlineAdapter.class, () -> adapter);
        context.register(AdapterHistoricalKlineProvider.class);
        context.refresh();
        return context;
    }

    private static MarketdataIngestionJob job() {
        return new MarketdataIngestionJob(
                UUID.randomUUID(),
                "OKX",
                "SPOT",
                "BTC-USDT",
                BarInterval.ONE_MINUTE,
                AT,
                AT.plusSeconds(180),
                MarketdataIngestionStatus.CREATED,
                "EXCHANGE_HISTORICAL",
                "synthetic-operator",
                AT,
                AT,
                "{}");
    }

    private record Settings(String[] profiles, boolean enabled, boolean noOutbound, boolean ci) {}
}
