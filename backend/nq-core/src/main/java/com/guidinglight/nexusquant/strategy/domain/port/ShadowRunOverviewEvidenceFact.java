package com.guidinglight.nexusquant.strategy.domain.port;

import java.time.Instant;

/**
 * ShadowRunOverviewEvidenceFact 是 GateS-1 overview query port 的轻量证据锚点投影。
 *
 * <p>Why: overview 需要把聚合结论追溯到本地 Shadow Run facts，但不能复制 payload、credential
 * material、private endpoint response 或真实账户/订单状态。本 record 只保存可审计的 source id、
 * source version、时间和可选 checksum，供 read model 输出 evidenceAnchors。
 */
public record ShadowRunOverviewEvidenceFact(
        String sourceType,
        String sourceId,
        String sourceVersion,
        Instant sourceTimestamp,
        String checksum
) {
}
