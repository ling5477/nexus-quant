import {App, Alert, Button, Descriptions, Drawer, Empty, Form, Input, Modal, Space, Timeline, Typography} from 'antd';
import {useEffect, useMemo, useState} from 'react';

import {NqStatusTag} from '@/components/nq';
import {
    useValidationReviewDetailQuery,
    useValidationReviewEventsQuery,
    useValidationReviewLifecycleMutation,
} from '@/hooks/useValidationReviewQueries';
import type {AppApiError} from '@/types/api';
import type {
    ValidationReviewAction,
    ValidationReviewLifecycleRequest,
    ValidationReviewState,
} from '@/types/validation-review';
import {formatDateTime} from '@/utils/formatters';

const {Paragraph, Text} = Typography;

const ACTION_LABELS: Record<ValidationReviewAction, string> = {
    acknowledge: '确认已阅',
    escalate: '升级处理',
    resolve: '标记已解决',
    close: '关闭 Case',
};

const ACTIONS_BY_STATE: Record<ValidationReviewState, ValidationReviewAction[]> = {
    OPEN: ['acknowledge', 'escalate'],
    ACKNOWLEDGED: ['escalate', 'resolve'],
    ESCALATED: ['resolve'],
    RESOLVED: ['close'],
    CLOSED: [],
};

interface ValidationReviewCaseDrawerProps {
    caseId: string | null;
    onClose: () => void;
}

/** 每次用户确认提交生成一个新 key；mutation 不 retry，因此同一请求过程只使用该稳定值。 */
function createIdempotencyKey(): string {
    if (typeof globalThis.crypto?.randomUUID !== 'function') {
        throw new Error('secure UUID generation is unavailable');
    }
    const key = globalThis.crypto.randomUUID();
    if (!key) {
        throw new Error('secure UUID generation returned an empty key');
    }
    return key;
}

function actionErrorMessage(error: AppApiError): string {
    if (error.status === 403) return '当前身份无权执行该复核动作。';
    if (error.status === 404) return 'Case 已不存在或不在当前可见范围。';
    if (error.status === 409 || error.status === 422) return 'Case 状态已变化或流转不再合法，已重新获取最新详情。';
    if (error.status === 401) return '认证已失效，请重新登录。';
    return error.status >= 500 ? '服务暂时不可用，请稍后重试。' : '请求未被接受，请检查输入后重试。';
}

/**
 * Case detail Drawer 展示后端 allowlisted 字段、最多 100 条 events 与真实状态机动作。
 * 不展示 raw metadata、credential、stack trace 或服务端未公开的诊断锚点。
 */
