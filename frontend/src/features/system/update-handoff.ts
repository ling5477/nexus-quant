import type {RuntimeVersionSnapshot} from './api/version';

export const handoffFields = [
    'format',
    'action',
    'requestId',
    'checkRequestId',
    'checkReceiptSha256',
    'installationId',
    'environmentId',
    'sourceVersion',
    'sourceManifestSha256',
    'sourceCommit',
    'targetVersion',
    'targetManifestSha256',
    'targetArtifactSha256',
    'targetArchitecture',
    'targetSourceCommit',
] as const;
const receiptFields = [
    ...handoffFields,
    'status',
    'updateAvailable',
    'transactionId',
    'resultCode',
    'completedAt',
    'installedVersion',
    'installedManifestSha256',
    'healthStatus',
] as const;
export type UpdateRequest = Record<(typeof handoffFields)[number], string>;
export type UpdateReceipt = UpdateRequest &
    Record<Exclude<(typeof receiptFields)[number], keyof UpdateRequest>, string>;
export interface UpdateHandoffState {
    check: UpdateRequest;
    checkReceipt?: UpdateReceipt;
    checkReceiptSha256?: string;
    update?: UpdateRequest;
    updateReceipt?: UpdateReceipt;
    consumed: string[];
}

const uuid = /^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/;
const hash = /^[a-f0-9]{64}$/;
const commit = /^[a-f0-9]{40}$/;
const version = /^(0|[1-9][0-9]{0,5})\.(0|[1-9][0-9]{0,5})\.(0|[1-9][0-9]{0,5})$/;
const manifestFields = [
    'FORMAT',
    'VERSION',
    'SOURCE_SHA',
    'RELEASE_SOURCE_HASH',
    'SCHEMA_VERSION',
    'ARCH',
    'ARCHIVE',
    'ARCHIVE_SHA256',
    'BACKEND_IMAGE',
    'FRONTEND_IMAGE',
    'POSTGRES_IMAGE',
    'BACKEND_CONFIG_DIGEST',
    'FRONTEND_CONFIG_DIGEST',
    'POSTGRES_CONFIG_DIGEST',
    'COMPOSE_SHA256',
    'RUNTIME_SHA256',
    'PS_INSTALLER_SHA256',
    'SH_INSTALLER_SHA256',
];

function requireValue(valid: unknown): asserts valid {
    if (!valid) throw new Error('INVALID_HANDOFF');
}

/** 只接受有界、唯一键的平面字符串合同；不执行导入文件中的命令，也不接受JSON重复键覆盖身份。 */
function parseFlat(text: string, fields: readonly string[]): Record<string, string> {
    requireValue(text.length <= 16384 && /^[\x20-\x7e\r\n\t]*$/.test(text));
    const entries = [...text.matchAll(/"([A-Za-z][A-Za-z0-9]*)"\s*:\s*"([A-Za-z0-9_.:+-]+)"/g)];
    requireValue(
        entries.length === fields.length &&
            new Set(entries.map((e) => e[1])).size === fields.length,
    );
    const parsed = JSON.parse(text) as Record<string, unknown>;
    requireValue(Object.keys(parsed).length === fields.length);
    for (const field of fields)
        requireValue(
            typeof parsed[field] === 'string' &&
                entries.some((e) => e[1] === field && e[2] === parsed[field]),
        );
    return parsed as Record<string, string>;
}

function validateRequest(value: UpdateRequest) {
    requireValue(
        value.format === 'NQ_UPDATE_HANDOFF_V1' && ['CHECK', 'UPDATE'].includes(value.action),
    );
    requireValue(
        uuid.test(value.requestId) && /^[a-z0-9][a-z0-9-]{0,48}$/.test(value.environmentId),
    );
    requireValue(version.test(value.sourceVersion) && version.test(value.targetVersion));
    requireValue(
        hash.test(value.sourceManifestSha256) &&
            hash.test(value.targetManifestSha256) &&
            hash.test(value.targetArtifactSha256),
    );
    requireValue(
        commit.test(value.sourceCommit) &&
            commit.test(value.targetSourceCommit) &&
            ['amd64', 'arm64'].includes(value.targetArchitecture),
    );
    requireValue(
        value.action === 'CHECK'
            ? value.checkRequestId === 'NONE' &&
                  value.checkReceiptSha256 === 'NONE' &&
                  value.installationId === 'UNCONFIRMED'
            : uuid.test(value.checkRequestId) &&
                  hash.test(value.checkReceiptSha256) &&
                  uuid.test(value.installationId),
    );
}

