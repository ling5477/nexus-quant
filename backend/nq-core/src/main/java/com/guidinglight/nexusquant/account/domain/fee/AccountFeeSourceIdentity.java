package com.guidinglight.nexusquant.account.domain.fee;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 保持已保存V1观察摘要的原编码；冻结模型复核同一来源，不另造一套费率事实。 */
public final class AccountFeeSourceIdentity {
    private AccountFeeSourceIdentity() {}

    public static String digest(AccountFeeObservation row) {
        var quote = row.quote();
        if (quote.commissionTerms() != null) {
            // 新来源使用独立V2编码；不把缺失的等级/供应时点填成字符串，不改变任何V1旧摘要。
            StringBuilder value = new StringBuilder("NQ/ACCOUNT_FEE_OBSERVATION/2\n");
            for (String field :
                    new String[] {
                        Long.toString(row.ownerUserId()),
                        Long.toString(row.exchangeAccountId()),
                        Long.toString(row.credentialId()),
                        row.exchangeCode(),
                        row.tradeEnv(),
                        row.symbol(),
                        quote.source(),
                        quote.rateConvention(),
                        quote.providerTimeBasis(),
                        quote.providerAt() == null ? "" : quote.providerAt().toString(),
                        quote.observedAt().toString(),
                        quote.commissionTerms().snapshot(new ObjectMapper()).toString()
                    }) {
                value.append(field.length()).append(':').append(field).append('\n');
            }
            return sha256(value.toString());
        }
        String value =
                String.join(
                        "|",
                        "NQ/ACCOUNT_FEE_OBSERVATION/1",
                        Long.toString(row.ownerUserId()),
                        Long.toString(row.exchangeAccountId()),
                        Long.toString(row.credentialId()),
                        row.exchangeCode(),
                        row.tradeEnv(),
                        row.symbol(),
                        AccountFeeObservation.SOURCE,
                        AccountFeeObservation.CONVENTION,
                        quote.makerProviderRate().stripTrailingZeros().toPlainString(),
                        quote.takerProviderRate().stripTrailingZeros().toPlainString(),
                        quote.tier(),
                        quote.providerAt().toString(),
                        quote.observedAt().toString());
        return sha256(value);
    }

    /** 输入来自固定业务编码，不使用平台默认字符集，保证重启与不同主机摘要一致。 */
    public static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
