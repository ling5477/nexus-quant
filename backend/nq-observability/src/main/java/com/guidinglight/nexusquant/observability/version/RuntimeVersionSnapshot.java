package com.guidinglight.nexusquant.observability.version;

import java.util.List;

/** 服务制品版本与安装器声明的包身份分别呈现；缺失字段保持null，不由根目录VERSION或Git工作区代填。 */
public record RuntimeVersionSnapshot(
        String serviceVersion,
        String serviceArtifact,
        String installedPackageVersion,
        String sourceCommit,
        String releaseSourceHash,
        String packageManifestSha256,
        String packagedSchemaVersion,
        String packageIdentityStatus,
        boolean automaticUpdate,
        String updateExecutor,
        List<String> reasons) {
    public RuntimeVersionSnapshot {
        reasons = List.copyOf(reasons);
    }
}
