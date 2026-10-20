import {describe, expect, it} from 'vitest';
import {
    bytesSha256,
    confirmUpdateRequest,
    createUpdateCheck,
    importUpdateReceipt,
    requestText,
    restoreUpdateHandoff,
    updateRuntimeMatches,
    type UpdateRequest,
} from '../../src/features/system/update-handoff';
import type {RuntimeVersionSnapshot} from '../../src/features/system/api/version';

const source: RuntimeVersionSnapshot = {
    serviceVersion: '0.1.0-SNAPSHOT',
    serviceArtifact: 'nq-app',
    installedPackageVersion: '1.0.0',
    sourceCommit: 'a'.repeat(40),
    releaseSourceHash: 'b'.repeat(64),
    packageManifestSha256: 'c'.repeat(64),
    packagedSchemaVersion: '30',
    packageIdentityStatus: 'INSTALLER_DECLARED',
    automaticUpdate: false,
    updateExecutor: 'HOST_INSTALLER',
    reasons: [],
};
const host = '1a1a1a1a-1111-4111-8111-111111111111';
const encode = (text: string) => new TextEncoder().encode(text);
const manifest = encode(
    Object.entries({
        FORMAT: '1',
        VERSION: '1.1.0',
        SOURCE_SHA: 'd'.repeat(40),
        RELEASE_SOURCE_HASH: 'e'.repeat(64),
        SCHEMA_VERSION: '30',
        ARCH: 'amd64',
        ARCHIVE: 'images-amd64.tar',
        ARCHIVE_SHA256: 'f'.repeat(64),
        BACKEND_IMAGE: 'sha256:' + '1'.repeat(64),
        FRONTEND_IMAGE: 'sha256:' + '2'.repeat(64),
        POSTGRES_IMAGE: 'sha256:' + '3'.repeat(64),
        BACKEND_CONFIG_DIGEST: 'sha256:' + '4'.repeat(64),
        FRONTEND_CONFIG_DIGEST: 'sha256:' + '5'.repeat(64),
        POSTGRES_CONFIG_DIGEST: 'sha256:' + '6'.repeat(64),
        COMPOSE_SHA256: '7'.repeat(64),
        RUNTIME_SHA256: '8'.repeat(64),
        PS_INSTALLER_SHA256: '9'.repeat(64),
        SH_INSTALLER_SHA256: '0'.repeat(64),
    })
        .map(([k, v]) => `${k}=${v}`)
        .join('\n') + '\n',
);
const check = async () =>
    createUpdateCheck(source, 'nq-isolated', await bytesSha256(manifest), manifest);
function receipt(request: UpdateRequest, overrides: Record<string, string> = {}) {
    return encode(
        JSON.stringify(
            {
                ...request,
                installationId: host,
                status: request.action === 'CHECK' ? 'CHECKED' : 'SUCCEEDED',
                updateAvailable: 'true',
                transactionId: request.action === 'CHECK' ? 'NONE' : '20261017T120000000Z-1234abcd',
                resultCode: request.action === 'CHECK' ? 'UPDATE_ELIGIBLE' : 'UPDATE_SUCCESS',
                completedAt: '2026-10-17T12:00:00Z',
                installedVersion:
                    request.action === 'CHECK' ? request.sourceVersion : request.targetVersion,
                installedManifestSha256:
                    request.action === 'CHECK'
                        ? request.sourceManifestSha256
                        : request.targetManifestSha256,
                healthStatus: request.action === 'CHECK' ? 'NOT_CHECKED' : 'VERIFIED',
                ...overrides,
            },
            null,
            2,
        ) + '\n',
    );
}

