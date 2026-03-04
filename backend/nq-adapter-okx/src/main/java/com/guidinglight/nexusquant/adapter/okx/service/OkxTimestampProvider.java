package com.guidinglight.nexusquant.adapter.okx.service;

/**
 * OkxTimestampProvider 抽象 OKX 请求时间戳生成。
 * <p>
 * Why:
 * OKX 签名强依赖 timestamp，若把当前时间直接写死在 HTTP client 内，就无法稳定做签名单测。
 */
@FunctionalInterface
public interface OkxTimestampProvider {

    /**
     * 生成 ISO-8601 格式的请求时间戳。
     *
     * @return 供 `OK-ACCESS-TIMESTAMP` 使用的字符串
     */
    String now();
}
