package com.guidinglight.nexusquant.strategy.domain.shadowrun;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/**
 * Shadow Run JSONB 结构性敏感字段保护。
 *
 * <p>这是 GateR-2 本地 fact model 的 fail-closed guard：它阻断明确的 credential、
 * private endpoint、真实订单/账户余额字段名和授权语义字段名。它不是通用 secret scanner；
 * 后续 API/runner 层仍应继续做输入脱敏和 no-egress/no-credential guard。
 */
public final class ShadowRunSensitiveDataGuard {

    private static final Set<String> FORBIDDEN_FIELD_NAMES = Set.of(
            "apikey",
            "secret",
            "passphrase",
            "token",
            "privatekey",
            "rawsignature",
            "rawprivaterequest",
            "rawprivateresponse",
            "credentialmaterial",
            "decryptedpayload",
            "encryptedpayload",
            "privateendpointpayload",
            "realorderid",
            "realaccountbalance",
            "realposition",
            "tradingready",
            "liveready",
            "authorizedfortrading",
            "tradeapproved",
            "liveapproved"
    );

    private ShadowRunSensitiveDataGuard() {
    }

    public static void validateJson(String fieldName, JsonNode value) {
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        scan(fieldName, value);
    }

    public static void validateOptionalJson(String fieldName, JsonNode value) {
        if (value != null && !value.isNull()) {
            scan(fieldName, value);
        }
    }

    public static boolean isForbiddenFieldName(String fieldName) {
        return FORBIDDEN_FIELD_NAMES.contains(normalize(fieldName));
    }

    private static void scan(String path, JsonNode value) {
        if (value.isObject()) {
            Iterator<String> fieldNames = value.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                if (isForbiddenFieldName(fieldName)) {
                    throw new IllegalArgumentException(path + " contains forbidden sensitive field: " + fieldName);
                }
                scan(path + "." + fieldName, value.get(fieldName));
            }
            return;
        }
        if (value.isArray()) {
            for (int i = 0; i < value.size(); i++) {
                scan(path + "[" + i + "]", value.get(i));
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
