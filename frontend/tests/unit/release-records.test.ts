import {describe, expect, it} from 'vitest';
import {installedRelease, releaseRecords, whatsNewKey} from '../../src/features/system/release-records';
import type {RuntimeVersionSnapshot} from '../../src/features/system/api/version';

const runtime: RuntimeVersionSnapshot = {
    serviceVersion: '0.1.0-SNAPSHOT', serviceArtifact: 'nq-app',
    installedPackageVersion: '1.0.1', sourceCommit: 'a'.repeat(40),
    releaseSourceHash: 'b'.repeat(64), packageManifestSha256: 'c'.repeat(64),
    packagedSchemaVersion: '47', packageIdentityStatus: 'INSTALLER_DECLARED',
    automaticUpdate: false, updateExecutor: 'HOST_INSTALLER', reasons: [],
};

describe('版本说明与安装身份', () => {
    it('已发布版本具有日期与真实链接，候选不伪造发布身份', () => {
        const published = releaseRecords.find((r) => r.version === '1.0.0')!;
        expect(published.status).toBe('RELEASED');
        expect(published.releaseDate).toBe('2026-10-20');
        expect(published.releaseUrl).toBe('https://github.com/ling5477/nexus-quant/releases/tag/v1.0.0');
        const previous = installedRelease(runtime)!;
        expect(previous.status).toBe('RELEASED');
        expect(previous.releaseDate).toBe('2026-10-25');
        expect(previous.releaseUrl).toBe('https://github.com/ling5477/nexus-quant/releases/tag/v1.0.1');
        const candidate = installedRelease({...runtime, installedPackageVersion: '1.0.2', packagedSchemaVersion: '49'})!;
        expect(candidate.status).toBe('CANDIDATE');
        expect(candidate.releaseUrl).toBeNull();
        expect(candidate.releaseDate).toBeNull();
    });
    it('每个版本的全部结构化字段都有中英文内容', () => {
        for (const record of releaseRecords) {
            for (const field of ['features', 'improvements', 'bugFixes', 'breakingChanges', 'knownIssues'] as const) {
                for (const item of record[field]) {
                    expect(item['zh-CN'].length).toBeGreaterThan(0);
                    expect(item['en-US'].length).toBeGreaterThan(0);
                }
            }
        }
    });
    it('未知或无效安装身份不能用根VERSION制造更新提示', () => {
        expect(installedRelease({...runtime, packageIdentityStatus: 'UNAVAILABLE'})).toBeUndefined();
        expect(installedRelease({...runtime, installedPackageVersion: '9.0.0'})).toBeUndefined();
        expect(whatsNewKey(1, {...runtime, packageIdentityStatus: 'INVALID'})).toBeNull();
        expect(whatsNewKey(1, {...runtime, packageManifestSha256: null})).toBeNull();
        expect(whatsNewKey(0, runtime)).toBeNull();
        expect(whatsNewKey(undefined, runtime)).toBeNull();
    });
    it('阅读标记按用户、安装包和实际安装版本隔离，与服务Maven版本无关', () => {
        const key = whatsNewKey(1, runtime);
        expect(key).toContain(':1.0.1');
        expect(whatsNewKey(2, runtime)).not.toBe(key);
        expect(whatsNewKey(1, {...runtime, packageManifestSha256: 'd'.repeat(64)})).not.toBe(key);
        expect(whatsNewKey(1, {...runtime, installedPackageVersion: '1.0.0'})).not.toBe(key);
        expect(whatsNewKey(1, {...runtime, serviceVersion: 'different'})).toBe(key);
    });
});
