package com.guidinglight.nexusquant.app.config;

import org.springframework.core.env.Environment;

/**
 * 业务能力只读取稳定配置；退休输入由统一启动 guard 拒绝。
 */
public final class StableCapabilityPropertyResolver {

    private StableCapabilityPropertyResolver() {
    }

    public static String value(Environment environment, String key, String defaultValue) {
        return environment.getProperty(key, defaultValue);
    }

    /**
     * 安全开关必须显式且严格匹配；缺失或非法布尔值均拒绝能力装配。
     */
    public static boolean matchesExactBoolean(Environment environment, String key, boolean required) {
        String value = value(environment, key, null);
        return value != null && Boolean.toString(required).equalsIgnoreCase(value.trim());
    }
}
