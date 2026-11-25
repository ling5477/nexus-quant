package com.guidinglight.nexusquant.app.marketdata;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/** 在公开来源读取前复核手动外联许可，测试、CI 和未知 profile 保持关闭。 */
public final class ManualPublicMarketRead {
    private ManualPublicMarketRead() {}

    public static boolean allowed(Environment environment) {
        if (environment instanceof ConfigurableEnvironment configurable) {
            return allowed(environment, configurable.getSystemEnvironment());
        }
        return allowed(environment, System.getenv());
    }

    private static boolean allowed(Environment environment, Map<String, ?> processEnvironment) {
        Set<String> profiles = Set.copyOf(Arrays.asList(environment.getActiveProfiles()));
        return profiles.contains("public-marketdata-manual")
                && Set.of("local", "prod", "public-marketdata-manual").containsAll(profiles)
                && environment.getProperty(
                        "nq.public-marketdata.outbound.enabled", Boolean.class, false)
                && !vetoed(environment, processEnvironment, "nq.env-safety.no-outbound", "NQ_NO_OUTBOUND")
                && !vetoed(environment, processEnvironment, "nq.env-safety.ci", "CI");
    }

    private static boolean vetoed(Environment environment, Map<String, ?> processEnvironment,
                                  String property, String variable) {
        // 进程级否决与 Spring 配置独立，任一显式禁止信号都能关停公开读取。
        Object processValue = processEnvironment.get(variable);
        return (processValue != null && Boolean.parseBoolean(processValue.toString().trim()))
                || environment.getProperty(variable, Boolean.class, false)
                || environment.getProperty(property, Boolean.class, false);
    }
}
