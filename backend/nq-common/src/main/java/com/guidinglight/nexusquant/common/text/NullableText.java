package com.guidinglight.nexusquant.common.text;

/** 可空文本的最小归一化，不解释调用方的领域默认值。 */
public final class NullableText {

    private NullableText() {
    }

    /** 优先使用非空原值，否则使用非空回退值；两者都为空时返回 null。 */
    public static String firstNonBlank(String value, String fallback) {
        String normalized = nonBlankOrNull(value);
        return normalized != null ? normalized : nonBlankOrNull(fallback);
    }

    /** 多个只读事实按调用方给定的优先级选择，保持原有 null 回退合同。 */
    public static String firstNonBlank(String first, String second, String third, String... remaining) {
        String normalized = firstNonBlank(first, second);
        if (normalized != null) {
            return normalized;
        }
        normalized = nonBlankOrNull(third);
        if (normalized != null) {
            return normalized;
        }
        for (String value : remaining) {
            normalized = nonBlankOrNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private static String nonBlankOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
