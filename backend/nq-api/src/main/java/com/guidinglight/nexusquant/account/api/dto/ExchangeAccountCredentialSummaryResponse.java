package com.guidinglight.nexusquant.account.api.dto;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * ExchangeAccountCredentialSummaryResponse 描述 active 凭证摘要与校验状态。
 */
@Schema(name = "ExchangeAccountCredentialSummaryResponse", description = "账户凭证摘要")
public record ExchangeAccountCredentialSummaryResponse(
        @Schema(description = "凭证主键")
        Long credentialId,
        @Schema(description = "账户主键")
        Long exchangeAccountId,
        @Schema(description = "凭证类型")
        String credentialType,
        @Schema(description = "掩码后的 access key")
        String maskedAccessKey,
        @Schema(description = "校验状态")
        String verificationStatus,
        @Schema(description = "是否 active")
        boolean isActive,
        @Schema(description = "轮换来源 credentialId")
        Long rotatedFromCredentialId,
        @Schema(description = "最近校验时间")
        Instant lastVerifiedAt,
        @Schema(description = "最近校验错误摘要")
        String lastVerificationError,
        @Schema(description = "最后更新时间")
        Instant updatedAt
) {

    public static ExchangeAccountCredentialSummaryResponse from(ExchangeAccountCredentialSummary summary) {
        return new ExchangeAccountCredentialSummaryResponse(
                summary.credentialId(),
                summary.exchangeAccountId(),
                summary.credentialType(),
                summary.maskedAccessKey(),
                summary.verificationStatus(),
                summary.isActive(),
                summary.rotatedFromCredentialId(),
                summary.lastVerifiedAt(),
                summary.lastVerificationError(),
                summary.updatedAt()
        );
    }
}
