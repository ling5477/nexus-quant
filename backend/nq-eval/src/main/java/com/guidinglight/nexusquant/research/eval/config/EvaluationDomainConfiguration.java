package com.guidinglight.nexusquant.research.eval.config;

import com.guidinglight.nexusquant.research.domain.eval.DrawdownCalculator;
import com.guidinglight.nexusquant.research.domain.eval.EvaluationMetricCalculator;
import com.guidinglight.nexusquant.research.domain.eval.SharpeCalculator;
import com.guidinglight.nexusquant.research.domain.eval.TradeOutcomeCalculator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * EvaluationDomainConfiguration 显式装配评估域内的纯计算对象。
 * <p>
 * Why:
 * 评估指标计算属于 research/eval 领域规则，不应再通过 `@Component`
 * 直接耦合到 Spring 扫描机制。
 */
@Configuration
public class EvaluationDomainConfiguration {

    @Bean
    public DrawdownCalculator drawdownCalculator() {
        return new DrawdownCalculator();
    }

    @Bean
    public SharpeCalculator sharpeCalculator() {
        return new SharpeCalculator();
    }

    @Bean
    public TradeOutcomeCalculator tradeOutcomeCalculator() {
        return new TradeOutcomeCalculator();
    }

    @Bean
    public EvaluationMetricCalculator evaluationMetricCalculator(
            DrawdownCalculator drawdownCalculator,
            SharpeCalculator sharpeCalculator,
            TradeOutcomeCalculator tradeOutcomeCalculator
    ) {
        return new EvaluationMetricCalculator(drawdownCalculator, sharpeCalculator, tradeOutcomeCalculator);
    }
}