export async function bytesSha256(bytes: Uint8Array): Promise<string> {
    const digest = await crypto.subtle.digest('SHA-256', new Uint8Array(bytes).buffer);
    return [...new Uint8Array(digest)].map((v) => v.toString(16).padStart(2, '0')).join('');
}

/** 目标清单必须匹配用户从独立可信渠道取得的hash；此处只校验身份，完整包和宿主前提仍由原安装器校验。 */
export async function createUpdateCheck(
    runtime: RuntimeVersionSnapshot,
    environmentId: string,
    trustedHash: string,
    manifestBytes: Uint8Array,
): Promise<UpdateHandoffState> {
    requireValue(runtime.packageIdentityStatus === 'INSTALLER_DECLARED' && hash.test(trustedHash));
    requireValue(
        manifestBytes.length > 0 &&
            manifestBytes.length <= 16384 &&
            (await bytesSha256(manifestBytes)) === trustedHash,
    );
    const text = new TextDecoder('utf-8', {fatal: true}).decode(manifestBytes);
    const entries = text
        .trim()
        .split(/\r?\n/)
        .map((line) => line.split('='));
    requireValue(
        entries.length === manifestFields.length &&
            new Set(entries.map((e) => e[0])).size === entries.length,
    );
    requireValue(
        entries.every(
            (e) =>
                e.length === 2 &&
                manifestFields.includes(e[0]) &&
                /^[A-Za-z0-9_.:/+-]+$/.test(e[1]),
        ),
    );
    const manifest = Object.fromEntries(entries);
    requireValue(manifest.FORMAT === '1' && manifest.ARCHIVE === `images-${manifest.ARCH}.tar`);
    const check: UpdateRequest = {
        format: 'NQ_UPDATE_HANDOFF_V1',
        action: 'CHECK',
        requestId: crypto.randomUUID(),
        checkRequestId: 'NONE',
        checkReceiptSha256: 'NONE',
        installationId: 'UNCONFIRMED',
        environmentId,
        sourceVersion: runtime.installedPackageVersion ?? '',
        sourceManifestSha256: runtime.packageManifestSha256 ?? '',
        sourceCommit: runtime.sourceCommit ?? '',
        targetVersion: manifest.VERSION,
        targetManifestSha256: trustedHash,
        targetArtifactSha256: manifest.ARCHIVE_SHA256,
        targetArchitecture: manifest.ARCH,
        targetSourceCommit: manifest.SOURCE_SHA,
    };
    validateRequest(check);
    return {check, consumed: []};
}

export function requestText(request: UpdateRequest): string {
    validateRequest(request);
    return (
        JSON.stringify(
            Object.fromEntries(handoffFields.map((key) => [key, request[key]])),
            null,
            2,
        ) + '\n'
    );
}

/** 只有匹配原请求的回执可推进界面；相关性校验不是数字签名认证，执行端仍须核验本地持久检查结果。 */
function receiptFor(text: string, expected: UpdateRequest): UpdateReceipt {
    const parsed = parseFlat(text, receiptFields) as UpdateReceipt;
    validateRequest(expected);
    for (const key of handoffFields) {
        if (expected.action === 'CHECK' && key === 'installationId')
            requireValue(uuid.test(parsed[key]));
        else requireValue(parsed[key] === expected[key]);
    }
    requireValue(['CHECKED', 'REJECTED', 'UNKNOWN', 'SUCCEEDED', 'FAILED'].includes(parsed.status));
    requireValue(['true', 'false', 'UNKNOWN'].includes(parsed.updateAvailable));
    requireValue(['NOT_CHECKED', 'VERIFIED', 'UNKNOWN'].includes(parsed.healthStatus));
    requireValue(
        /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/.test(parsed.completedAt) &&
            Number.isFinite(Date.parse(parsed.completedAt)),
    );
    requireValue(parsed.installedVersion === 'UNKNOWN' || version.test(parsed.installedVersion));
    requireValue(
        parsed.installedManifestSha256 === 'UNKNOWN' || hash.test(parsed.installedManifestSha256),
    );
    requireValue(
        expected.action === 'CHECK'
            ? ['CHECKED', 'REJECTED', 'UNKNOWN'].includes(parsed.status)
            : parsed.status !== 'CHECKED',
    );
    if (parsed.status === 'CHECKED')
        requireValue(
            parsed.installedVersion === expected.sourceVersion &&
                parsed.installedManifestSha256 === expected.sourceManifestSha256,
        );
    if (parsed.status === 'SUCCEEDED')
        requireValue(
            parsed.healthStatus === 'VERIFIED' &&
                parsed.installedVersion === expected.targetVersion &&
                parsed.installedManifestSha256 === expected.targetManifestSha256,
        );
    return parsed;
}

