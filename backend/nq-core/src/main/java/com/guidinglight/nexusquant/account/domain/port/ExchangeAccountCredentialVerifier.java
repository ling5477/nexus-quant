package com.guidinglight.nexusquant.account.domain.port;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialMaterial;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialVerificationResult;

/**
 * ExchangeAccountCredentialVerifier 抽象 active 凭证的结构性校验能力。
 * <p>
 * Why:
 * `nq-core` 不能直接依赖交易所具体实现；
 * 校验逻辑必须通过 port 向上暴露，再由运行时模块用 OKX/Binance signer/runtime 提供具体实现。
 */
public interface ExchangeAccountCredentialVerifier {

    ExchangeAccountCredentialVerificationResult verify(ExchangeAccountCredentialMaterial credentialMaterial);
}
