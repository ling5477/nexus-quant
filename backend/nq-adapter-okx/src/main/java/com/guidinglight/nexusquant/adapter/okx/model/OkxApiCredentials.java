package com.guidinglight.nexusquant.adapter.okx.model;

/**
 * OkxApiCredentials 表示 OKX v5 的鉴权凭证集合。
 * <p>
 * Why:
 * Signer 与 HTTP client 都需要稳定访问 key/secret/passphrase，单独建模可以避免把环境变量读取散落到每次请求中。
 * 这里不在日志或异常里暴露 secret，防止敏感信息泄露。
 *
 * @param apiKey OKX API key
 * @param secretKey OKX secret key
 * @param passphrase OKX passphrase
 */
public record OkxApiCredentials(
        String apiKey,
        String secretKey,
        String passphrase
) {

    /**
     * 返回未配置占位凭证。
     * <p>
     * Why:
     * No-real hardening (GateL-1B-B)：runtime config 默认不再从进程环境读取 credential material，
     * 统一返回该占位凭证（{@link #isConfigured()} = false）。真实 credential 必须由后续 NQ credential
     * governance bridge 注入（另起 Gate），不得从 env / system property / .env 派生。
     *
     * @return 全空、未配置的凭证占位
     */
    public static OkxApiCredentials unconfigured() {
        return new OkxApiCredentials("", "", "");
    }

    /**
     * 判断凭证是否完整。
     *
     * @return true 表示可用于签名请求
     */
    public boolean isConfigured() {
        return isPresent(apiKey) && isPresent(secretKey) && isPresent(passphrase);
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
