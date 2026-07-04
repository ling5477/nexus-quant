package com.guidinglight.nexusquant.integration.dh;

import java.util.List;

/**
 * DhDryRunHeaderNames 固定 NQ -> DH dry-run 的 canonical `X-NQ-DH-*` header 名称。
 *
 * <p>Why: header 名称本身不进入 HMAC material，但 header 值必须与 payload 安全绑定。集中常量能避免误用
 * legacy `X-DH-NQ-*` 或匿名 source。</p>
 */
public final class DhDryRunHeaderNames {

    /** requestId header。 */
    public static final String REQUEST_ID = "X-NQ-DH-Request-Id";
    /** traceId header。 */
    public static final String TRACE_ID = "X-NQ-DH-Trace-Id";
    /** tenantId header。 */
    public static final String TENANT_ID = "X-NQ-DH-Tenant-Id";
    /** source header；当前只允许 NQ_DRYRUN。 */
    public static final String SOURCE = "X-NQ-DH-Source";
    /** RFC3339 UTC Z timestamp header。 */
    public static final String TIMESTAMP = "X-NQ-DH-Timestamp";
    /** 单次请求唯一 nonce header。 */
    public static final String NONCE = "X-NQ-DH-Nonce";
    /** schema version header。 */
    public static final String SCHEMA_VERSION = "X-NQ-DH-Schema-Version";
    /** HMAC-SHA256 signature header。 */
    public static final String SIGNATURE = "X-NQ-DH-Signature";

    private static final List<String> ALL = List.of(
            REQUEST_ID,
            TRACE_ID,
            TENANT_ID,
            SOURCE,
            TIMESTAMP,
            NONCE,
            SCHEMA_VERSION,
            SIGNATURE);

    private DhDryRunHeaderNames() {
    }

    /**
     * 返回 canonical headers 的完整集合。
     *
     * @return 不可变 header name list，用于 request generation 测试
     */
    public static List<String> all() {
        return ALL;
    }
}
