import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {Alert, Button, Card, Space, Table, Tag, Typography} from 'antd';
import type {ColumnsType} from 'antd/es/table';

import {formatApiError} from '@/api/errors';
import {NqPageHeader} from '@/components/nq/NqPageHeader';
import {useAdapterReadinessQuery} from '@/hooks/useAdapterReadinessQuery';
import type {AppApiError} from '@/types/api';
import type {AdapterReadinessItem} from '@/types/adapter-readiness';
import {formatDateTime} from '@/utils/formatters';

const {Text, Paragraph} = Typography;

// 真实交易所 venue：必须明显展示为未就绪 / 未授权，绝不暗示可真实交易。
const EXCHANGE_VENUES = new Set(['OKX', 'BINANCE']);

interface LabeledTone {
    label: string;
    color: string;
}

/**
 * status → 展示标签与颜色。任何未知 status 一律按 fail-closed（红色）处理，避免把未知误显示成可用。
 */
function statusPresentation(status: string): LabeledTone {
    switch (status) {
        case 'READY':
            // 当前 baseline 不会出现；保留映射但不作为默认。
            return {label: t('pages:readyReady'), color: 'green'};
        case 'NOT_READY':
            return {label: t('pages:notReadyNotReady'), color: 'red'};
        case 'NO_REAL':
            return {label: t('pages:simulationOnlyNoReal'), color: 'default'};
        case 'DISABLED_SENTINEL':
            return {label: t('pages:endpointDisabledDisabledSentinel'), color: 'orange'};
        case 'CREDENTIAL_UNCONFIGURED':
            return {label: t('pages:credentialsNotConfiguredCredentialUnconfigured'), color: 'orange'};
        case 'LIVE_NOT_AUTHORIZED':
            return {label: t('pages:liveNotAuthorizedLiveNotAuthorized'), color: 'red'};
        case 'CAPABILITY_NOT_IMPLEMENTED':
            return {label: t('pages:capabilityNotImplementedCapabilityNotImplemented'), color: 'orange'};
        case 'UNKNOWN_REQUIRES_REVIEW':
            return {label: t('pages:unknownReviewRequiredUnknownRequiresReview'), color: 'red'};
        default:
            return {label: t('pages:notReadyValue1', {value1: status}), color: 'red'};
    }
}

const REASON_LABELS: Record<string, string> = {
    get NO_REAL_DISABLED() { return t('pages:noopHasNoRealCapability'); },
    get ENDPOINT_DISABLED_SENTINEL() { return t('pages:endpointDisabledSentinel'); },
    get CREDENTIALS_MISSING() { return t('pages:credentialsNotConfigured'); },
    get REAL_PROVIDER_NOT_IMPLEMENTED() { return t('pages:realProviderNotImplemented2'); },
    get LIVE_DISABLED() { return t('pages:liveDisabled3'); },
    get CAPABILITY_FORBIDDEN_IN_GATEL() { return t('pages:prohibitedInTheCurrentPhase'); },
    get RAW_PAYLOAD_SUPPRESSED() { return t('pages:rawPayloadSuppressed'); },
    get UNKNOWN_REQUIRES_REVIEW() { return t('pages:unknownReviewRequired'); },
};

function reasonLabel(reason: string): string {
    return REASON_LABELS[reason] ?? reason;
}

