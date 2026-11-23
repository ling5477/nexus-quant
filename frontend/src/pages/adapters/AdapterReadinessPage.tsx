import {
    Alert,
    Button,
    Card,
    Collapse,
    Descriptions,
    Drawer,
    Select,
    Space,
    Tag,
    Typography,
} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useMemo, useState, type HTMLAttributes} from 'react';
import {useTranslation} from 'react-i18next';
import {Link} from 'react-router-dom';
import {formatApiError} from '@/api/errors';
import {NqPageHeader} from '@/components/nq/NqPageHeader';
import {NqDataTable} from '@/components/nq/NqDataTable';
import {NqPageScaffold} from '@/nq-design-system/shell/NqPageScaffold';
import {ExchangeIcon} from '@/nq-design-system/brand/ExchangeBadge';
import {useAdapterReadinessQuery} from '@/hooks/useAdapterReadinessQuery';
import type {AdapterReadinessItem} from '@/types/adapter-readiness';
import {formatDateTime} from '@/utils/formatters';
import {
    adapterCapabilityKey,
    adapterStatusView,
    adapterVenueRows,
} from './adapterReadinessPresentation';

const reasonKeys: Record<string, string> = {
    NO_REAL_DISABLED: 'noReal',
    NO_REAL_PROVIDER: 'noReal',
    ENDPOINT_DISABLED_SENTINEL: 'endpointDisabled',
    CREDENTIALS_MISSING: 'credentialsMissing',
    CREDENTIAL_UNCONFIGURED: 'credentialsMissing',
    REAL_PROVIDER_NOT_IMPLEMENTED: 'providerMissing',
    LIVE_DISABLED: 'liveDisabled',
    CAPABILITY_FORBIDDEN_IN_GATEL: 'policyRestricted',
    RAW_PAYLOAD_SUPPRESSED: 'sourceSuppressed',
    UNKNOWN_REQUIRES_REVIEW: 'unknown',
    READY_FOR_PAPER_ONLY: 'paperOnly',
    PAPER_ARTIFACT_NOT_REAL_AUTHORIZATION: 'paperNotAuthority',
    LIVE_NOT_AUTHORIZED: 'liveUnauthorized',
    PERMISSION_PROBE_DISABLED: 'probeDisabled',
    FUTURE_REAL_DISABLED: 'providerMissing',
    FAKE_ADAPTER_DISABLED: 'testOnly',
    STUB_ADAPTER_DISABLED: 'testOnly',
};
const venueOrder: Record<string, number> = {OKX: 0, BINANCE: 1, NOOP: 2, PAPER: 3, SIM: 4};

