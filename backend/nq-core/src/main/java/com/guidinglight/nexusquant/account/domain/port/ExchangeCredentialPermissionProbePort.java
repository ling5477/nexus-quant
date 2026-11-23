package com.guidinglight.nexusquant.account.domain.port;

import com.guidinglight.nexusquant.account.domain.ExchangeCredentialPermissionProbeRequest;
import com.guidinglight.nexusquant.account.domain.ExchangeCredentialPermissionProbeResult;

/**
 * ExchangeCredentialPermissionProbePort 抽象交易所 credential 权限探活能力。
 *
 * <p>Why: credential Service 只能做 owner/account/credential gate、状态写回和 audit 编排；
 * 真实 HTTP、交易所错误分类和 endpoint allowlist 必须隔离在 adapter 层，才能在测试中用
 * fake/mock 证明不会访问真实 OKX/Binance，也不会调用 order/cancel/transfer/withdraw。</p>
 */
@FunctionalInterface
public interface ExchangeCredentialPermissionProbePort {

    /**
     * 是否允许在全局 LIVE 仍关闭时，对 LIVE account credential 执行唯一 account/config 只读检查。
     * 默认 false，确保 NoReal、测试 double 和未来未显式审查的 adapter 均保持 fail-closed。
     */
    default boolean supportsControlledLiveReadOnlyProbe() {
        return false;
    }

    /**
     * 执行一次权限探活并返回脱敏结果。
     *
     * <p>边界：实现不得读写 NQ DB，不得访问其他 Service，不得下单、撤单、转账、提现，
     * 不得把 raw response、headers、signature、request body 或 credential material 放入返回值。</p>
     */
    ExchangeCredentialPermissionProbeResult probe(ExchangeCredentialPermissionProbeRequest request);
}
