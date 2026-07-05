package com.guidinglight.nexusquant.integration.dh;

import java.time.Duration;
import java.util.Map;

/**
 * DhDryRunTransportRequest 保存 fake transport 可观察的 signed request。
 *
 * @param endpointUrl DH endpoint URL；本轮测试只能传给 fake，不执行真实网络
 * @param timeout     client timeout；timeout 后必须 fail-closed
 * @param headers     canonical `X-NQ-DH-*` headers；不包含 legacy `X-DH-NQ-*`
 * @param body        JSON body；不包含 credential 或可执行 order payload
 */
public record DhDryRunTransportRequest(
        String endpointUrl,
        Duration timeout,
        Map<String, String> headers,
        String body) {
}
