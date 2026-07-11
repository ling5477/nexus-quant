package com.guidinglight.nexusquant.validationreview.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/**
 * Review evidence/metadata 的结构性敏感字段保护。
 *
 * <p>Guard 只允许脱敏本地证据锚点和操作上下文进入 JSONB；它不读取环境变量、credential
 * service、private endpoint 或外部文件。否定性安全字段如 {@code notTradingAuthorization} 允许保留。
 */
public final class ValidationReviewSensitiveDataGuard {

    private static final Set<String> FORBIDDEN_FIELD_NAMES = Set.of(
            "apikey",
            "secret",
            "passphrase",
            "token",
            "privatekey",
            "credentialmaterial",
            "decryptedpayload",
            "rawprivaterequest",
            "rawprivateresponse",
            "realaccountbalance",
            "realorderid",
            "withdrawaddress",
            "authorizedfortrading",
            "tradeauthorized",
            "tradingready",
            "liveready",
            "approvedfortrading"
    );

    private ValidationReviewSensitiveDataGuard() {
    }

    /**
     * 校验 JSONB 必须为 object，且任何深度都不含禁止字段名。
     *
     * @param fieldName 顶层字段名，仅用于脱敏错误定位
     * @param value 待校验 JSON
     */
    public static void validateObject(String fieldName, JsonNode value) {
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException(fieldName + " must be a JSON object");
        }
        scan(fieldName, value);
    }

    /**
     * 判断字段名是否属于禁止的敏感或交易授权语义。
     *
     * @param fieldName 任意 JSON 字段名
     * @return 命中禁止集合时返回 true
     */
    public static boolean isForbiddenFieldName(String fieldName) {
        return FORBIDDEN_FIELD_NAMES.contains(normalize(fieldName));
    }

    /**
     * 校验将持久化的自由文本不包含敏感字段或交易授权 marker。
     *
     * <p>该检查只用于 title/summary/evidence source 等本地摘要；允许 null summary，
     * 不尝试读取或识别真实 credential value。
     *
     * @param fieldName 脱敏字段名
     * @param value 可空持久化文本
     */
    public static void validateText(String fieldName, String value) {
        if (value == null) {
            return;
        }
        String normalized = normalize(value);
        for (String marker : FORBIDDEN_FIELD_NAMES) {
            if (normalized.contains(marker)) {
                throw new IllegalArgumentException(fieldName + " contains forbidden sensitive marker");
            }
        }
    }

    private static void scan(String path, JsonNode value) {
        if (value.isObject()) {
            Iterator<String> names = value.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (isForbiddenFieldName(name)) {
                    throw new IllegalArgumentException(path + " contains forbidden sensitive field: " + name);
                }
                scan(path + "." + name, value.get(name));
            }
            return;
        }
        if (value.isArray()) {
            for (int index = 0; index < value.size(); index++) {
                scan(path + "[" + index + "]", value.get(index));
            }
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("_", "")
                .replace("-", "")
                .replace(" ", "")
                .toLowerCase(Locale.ROOT);
    }
}