export async function importUpdateReceipt(
    state: UpdateHandoffState,
    bytes: Uint8Array,
): Promise<UpdateHandoffState> {
    requireValue(bytes.length <= 16384 && state.consumed.length < 16);
    const expected = state.update ?? state.check;
    const parsed = receiptFor(new TextDecoder('utf-8', {fatal: true}).decode(bytes), expected);
    const digest = await bytesSha256(bytes);
    requireValue(!state.consumed.includes(digest));
    // UNKNOWN可导入后续终态；已经确认的终态不允许被另一份回执覆盖。
    const prior = state.update ? state.updateReceipt : state.checkReceipt;
    requireValue(!prior || prior.status === 'UNKNOWN');
    return expected.action === 'CHECK'
        ? {
              ...state,
              checkReceipt: parsed,
              checkReceiptSha256: digest,
              consumed: [...state.consumed, digest],
          }
        : {...state, updateReceipt: parsed, consumed: [...state.consumed, digest]};
}

/** 用户确认后才固定UPDATE身份；重复下载始终复用同一UUID，等待结果时不生成新请求绕过宿主幂等。 */
export function confirmUpdateRequest(state: UpdateHandoffState): UpdateHandoffState {
    if (state.update) return state;
    requireValue(
        state.checkReceipt?.status === 'CHECKED' &&
            state.checkReceipt.updateAvailable === 'true' &&
            state.checkReceipt.resultCode === 'UPDATE_ELIGIBLE' &&
            state.checkReceiptSha256,
    );
    const update: UpdateRequest = {
        ...state.check,
        action: 'UPDATE',
        requestId: crypto.randomUUID(),
        checkRequestId: state.check.requestId,
        checkReceiptSha256: state.checkReceiptSha256,
        installationId: state.checkReceipt.installationId,
    };
    validateRequest(update);
    return {...state, update};
}

export function updateRuntimeMatches(
    state: UpdateHandoffState,
    runtime: RuntimeVersionSnapshot,
    health: string,
): boolean {
    return (
        state.updateReceipt?.status === 'SUCCEEDED' &&
        health === 'UP' &&
        runtime.packageIdentityStatus === 'INSTALLER_DECLARED' &&
        runtime.installedPackageVersion === state.check.targetVersion &&
        runtime.packageManifestSha256 === state.check.targetManifestSha256 &&
        runtime.sourceCommit === state.check.targetSourceCommit
    );
}

/** 只恢复已保存的请求与阅读进度；损坏存储显式报错，不能静默丢掉未确认的更新身份。 */
export function restoreUpdateHandoff(text: string): UpdateHandoffState {
    requireValue(text.length <= 65536);
    const state = JSON.parse(text) as UpdateHandoffState;
    validateRequest(state.check);
    requireValue(state.check.action === 'CHECK');
    requireValue(
        Array.isArray(state.consumed) &&
            state.consumed.length <= 16 &&
            state.consumed.every((v) => hash.test(v)),
    );
    if (state.checkReceipt) {
        receiptFor(JSON.stringify(state.checkReceipt), state.check);
        requireValue(
            state.checkReceiptSha256 &&
                hash.test(state.checkReceiptSha256) &&
                state.consumed.includes(state.checkReceiptSha256),
        );
    }
    if (state.update) {
        validateRequest(state.update);
        requireValue(
            state.checkReceipt?.status === 'CHECKED' &&
                state.checkReceipt.updateAvailable === 'true' &&
                state.checkReceipt.resultCode === 'UPDATE_ELIGIBLE',
        );
        requireValue(
            state.update.action === 'UPDATE' &&
                state.update.checkRequestId === state.check.requestId &&
                state.update.checkReceiptSha256 === state.checkReceiptSha256 &&
                state.update.installationId === state.checkReceipt.installationId,
        );
        for (const key of [
            'environmentId',
            'sourceVersion',
            'sourceManifestSha256',
            'sourceCommit',
            'targetVersion',
            'targetManifestSha256',
            'targetArtifactSha256',
            'targetArchitecture',
            'targetSourceCommit',
        ] as const)
            requireValue(state.update[key] === state.check[key]);
    }
    if (state.updateReceipt) {
        requireValue(state.update);
        receiptFor(JSON.stringify(state.updateReceipt), state.update);
    }
    return state;
}