describe('原安装器更新交接', () => {
    it('裸服务及不匹配独立hash不能生成检查请求', async () => {
        await expect(
            createUpdateCheck(
                {...source, packageIdentityStatus: 'UNAVAILABLE'},
                'nq-isolated',
                await bytesSha256(manifest),
                manifest,
            ),
        ).rejects.toThrow();
        await expect(
            createUpdateCheck(source, 'nq-isolated', '0'.repeat(64), manifest),
        ).rejects.toThrow();
    });
    it('请求刷新与重复下载保持原身份，未收到回执不能确认更新', async () => {
        const state = await check();
        const restored = restoreUpdateHandoff(JSON.stringify(state));
        expect(requestText(restored.check)).toBe(requestText(state.check));
        expect(() => confirmUpdateRequest(restored)).toThrow();
    });
    it('检查回执绑定同一请求环境和制品，拒绝重复消费及重复JSON键', async () => {
        const state = await check();
        const bytes = receipt(state.check);
        for (const mismatch of [
            {requestId: host},
            {environmentId: 'other-host'},
            {targetManifestSha256: '1'.repeat(64)},
            {installedVersion: '9.0.0'},
        ])
            await expect(
                importUpdateReceipt(state, receipt(state.check, mismatch)),
            ).rejects.toThrow();
        const accepted = await importUpdateReceipt(state, bytes);
        await expect(importUpdateReceipt(accepted, bytes)).rejects.toThrow();
        const duplicate = new TextDecoder()
            .decode(bytes)
            .replace('"action": "CHECK",', '"action": "CHECK", "action": "CHECK",');
        await expect(importUpdateReceipt(state, encode(duplicate))).rejects.toThrow();
    });
    it('确认后使用宿主原检查hash与安装身份，重复确认不生成新UUID', async () => {
        const state = await check();
        const bytes = receipt(state.check);
        const ineligible = await importUpdateReceipt(
            state,
            receipt(state.check, {resultCode: 'UPDATE_INELIGIBLE'}),
        );
        expect(() => confirmUpdateRequest(ineligible)).toThrow();
        const accepted = await importUpdateReceipt(state, bytes);
        const confirmed = confirmUpdateRequest(accepted);
        expect(confirmed.update?.checkReceiptSha256).toBe(await bytesSha256(bytes));
        expect(confirmed.update?.installationId).toBe(host);
        expect(confirmUpdateRequest(confirmed).update?.requestId).toBe(confirmed.update?.requestId);
        expect(restoreUpdateHandoff(JSON.stringify(confirmed)).update).toEqual(confirmed.update);
    });
    it('缺失回执和UNKNOWN不解释为失败，并允许同请求后续终态', async () => {
        const state = await check();
        const checked = await importUpdateReceipt(state, receipt(state.check));
        const confirmed = confirmUpdateRequest(checked);
        const pending = await importUpdateReceipt(
            confirmed,
            receipt(confirmed.update!, {
                status: 'UNKNOWN',
                healthStatus: 'UNKNOWN',
                installedVersion: 'UNKNOWN',
                installedManifestSha256: 'UNKNOWN',
            }),
        );
        expect(pending.updateReceipt?.status).toBe('UNKNOWN');
        expect(confirmUpdateRequest(pending).update).toEqual(confirmed.update);
        const done = await importUpdateReceipt(pending, receipt(confirmed.update!));
        expect(done.updateReceipt?.status).toBe('SUCCEEDED');
    });
    it('成功回执不能替代当前正式运行版本和健康核对', async () => {
        const state = await check();
        const confirmed = confirmUpdateRequest(
            await importUpdateReceipt(state, receipt(state.check)),
        );
        await expect(
            importUpdateReceipt(
                confirmed,
                receipt(confirmed.update!, {
                    installationId: '22222222-2222-4222-8222-222222222222',
                }),
            ),
        ).rejects.toThrow();
        await expect(
            importUpdateReceipt(confirmed, receipt(confirmed.update!, {healthStatus: 'UNKNOWN'})),
        ).rejects.toThrow();
        const done = await importUpdateReceipt(confirmed, receipt(confirmed.update!));
        expect(updateRuntimeMatches(done, source, 'UP')).toBe(false);
        const target = {
            ...source,
            installedPackageVersion: '1.1.0',
            packageManifestSha256: state.check.targetManifestSha256,
            sourceCommit: 'd'.repeat(40),
        };
        expect(updateRuntimeMatches(done, target, 'DOWN')).toBe(false);
        expect(updateRuntimeMatches(done, target, 'UP')).toBe(true);
    });
    it('损坏保存记录不能被当作已验证检查或跨环境更新', async () => {
        const state = await check();
        const confirmed = confirmUpdateRequest(
            await importUpdateReceipt(state, receipt(state.check)),
        );
        expect(() =>
            restoreUpdateHandoff(
                JSON.stringify({
                    ...confirmed,
                    checkReceipt: {...confirmed.checkReceipt, requestId: host},
                }),
            ),
        ).toThrow();
        expect(() =>
            restoreUpdateHandoff(
                JSON.stringify({
                    ...confirmed,
                    update: {...confirmed.update, environmentId: 'other'},
                }),
            ),
        ).toThrow();
    });
});
