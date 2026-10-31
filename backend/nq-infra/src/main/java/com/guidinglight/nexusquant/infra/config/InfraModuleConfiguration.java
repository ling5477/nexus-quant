package com.guidinglight.nexusquant.infra.config;

import org.springframework.context.annotation.Configuration;

/**
 * InfraModuleConfiguration 是基础设施模块的占位配置入口。
 *
 * Why:
 * 只要求 infra 提供可装配与可迁移骨架，
 * 此配置只提供模块装配入口；连接池、Outbox 与 MQ 客户端由各自的配置负责。
 */
@Configuration
public class InfraModuleConfiguration {
}