/** 完整保留正式venue×capability快照；筛选与分页只改善呈现，静态策略不冒充历史行情覆盖或LIVE授权。 */
export function AdapterReadinessPage() {
    const {t} = useTranslation();
    const readiness = useAdapterReadinessQuery();
    const [venue, setVenue] = useState('ALL');
    const [selected, setSelected] = useState<{row: AdapterReadinessItem; at: string | null} | null>(
        null,
    );
    const items = readiness.data?.items ?? [];
    const venues = useMemo(() => Array.from(new Set(items.map((item) => item.venue))), [items]);
    // 默认先展示用户关心的交易所；仅排序副本，原快照、所有模拟/未知venue记录和授权字段都保留。
    const rows = adapterVenueRows(items, venue).sort(
        (left, right) =>
            (Object.hasOwn(venueOrder, left.venue) ? venueOrder[left.venue] : 99) -
            (Object.hasOwn(venueOrder, right.venue) ? venueOrder[right.venue] : 99),
    );
    const columns: ColumnsType<AdapterReadinessItem> = [
        {
            title: t('adapterCapabilities.venue'),
            dataIndex: 'venue',
            key: 'venue',
            width: 140,
            render: (value: string) => (
                <Space size={6}>
                    <ExchangeIcon code={value} />
                    <Typography.Text>{value}</Typography.Text>
                </Space>
            ),
        },
        {
            title: t('adapterCapabilities.capability'),
            dataIndex: 'capability',
            key: 'capability',
            width: 180,
            render: (value: string) => (
                <Typography.Text>{t(adapterCapabilityKey(value))}</Typography.Text>
            ),
        },
        {
            title: t('adapterCapabilities.status'),
            dataIndex: 'status',
            key: 'status',
            width: 150,
            render: (value: string) => {
                const view = adapterStatusView(value);
                return <Tag color={view.color}>{t(view.key)}</Tag>;
            },
        },
        {
            title: t('adapterCapabilities.allowed'),
            dataIndex: 'allowed',
            key: 'allowed',
            width: 130,
            render: (value: boolean) =>
                value === true ? (
                    <Tag color="green">{t('adapterCapabilities.permitted')}</Tag>
                ) : (
                    <Tag color="red">{t('adapterCapabilities.notPermitted')}</Tag>
                ),
        },
        {
            title: t('adapterCapabilities.live'),
            dataIndex: 'liveAuthorized',
            key: 'liveAuthorized',
            width: 130,
            render: (value: boolean) =>
                value === true ? (
                    <Tag color="green">{t('adapterCapabilities.authorized')}</Tag>
                ) : (
                    <Tag color="red">{t('adapterCapabilities.notAuthorized')}</Tag>
                ),
        },
        {
            title: t('adapterCapabilities.reasons'),
            dataIndex: 'reasons',
            key: 'reasons',
            width: 240,
            render: (values: string[]) => (
                <Space direction="vertical" size={2}>
                    {Array.from(
                        new Set(
                            (values ?? []).map((value) =>
                                Object.hasOwn(reasonKeys, value) ? reasonKeys[value] : 'unknown',
                            ),
                        ),
                    ).map((key) => (
                        <Typography.Text key={key} type="secondary">
                            {t(`adapterCapabilities.reasonsMap.${key}`)}
                        </Typography.Text>
                    ))}
                </Space>
            ),
        },
        {
            title: t('adapterCapabilities.details'),
            key: 'details',
            width: 120,
            render: (_, row) => (
                <Button
                    size="small"
                    onClick={() => setSelected({row, at: readiness.data?.generatedAt ?? null})}
                >
                    {t('adapterCapabilities.view')}
                </Button>
            ),
        },
    ];
    return (
        <NqPageScaffold>
            <NqPageHeader
                title={t('adapterCapabilities.title')}
                description={t('adapterCapabilities.description')}
                badge={t('adapterCapabilities.readonly')}
            />
            <Alert
                type="info"
                showIcon
                message={t('adapterCapabilities.scope')}
                description={
                    <Space wrap>
                        <Link to="/marketdata">{t('adapterCapabilities.marketLink')}</Link>
                        <Link to="/runtime/readiness">{t('adapterCapabilities.runtimeLink')}</Link>
                    </Space>
                }
            />
            <Card
                title={t('adapterCapabilities.matrix')}
                extra={
                    <Button loading={readiness.isFetching} onClick={() => void readiness.refetch()}>
                        {t('adapterCapabilities.refresh')}
                    </Button>
                }
            >
                <Space wrap size={12} style={{marginBottom: 12, width: '100%'}}>
                    <label>
                        {t('adapterCapabilities.filter')}{' '}
                        <Select
                            aria-label={t('adapterCapabilities.filter')}
                            value={venue}
                            style={{minWidth: 190, maxWidth: '100%'}}
                            onChange={setVenue}
                            options={[
                                {
                                    value: 'ALL',
                                    label: t('adapterCapabilities.all', {count: items.length}),
                                },
                                ...venues.map((value) => ({
                                    value,
                                    label: `${value} · ${items.filter((item) => item.venue === value).length}`,
                                })),
                            ]}
                        />
                    </label>
                    <Typography.Text type="secondary">
                        {t('adapterCapabilities.rowCount', {
                            shown: rows.length,
                            total: items.length,
                        })}
                    </Typography.Text>
                    {readiness.data?.generatedAt && (
                        <Typography.Text type="secondary">
                            {t('adapterCapabilities.snapshot')}{' '}
                            {formatDateTime(readiness.data.generatedAt)}
                        </Typography.Text>
                    )}
                </Space>
                {readiness.isError ? (
                    <Alert
                        type="error"
                        showIcon
                        message={t('adapterCapabilities.queryFailed')}
                        description={formatApiError(readiness.error)}
                    />
                ) : (
                    <NqDataTable<AdapterReadinessItem>
                        rowKey={(row) => `${row.venue}-${row.capability}`}
                        columns={columns}
                        dataSource={rows}
                        onRow={(row) =>
                            ({
                                'data-venue': row.venue,
                                'data-capability': row.capability,
                            }) as HTMLAttributes<HTMLTableRowElement>
                        }
                        loading={readiness.isFetching}
                        scroll={{x: 1230}}
                        pagination={{pageSize: 9, showSizeChanger: false, hideOnSinglePage: true}}
                        locale={{emptyText: t('adapterCapabilities.empty')}}
                    />
                )}
            </Card>
            <Drawer
                open={Boolean(selected)}
                onClose={() => setSelected(null)}
                width={640}
                destroyOnClose
                title={
                    selected
                        ? `${selected.row.venue} · ${t(adapterCapabilityKey(selected.row.capability))}`
                        : t('adapterCapabilities.details')
                }
            >
                {selected && (
                    <Space
                        direction="vertical"
                        size={12}
                        style={{width: '100%', overflowWrap: 'anywhere'}}
                    >
                        {/* 详情绑定点击时的完整快照；刷新或换筛选不能将已有状态暗换成另一对象。 */}
                        <Descriptions column={1} size="small">
                            <Descriptions.Item label={t('adapterCapabilities.venue')}>
                                {selected.row.venue}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('adapterCapabilities.capability')}>
                                {t(adapterCapabilityKey(selected.row.capability))}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('adapterCapabilities.snapshot')}>
                                {selected.at ? formatDateTime(selected.at) : '—'}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('adapterCapabilities.status')}>
                                {t(adapterStatusView(selected.row.status).key)}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('adapterCapabilities.allowed')}>
                                {t(
                                    selected.row.allowed === true
                                        ? 'adapterCapabilities.permitted'
                                        : 'adapterCapabilities.notPermitted',
                                )}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('adapterCapabilities.live')}>
                                {t(
                                    selected.row.liveAuthorized === true
                                        ? 'adapterCapabilities.authorized'
                                        : 'adapterCapabilities.notAuthorized',
                                )}
                            </Descriptions.Item>
                        </Descriptions>
                        <Alert type="info" message={t('adapterCapabilities.scope')} />
                        <Collapse
                            size="small"
                            items={[
                                {
                                    key: 'raw',
                                    label: t('adapterCapabilities.original'),
                                    children: (
                                        <Space
                                            direction="vertical"
                                            size={4}
                                            style={{overflowWrap: 'anywhere'}}
                                        >
                                            <Typography.Text code>
                                                {selected.row.venue} · {selected.row.capability}
                                            </Typography.Text>
                                            <Typography.Text code>
                                                {selected.row.status}
                                            </Typography.Text>
                                            <Typography.Text code>
                                                {JSON.stringify({
                                                    allowed: selected.row.allowed,
                                                    liveAuthorized: selected.row.liveAuthorized,
                                                })}
                                            </Typography.Text>
                                            {(selected.row.reasons ?? []).map((reason) => (
                                                <Typography.Text code key={reason}>
                                                    {reason}
                                                </Typography.Text>
                                            ))}
                                            <Typography.Text type="secondary">
                                                {selected.row.message}
                                            </Typography.Text>
                                        </Space>
                                    ),
                                },
                            ]}
                        />
                    </Space>
                )}
            </Drawer>
        </NqPageScaffold>
    );
}
