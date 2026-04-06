package com.guidinglight.nexusquant.account.domain.port;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialMaterial;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;

import java.time.Instant;
import java.util.Optional;

/**
 * ExchangeAccountCredentialRepository 定义账户凭证版本链端口。
 * <p>
 * Why:
 * RC1-4 要求凭证必须以“新增版本 + active 切换”方式轮换，
 * 因此读 active 摘要、读解密材料、停用旧版本和回写校验状态都要统一从领域端口进入。
 */
public interface ExchangeAccountCredentialRepository {

    Optional<ExchangeAccountCredentialSummary> findActiveSummary(Long ownerUserId, Long exchangeAccountId);

    Optional<ExchangeAccountCredentialSummary> findActiveByAccountAndType(Long exchangeAccountId, String credentialType);

    Optional<ExchangeAccountCredentialMaterial> findActiveMaterial(Long ownerUserId, Long exchangeAccountId);

    void deactivateActiveByAccountAndType(Long exchangeAccountId, String credentialType, Instant revokedAt);

    ExchangeAccountCredentialSummary insertNewVersion(
            Long exchangeAccountId,
            String credentialType,
            String encryptedPayloadJson,
            int keyVersion,
            String cipherSuite,
            String maskedAccessKey,
            Long rotatedFromCredentialId,
            Instant now
    );

    boolean markVerificationResult(
            Long credentialId,
            String verificationStatus,
            Instant verifiedAt,
            String lastVerificationError,
            Instant updatedAt
    );
}
