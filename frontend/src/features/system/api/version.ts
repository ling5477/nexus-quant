import {apiClient} from '@/api/client';

/** 服务制品与安装器包身份分别展示；包元数据并不代表签名认证或最新版本查询。 */
export interface RuntimeVersionSnapshot {
    serviceVersion: string | null;
    serviceArtifact: string | null;
    installedPackageVersion: string | null;
    sourceCommit: string | null;
    releaseSourceHash: string | null;
    packageManifestSha256: string | null;
    packagedSchemaVersion: string | null;
    packageIdentityStatus: 'INSTALLER_DECLARED' | 'UNAVAILABLE' | 'INVALID';
    automaticUpdate: boolean;
    updateExecutor: 'HOST_INSTALLER';
    reasons: string[];
}

/** 正式读取没有文件路径或命令参数，不把浏览器状态当作已安装版本。 */
export async function readRuntimeVersion(signal: AbortSignal): Promise<RuntimeVersionSnapshot> {
    return (await apiClient.get<RuntimeVersionSnapshot>('/system/version', {signal})).data;
}
