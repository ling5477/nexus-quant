import {useEffect, useState} from 'react';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {
    Alert,
    Button,
    Card,
    Col,
    Collapse,
    DatePicker,
    Descriptions,
    Form,
    Row,
    Space,
    Tag,
    Typography,
} from 'antd';
import {Link, useSearchParams} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import dayjs, {type Dayjs} from 'dayjs';
import {marketdataQueryKeys} from '@/api/query-keys';
import {formatApiError} from '@/api/errors';
import {useAuthStore, selectSessionIdentity} from '@/store/auth-store';
import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import {formatDateTime} from '@/utils/formatters';
import {confirmTimeWithoutSubmittingForm, endAfterStartRule} from '@/utils/time-range';
import type {AppApiError} from '@/types/api';
import type {PublicCaptureOperation} from '@/types/marketdata';
import {
    captureChanged,
    captureOperations,
    captureReads,
    readCapture,
    validCaptureId,
} from './public-capture-operations';
import './PublicMarketCaptureWorkspace.css';

interface Values {
    start: Dayjs;
    end: Dayjs;
}

/** 原请求恢复与已选结果分开；界面只在明确点击时写入，刷新、返回和换标签只读取。 */
export function PublicMarketCaptureWorkspace({active}: {active: boolean}) {
    const {t} = useTranslation('pages');
    const user = useAuthStore((state) => state.currentUser);
    const owner = user?.userId;
    const token = useAuthStore(selectSessionIdentity);
    const canWrite = Boolean(
        user?.roles.some((role) =>
            ['ADMIN', 'OPERATOR', 'ROLE_ADMIN', 'ROLE_OPERATOR'].includes(role),
        ),
    );
    const [params, setParams] = useSearchParams();
    const [form] = useLocalizedForm<Values>();
    const client = useQueryClient();
    const [storage, setStorage] = useState(() => ({owner, value: readCapture(owner)}));
    useEffect(() => {
        const refresh = () => setStorage({owner, value: readCapture(owner)});
        refresh();
        window.addEventListener(captureChanged, refresh);
        window.addEventListener('storage', refresh);
        return () => {
            window.removeEventListener(captureChanged, refresh);
            window.removeEventListener('storage', refresh);
        };
    }, [owner]);
    const saved = storage.owner === owner ? storage.value : readCapture(owner);
    const pending = saved.kind === 'saved' ? saved.pending : null;
    const rawId = params.get('captureId');
    const invalid = rawId !== null && !validCaptureId(rawId);
    const selected = !invalid ? (rawId ?? pending?.captureId ?? null) : null;
    function select(id: string | null) {
        setParams((current) => {
            const next = new URLSearchParams(current);
            next.set('section', 'preparation');
            next.set('preparationTab', 'capture');
            if (id) next.set('captureId', id);
            else next.delete('captureId');
            return next;
        });
    }
    useEffect(() => {
        if (active && !rawId && pending) select(pending.captureId);
    }, [active, rawId, pending?.captureId]);
    const query = useQuery({
        queryKey: marketdataQueryKeys.publicCapture(owner, selected),
        queryFn: ({signal}) => captureReads.one(selected!, signal),
        enabled: active && Boolean(owner && selected),
        retry: false,
        staleTime: 0,
    });
    const result = query.isSuccess && query.data.captureId === selected ? query.data : null;
    async function received(value: PublicCaptureOperation) {
        // 旧会话的延迟回执不能导航或更新后来登录用户的工作区。
        const current = useAuthStore.getState();
        if (current.currentUser?.userId !== owner || selectSessionIdentity(current) !== token) return;
        const key = marketdataQueryKeys.publicCapture(owner, value.captureId);
        await client.cancelQueries({queryKey: key, exact: true});
        client.setQueryData(key, value);
        select(value.captureId);
        if (value.state === 'SUCCEEDED')
            await client.invalidateQueries({queryKey: marketdataQueryKeys.datasets()});
    }
    const create = useMutation({
        mutationFn: (value: Values) =>
            captureOperations.create(
                {start: value.start.toISOString(), end: value.end.toISOString()},
                (id) => select(id),
            ),
        onSuccess: received,
    });
    const resume = useMutation({mutationFn: captureOperations.resume, onSuccess: received});
    const acknowledge = useMutation({mutationFn: captureOperations.acknowledge});
    const busy =
        create.isPending ||
        resume.isPending ||
        acknowledge.isPending ||
        pending?.phase === 'SUBMITTING';
    const originalSelected = Boolean(pending && selected === pending.captureId);
    const terminal = result && result.state !== 'PENDING';
    useEffect(() => {
        // 原请求已有正式终态，先前连接失败的未知提示已失效；清除后也不能在确认阅读时重新出现。
        // 本机确认失败仍需保留，不能因为服务端成功就掩盖存储未释放等问题。
        if (terminal && originalSelected) {
            if (create.error) create.reset();
            if (resume.error) resume.reset();
        }
    }, [terminal, originalSelected, create.error, create.reset, resume.error, resume.reset]);
    const notVisible = (query.error as AppApiError | null)?.status === 404;
    const continuation =
        originalSelected && !query.isFetching && (notVisible || result?.state === 'PENDING');
    const mutationError = create.error ?? resume.error ?? acknowledge.error;
    function errorText(error: unknown) {
        const code = (error as AppApiError)?.code;
        if (code?.startsWith('PUBLIC_CAPTURE_')) {
            const key = 'publicCaptureRecovery.errors.' + code.slice('PUBLIC_CAPTURE_'.length);
            if (t(key, {defaultValue: ''})) return t(key);
        }
        return formatApiError(error as AppApiError);
    }
    const end = dayjs(Math.floor((Date.now() - 2 * 3600000) / 3600000) * 3600000);
    const capture = result?.state === 'SUCCEEDED' ? result.result : null;
    return (
        <Card
            className="page-section public-capture-workspace"
            bordered={false}
            title={t('publicCaptureTitle')}
            data-testid="public-market-capture"
        >
            <Typography.Paragraph>{t('publicCaptureRecovery.description')}</Typography.Paragraph>
            <Space wrap>
                <Tag>OKX</Tag>
                <Tag>{t('publicCaptureRecovery.spot')}</Tag>
                <Tag>BTC-USDT</Tag>
                <Tag>1h</Tag>
            </Space>
            <Typography.Paragraph type="secondary">
                {t('publicCaptureRecovery.windowHelp')}
            </Typography.Paragraph>
            {!canWrite && (
                <Alert showIcon type="info" message={t('publicCaptureRecovery.readOnly')} />
            )}
            {saved.kind === 'unavailable' && (
                <Alert
                    showIcon
                    type="error"
                    message={t('publicCaptureRecovery.storageUnavailable')}
                />
            )}
            {invalid && (
                <Alert
                    showIcon
                    type="warning"
                    message={t('publicCaptureRecovery.invalidLink')}
                    action={
                        <Button onClick={() => select(pending?.captureId ?? null)}>
                            {t('publicCaptureRecovery.resetLink')}
                        </Button>
                    }
                />
            )}
            {pending && (
                <Card size="small" title={t('publicCaptureRecovery.original')}>
                    <Alert
                        showIcon
                        type={terminal && originalSelected ? 'info' : 'warning'}
                        message={t(
                            terminal && originalSelected
                                ? 'publicCaptureRecovery.resultFound'
                                : 'publicCaptureRecovery.unknown',
                        )}
                    />
                    <Descriptions
                        size="small"
                        column={{xs: 1, md: 2}}
                        items={[
                            {
                                key: 'start',
                                label: t('startTime'),
                                children: formatDateTime(pending.input.start),
                            },
                            {
                                key: 'end',
                                label: t('endTime'),
                                children: formatDateTime(pending.input.end),
                            },
                        ]}
                    />
                    <div className="capture-actions">
                        {!originalSelected && (
                            <Button onClick={() => select(pending.captureId)}>
                                {t('publicCaptureRecovery.locate')}
                            </Button>
                        )}
                        <Button
                            loading={query.isFetching}
                            disabled={busy}
                            onClick={() => {
                                if (!originalSelected) select(pending.captureId);
                                else void query.refetch();
                            }}
                        >
                            {t('publicCaptureRecovery.lookup')}
                        </Button>
                        {canWrite && (
                            <Button
                                disabled={busy || !continuation}
                                loading={resume.isPending}
                                onClick={() => resume.mutate()}
                            >
                                {t('publicCaptureRecovery.continue')}
                            </Button>
                        )}
                        <Button
                            disabled={busy || query.isFetching || !originalSelected || !terminal}
                            loading={acknowledge.isPending}
                            onClick={() => acknowledge.mutate()}
                        >
                            {t('publicCaptureRecovery.acknowledge')}
                        </Button>
                    </div>
                    <Typography.Paragraph type="secondary">
                        {t('publicCaptureRecovery.continueHelp')}
                    </Typography.Paragraph>
                </Card>
            )}
            <Form<Values>
                name="marketdata-public-capture"
                form={form}
                layout="vertical"
                initialValues={{start: end.subtract(24, 'hour'), end}}
                onFinish={(value) => {
                    create.reset();
                    resume.reset();
                    acknowledge.reset();
                    create.mutate(value);
                }}
            >
                <Row gutter={16}>
                    <Col xs={24} md={12}>
                        <Form.Item
                            label={t('startTime')}
                            name="start"
                            rules={[{required: true, message: t('selectAStartTime')}]}
                        >
                            <DatePicker
                                disabled={!canWrite || saved.kind !== 'none' || busy}
                                onKeyDown={confirmTimeWithoutSubmittingForm}
                                showTime={{format: 'HH:mm:ss'}}
                                format="YYYY-MM-DD HH:mm:ss"
                            />
                        </Form.Item>
                    </Col>
                    <Col xs={24} md={12}>
                        <Form.Item
                            label={t('endTime')}
                            name="end"
                            dependencies={['start']}
                            rules={[
                                {required: true, message: t('selectAnEndTime')},
                                endAfterStartRule('start'),
                            ]}
                        >
                            <DatePicker
                                disabled={!canWrite || saved.kind !== 'none' || busy}
                                onKeyDown={confirmTimeWithoutSubmittingForm}
                                showTime={{format: 'HH:mm:ss'}}
                                format="YYYY-MM-DD HH:mm:ss"
                            />
                        </Form.Item>
                    </Col>
                </Row>
                <Button
                    type="primary"
                    htmlType="submit"
                    disabled={!canWrite || saved.kind !== 'none' || busy}
                    loading={create.isPending}
                >
                    {t('publicCaptureAction')}
                </Button>
            </Form>
            {mutationError && <Alert showIcon type="error" message={errorText(mutationError)} />}
            {selected && query.isPending && (
                <Typography.Paragraph>{t('publicCaptureRecovery.loading')}</Typography.Paragraph>
            )}
            {selected && query.error && (
                <Alert
                    showIcon
                    type={notVisible ? 'warning' : 'error'}
                    message={t(
                        notVisible
                            ? 'publicCaptureRecovery.notVisible'
                            : 'publicCaptureRecovery.readFailed',
                    )}
                    action={
                        <Button onClick={() => void query.refetch()}>
                            {t('publicCaptureRecovery.retry')}
                        </Button>
                    }
                />
            )}
            {result && (
                <Card
                    size="small"
                    title={
                        <Space wrap>
                            {t('publicCaptureRecovery.result')}
                            <Tag
                                color={
                                    result.state === 'SUCCEEDED'
                                        ? 'success'
                                        : result.state === 'FAILED'
                                          ? 'error'
                                          : 'warning'
                                }
                            >
                                {t('publicCaptureRecovery.states.' + result.state)}
                            </Tag>
                        </Space>
                    }
                >
                    {result.state === 'PENDING' && (
                        <Alert
                            showIcon
                            type="info"
                            message={t('publicCaptureRecovery.registered')}
                        />
                    )}
                    {result.state === 'FAILED' && (
                        <Alert
                            showIcon
                            type="error"
                            message={t(
                                result.failureCode === 'PUBLIC_CAPTURE_OUTBOUND_DISABLED'
                                    ? 'publicCaptureRecovery.sourceDisabled'
                                    : 'publicCaptureFailed',
                            )}
                        />
                    )}
                    <Descriptions
                        size="small"
                        column={{xs: 1, md: 2}}
                        items={[
                            {
                                key: 'start',
                                label: t('startTime'),
                                children: formatDateTime(result.start),
                            },
                            {key: 'end', label: t('endTime'), children: formatDateTime(result.end)},
                            {
                                key: 'created',
                                label: t('publicCaptureRecovery.registeredAt'),
                                children: formatDateTime(result.createdAt),
                            },
                            ...(result.completedAt
                                ? [
                                      {
                                          key: 'completed',
                                          label: t('publicCaptureRecovery.completedAt'),
                                          children: formatDateTime(result.completedAt),
                                      },
                                  ]
                                : []),
                            ...(capture
                                ? [
                                      {
                                          key: 'count',
                                          label: t('barCount'),
                                          children: capture.barCount,
                                      },
                                      {
                                          key: 'observed',
                                          label: t('marketdataObservedAt'),
                                          children: formatDateTime(capture.observedAt),
                                      },
                                  ]
                                : []),
                        ]}
                    />
                    {capture && (
                        <Link to="/marketdata?section=preparation&preparationTab=datasets">
                            {t('publicCaptureRecovery.datasets')}
                        </Link>
                    )}
                    <Collapse
                        ghost
                        items={[
                            {
                                key: 'source',
                                label: t('publicCaptureRecovery.details'),
                                children: (
                                    <>
                                        <Typography.Paragraph>
                                            {t('publicCaptureDescription')}
                                        </Typography.Paragraph>
                                        <Descriptions
                                            size="small"
                                            column={1}
                                            items={[
                                                {
                                                    key: 'id',
                                                    label: t('publicCaptureRecovery.captureId'),
                                                    children: result.captureId,
                                                },
                                                ...(result.failureCode
                                                    ? [
                                                          {
                                                              key: 'failure',
                                                              label: t(
                                                                  'publicCaptureRecovery.failureCode',
                                                              ),
                                                              children: result.failureCode,
                                                          },
                                                      ]
                                                    : []),
                                                ...(capture
                                                    ? [
                                                          {
                                                              key: 'dataset',
                                                              label: t('datasetId'),
                                                              children: capture.datasetId,
                                                          },
                                                          {
                                                              key: 'raw',
                                                              label: t(
                                                                  'publicCaptureRecovery.rawDigest',
                                                              ),
                                                              children: capture.rawSha256,
                                                          },
                                                          {
                                                              key: 'normalized',
                                                              label: t(
                                                                  'publicCaptureRecovery.normalizedDigest',
                                                              ),
                                                              children: capture.normalizedSha256,
                                                          },
                                                          {
                                                              key: 'consumed',
                                                              label: t('marketdataDataDigest'),
                                                              children: capture.consumedSha256,
                                                          },
                                                          {
                                                              key: 'ruleRaw',
                                                              label: t(
                                                                  'publicCaptureRecovery.ruleRawDigest',
                                                              ),
                                                              children: capture.ruleRawSha256,
                                                          },
                                                          {
                                                              key: 'rule',
                                                              label: t('marketdataRuleDigest'),
                                                              children: capture.ruleSha256,
                                                          },
                                                          {
                                                              key: 'ruleObserved',
                                                              label: t(
                                                                  'publicCaptureRecovery.ruleObserved',
                                                              ),
                                                              children: formatDateTime(
                                                                  capture.ruleObservedAt,
                                                              ),
                                                          },
                                                          {
                                                              key: 'visibility',
                                                              label: t(
                                                                  'publicCaptureRecovery.visibility',
                                                              ),
                                                              children: `${capture.replayVisibilityVersion} · ${capture.replayVisibilitySource}`,
                                                          },
                                                          {
                                                              key: 'policy',
                                                              label: t(
                                                                  'publicCaptureRecovery.rulePolicy',
                                                              ),
                                                              children: capture.rulePolicy,
                                                          },
                                                      ]
                                                    : []),
                                            ]}
                                        />
                                    </>
                                ),
                            },
                        ]}
                    />
                </Card>
            )}
        </Card>
    );
}
