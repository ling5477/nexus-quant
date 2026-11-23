import {useEffect, useState, type KeyboardEvent} from 'react';
import {useMutation, useQueryClient} from '@tanstack/react-query';
import {
    Alert,
    App,
    Button,
    Checkbox,
    Collapse,
    Col,
    Descriptions,
    Drawer,
    Form,
    Input,
    Row,
    Select,
    Space,
    Switch,
    TimePicker,
    Typography,
} from 'antd';
import type {Dayjs} from 'dayjs';
import {useTranslation} from 'react-i18next';
import {scheduleQueryKeys} from '@/api/query-keys';
import {formatApiError} from '@/api/errors';
import {useAuthStore} from '@/store/auth-store';
import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import {StrategyDefinitionPicker} from '@/features/strategies/components/StrategyDefinitionPicker';
import type {AppApiError} from '@/types/api';
import {formatDateTime} from '@/utils/formatters';
import {
    dailyScheduleWindow,
    normalizeScheduleCreation,
    type ScheduleCreationInput,
} from '../creation-input';
import {
    readScheduleCreation,
    scheduleCreation,
    scheduleCreationChanged,
    type ScheduleCreationReceipt,
} from '../creation';

interface Values {
    strategyId: string;
    cronPreset: string;
    cronExpr: string;
    enabled: boolean;
    windowMode: 'none' | 'daily' | 'advanced';
    startTime?: Dayjs;
    endTime?: Dayjs;
    days: string[];
    windowConfig: string;
    dedupScope: ScheduleCreationInput['dedupScope'];
}

/** 时间输入的Enter只确认时间，不触发外层表单的隐式创建；仍保留控件自身的时间提交。 */
function keepTimeEnterLocal(event: KeyboardEvent<HTMLElement>) {
    if (event.key === 'Enter') {
        event.preventDefault();
        event.stopPropagation();
    }
}

/** 订阅本主体的持久原请求；页面重新进入只读取本地状态，不触发后台重发。 */
function useSavedCreation(owner: number | undefined) {
    const [state, setState] = useState(() => ({owner, value: readScheduleCreation(owner)}));
    useEffect(() => {
        const refresh = () => setState({owner, value: readScheduleCreation(owner)});
        refresh();
        window.addEventListener(scheduleCreationChanged, refresh);
        window.addEventListener('storage', refresh);
        return () => {
            window.removeEventListener(scheduleCreationChanged, refresh);
            window.removeEventListener('storage', refresh);
        };
    }, [owner]);
    return state.owner === owner ? state.value : readScheduleCreation(owner);
}

