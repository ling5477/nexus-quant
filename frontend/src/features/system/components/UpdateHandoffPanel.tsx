import {useEffect, useRef, useState} from 'react';
import {Alert, App, Button, Card, Descriptions, Form, Input, Space, Typography} from 'antd';
import {useTranslation} from 'react-i18next';
import {useAuthStore} from '@/store/auth-store';
import {apiClient} from '@/api/client';
import {readRuntimeVersion, type RuntimeVersionSnapshot} from '../api/version';
import {
    confirmUpdateRequest,
    createUpdateCheck,
    importUpdateReceipt,
    requestText,
    restoreUpdateHandoff,
    updateRuntimeMatches,
    type UpdateHandoffState,
    type UpdateRequest,
} from '../update-handoff';

/** Web仅管理稳定请求和回执；宿主原安装器始终拥有制品校验、确认、安装及回滚。 */
export function UpdateHandoffPanel({runtime}: {runtime?: RuntimeVersionSnapshot}) {
    const {t} = useTranslation();
    const {modal} = App.useApp();
    const owner = useAuthStore((s) => s.currentUser?.userId);
    const key = `nq-update-handoff-v1:${owner ?? 'anonymous'}`;
    const scope = useRef(owner);
    const verificationGeneration = useRef(0);
    scope.current = owner;
    const [state, setState] = useState<UpdateHandoffState | null>(null);
    const [problem, setProblem] = useState<string | null>(null);
    const [storageFailed, setStorageFailed] = useState(false);
    const [busy, setBusy] = useState(false);
    const [manifest, setManifest] = useState<File | null>(null);
    const [verified, setVerified] = useState<boolean | null>(null);
    const [form] = Form.useForm<{environmentId: string; trustedHash: string}>();
    useEffect(() => {
        setState(null);
        setProblem(null);
        setStorageFailed(false);
        setManifest(null);
        setVerified(null);
        const restore = () => {
            verificationGeneration.current += 1;
            setVerified(null);
            try {
                const saved = localStorage.getItem(key);
                setState(saved ? restoreUpdateHandoff(saved) : null);
            } catch {
                setStorageFailed(true);
                setProblem(t('updateHandoff.storageFailed'));
            }
        };
        restore();
        const changed = (event: StorageEvent) => {
            if (event.storageArea === localStorage && (event.key === key || event.key === null))
                restore();
        };
        window.addEventListener('storage', changed);
        return () => window.removeEventListener('storage', changed);
    }, [key, t]);
    const persist = async (next: UpdateHandoffState | null) => {
        if (!navigator.locks) throw new Error('EXCLUSIVE_STORAGE_UNAVAILABLE');
        // 捕获本次操作所见的状态，不能在异步读文件后使用其他标签的新状态作为覆盖许可。
        const expected = state ? JSON.stringify(state) : null;
        await navigator.locks.request(key, {mode: 'exclusive'}, () => {
            if (!owner || scope.current !== owner) throw new Error('SESSION_CHANGED');
            const saved = localStorage.getItem(key);
            if (saved !== expected) {
                setState(saved ? restoreUpdateHandoff(saved) : null);
                setVerified(null);
                throw new Error('HANDOFF_CHANGED_IN_ANOTHER_TAB');
            }
            // 先锁定并持久化原身份，再允许下载；归档仅适用于界面已确认的终态。
            if (next) localStorage.setItem(key, JSON.stringify(next));
            else {
                localStorage.setItem(`${key}:last-completed`, JSON.stringify(state));
                localStorage.removeItem(key);
            }
            setState(next);
            verificationGeneration.current += 1;
            setVerified(null);
        });
    };
    const execute = async (work: () => Promise<void>) => {
        setBusy(true);
        setProblem(null);
        try {
            await work();
        } catch {
            if (scope.current === owner) setProblem(t('updateHandoff.invalid'));
        } finally {
            if (scope.current === owner) setBusy(false);
        }
    };
    const download = (request: UpdateRequest) => {
        if (localStorage.getItem(key) !== JSON.stringify(state)) {
            setProblem(t('updateHandoff.invalid'));
            return;
        }
        const url = URL.createObjectURL(
            new Blob([requestText(request)], {type: 'application/json'}),
        );
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `nq-${request.action.toLowerCase()}-${request.requestId}.json`;
        anchor.click();
        URL.revokeObjectURL(url);
    };
    const current = state?.update ?? state?.check;
    const receipt = state?.update ? state.updateReceipt : state?.checkReceipt;
    const canConfirm =
        state?.checkReceipt?.status === 'CHECKED' &&
        state.checkReceipt.updateAvailable === 'true' &&
        state.checkReceipt.resultCode === 'UPDATE_ELIGIBLE' &&
        !state.update;
    const canStartNext =
        state &&
        (verified === true ||
            (!state.update &&
                (receipt?.status === 'REJECTED' ||
                    (receipt?.status === 'CHECKED' && receipt.resultCode !== 'UPDATE_ELIGIBLE'))));
    return (
        <Card size="small" title={t('updateHandoff.title')}>
            <Space direction="vertical" size={12} style={{width: '100%'}}>
                <Typography.Paragraph>{t('updateHandoff.scope')}</Typography.Paragraph>
                {problem && <Alert type="error" message={problem} />}
                {!state && runtime?.packageIdentityStatus !== 'INSTALLER_DECLARED' && (
                    <Alert type="info" message={t('updateHandoff.uninstalled')} />
                )}
                {!state && (
                    <Form
                        form={form}
                        layout="vertical"
                        onFinish={(values) =>
                            void execute(async () => {
                                if (!runtime || !manifest || manifest.size > 16384 || storageFailed)
                                    throw Error('UNAVAILABLE');
                                await persist(
                                    await createUpdateCheck(
                                        runtime,
                                        values.environmentId,
                                        values.trustedHash,
                                        new Uint8Array(await manifest.arrayBuffer()),
                                    ),
                                );
                            })
                        }
                    >
                        <Form.Item
                            name="environmentId"
                            label={t('updateHandoff.environment')}
                            rules={[{required: true, pattern: /^[a-z0-9][a-z0-9-]{0,48}$/}]}
                        >
                            <Input
                                maxLength={49}
                                placeholder="nexusquant"
                                disabled={busy || storageFailed}
                            />
                        </Form.Item>
                        <Form.Item
                            name="trustedHash"
                            label={t('updateHandoff.trustedHash')}
                            rules={[{required: true, pattern: /^[a-f0-9]{64}$/}]}
                        >
                            <Input
                                maxLength={64}
                                autoComplete="off"
                                disabled={busy || storageFailed}
                            />
                        </Form.Item>
                        <Form.Item label={t('updateHandoff.manifest')}>
                            <input
                                type="file"
                                aria-label={t('updateHandoff.manifest')}
                                accept=".env"
                                disabled={busy || storageFailed}
                                onChange={(e) => setManifest(e.target.files?.[0] ?? null)}
                            />
                        </Form.Item>
                        <Button
                            type="primary"
                            htmlType="submit"
                            loading={busy}
                            disabled={
                                storageFailed ||
                                !manifest ||
                                runtime?.packageIdentityStatus !== 'INSTALLER_DECLARED'
                            }
                        >
                            {t('updateHandoff.prepare')}
                        </Button>
                    </Form>
                )}
                {state && current && (
                    <>
                        <Descriptions size="small" column={{xs: 1, md: 2}}>
                            <Descriptions.Item label={t('updateHandoff.environment')}>
                                {current.environmentId}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('updateHandoff.versions')}>
                                {current.sourceVersion} → {current.targetVersion}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('updateHandoff.requestId')}>
                                <Typography.Text style={{overflowWrap: 'anywhere'}} copyable>
                                    {current.requestId}
                                </Typography.Text>
                            </Descriptions.Item>
                            <Descriptions.Item label={t('updateHandoff.installationId')}>
                                <Typography.Text style={{overflowWrap: 'anywhere'}}>
                                    {state.checkReceipt?.installationId ??
                                        t('updateHandoff.unknown')}
                                </Typography.Text>
                            </Descriptions.Item>
                            <Descriptions.Item label={t('updateHandoff.targetHash')} span={2}>
                                <Typography.Text style={{overflowWrap: 'anywhere'}} copyable>
                                    {current.targetManifestSha256}
                                </Typography.Text>
                            </Descriptions.Item>
                        </Descriptions>
                        <Alert
                            type={
                                verified === true
                                    ? 'success'
                                    : receipt?.status === 'REJECTED' || receipt?.status === 'FAILED'
                                      ? 'error'
                                      : 'info'
                            }
                            message={
                                verified === true
                                    ? t('updateHandoff.runtimeVerified')
                                    : t(`updateHandoff.status_${receipt?.status ?? 'UNKNOWN'}`)
                            }
                            description={
                                receipt
                                    ? `${receipt.resultCode} · ${receipt.completedAt}`
                                    : t('updateHandoff.awaitReceipt')
                            }
                        />
                        {verified === false && (
                            <Alert type="warning" message={t('updateHandoff.runtimeMismatch')} />
                        )}
                        <Space wrap>
                            <Button
                                disabled={busy || storageFailed}
                                onClick={() => download(current)}
                            >
                                {t(
                                    state.update
                                        ? 'updateHandoff.downloadUpdate'
                                        : 'updateHandoff.downloadCheck',
                                )}
                            </Button>
                            <label>
                                <Typography.Text>
                                    {t('updateHandoff.importReceipt')}{' '}
                                </Typography.Text>
                                <input
                                    type="file"
                                    aria-label={t('updateHandoff.importReceipt')}
                                    accept=".json"
                                    disabled={busy || storageFailed}
                                    onChange={(e) => {
                                        const file = e.target.files?.[0];
                                        e.target.value = '';
                                        if (file)
                                            void execute(async () => {
                                                if (file.size > 16384) throw Error('TOO_LARGE');
                                                await persist(
                                                    await importUpdateReceipt(
                                                        state,
                                                        new Uint8Array(await file.arrayBuffer()),
                                                    ),
                                                );
                                            });
                                    }}
                                />
                            </label>
                            {canConfirm && (
                                <Button
                                    type="primary"
                                    disabled={busy || storageFailed}
                                    onClick={() =>
                                        modal.confirm({
                                            title: t('updateHandoff.confirmTitle'),
                                            content: t('updateHandoff.confirmMeaning'),
                                            okText: t('updateHandoff.confirm'),
                                            cancelText: t('cancel'),
                                            onOk: () =>
                                                execute(async () => {
                                                    await persist(confirmUpdateRequest(state));
                                                }),
                                        })
                                    }
                                >
                                    {t('updateHandoff.confirm')}
                                </Button>
                            )}
                            {state.updateReceipt?.status === 'SUCCEEDED' && (
                                <Button
                                    loading={busy}
                                    onClick={() =>
                                        void execute(async () => {
                                            const generation = ++verificationGeneration.current;
                                            const originalState = JSON.stringify(state);
                                            setVerified(null);
                                            const [version, health] = await Promise.all([
                                                readRuntimeVersion(new AbortController().signal),
                                                apiClient.get<{status: string}>('/system/health'),
                                            ]);
                                            // 其他标签可能已归档并开始下一次检查；旧请求迟到的成功不得给新UNKNOWN染绿。
                                            if (
                                                scope.current === owner &&
                                                generation === verificationGeneration.current &&
                                                localStorage.getItem(key) === originalState
                                            )
                                                setVerified(
                                                    updateRuntimeMatches(
                                                        state,
                                                        version,
                                                        health.data.status,
                                                    ),
                                                );
                                        })
                                    }
                                >
                                    {t('updateHandoff.verifyRuntime')}
                                </Button>
                            )}
                            {canStartNext && (
                                <Button
                                    disabled={busy || storageFailed}
                                    onClick={() =>
                                        void execute(async () => {
                                            // 只归档已确认终态；UNKNOWN保留原请求，不能借新检查绕开未确认执行。
                                            await persist(null);
                                            setManifest(null);
                                            setVerified(null);
                                            form.resetFields();
                                        })
                                    }
                                >
                                    {t('updateHandoff.newCheck')}
                                </Button>
                            )}
                        </Space>
                        <Typography.Paragraph type="secondary">
                            {t('updateHandoff.hostInstructions')}
                        </Typography.Paragraph>
                        <Typography.Paragraph code style={{overflowWrap: 'anywhere'}}>
                            {current.action === 'CHECK'
                                ? 'nexusquant.ps1 -Action check-update -UpdateRequestFile <request.json> -InstallRoot <installed> -TargetPackageRoot <target> -TrustedManifestSha256 <trusted-hash>'
                                : 'nexusquant.ps1 -Action update -UpdateRequestFile <request.json> -InstallRoot <installed> -TargetPackageRoot <target> -TrustedManifestSha256 <trusted-hash>'}
                        </Typography.Paragraph>
                        <Typography.Paragraph
                            code
                            style={{overflowWrap: 'anywhere'}}
                        >{`NQ_INSTALL_ROOT='<installed>' sh '<installed>/runtime/nexusquant.sh' ${current.action === 'CHECK' ? 'check-update' : 'update'} --handoff-request '<request.json>' --package '<target>' --manifest-sha256 '<trusted-hash>'`}</Typography.Paragraph>
                        <Typography.Paragraph type="secondary">
                            {t('updateHandoff.receiptBoundary')}
                        </Typography.Paragraph>
                    </>
                )}
            </Space>
        </Card>
    );
}
