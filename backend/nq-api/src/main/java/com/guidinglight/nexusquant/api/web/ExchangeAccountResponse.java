package com.guidinglight.nexusquant.api.web;

import com.guidinglight.nexusquant.core.account.domain.ExchangeAccountSummary;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * ExchangeAccountResponse 描述账户上下文与账户管理页需要的最小账户摘要。
 */
@Schema(name = "ExchangeAccountResponse", description = "账户上下文摘要")
public record ExchangeAccountResponse(
        @Schema(description = "exchange account ID")
        Long exchangeAccountId,
        @Schema(description = "legacy account ID for compatibility")
        Long legacyAccountId,
        @Schema(description = "交易所编码")
        String exchangeCode,
        @Schema(description = "交易环境")
        String tradeEnv,
        @Schema(description = "账户别名")
        String accountAlias,
        @Schema(description = "外部账户引用")
        String externalAccountRef,
        @Schema(description = "是否默认账户")
        boolean isDefault,
        @Schema(description = "账户状态")
        String status
) {
    public static ExchangeAccountResponse from(ExchangeAccountSummary summary) {
        return new ExchangeAccountResponse(
                summary.exchangeAccountId(),
                summary.legacyAccountId(),
                summary.exchangeCode(),
                summary.tradeEnv(),
                summary.accountAlias(),
                summary.externalAccountRef(),
                summary.isDefault(),
                summary.status()
        );
    }
}
