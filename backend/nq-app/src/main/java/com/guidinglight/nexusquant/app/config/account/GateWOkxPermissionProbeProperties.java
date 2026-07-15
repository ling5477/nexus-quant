package com.guidinglight.nexusquant.app.config.account;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * GateW OKX production permission probe 的非敏感显式开关与预期服务器 IP。
 * 默认关闭；expectedIp 只允许 IP literal，不允许 hostname/CIDR。
 */
@ConfigurationProperties(prefix = "nq.gatew.okx-private-readonly.permission-probe")
public class GateWOkxPermissionProbeProperties {

    private boolean enabled;
    private String expectedIp;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getExpectedIp() {
        return expectedIp;
    }

    public void setExpectedIp(String expectedIp) {
        this.expectedIp = expectedIp;
    }
}