/** 创建与恢复共享同一原输入，成功快照保留至明确查看或继续；当前计划状态另读详情。 */
export function ScheduleCreationWorkspace({
    onView,
}: {
    onView: (receipt: ScheduleCreationReceipt) => void;
}) {
    const {t} = useTranslation('pages');
    const {modal} = App.useApp();
    const cache = useQueryClient();
    const owner = useAuthStore((state) => state.currentUser?.userId);
    const roles = useAuthStore((state) => state.currentUser?.roles ?? []);
    const allowed = roles.some((role) =>
        ['ADMIN', 'OPERATOR', 'ROLE_ADMIN', 'ROLE_OPERATOR'].includes(role),
    );
    const [open, setOpen] = useState(false);
    const [form] = useLocalizedForm<Values>();
    const [zone] = useState(() => Intl.DateTimeFormat().resolvedOptions().timeZone || '');
    const stored = useSavedCreation(owner);
    const pending = stored.kind === 'saved' ? stored.pending : null;
    const receipt = pending?.phase === 'COMPLETED' ? pending.receipt : undefined;
    const changed = () => void cache.invalidateQueries({queryKey: scheduleQueryKeys.all});
    const submit = useMutation({
        mutationFn: scheduleCreation.submit,
        retry: false,
        onSuccess: changed,
    });
    const lookup = useMutation({
        mutationFn: scheduleCreation.lookup,
        retry: false,
        onSuccess: changed,
    });
    const resume = useMutation({
        mutationFn: scheduleCreation.resume,
        retry: false,
        onSuccess: changed,
    });
    const acknowledge = useMutation({mutationFn: scheduleCreation.acknowledge, retry: false});
    const busy =
        submit.isPending ||
        lookup.isPending ||
        resume.isPending ||
        acknowledge.isPending ||
        pending?.phase === 'SUBMITTING';
    const missing = lookup.isError && (lookup.error as AppApiError).status === 404;
    const windowMode = Form.useWatch('windowMode', form) ?? 'none';
    const cronPreset = Form.useWatch('cronPreset', form) ?? '0 * * * * *';
    const describe = (failure: unknown) => {
        const problem = failure as AppApiError;
        const local: Record<string, string> = {
            SCHEDULE_CREATION_STORAGE_UNAVAILABLE: 'storageUnavailable',
            SCHEDULE_CREATION_COORDINATION_UNAVAILABLE: 'coordinationUnavailable',
            SCHEDULE_CREATION_PENDING: 'unknownHelp',
            SCHEDULE_CREATION_SCOPE_UNAVAILABLE: 'readOnly',
            SCHEDULE_CREATION_SCOPE_CHANGED: 'scopeChanged',
            SCHEDULE_CREATION_IDENTITY_UNAVAILABLE: 'identityUnavailable',
            SCHEDULE_CREATION_LOOKUP_REQUIRED: 'missing',
            SCHEDULE_CREATION_RESPONSE_INVALID: 'invalidReceipt',
        };
        return local[problem.code]
            ? t(`scheduleCreation.${local[problem.code]}`)
            : formatApiError(problem);
    };
    const errors = (
        receipt
            ? [acknowledge.error]
            : [submit.error, missing ? null : lookup.error, resume.error, acknowledge.error]
    ).filter(Boolean);
    useEffect(() => {
        lookup.reset();
        resume.reset();
        acknowledge.reset();
    }, [owner, pending?.creationId, lookup.reset, resume.reset, acknowledge.reset]);
    const finish = async (view: boolean) => {
        if (!pending || !receipt) return;
        await acknowledge.mutateAsync(pending);
        submit.reset();
        lookup.reset();
        resume.reset();
        form.resetFields();
        setOpen(!view);
        if (view) onView(receipt);
    };
    const result = pending && (
        <Space direction="vertical" style={{display: 'flex'}} size={12}>
            <Alert
                showIcon
                type={receipt ? 'success' : 'warning'}
                message={t(
                    receipt
                        ? 'scheduleCreation.completed'
                        : pending.phase === 'SUBMITTING'
                          ? 'scheduleCreation.submitting'
                          : 'scheduleCreation.unknown',
                )}
                description={t(
                    receipt
                        ? 'scheduleCreation.snapshotMeaning'
                        : pending.phase === 'SUBMITTING'
                          ? 'scheduleCreation.submittingHelp'
                          : 'scheduleCreation.unknownHelp',
                )}
            />
            <Descriptions
                size="small"
                column={{xs: 1, md: 2}}
                styles={{label: {whiteSpace: 'nowrap'}}}
            >
                <Descriptions.Item label={t('scheduleCreation.identity')}>
                    <Typography.Text copyable style={{overflowWrap: 'anywhere'}}>
                        {pending.creationId}
                    </Typography.Text>
                </Descriptions.Item>
                <Descriptions.Item label={t('strategyId')}>
                    <Typography.Text style={{overflowWrap: 'anywhere'}}>
                        {pending.request.strategyId}
                    </Typography.Text>
                </Descriptions.Item>
                <Descriptions.Item label="Cron">{pending.request.cronExpr}</Descriptions.Item>
                <Descriptions.Item label={t('timeZone')}>
                    {pending.request.timezone}
                </Descriptions.Item>
                <Descriptions.Item label={t('scheduleCreation.initialState')}>
                    {t(pending.request.enabled ? 'enabled' : 'disabled')}
                </Descriptions.Item>
                {receipt && (
                    <Descriptions.Item label={t('scheduleId')}>
                        <Typography.Text copyable style={{overflowWrap: 'anywhere'}}>
                            {receipt.result.scheduleJobId}
                        </Typography.Text>
                    </Descriptions.Item>
                )}
                {receipt && (
                    <Descriptions.Item label={t('scheduleCreation.completedAt')}>
                        {formatDateTime(receipt.completedAt)}
                    </Descriptions.Item>
                )}
            </Descriptions>
            <Collapse
                items={[
                    {
                        key: 'input',
                        label: t('scheduleCreation.originalInput'),
                        children: (
                            <Typography.Text
                                style={{whiteSpace: 'pre-wrap', overflowWrap: 'anywhere'}}
                            >
                                {JSON.stringify(pending.request, null, 2)}
                                {receipt ? `\ntraceId: ${receipt.traceId}` : ''}
                            </Typography.Text>
                        ),
                    },
                ]}
            />
            {missing && !receipt && (
                <Alert type="info" showIcon message={t('scheduleCreation.missing')} />
            )}
            {!receipt ? (
                <Space wrap>
                    <Button
                        loading={lookup.isPending}
                        disabled={busy || !allowed}
                        onClick={() => lookup.mutate(pending)}
                    >
                        {t('scheduleCreation.lookup')}
                    </Button>
                    <Button
                        loading={resume.isPending}
                        disabled={busy || !allowed || !missing}
                        onClick={() =>
                            modal.confirm({
                                title: t('scheduleCreation.resume'),
                                content: t('scheduleCreation.resumeHelp'),
                                okText: t('confirmRetry'),
                                cancelText: t('cancel'),
                                onOk: () => resume.mutateAsync(pending),
                            })
                        }
                    >
                        {t('scheduleCreation.resume')}
                    </Button>
                </Space>
            ) : (
                <Space wrap>
                    <Button
                        type="primary"
                        loading={acknowledge.isPending}
                        disabled={busy || !allowed}
                        onClick={() => void finish(true).catch(() => undefined)}
                    >
                        {t('scheduleCreation.view')}
                    </Button>
                    <Button
                        disabled={busy || !allowed}
                        onClick={() => void finish(false).catch(() => undefined)}
                    >
                        {t('scheduleCreation.another')}
                    </Button>
                </Space>
            )}
        </Space>
    );
    const problems = errors.length > 0 && (
        <Alert
            type="error"
            showIcon
            message={t('scheduleCreation.actionFailed')}
            description={
                <Space direction="vertical">
                    {errors.map((failure, i) => (
                        <Typography.Text key={i}>{describe(failure)}</Typography.Text>
                    ))}
                </Space>
            }
        />
    );
    const formDisabled = !allowed || busy || stored.kind !== 'none' || !zone;
    return (
        <Space direction="vertical" style={{display: 'flex'}} size={12}>
            <Space wrap>
                <Button type="primary" disabled={!allowed} onClick={() => setOpen(true)}>
                    {t(pending ? 'scheduleCreation.openSaved' : 'scheduleCreation.create')}
                </Button>
                {!allowed && (
                    <Typography.Text type="secondary">
                        {t('scheduleCreation.readOnly')}
                    </Typography.Text>
                )}
            </Space>
            {stored.kind === 'unavailable' && (
                <Alert showIcon type="error" message={t('scheduleCreation.storageUnavailable')} />
            )}
            {pending && !open && result}
            {!open && problems}
            <Drawer
                open={open}
                title={t('scheduleCreation.create')}
                width={820}
                onClose={() => setOpen(false)}
            >
                <Space direction="vertical" size={16} style={{display: 'flex'}}>
                    {result}
                    {problems}
                    {stored.kind === 'unavailable' && (
                        <Alert
                            type="error"
                            showIcon
                            message={t('scheduleCreation.storageUnavailable')}
                        />
                    )}
                    {!pending && (
                        <Form
                            name="schedule-creation"
                            form={form}
                            layout="vertical"
                            disabled={formDisabled}
                            initialValues={{
                                cronPreset: '0 * * * * *',
                                cronExpr: '',
                                enabled: false,
                                windowMode: 'none',
                                days: [],
                                windowConfig: '{}',
                                dedupScope: 'SCHEDULE_WINDOW',
                            }}
                            onFinish={(values) => {
                                let input: ScheduleCreationInput;
                                try {
                                    input = normalizeScheduleCreation({
                                        strategyId: values.strategyId,
                                        scheduleType: 'CRON',
                                        cronExpr:
                                            values.cronPreset === 'custom'
                                                ? values.cronExpr
                                                : values.cronPreset,
                                        timezone: zone,
                                        enabled: values.enabled,
                                        windowConfig:
                                            values.windowMode === 'daily'
                                                ? dailyScheduleWindow(
                                                      values.startTime?.format('HH:mm:ss') ?? '',
                                                      values.endTime?.format('HH:mm:ss') ?? '',
                                                      values.days,
                                                  )
                                                : values.windowMode === 'advanced'
                                                  ? values.windowConfig
                                                  : '{}',
                                        dedupScope: values.dedupScope,
                                    });
                                } catch {
                                    form.setFields([
                                        {
                                            name:
                                                values.windowMode === 'advanced'
                                                    ? 'windowConfig'
                                                    : 'startTime',
                                            errors: [t('scheduleCreation.invalidInput')],
                                        },
                                    ]);
                                    return;
                                }
                                submit.mutate(input);
                            }}
                        >
                            <Form.Item
                                name="strategyId"
                                label={t('strategy')}
                                rules={[{required: true}]}
                            >
                                <StrategyDefinitionPicker
                                    active={open}
                                    purpose="schedule"
                                    disabled={formDisabled}
                                />
                            </Form.Item>
                            <Row gutter={16}>
                                <Col xs={24} md={14}>
                                    <Form.Item
                                        name="cronPreset"
                                        label={t('scheduleCreation.frequency')}
                                        rules={[{required: true}]}
                                    >
                                        <Select
                                            options={[
                                                {
                                                    value: '0 * * * * *',
                                                    label: t('scheduleCreation.everyMinute'),
                                                },
                                                {
                                                    value: '0 0 * * * *',
                                                    label: t('scheduleCreation.everyHour'),
                                                },
                                                {
                                                    value: '0 0 0 * * *',
                                                    label: t('scheduleCreation.everyDay'),
                                                },
                                                {
                                                    value: 'custom',
                                                    label: t('scheduleCreation.customCron'),
                                                },
                                            ]}
                                        />
                                    </Form.Item>
                                </Col>
                                <Col xs={24} md={10}>
                                    <Form.Item
                                        label={t('timeZone')}
                                        extra={t('scheduleCreation.zoneHelp')}
                                    >
                                        <Input value={zone} readOnly aria-label={t('timeZone')} />
                                    </Form.Item>
                                </Col>
                            </Row>
                            {cronPreset === 'custom' && (
                                <Form.Item
                                    name="cronExpr"
                                    label="Cron"
                                    extra={t('scheduleCreation.cronHelp')}
                                    rules={[{required: true, whitespace: true, max: 128}]}
                                >
                                    <Input placeholder="0 */5 * * * *" maxLength={128} />
                                </Form.Item>
                            )}
                            <Form.Item name="windowMode" label={t('scheduleCreation.windowMode')}>
                                <Select
                                    options={['none', 'daily', 'advanced'].map((value) => ({
                                        value,
                                        label: t(`scheduleCreation.window.${value}`),
                                    }))}
                                />
                            </Form.Item>
                            {windowMode === 'daily' && (
                                <>
                                    <Row gutter={16}>
                                        <Col xs={12}>
                                            <Form.Item
                                                name="startTime"
                                                label={t('scheduleCreation.startTime')}
                                                rules={[{required: true}]}
                                            >
                                                <TimePicker
                                                    onKeyDown={keepTimeEnterLocal}
                                                    format="HH:mm:ss"
                                                    showNow={false}
                                                    style={{width: '100%'}}
                                                />
                                            </Form.Item>
                                        </Col>
                                        <Col xs={12}>
                                            <Form.Item
                                                name="endTime"
                                                label={t('scheduleCreation.endTime')}
                                                rules={[{required: true}]}
                                            >
                                                <TimePicker
                                                    onKeyDown={keepTimeEnterLocal}
                                                    format="HH:mm:ss"
                                                    showNow={false}
                                                    style={{width: '100%'}}
                                                />
                                            </Form.Item>
                                        </Col>
                                    </Row>
                                    <Typography.Paragraph type="secondary">
                                        {t('scheduleCreation.dailyHelp')}
                                    </Typography.Paragraph>
                                    <Form.Item
                                        name="days"
                                        label={t('scheduleCreation.days')}
                                        extra={t('scheduleCreation.daysHelp')}
                                    >
                                        <Checkbox.Group
                                            options={[
                                                'MONDAY',
                                                'TUESDAY',
                                                'WEDNESDAY',
                                                'THURSDAY',
                                                'FRIDAY',
                                                'SATURDAY',
                                                'SUNDAY',
                                            ].map((value) => ({
                                                value,
                                                label: t(`scheduleCreation.day.${value}`),
                                            }))}
                                        />
                                    </Form.Item>
                                </>
                            )}
                            {windowMode === 'advanced' && (
                                <Form.Item
                                    name="windowConfig"
                                    label={t('windowConfiguration')}
                                    extra={t('scheduleCreation.jsonHelp')}
                                    rules={[
                                        {required: true},
                                        {
                                            validator: async (_, value: string) => {
                                                try {
                                                    const parsed: unknown = JSON.parse(value);
                                                    if (
                                                        !parsed ||
                                                        typeof parsed !== 'object' ||
                                                        Array.isArray(parsed) ||
                                                        new TextEncoder().encode(value.trim())
                                                            .length > 8192
                                                    )
                                                        throw new Error();
                                                } catch {
                                                    throw new Error(
                                                        t('scheduleCreation.invalidInput'),
                                                    );
                                                }
                                            },
                                        },
                                    ]}
                                >
                                    <Input.TextArea rows={6} spellCheck={false} />
                                </Form.Item>
                            )}
                            <Form.Item
                                name="dedupScope"
                                label={t('deduplicationScope')}
                                extra={t('scheduleCreation.dedupHelp')}
                            >
                                <Select
                                    options={['SCHEDULE_WINDOW', 'REQUEST', 'STRATEGY'].map(
                                        (value) => ({
                                            value,
                                            label: t(`scheduleCreation.dedup.${value}`),
                                        }),
                                    )}
                                />
                            </Form.Item>
                            <Form.Item
                                name="enabled"
                                label={t('scheduleCreation.enableInitially')}
                                valuePropName="checked"
                                extra={t('scheduleCreation.enableHelp')}
                            >
                                <Switch />
                            </Form.Item>
                            <Space wrap>
                                <Button
                                    type="primary"
                                    htmlType="submit"
                                    loading={submit.isPending}
                                    disabled={formDisabled}
                                >
                                    {t('scheduleCreation.submit')}
                                </Button>
                                <Button onClick={() => setOpen(false)}>{t('cancel')}</Button>
                            </Space>
                        </Form>
                    )}
                </Space>
            </Drawer>
        </Space>
    );
}
