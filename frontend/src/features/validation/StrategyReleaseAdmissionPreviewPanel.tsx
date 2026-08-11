import {PlusOutlined, RedoOutlined, ReloadOutlined} from '@ant-design/icons';
import {Alert, Button, Card, Descriptions, Modal, Skeleton, Space, Typography} from 'antd';
import {useEffect, useState} from 'react';

import {formatApiError} from '@/api/errors';
import {useStrategyReleaseShadowRunMaterialization} from '@/hooks/useStrategyReleaseQueries';
import {StatusTag} from '@/nq-design-system/status/StatusTag';
import {useAuthStore} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';
import type {
    StrategyReleaseAdmissionPreviewResponse,
    StrategyReleaseShadowRunMaterializationResponse,
} from '@/types/strategy-releases';

const {Text} = Typography;

interface AdmissionPreviewQueryState {
    data?: StrategyReleaseAdmissionPreviewResponse;
    isLoading: boolean;
    isFetching: boolean;
    isError: boolean;
    error: unknown;
    refetch: () => unknown;
}

const REASON_TEXT: Record<string, string> = {
    ELIGIBLE_FOR_CREATION_PLAN_ONLY: '允许受控创建未启动的 CREATED Shadow Run',
    ARTIFACT_LOCATION_UNBOUND: '历史发布未绑定服务端制品位置',
    ARTIFACT_ROOT_NOT_CONFIGURED: '服务端可信制品根目录未配置',
    ARTIFACT_LOCATION_UNSAFE: '服务端制品位置未通过安全校验',
    ARTIFACT_MANIFEST_INVALID: '制品清单无效',
    ARTIFACT_RELEASE_IDENTITY_MISMATCH: '制品身份与发布记录不一致',
    RELEASE_REJECTED: 'Strategy Release 已拒绝',
    ARTIFACT_NOT_VERIFIED: '制品尚未通过验证',
    RELEASE_BINDING_REQUIRED: '缺少已验证的 Release 绑定',
    VALIDATION_EVIDENCE_MISSING: '验证证据缺失',
    VALIDATION_EVIDENCE_STALE: '验证证据已过期',
    VALIDATION_NOT_APPROVED: '验证结论未通过',
    SHADOW_WINDOW_MISSING: 'Shadow 观察窗口缺失',
    SHADOW_WINDOW_INVALID: 'Shadow 观察窗口无效',
    AUTHORIZATION_BOUNDARY_MISSING: '只读授权边界缺失',
    AUTHORIZATION_BOUNDARY_INVALID: '授权边界不允许进入预览',
    SIDE_EFFECT_POLICY_MISSING: '无副作用策略缺失',
    NO_ORDER_SUBMISSION_REQUIRED: '必须禁止订单提交',
    NO_CREDENTIAL_ACCESS_REQUIRED: '必须禁止凭证访问',
    NO_PRIVATE_ENDPOINT_REQUIRED: '必须禁止私有接口调用',
    NO_LEDGER_MUTATION_REQUIRED: '必须禁止 ledger 写入',
    NO_ACCOUNT_MUTATION_REQUIRED: '必须禁止账户写入',
    NO_EXTERNAL_PRIVATE_IO_REQUIRED: '必须禁止外部私有 IO',
};

function statusLabel(status: string | null | undefined): string {
    switch (status?.toUpperCase()) {
        case 'VERIFIED': return '已验证';
        case 'APPROVED': return '验证通过';
        case 'ELIGIBLE': return '可进入 Shadow';
        case 'BLOCKED': return '已阻断';
        case 'REJECTED': return '已拒绝';
        case 'RELEASE_BOUND': return 'Release 已绑定';
        case 'LEGACY_UNBOUND':
        case 'LEGACY_PUBLISH_ONLY': return '历史未绑定';
        case 'NO_EVIDENCE': return '无验证证据';
        case 'STALE_EVIDENCE': return '验证证据过期';
        case 'NEEDS_REVIEW': return '需要复核';
        default: return '不可用';
    }
}

function isNotFound(error: unknown): boolean {
    return (error as AppApiError | undefined)?.status === 404;
}

function unavailable(value: string | null | undefined): string {
    return value?.trim() || '未提供';
}

function createCommandIdentity(): string | null {
    if (typeof crypto === 'undefined' || typeof crypto.randomUUID !== 'function') {
        return null;
    }
    return `shadow-materialization-${crypto.randomUUID()}`;
}

type ConfirmationMode = 'new' | 'retry' | null;
type MaterializationNotice = {
    type: 'success' | 'warning' | 'error';
    message: string;
    description: string;
} | null;

/**
 * 现有 Strategy Validation workspace 内的最小 Shadow admission preview 区块。
 *
 * <p>仅在 ELIGIBLE 且当前用户具备 OPERATOR/ADMIN 时提供 CREATE-only materialization；
 * 不提供启动、执行、重绑、上传或交易动作。
 */
