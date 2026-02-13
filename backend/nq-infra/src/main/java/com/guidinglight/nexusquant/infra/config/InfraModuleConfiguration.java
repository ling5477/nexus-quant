package com.guidinglight.nexusquant.infra.config;

import org.springframework.context.annotation.Configuration;

/**
 * InfraModuleConfiguration 是基础设施模块的占位配置入口。
 *
 * Why:
 * Gate A 只要求 infra 提供可装配与可迁移骨架，
 * 真实连接池、Outbox、MQ 客户端配置在后续 Gate 按需补全。
 */
@Configuration
public class InfraModuleConfiguration {
}