export function ValidationReviewCaseDrawer({caseId, onClose}: ValidationReviewCaseDrawerProps) {
    const {message} = App.useApp();
    const [form] = Form.useForm<ValidationReviewLifecycleRequest>();
    const [action, setAction] = useState<ValidationReviewAction | null>(null);
    const [actionForbidden, setActionForbidden] = useState(false);
    const [actionNotice, setActionNotice] = useState<string | null>(null);
    const detailQuery = useValidationReviewDetailQuery(caseId);
    const eventsQuery = useValidationReviewEventsQuery(caseId);
    const mutation = useValidationReviewLifecycleMutation();

    useEffect(() => {
        setActionForbidden(false);
        setActionNotice(null);
        setAction(null);
        form.resetFields();
    }, [caseId, form]);

    const allowedActions = useMemo(
        () => detailQuery.data ? (ACTIONS_BY_STATE[detailQuery.data.state] ?? []) : [],
        [detailQuery.data],
    );
    const detailError = detailQuery.error as AppApiError | null;
    const eventsError = eventsQuery.error as AppApiError | null;

    function submitAction(values: ValidationReviewLifecycleRequest) {
        if (!caseId || !action || !detailQuery.data) return;
        if (!allowedActions.includes(action) || !Number.isSafeInteger(detailQuery.data.version) || detailQuery.data.version < 0) {
            setActionNotice('Case 状态或 version 不可用于提交；未发送请求，请刷新最新详情。');
            return;
        }
        let idempotencyKey: string;
        try {
            idempotencyKey = createIdempotencyKey();
        } catch {
            setActionNotice('无法生成安全的 Idempotency-Key；本次请求未发送。');
            return;
        }
        mutation.mutate({
            caseId,
            action,
            idempotencyKey,
            payload: {expectedVersion: detailQuery.data.version, reason: values.reason.trim()},
        }, {
            onSuccess: () => {
                message.success(`${ACTION_LABELS[action]}已提交。`);
                setAction(null);
                setActionNotice(null);
                form.resetFields();
            },
            onError: (error) => {
                const apiError = error as AppApiError;
                if (apiError.status === 403) setActionForbidden(true);
                setActionNotice(actionErrorMessage(apiError));
            },
        });
    }

    return (
        <>
            <Drawer
                open={Boolean(caseId)}
                width={760}
                title="Validation Review Case"
                onClose={onClose}
                destroyOnClose={false}
                data-testid="validation-review-case-drawer"
            >
                <Space direction="vertical" size={16} style={{display: 'flex'}}>
                    <Alert
                        type="warning"
                        showIcon
                        message="诊断审查，不构成交易授权"
                        description="Lifecycle 状态仅记录本地人工复核进度，不会启动 LIVE、Shadow trading 或任何交易动作。"
                    />
                    {detailError?.status === 404 ? (
                        <Alert type="error" showIcon message="Case 已不存在" description="该 case 不存在或不在当前权限范围。"/>
                    ) : detailError ? (
                        <Alert type="error" showIcon message="Case detail 加载失败" description="未展示后端原始错误信息。"/>
                    ) : null}
                    {detailQuery.isLoading ? <Text type="secondary">正在加载 case detail…</Text> : null}
                    {detailQuery.data ? (
                        <Descriptions bordered size="small" column={2}>
                            <Descriptions.Item label="Case ID" span={2}><Text code copyable>{detailQuery.data.id}</Text></Descriptions.Item>
                            <Descriptions.Item label="State"><NqStatusTag status={detailQuery.data.state}/></Descriptions.Item>
                            <Descriptions.Item label="Severity"><NqStatusTag status={detailQuery.data.severity}/></Descriptions.Item>
                            <Descriptions.Item label="Owner">{detailQuery.data.ownerId}</Descriptions.Item>
                            <Descriptions.Item label="Version">{detailQuery.data.version}</Descriptions.Item>
                            <Descriptions.Item label="Evidence type">{detailQuery.data.evidenceType}</Descriptions.Item>
                            <Descriptions.Item label="Evidence source">{detailQuery.data.evidenceSource}</Descriptions.Item>
                            <Descriptions.Item label="Title" span={2}>{detailQuery.data.title}</Descriptions.Item>
                            <Descriptions.Item label="Summary" span={2}>{detailQuery.data.summary}</Descriptions.Item>
                            <Descriptions.Item label="Created">{formatDateTime(detailQuery.data.createdAt)}</Descriptions.Item>
                            <Descriptions.Item label="Updated">{formatDateTime(detailQuery.data.updatedAt)}</Descriptions.Item>
                            <Descriptions.Item label="Retention until" span={2}>{formatDateTime(detailQuery.data.retentionUntil)}</Descriptions.Item>
                            <Descriptions.Item label="诊断元数据" span={2}>
                                后端安全 DTO 未公开 trace/schema/checksum/evidence anchor；前端不推断或补造。
                            </Descriptions.Item>
                        </Descriptions>
                    ) : null}

                    {actionNotice ? <Alert type="warning" showIcon message={actionNotice}/> : null}
                    {detailQuery.data ? (
                        <Space wrap data-testid="validation-review-actions">
                            {allowedActions.map((item) => (
                                <Button
                                    key={item}
                                    danger={item === 'close'}
                                    disabled={actionForbidden || mutation.isPending}
                                    onClick={() => {
                                        setAction(item);
                                        form.setFieldsValue({expectedVersion: detailQuery.data?.version, reason: ''});
                                    }}
                                >
                                    {ACTION_LABELS[item]}
                                </Button>
                            ))}
                            {allowedActions.length === 0 ? <Text type="secondary">当前状态没有可执行动作。</Text> : null}
                        </Space>
                    ) : null}

                    <div data-testid="validation-review-event-timeline">
                        <Text strong>Lifecycle events（最多 100 条）</Text>
                        {eventsError ? (
                            <Alert type="error" showIcon message="Event timeline 加载失败" description="失败不会被解释为没有历史事件。"/>
                        ) : eventsQuery.isLoading ? (
                            <Paragraph type="secondary">正在加载 events…</Paragraph>
                        ) : (eventsQuery.data ?? []).length === 0 ? (
                            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无 lifecycle event"/>
                        ) : (
                            <Timeline
                                items={(eventsQuery.data ?? []).map((event) => ({
                                    children: (
                                        <Space direction="vertical" size={0}>
                                            <Text strong>{event.eventType}</Text>
                                            <Text>{event.fromState} → {event.toState}</Text>
                                            <Text type="secondary">actor {event.actorId} · version {event.caseVersion} · {formatDateTime(event.createdAt)}</Text>
                                        </Space>
                                    ),
                                }))}
                            />
                        )}
                    </div>
                </Space>
            </Drawer>

            <Modal
                open={Boolean(action)}
                title={action && caseId ? `${ACTION_LABELS[action]}确认 · ${caseId}` : 'Lifecycle action'}
                okText="确认提交"
                cancelText="取消"
                confirmLoading={mutation.isPending}
                okButtonProps={{disabled: actionForbidden || mutation.isPending}}
                zIndex={1100}
                onCancel={() => !mutation.isPending && setAction(null)}
                onOk={() => form.submit()}
                destroyOnHidden
                forceRender
            >
                <Alert type="warning" showIcon message="此操作仅更新本地复核状态，不构成交易授权。" style={{marginBottom: 16}}/>
                <Form form={form} layout="vertical" onFinish={submitAction}>
                    <Form.Item name="reason" label="复核原因" rules={[
                        {required: true, whitespace: true, message: '请输入复核原因。'},
                        {max: 1000, message: '复核原因不能超过 1000 个字符。'},
                    ]}>
                        <Input.TextArea rows={4} placeholder="仅填写脱敏的人工复核说明"/>
                    </Form.Item>
                </Form>
            </Modal>
        </>
    );
}
