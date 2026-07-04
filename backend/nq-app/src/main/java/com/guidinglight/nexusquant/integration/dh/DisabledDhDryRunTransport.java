package com.guidinglight.nexusquant.integration.dh;

/**
 * DisabledDhDryRunTransport 是不会发起网络调用的默认 transport。
 *
 * <p>Why: production code 不引入真实网络客户端。即使误把 client flags 打开，未显式注入测试 fake transport
 * 时也只能返回 CLIENT_DISABLED error envelope。</p>
 */
public final class DisabledDhDryRunTransport implements DhDryRunTransport {

    /**
     * 返回 disabled error envelope，不访问网络。
     *
     * @param request 已签名请求摘要；不会被发送到外部系统
     * @return CLIENT_DISABLED error envelope
     */
    @Override
    public DhDryRunTransportResponse send(DhDryRunTransportRequest request) {
        return new DhDryRunTransportResponse(
                503,
                "{\"dryRun\":true,\"schemaVersion\":\""
                        + DhDryRunRuntimeProperties.DEFAULT_SCHEMA_VERSION
                        + "\",\"error\":{\"code\":\"CLIENT_DISABLED\",\"message\":\"disabled transport\"}}");
    }
}