export function StrategyReleaseAdmissionPreviewPanel({
    publishRecordId,
    query,
}: {
    publishRecordId: string | null;
    query: AdmissionPreviewQueryState;
}) {
    const preview = query.data;
    const roles = useAuthStore((state) => state.currentUser?.roles ?? []);
    const canMaterialize = roles.some((role) => ['OPERATOR', 'ADMIN'].includes(role.toUpperCase()));
    const materialization = useStrategyReleaseShadowRunMaterialization();
    const [confirmationMode, setConfirmationMode] = useState<ConfirmationMode>(null);
    const [activeCommandIdentity, setActiveCommandIdentity] = useState<string | null>(null);
    const [result, setResult] = useState<StrategyReleaseShadowRunMaterializationResponse | null>(null);
    const [notice, setNotice] = useState<MaterializationNotice>(null);
    const legacyUnbound = preview?.bindingMode === 'LEGACY_UNBOUND'
        || preview?.bindingMode === 'LEGACY_PUBLISH_ONLY';
    const eligible = preview?.admissionDecision === 'ELIGIBLE';

    useEffect(() => {
        setConfirmationMode(null);
        setActiveCommandIdentity(null);
        setResult(null);
        setNotice(null);
        materialization.reset();
        // publish 变化代表新的 release command scope；旧 command identity 绝不能跨 publish 复用。
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [publishRecordId]);

    const submitConfirmedCommand = () => {
        if (!publishRecordId || !eligible || !canMaterialize) {
            setConfirmationMode(null);
            return;
        }
        const commandIdentity = confirmationMode === 'retry'
            ? activeCommandIdentity
            : createCommandIdentity();
        if (!commandIdentity) {
            setNotice({
                type: 'error',
                message: '无法创建 Shadow Run',
                description: '无法生成安全的 Idempotency-Key；本次请求未发送。',
            });
            setConfirmationMode(null);
            return;
        }
        setActiveCommandIdentity(commandIdentity);
        setConfirmationMode(null);
        setNotice(null);
        materialization.mutate(
            {publishRecordId, idempotencyKey: commandIdentity},
            {
                onSuccess: (created) => {
                    setResult(created);
                    setNotice({
                        type: 'success',
                        message: created.idempotentReplay ? '同一创建命令已安全重放' : 'Shadow Run 已创建',
                        description: `状态 ${created.status}；未启动、未下单，也不构成交易授权。`,
                    });
                    query.refetch();
                },
                onError: (error) => {
                    const apiError = error as AppApiError;
                    if (apiError.code === 'ADMISSION_STALE') {
                        setNotice({
                            type: 'warning',
                            message: '准入事实已变化',
                            description: '已刷新准入预览，但不会自动再次创建。请复核新结果后重新确认。',
                        });
                        query.refetch();
                        return;
                    }
                    setNotice({
                        type: apiError.code === 'ADMISSION_BLOCKED' ? 'warning' : 'error',
                        message: apiError.code === 'ADMISSION_BLOCKED' ? '当前准入已阻断' : 'Shadow Run 创建失败',
                        description: formatApiError(apiError),
                    });
                },
            },
        );
    };

    return (
        <Card
            data-testid="strategy-release-admission-preview"
            title="Shadow 准入预览"
            extra={publishRecordId ? (
                <Button
                    size="small"
                    icon={<ReloadOutlined/>}
                    loading={query.isFetching}
                    onClick={() => query.refetch()}
                >
                    刷新预览
                </Button>
            ) : null}
        >
            {!publishRecordId ? (
                <Alert
                    type="info"
                    showIcon
                    message="请输入 publish ID 后查询"
                    description="系统只使用 publish ID 在服务端解析 Release、制品与验证事实。"
                />
            ) : query.isLoading ? (
                <Skeleton data-testid="strategy-release-admission-loading" active paragraph={{rows: 4}}/>
            ) : query.isError && isNotFound(query.error) ? (
                <Alert
                    type="warning"
                    showIcon
                    message="未找到发布记录"
                    description="该 publish ID 没有对应的服务端发布事实，准入保持不可用。"
                />
            ) : query.isError ? (
                <Alert
                    type="error"
                    showIcon
                    message="准入预览请求失败"
                    description={`请求失败时按不可用处理，不会推断为可进入 Shadow。${formatApiError(query.error as AppApiError)}`}
                />
            ) : preview ? (
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <Alert
                        type={preview.admissionDecision === 'ELIGIBLE' ? 'success' : 'warning'}
                        showIcon
                        message={legacyUnbound
                            ? '历史未绑定'
                            : preview.admissionDecision === 'ELIGIBLE'
                                ? '可创建未启动的 Shadow Run'
                                : '准入已阻断'}
                        description={preview.admissionDecision === 'ELIGIBLE'
                            ? '准入允许受控创建 CREATED Shadow Run；创建不会启动、不会下单，也不构成交易授权。'
                            : '请查看阻断原因与 provenance。当前结果不会触发任何创建、启动、执行或交易动作。'}
                    />
                    {notice ? (
                        <Alert
                            data-testid="shadow-materialization-notice"
                            type={notice.type}
                            showIcon
                            message={notice.message}
                            description={notice.description}
                        />
                    ) : null}
                    {result ? (
                        <Alert
                            data-testid="shadow-materialization-result"
                            type="info"
                            showIcon
                            message={`Shadow Run：${result.status}`}
                            description={(
                                <Space direction="vertical" size={2}>
                                    <Text code>{result.shadowRunId}</Text>
                                    <Text type="secondary">
                                        {result.idempotentReplay ? '同一命令重放，未新增 CREATED 事件。' : '已创建 RELEASE_BOUND 事实。'}
                                    </Text>
                                </Space>
                            )}
                        />
                    ) : null}
                    <Descriptions size="small" bordered column={{xs: 1, sm: 2, lg: 3}}>
                        <Descriptions.Item label="制品验证">
                            <StatusTag
                                status={preview.artifactVerificationStatus}
                                label={statusLabel(preview.artifactVerificationStatus)}
                                variant="pill"
                            />
                        </Descriptions.Item>
                        <Descriptions.Item label="Release 绑定">
                            <StatusTag
                                status={legacyUnbound ? 'BLOCKED' : preview.bindingMode}
                                label={statusLabel(preview.bindingMode)}
                                variant="pill"
                            />
                        </Descriptions.Item>
                        <Descriptions.Item label="验证结论">
                            <StatusTag
                                status={preview.validationDecision}
                                label={statusLabel(preview.validationDecision)}
                                variant="pill"
                            />
                        </Descriptions.Item>
                        <Descriptions.Item label="Shadow 准入结论">
                            <StatusTag
                                status={preview.admissionDecision}
                                label={statusLabel(preview.admissionDecision)}
                                tone={preview.admissionDecision === 'ELIGIBLE' ? 'success' : undefined}
                                variant="pill"
                            />
                        </Descriptions.Item>
                        <Descriptions.Item label="publish ID">
                            <Text code copyable>{preview.publishRecordId}</Text>
                        </Descriptions.Item>
                        <Descriptions.Item label="release anchor">
                            <Text code copyable>{preview.releaseAnchorId}</Text>
                        </Descriptions.Item>
                        <Descriptions.Item label="strategy version">
                            <Text code>{unavailable(preview.strategyVersionId)}</Text>
                        </Descriptions.Item>
                        <Descriptions.Item label="dataset">
                            <Text code>{unavailable(preview.datasetId)}</Text>
                        </Descriptions.Item>
                        <Descriptions.Item label="evaluation">
                            <Text code>{unavailable(preview.evaluationId)}</Text>
                        </Descriptions.Item>
                        <Descriptions.Item label="artifact digest" span={3}>
                            <Text code>{unavailable(preview.artifactDigest)}</Text>
                        </Descriptions.Item>
                    </Descriptions>
                    <div>
                        <Text strong>阻断原因 / 说明</Text>
                        <Space direction="vertical" size={4} style={{display: 'flex', marginTop: 8}}>
                            {preview.reasonCodes.map((reason) => (
                                <Text key={reason} type={reason === 'ELIGIBLE_FOR_CREATION_PLAN_ONLY' ? 'secondary' : 'danger'}>
                                    {reason}：{REASON_TEXT[reason] ?? '未知原因，按阻断处理'}
                                </Text>
                            ))}
                        </Space>
                    </div>
                    {eligible && canMaterialize ? (
                        <Space wrap>
                            <Button
                                type="primary"
                                icon={<PlusOutlined/>}
                                loading={materialization.isPending}
                                onClick={() => setConfirmationMode('new')}
                            >
                                {activeCommandIdentity ? '创建新的 Shadow Run' : '创建 Shadow Run'}
                            </Button>
                            {activeCommandIdentity ? (
                                <Button
                                    icon={<RedoOutlined/>}
                                    disabled={materialization.isPending}
                                    onClick={() => setConfirmationMode('retry')}
                                >
                                    重试同一创建命令
                                </Button>
                            ) : null}
                        </Space>
                    ) : null}
                </Space>
            ) : (
                <Alert type="warning" showIcon message="准入预览不可用"/>
            )}
            <Modal
                title={confirmationMode === 'retry' ? '重新确认同一创建命令' : '确认创建 Shadow Run'}
                open={confirmationMode !== null}
                okText={confirmationMode === 'retry' ? '确认重试' : '确认创建'}
                cancelText="取消"
                confirmLoading={materialization.isPending}
                onOk={submitConfirmedCommand}
                onCancel={() => setConfirmationMode(null)}
            >
                <Alert
                    type="warning"
                    showIcon
                    message="仅创建 CREATED Shadow Run"
                    description="本操作不会启动 Runner 或 Scheduler，不会下单，不会访问交易凭证，也不构成交易授权。"
                />
            </Modal>
        </Card>
    );
}
