package com.guidinglight.nexusquant.app.config.account;

/**
 * OKX private read-only permission probe 的非敏感显式开关与预期服务器 IP。
 * 默认关闭；expectedIp 只允许 IP literal，不允许 hostname/CIDR。
 */
public record OkxPrivateReadOnlyPermissionProbeProperties(boolean enabled, String expectedIp) {
}
