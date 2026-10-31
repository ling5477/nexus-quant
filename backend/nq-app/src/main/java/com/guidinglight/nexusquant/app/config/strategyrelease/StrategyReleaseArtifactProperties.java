package com.guidinglight.nexusquant.app.config.strategyrelease;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strategy Release artifact storage 的服务端配置。
 *
 * <p>trusted root 无默认值；缺失或空白时 capability 保持 fail-closed，不回退到 cwd、home 或 temp。
 */
@ConfigurationProperties(prefix = "nq.strategy-release.artifacts")
public class StrategyReleaseArtifactProperties {

    private String trustedRoot;

    public String getTrustedRoot() {
        return trustedRoot;
    }

    public void setTrustedRoot(String trustedRoot) {
        this.trustedRoot = trustedRoot;
    }
}
