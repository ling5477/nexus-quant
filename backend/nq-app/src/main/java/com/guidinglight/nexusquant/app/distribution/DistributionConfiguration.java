package com.guidinglight.nexusquant.app.distribution;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointProperties;
import org.springframework.boot.actuate.endpoint.Show;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** 在数据源和后台任务实例化前验证发行身份，误配不能部分启动。 */
@Configuration
public class DistributionConfiguration {
    @Bean public static BeanFactoryPostProcessor distributionGuard(Environment environment) {
        return factory -> DistributionIdentity.fromArtifact(DistributionConfiguration.class.getClassLoader())
                .validateRuntime(environment);
    }

    @Bean public DistributionIdentity distributionIdentity() {
        return DistributionIdentity.fromArtifact(DistributionConfiguration.class.getClassLoader());
    }

    @Bean public static BeanPostProcessor distributionHealthPrivacy() {
        var identity = DistributionIdentity.fromArtifact(DistributionConfiguration.class.getClassLoader());
        return new BeanPostProcessor() {
            @Override public Object postProcessBeforeInitialization(Object bean, String name) {
                // 配置绑定完成后固定公开投影，环境变量和健康组不能重新打开数据库或组件细节。
                if (bean instanceof HealthEndpointProperties properties) restrictHealth(identity, properties);
                return bean;
            }
        };
    }

    static void restrictHealth(DistributionIdentity identity, HealthEndpointProperties properties) {
        if (identity != DistributionIdentity.NQ_PUBLIC_DEMO) return;
        properties.setShowDetails(Show.NEVER);
        properties.setShowComponents(Show.NEVER);
        properties.getGroup().values().forEach(group -> {
            group.setShowDetails(Show.NEVER);
            group.setShowComponents(Show.NEVER);
        });
    }
}
