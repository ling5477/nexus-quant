package com.guidinglight.nexusquant.scheduler.paper;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import com.guidinglight.nexusquant.marketdata.domain.port.ClosedBarMarketFeed;
import com.guidinglight.nexusquant.research.application.paper.service.PaperTradingRunService;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

class ContinuousSimRunServiceContextTest {
    @Test
    void manualProfileWithEnabledContinuousSimCreatesRuntimeBean() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("public-marketdata-manual");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "continuous-sim-test", Map.of("nq.continuous-sim.enabled", "true")));
            context.getBeanFactory().registerSingleton("progress", mock(ContinuousSimRepository.class));
            context.getBeanFactory().registerSingleton("sim", mock(StrategySimRunService.class));
            context.getBeanFactory().registerSingleton("paperRuns", mock(PaperTradingRunService.class));
            context.getBeanFactory().registerSingleton("feed", mock(ClosedBarMarketFeed.class));
            context.getBeanFactory().registerSingleton("jdbc", mock(JdbcTemplate.class));
            context.register(ContinuousSimRunService.class);
            context.refresh();
            assertNotNull(context.getBean(ContinuousSimRunService.class));
        }
    }
}
