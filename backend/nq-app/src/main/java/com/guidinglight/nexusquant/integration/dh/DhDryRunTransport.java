package com.guidinglight.nexusquant.integration.dh;

/**
 * DhDryRunTransport 是 limited dry-run client 的唯一 I/O 端口。
 *
 * <p>Why: 本轮不允许真实 DH HTTP、真实外网 HTTP、provider 或 exchange 调用。生产代码只定义 transport
 * 抽象，测试注入 in-memory fake；默认 Spring 装配也只提供 disabled transport。</p>
 */
public interface DhDryRunTransport {

    /**
     * 发送已签名 dry-run 请求。
     *
     * @param request 已包含 endpoint、timeout、headers 和 JSON body 的请求摘要
     * @return fake/transport response；不得包含 credential 或 raw provider payload
     */
    DhDryRunTransportResponse send(DhDryRunTransportRequest request);
}