function buildColumns(): ColumnsType<AdapterReadinessItem> {
    return [
        {
            title: t('pages:venue'),
            dataIndex: 'venue',
            key: 'venue',
            width: 130,
            render: (venue: string) => (
                <Tag color={EXCHANGE_VENUES.has(venue) ? 'blue' : 'default'}>{venue}</Tag>
            ),
        },
        {
            title: t('pages:capability'),
            dataIndex: 'capability',
            key: 'capability',
            width: 200,
            render: (capability: string) => <Text code>{capability}</Text>,
        },
        {
            title: t('pages:status2'),
            dataIndex: 'status',
            key: 'status',
            width: 280,
            render: (status: string) => {
                const {label, color} = statusPresentation(status);
                return <Tag color={color}>{label}</Tag>;
            },
        },
        {
            title: t('pages:allowed'),
            dataIndex: 'allowed',
            key: 'allowed',
            width: 130,
            render: (allowed: boolean) => (
                allowed
                    ? <Tag color="green">{t('pages:available')}</Tag>
                    : <Tag color="red">{t('pages:unavailable')}</Tag>
            ),
        },
        {
            title: t('pages:liveAuthorization'),
            dataIndex: 'liveAuthorized',
            key: 'liveAuthorized',
            width: 140,
            render: (liveAuthorized: boolean) => (
                liveAuthorized
                    ? <Tag color="green">{t('pages:liveAuthorized')}</Tag>
                    : <Tag color="red">{t('pages:liveNotAuthorized')}</Tag>
            ),
        },
        {
            title: t('pages:reasons'),
            dataIndex: 'reasons',
            key: 'reasons',
            width: 320,
            render: (reasons: string[]) => (
                <Space size={[4, 4]} wrap>
                    {(reasons ?? []).map((reason) => (
                        <Tag key={reason} color="default" title={reason}>{reasonLabel(reason)}</Tag>
                    ))}
                </Space>
            ),
        },
        {
            title: t('pages:explanation'),
            dataIndex: 'message',
            key: 'message',
            render: (message: string) => <Text type="secondary">{message}</Text>,
        },
    ];
}

/**
 * AdapterReadinessPage 是适配器就绪策略的只读 adapter readiness 面板。
 *
 * Why:
 * 给运维 / 操作者一个明确入口，确认当前 OKX / Binance / Noop 各能力是否可实盘及原因。页面表达的核心事实：
 * 当前不是 ready、不是 real-trading enabled、不是 LIVE。所有 `allowed=false` 一律显示为「不可用」，
 * 失败态显示 "readiness API unavailable" 并按 fail-closed 处理，绝不回退成可用 / 可交易。
 */
export function AdapterReadinessPage() {
    useTranslation('pages');
    const readinessQuery = useAdapterReadinessQuery();
    const items = readinessQuery.data?.items ?? [];

    return (
        <Space direction="vertical" size={16} style={{display: 'flex'}}>
            <Card className="page-card" bordered={false}>
                <NqPageHeader
                    title={t('pages:adapterReadiness')}
                    description={t('pages:readOnlyRuntimeReadinessForExchangeAdaptersAndCapabilitiesUnderTheNoRealLiveDisabledBaselineCapabili')}
                    badge={t('pages:readOnlySafetyBoundaries')}
                />
            </Card>

            <Alert
                type="warning"
                showIcon
                message={t('pages:allExchangeAdaptersAreNotReadyNotFrozenNotAuthorized')}
                description={t('pages:liveIsDisabledOkxBinanceCannotPlaceOrCancelRealOrdersOrSubscribeToMarketDataNoopPaperSimAreSimulatio')}
            />

            <Card
                className="page-section"
                bordered={false}
                title={t('pages:adapterReadinessStatus')}
                extra={(
                    <Space size={12}>
                        {readinessQuery.data?.generatedAt ? (
                            <Text type="secondary">{t('pages:generatedAt2')}{formatDateTime(readinessQuery.data.generatedAt)}</Text>
                        ) : null}
                        <Button
                            onClick={() => readinessQuery.refetch()}
                            loading={readinessQuery.isFetching}
                        >
                            {t('pages:refresh')}</Button>
                    </Space>
                )}
            >
                {readinessQuery.isError ? (
                    <Alert
                        type="error"
                        showIcon
                        message={t('pages:readinessApiUnavailable')}
                        description={(
                            <Paragraph style={{marginBottom: 0}}>
                                {t('pages:adapterReadinessCouldNotBeRetrievedSafetyPolicyTreatsItAs')}<strong>{t('pages:notReadyFailClosed')}</strong>{t('pages:noVenueIsConsideredReadyOrPermittedToPlaceOrdersTryAgainLaterOrContactOperations')}<br />
                                <Text type="secondary">{formatApiError(readinessQuery.error as AppApiError)}</Text>
                            </Paragraph>
                        )}
                    />
                ) : (
                    <Table<AdapterReadinessItem>
                        rowKey={(record) => `${record.venue}-${record.capability}`}
                        columns={buildColumns()}
                        dataSource={items}
                        loading={readinessQuery.isLoading || readinessQuery.isFetching}
                        pagination={false}
                        scroll={{x: 1280}}
                        locale={{emptyText: t('pages:noAdapterReadinessData')}}
                    />
                )}
            </Card>
        </Space>
    );
}
