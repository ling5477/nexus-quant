package com.guidinglight.nexusquant.app.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.okx.service.OkxVenueRuleFactsReader;
import com.guidinglight.nexusquant.app.marketdata.OkxVenueRuleFactsSyncService;
import com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogService;
import com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogUpsertStats;
import com.guidinglight.nexusquant.marketdata.domain.instrument.InstrumentCatalogItem;
import com.guidinglight.nexusquant.marketdata.domain.instrument.VenueRuleFreshnessEvaluator;
import com.guidinglight.nexusquant.marketdata.domain.instrument.port.InstrumentCatalogRepository;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OkxVenueRuleSyncConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(OkxVenueRuleSyncConfiguration.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(
                    InstrumentCatalogService.class,
                    () -> new InstrumentCatalogService(new NoopInstrumentCatalogRepository())
            );

    @Test
    void defaultAndCiStyleConfigurationShouldRemainNoEgress() {
        contextRunner.run(context -> {
            assertTrue(context.containsBean("venueRuleFreshnessEvaluator"));
            assertFalse(context.containsBean("okxVenueRuleFactsReader"));
            assertFalse(context.containsBean("okxVenueRuleFactsSyncService"));
        });
    }

    @Test
    void enabledFlagWithoutManualProfileShouldStillRemainNoEgress() {
        contextRunner
                .withPropertyValues("nq.okx.venue-rule-sync.enabled=true")
                .run(context -> {
                    assertFalse(context.containsBean("okxVenueRuleFactsReader"));
                    assertFalse(context.containsBean("okxVenueRuleFactsSyncService"));
                });
    }

    @Test
    void manualProfileAndFlagShouldConstructReaderAndBoundedServiceWithoutFetching() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("okx-venue-rule-sync-manual"))
                .withPropertyValues(
                        "nq.okx.venue-rule-sync.enabled=true",
                        "nq.okx.venue-rule-sync.base-url=http://127.0.0.1:65535",
                        "nq.okx.venue-rule-sync.timeout=PT2S",
                        "nq.okx.venue-rule-sync.allowlist=BTC-USDT,ETH-USDT",
                        "nq.okx.venue-rule-sync.stale-after-seconds=600"
                )
                .run(context -> {
                    assertNotNull(context.getBean(OkxVenueRuleFactsReader.class));
                    assertNotNull(context.getBean(OkxVenueRuleFactsSyncService.class));
                    assertNotNull(context.getBean(VenueRuleFreshnessEvaluator.class));
                });
    }

    @Test
    void legacyProfileAndKeysShouldRemainCompatible() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("gatew-venue-rules-manual"))
                .withPropertyValues(
                        "nq.gatew.okx-venue-rules.enabled=true",
                        "nq.gatew.okx-venue-rules.base-url=http://127.0.0.1:65535",
                        "nq.gatew.okx-venue-rules.timeout=PT2S",
                        "nq.gatew.okx-venue-rules.allowlist=BTC-USDT"
                )
                .run(context -> {
                    assertNotNull(context.getBean(OkxVenueRuleFactsReader.class));
                    assertNotNull(context.getBean(OkxVenueRuleFactsSyncService.class));
                });
    }

    @Test
    void conflictingEnableKeysShouldFailClosed() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("okx-venue-rule-sync-manual"))
                .withPropertyValues(
                        "nq.okx.venue-rule-sync.enabled=true",
                        "nq.gatew.okx-venue-rules.enabled=false"
                )
                .run(context -> {
                    assertFalse(context.containsBean("okxVenueRuleFactsReader"));
                    assertFalse(context.containsBean("okxVenueRuleFactsSyncService"));
                });
    }

    @Test
    void invalidOrMissingFreshnessThresholdShouldYieldUnknownInsteadOfStartupFailure() {
        contextRunner
                .withPropertyValues("nq.okx.venue-rule-sync.stale-after-seconds=invalid")
                .run(context -> {
                    VenueRuleFreshnessEvaluator evaluator = context.getBean(VenueRuleFreshnessEvaluator.class);
                    assertNotNull(evaluator);
                    assertTrue(context.isRunning());
                });
    }

    private static final class NoopInstrumentCatalogRepository implements InstrumentCatalogRepository {

        @Override
        public List<InstrumentCatalogItem> list(String exchangeCode) {
            return List.of();
        }

        @Override
        public List<InstrumentCatalogItem> findByExchangeAndSymbols(
                String exchangeCode,
                List<String> exchangeSymbols
        ) {
            return List.of();
        }

        @Override
        public InstrumentCatalogUpsertStats upsertAll(List<InstrumentCatalogItem> items, Instant syncedAt) {
            return new InstrumentCatalogUpsertStats(0, 0);
        }

        @Override
        public InstrumentCatalogUpsertStats upsertVenueRuleFacts(
                List<InstrumentCatalogItem> items,
                Instant syncedAt
        ) {
            return new InstrumentCatalogUpsertStats(0, 0);
        }
    }
}
