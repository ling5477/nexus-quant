package com.guidinglight.nexusquant.research.backtest.config;

import com.guidinglight.nexusquant.research.domain.backtest.BacktestSignalPolicy;
import com.guidinglight.nexusquant.research.domain.backtest.BuiltinFixtureSignalPolicy;
import com.guidinglight.nexusquant.research.domain.backtest.ExecutionPricingPolicy;
import com.guidinglight.nexusquant.research.domain.backtest.FeeModel;
import com.guidinglight.nexusquant.research.domain.backtest.SlippageModel;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * BacktestDomainConfiguration 显式装配回测域内的纯算法对象。
 * <p>
 * Why:
 * 回测/评估 domain 包不再直接依赖 Spring stereotype，避免“domain 包看似纯净、
 * 实际靠框架注解参与装配”的伪分层。
 */
@Configuration
public class BacktestDomainConfiguration {

    @Bean
    public FeeModel feeModel() {
        return new FeeModel();
    }

    @Bean
    public SlippageModel slippageModel() {
        return new SlippageModel();
    }

    @Bean
    public ExecutionPricingPolicy executionPricingPolicy() {
        return new ExecutionPricingPolicy();
    }

    @Bean
    public BacktestSignalPolicy backtestSignalPolicy() {
        return new BuiltinFixtureSignalPolicy();
    }
}
