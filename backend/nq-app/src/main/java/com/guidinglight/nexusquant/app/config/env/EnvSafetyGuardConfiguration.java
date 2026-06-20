package com.guidinglight.nexusquant.app.config.env;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * EnvSafetyGuardConfiguration 在 Spring 启动期执行 GateK Batch 5B-ENV fail-closed 校验。
 *
 * <p>Why: CI/test/paper profile 必须默认阻断 LIVE、AI、DH runtime、real provider、real exchange
 * endpoint 和 exchange credential material。把 guard 放在 {@code nq-app} composition root，
 * 可以在任何 controller、scheduler、adapter 或 HTTP client 路径启动前失败，并且不需要读取真实
 * {@code .env} 文件。</p>
 *
 * <p>边界：本配置只读取 Spring {@link Environment}、进程环境变量名和值的存在性、JVM system
 * properties；错误信息只输出变量名和冲突类型，不输出 secret value。</p>
 */
@Configuration
public class EnvSafetyGuardConfiguration {

    private static final Map<String, String> CREDENTIAL_KEYS = Map.ofEntries(
            Map.entry("NQ_OKX_API_KEY", "nq.okx.api-key"),
            Map.entry("NQ_OKX_API_SECRET", "nq.okx.api-secret"),
            Map.entry("NQ_OKX_API_PASSPHRASE", "nq.okx.api-passphrase"),
            Map.entry("NQ_OKX_DOME_API_KEY", "nq.okx.dome.api-key"),
            Map.entry("NQ_OKX_DOME_API_SECRET", "nq.okx.dome.api-secret"),
            Map.entry("NQ_OKX_DOME_API_PASSPHRASE", "nq.okx.dome.api-passphrase"),
            Map.entry("NQ_OKX_REAL_API_KEY", "nq.okx.real.api-key"),
            Map.entry("NQ_OKX_REAL_API_SECRET", "nq.okx.real.api-secret"),
            Map.entry("NQ_OKX_REAL_API_PASSPHRASE", "nq.okx.real.api-passphrase"),
            Map.entry("NQ_BINANCE_DOME_API_KEY", "nq.binance.dome.api-key"),
            Map.entry("NQ_BINANCE_DOME_API_SECRET", "nq.binance.dome.api-secret"),
            Map.entry("NQ_BINANCE_DOME_PRIVATE_KEY", "nq.binance.dome.private-key"),
            Map.entry("NQ_BINANCE_DOME_PRIVATE_KEY_PATH", "nq.binance.dome.private-key-path"),
            Map.entry("NQ_BINANCE_REAL_API_KEY", "nq.binance.real.api-key"),
            Map.entry("NQ_BINANCE_REAL_API_SECRET", "nq.binance.real.api-secret"),
            Map.entry("NQ_BINANCE_REAL_PRIVATE_KEY", "nq.binance.real.private-key"),
            Map.entry("NQ_BINANCE_REAL_PRIVATE_KEY_PATH", "nq.binance.real.private-key-path")
    );

    private static final Map<String, String> ENDPOINT_KEYS = Map.ofEntries(
            Map.entry("NQ_OKX_BASE_URL", "nq.okx.base-url"),
            Map.entry("NQ_OKX_DOME_BASE_URL", "nq.okx.dome.base-url"),
            Map.entry("NQ_OKX_REAL_BASE_URL", "nq.okx.real.base-url"),
            Map.entry("NQ_OKX_WS_URL", "nq.okx.ws-url"),
            Map.entry("NQ_OKX_DOME_WS_URL", "nq.okx.dome.ws-url"),
            Map.entry("NQ_OKX_REAL_WS_URL", "nq.okx.real.ws-url"),
            Map.entry("NQ_BINANCE_DOME_BASE_URL", "nq.binance.dome.base-url"),
            Map.entry("NQ_BINANCE_REAL_BASE_URL", "nq.binance.real.base-url"),
            Map.entry("NQ_BINANCE_DOME_WS_URL", "nq.binance.dome.ws-url"),
            Map.entry("NQ_BINANCE_REAL_WS_URL", "nq.binance.real.ws-url")
    );

