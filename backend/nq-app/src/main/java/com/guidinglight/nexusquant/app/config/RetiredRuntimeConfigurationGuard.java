package com.guidinglight.nexusquant.app.config;

import java.util.Arrays;
import java.util.regex.Pattern;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

/**
 * 在业务 Bean 实例化前拒绝退休配置及 profile，避免旧配置被静默忽略后启动。
 *
 * <p>只枚举原始 key 名，不读取或解析对应值；同样识别系统环境变量的 relaxed 命名。
 * 不转换旧配置，也不为旧 profile 提供能力别名。</p>
 */
@Configuration(proxyBeanMethods = false)
public class RetiredRuntimeConfigurationGuard {

    private static final Pattern RETIRED_PROFILE = Pattern.compile(
            "(?i)^(?:gate.*|phase.*|freeze(?:[-_].*)?)$");
    private static final Pattern RETIRED_KEY = Pattern.compile("(?i)^nq[._-](?:gate|phase).*");

    @Bean
    static BeanFactoryPostProcessor retiredRuntimeConfigurationValidator(ConfigurableEnvironment environment) {
        return beanFactory -> {
            for (PropertySource<?> source : environment.getPropertySources()) {
                rejectRetiredKeys(source);
            }
            String[] active = environment.getActiveProfiles();
            String[] effective = active.length == 0 ? environment.getDefaultProfiles() : active;
            if (Arrays.stream(effective).anyMatch(profile -> RETIRED_PROFILE.matcher(profile).matches())) {
                throw new IllegalStateException("RETIRED_RUNTIME_PROFILE / select a supported runtime mode");
            }
            var profiles = Arrays.asList(effective);
            if (profiles.contains("okx-private-readonly-diagnostics")
                    && profiles.contains("scoped-okx-private-readonly")) {
                throw new IllegalStateException("CONFLICTING_RUNTIME_PROFILES / select one credential permission scope");
            }
        };
    }

    private static void rejectRetiredKeys(PropertySource<?> source) {
        if (source instanceof CompositePropertySource composite) {
            for (PropertySource<?> nested : composite.getPropertySources()) {
                rejectRetiredKeys(nested);
            }
        } else if (source instanceof EnumerablePropertySource<?> enumerable) {
            for (String key : enumerable.getPropertyNames()) {
                if (RETIRED_KEY.matcher(key).matches()) {
                    throw new IllegalStateException("RETIRED_RUNTIME_CONFIGURATION_KEY / key=" + key);
                }
            }
        }
    }
}
