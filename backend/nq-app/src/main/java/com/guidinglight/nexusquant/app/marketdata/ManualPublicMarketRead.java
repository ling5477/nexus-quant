package com.guidinglight.nexusquant.app.marketdata;

import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.Set;

/** 在公开来源读取前复核手动外联许可，测试、CI 和未知 profile 保持关闭。 */
public final class ManualPublicMarketRead {
    private ManualPublicMarketRead() {}

    public static boolean allowed(Environment environment) {
        Set<String> profiles = Set.copyOf(Arrays.asList(environment.getActiveProfiles()));
        return profiles.contains("public-marketdata-manual")
                && Set.of("local", "prod", "public-marketdata-manual").containsAll(profiles)
                && environment.getProperty(
                        "nq.public-marketdata.outbound.enabled", Boolean.class, false)
                && !vetoed(environment, "nq.env-safety.no-outbound", "NQ_NO_OUTBOUND")
                && !vetoed(environment, "nq.env-safety.ci", "CI");
    }

    private static boolean vetoed(Environment environment, String property, String variable) {
        // Spring 可以隐藏系统环境；进程禁令必须从 JVM 直接读取。
        String processValue = System.getenv(variable);
        return (processValue != null && Boolean.parseBoolean(processValue.trim()))
                || environment.getProperty(variable, Boolean.class, false)
                || environment.getProperty(property, Boolean.class, false);
    }
}
