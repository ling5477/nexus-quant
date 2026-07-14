package com.guidinglight.nexusquant.trading.application.riskpreflight;

import java.util.List;
import java.util.Objects;

/**
 * GateW-3 risk preflight 可消费的 credential-material-free 本地事实快照。
 *
 * <p>所有成员均由 internal server-side orchestration 构造。该合同不包含 API key、secret、
 * passphrase、private header、raw provider payload 或任意远端调用入口。</p>
 *
 * @param localAccountMetadata 本地账户 metadata；只表达存在性、scope 与本地状态
 * @param credentialMetadata   credential metadata 摘要；不包含 masked key 或 material
 * @param marketdataQuality    本地 marketdata quality 诊断状态
 */
public record RiskPreflightFactBundle(
        LocalAccountMetadataSnapshot localAccountMetadata,
        CredentialMetadataSummary credentialMetadata,
        MarketdataQualitySnapshot marketdataQuality
) {

    public RiskPreflightFactBundle {
        Objects.requireNonNull(localAccountMetadata, "localAccountMetadata must not be null");
        Objects.requireNonNull(credentialMetadata, "credentialMetadata must not be null");
        Objects.requireNonNull(marketdataQuality, "marketdataQuality must not be null");
    }

    /** 只读账户 metadata；configured=false 时其余字段允许为 null。 */
    public record LocalAccountMetadataSnapshot(
            boolean configured,
            String exchange,
            String marketType,
            String tradeEnvironment,
            String localStatus
    ) {
    }

    /**
     * Credential metadata 摘要。
     *
     * <p>activeSummaryCount 用于显式识别无配置或多 active 冲突；列表只允许生命周期与探活状态名称。</p>
     */
    public record CredentialMetadataSummary(
            boolean configured,
            int activeSummaryCount,
            List<String> credentialTypes,
            List<String> verificationStatuses,
            List<String> permissionProbeStatuses
    ) {
        public CredentialMetadataSummary {
            if (activeSummaryCount < 0) {
                throw new IllegalArgumentException("activeSummaryCount must not be negative");
            }
            credentialTypes = List.copyOf(credentialTypes == null ? List.of() : credentialTypes);
            verificationStatuses = List.copyOf(
                    verificationStatuses == null ? List.of() : verificationStatuses
            );
            permissionProbeStatuses = List.copyOf(
                    permissionProbeStatuses == null ? List.of() : permissionProbeStatuses
            );
        }
    }

    /** 本地 marketdata quality 仅是 diagnostic fact，OK 也不表示 trading ready。 */
    public record MarketdataQualitySnapshot(Quality quality) {
        public MarketdataQualitySnapshot {
            Objects.requireNonNull(quality, "quality must not be null");
        }

        public enum Quality {
            OK,
            WARNING,
            BLOCKED,
            UNKNOWN
        }
    }
}
