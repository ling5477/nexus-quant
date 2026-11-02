package com.guidinglight.nexusquant.common.currency;

import java.time.Instant;
import java.util.Set;

/** 估值依据随派生金额保存；UNKNOWN不允许携带貌似已确认的计价币种。 */
public record ValuationMetadata(
        String currency,
        Instant at,
        String source,
        String method,
        String status,
        String detailsJson) {
    public ValuationMetadata {
        if (status == null || !Set.of("UNKNOWN", "PARTIAL", "VERIFIED").contains(status)) {
            throw new IllegalArgumentException("INVALID_VALUATION_STATUS");
        }
        if ("UNKNOWN".equals(status)) {
            if (currency != null
                    || at != null
                    || source != null
                    || method != null
                    || detailsJson != null) {
                throw new IllegalArgumentException("UNKNOWN_VALUATION_HAS_UNVERIFIED_METADATA");
            }
        } else {
            CurrencyCode.require(currency);
            if (at == null
                    || source == null
                    || source.isBlank()
                    || source.length() > 128
                    || method == null
                    || method.isBlank()
                    || method.length() > 128
                    || detailsJson == null
                    || detailsJson.isBlank()
                    || detailsJson.length() > 262144) {
                throw new IllegalArgumentException("VALUATION_PROVENANCE_REQUIRED");
            }
        }
    }

    /** 只供读取旧记录和兼容旧对象；正式新写入必须另行验证完整依据。 */
    public static ValuationMetadata unknown() {
        return new ValuationMetadata(null, null, null, null, "UNKNOWN", null);
    }
}
