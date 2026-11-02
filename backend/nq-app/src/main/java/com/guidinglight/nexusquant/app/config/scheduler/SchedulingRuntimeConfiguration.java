package com.guidinglight.nexusquant.app.config.scheduler;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 仅提供 Spring 定时基础设施；业务执行仍由持久控制与固定任务目录共同决定。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "nq.runtime.provider-observation.enabled", havingValue = "false", matchIfMissing = true)
@EnableScheduling
public class SchedulingRuntimeConfiguration {
}
