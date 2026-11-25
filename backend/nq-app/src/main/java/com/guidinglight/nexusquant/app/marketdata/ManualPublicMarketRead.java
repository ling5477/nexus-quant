package com.guidinglight.nexusquant.app.marketdata;

import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.Set;

/** 在公开来源读取前复核手动外联许可，测试、CI 和未知 profile 保持关闭。 */
final class ManualPublicMarketRead {
    private ManualPublicMarketRead() {}

    static boolean allowed(Environment environment) {
        Set<String> profiles = Set.copyOf(Arrays.asList(environment.getActiveProfiles()));
        return profiles.contains("public-marketdata-manual")
                && Set.of("local", "prod", "public-marketdata-manual").containsAll(profiles)
                && environment.getProperty(
                        "nq.public-marketdata.outbound.enabled", Boolean.class, false)
                && !environment.getProperty(
                        "nq.env-safety.no-outbound",
                        Boolean.class,
                        environment.getProperty("NQ_NO_OUTBOUND", Boolean.class, false))
                && !environment.getProperty(
                        "nq.env-safety.ci",
                        Boolean.class,
                        environment.getProperty("CI", Boolean.class, false));
    }
}
