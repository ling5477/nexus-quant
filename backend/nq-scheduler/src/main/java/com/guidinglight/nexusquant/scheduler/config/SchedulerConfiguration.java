package com.guidinglight.nexusquant.scheduler.config;

import com.guidinglight.nexusquant.scheduler.service.NoopStrategyScheduler;
import com.guidinglight.nexusquant.scheduler.service.StrategyScheduler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SchedulerConfiguration 提供调度模块默认 Bean 装配。
 *
 * Why:
 * nq-app 需要在无业务实现时也能装配成功并启动，避免因为缺失调度实现导致启动失败。
 */
@Configuration
public class SchedulerConfiguration {

    @Bean
    public StrategyScheduler strategyScheduler() {
        return new NoopStrategyScheduler();
    }
}
