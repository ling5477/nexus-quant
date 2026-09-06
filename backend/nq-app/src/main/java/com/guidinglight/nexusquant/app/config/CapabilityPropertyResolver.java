package com.guidinglight.nexusquant.app.config;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

/**
 * 为 capability 配置提供稳定 key，并拒绝退役的 stage key。
 *
 * <p>日志只记录 key 名和选择策略，不记录配置值。安全开关应使用 fail-closed 入口；
 * 普通参数可以使用 stable-first 入口。</p>
 */
public final class CapabilityPropertyResolver {

    private static final Logger log = LoggerFactory.getLogger(CapabilityPropertyResolver.class);
    private static final Set<String> WARNED_LEGACY_KEYS = ConcurrentHashMap.newKeySet();
    private static final Set<String> WARNED_CONFLICT_KEYS = ConcurrentHashMap.newKeySet();

    private CapabilityPropertyResolver() {
    }

    /**
     * 只接受新 key；旧 key 一旦出现就返回保守默认值，禁止静默重启旧 runtime。
     */
    public static String stableFirst(
            Environment environment,
            String stableKey,
            String legacyKey,
            String defaultValue
    ) {
        Resolution resolution = resolve(environment, stableKey, legacyKey);
        if (resolution.conflict()) {
            warnConflictOnce(stableKey, legacyKey, "RETIRED_KEY_REJECTED");
            return defaultValue;
        }
        return resolution.selectedValue(defaultValue);
    }

    /**
     * 旧 key 存在时返回保守默认值，适用于 enable、权限和 endpoint binding。
     */
    public static String failClosed(
            Environment environment,
            String stableKey,
            String legacyKey,
            String defaultValue
    ) {
        Resolution resolution = resolve(environment, stableKey, legacyKey);
        if (resolution.conflict()) {
            warnConflictOnce(stableKey, legacyKey, "FAIL_CLOSED");
            return defaultValue;
        }
        return resolution.selectedValue(defaultValue);
    }

    /**
     * 只有显式、无冲突且与 required 相等的布尔值才通过；缺失或非法值均 fail-closed。
     */
    public static boolean matchesExactBoolean(
            Environment environment,
            String stableKey,
            String legacyKey,
            boolean required
    ) {
        String value = failClosed(environment, stableKey, legacyKey, null);
        return value != null && Boolean.toString(required).equalsIgnoreCase(value.trim());
    }

    private static Resolution resolve(Environment environment, String stableKey, String legacyKey) {
        String stableValue = environment.getProperty(stableKey);
        String legacyValue = environment.getProperty(legacyKey);
        if (legacyValue != null && WARNED_LEGACY_KEYS.add(legacyKey)) {
            log.warn(
                    "legacy_capability_property_deprecated legacy_key={} replacement_key={} action=migrate",
                    legacyKey,
                    stableKey
            );
        }
        boolean conflict = legacyValue != null;
        return new Resolution(stableValue, legacyValue, conflict);
    }

    private static void warnConflictOnce(String stableKey, String legacyKey, String resolution) {
        String warningKey = stableKey + "|" + legacyKey + "|" + resolution;
        if (WARNED_CONFLICT_KEYS.add(warningKey)) {
            log.warn(
                    "capability_property_alias_conflict stable_key={} legacy_key={} resolution={}",
                    stableKey,
                    legacyKey,
                    resolution
            );
        }
    }

    private record Resolution(String stableValue, String legacyValue, boolean conflict) {

        private String selectedValue(String defaultValue) {
            if (stableValue != null) {
                return stableValue;
            }
            return defaultValue;
        }
    }
}
