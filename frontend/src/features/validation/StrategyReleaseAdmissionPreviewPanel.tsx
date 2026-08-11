import {ReloadOutlined} from '@ant-design/icons';
import {Alert, Button, Card, Descriptions, Skeleton, Space, Typography} from 'antd';

import {formatApiError} from '@/api/errors';
import {StatusTag} from '@/nq-design-system/status/StatusTag';
import type {AppApiError} from '@/types/api';
import type {StrategyReleaseAdmissionPreviewResponse} from '@/types/strategy-releases';

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
    ELIGIBLE_FOR_CREATION_PLAN_ONLY: '仅可形成内存创建计划，不会创建或启动 Shadow Run',
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

/**
 * 现有 Strategy Validation workspace 内的最小 Shadow admission preview 区块。
 *
 * <p>仅展示 GET 查询结果与 provenance；唯一交互是刷新。组件不提供创建、启动、执行、重绑、上传或交易动作。
 */
export function StrategyReleaseAdmissionPreviewPanel({
    publishRecordId,
    query,
}: {
    publishRecordId: string | null;
    query: AdmissionPreviewQueryState;
}) {
    const preview = query.data;
    const legacyUnbound = preview?.bindingMode === 'LEGACY_UNBOUND'
        || preview?.bindingMode === 'LEGACY_PUBLISH_ONLY';

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
                                ? '可进入 Shadow（仅预览）'
                                : '准入已阻断'}
                        description={preview.admissionDecision === 'ELIGIBLE'
                            ? '仅表示服务端规则允许形成内存中的创建计划；不会创建或启动 Shadow Run，也不构成交易授权。'
                            : '请查看阻断原因与 provenance。当前结果不会触发任何创建、启动、执行或交易动作。'}
                    />
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
                </Space>
            ) : (
                <Alert type="warning" showIcon message="准入预览不可用"/>
            )}
        </Card>
    );
}