    @Bean
    public ApplicationRunner envSafetyStartupGuard(Environment environment) {
        return args -> {
            EnvSafetyValidator.Facts facts = factsFrom(environment, System.getenv(), System.getProperties());
            var violations = EnvSafetyValidator.validate(facts);
            if (!violations.isEmpty()) {
                throw new IllegalStateException("NQ 5B-ENV startup guard failed closed: " + String.join("; ", violations));
            }
        };
    }

    /**
     * 从 Spring、进程环境和 JVM system properties 构造脱敏事实。
     *
     * <p>Why: Spring relaxed binding、GitHub Actions env、Maven {@code -D} 参数可能来自不同来源。
     * 这里按“显式 Spring 属性优先，其次 env，最后 system property”的顺序读取，保证 CI
     * 传入的 fail-closed 开关可以覆盖默认值，但不需要读取本地真实 {@code .env} 文件。</p>
     */
    static EnvSafetyValidator.Facts factsFrom(
            Environment environment,
            Map<String, String> processEnv,
            Properties systemProperties
    ) {
        Set<String> activeProfiles = Arrays.stream(environment.getActiveProfiles()).collect(Collectors.toSet());
        boolean ci = booleanValue(environment, processEnv, systemProperties, "nq.env-safety.ci", "CI", false);
        boolean live = booleanValue(environment, processEnv, systemProperties, "nq.env-safety.live-enabled", "NQ_LIVE_ENABLED", false);
        boolean ai = booleanValue(environment, processEnv, systemProperties, "nq.env-safety.ai-enabled", "NQ_AI_ENABLED", false);
        boolean dh = booleanValue(environment, processEnv, systemProperties, "nq.env-safety.dh-runtime-enabled", "NQ_DH_RUNTIME_ENABLED", false);
        boolean realProvider = booleanValue(environment, processEnv, systemProperties, "nq.env-safety.real-provider-enabled", "NQ_REAL_PROVIDER_ENABLED", false);
        boolean realClient = booleanValue(environment, processEnv, systemProperties, "nq.env-safety.real-client-enabled", "NQ_REAL_CLIENT_ENABLED", false);
        boolean realExchange = booleanValue(environment, processEnv, systemProperties, "nq.env-safety.real-exchange-enabled", "NQ_REAL_EXCHANGE_ENABLED", false);
        boolean noOutbound = booleanValue(environment, processEnv, systemProperties, "nq.env-safety.no-outbound", "NQ_NO_OUTBOUND", false);

        if ("real".equalsIgnoreCase(value(environment, processEnv, systemProperties, "nq.okx.env", "NQ_OKX_ENV"))
                || "real".equalsIgnoreCase(value(environment, processEnv, systemProperties, "nq.binance.env", "NQ_BINANCE_ENV"))) {
            realExchange = true;
        }

        return new EnvSafetyValidator.Facts(
                activeProfiles,
                ci,
                live,
                ai,
                dh,
                realProvider,
                realClient,
                realExchange,
                noOutbound,
                values(environment, processEnv, systemProperties, CREDENTIAL_KEYS),
                values(environment, processEnv, systemProperties, ENDPOINT_KEYS)
        );
    }

    private static Map<String, String> values(
            Environment environment,
            Map<String, String> processEnv,
            Properties systemProperties,
            Map<String, String> keys
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        keys.forEach((envName, propertyName) -> {
            String value = value(environment, processEnv, systemProperties, propertyName, envName);
            if (value != null && !value.isBlank()) {
                values.put(envName, value);
            }
        });
        return values;
    }

    private static boolean booleanValue(
            Environment environment,
            Map<String, String> processEnv,
            Properties systemProperties,
            String propertyName,
            String envName,
            boolean defaultValue
    ) {
        String value = value(environment, processEnv, systemProperties, propertyName, envName);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value.trim());
    }

    private static String value(
            Environment environment,
            Map<String, String> processEnv,
            Properties systemProperties,
            String propertyName,
            String envName
    ) {
        String propertyValue = environment.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }
        String envValue = processEnv.get(envName);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return systemProperties.getProperty(propertyName);
    }
}
