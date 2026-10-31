import {Alert, Button, Card, Col, Descriptions, Row, Space, Statistic, Table, Typography} from 'antd';
import {useQuery} from '@tanstack/react-query';
import {Link, useParams} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {ApiErrorNotice} from '@/errors/ApiErrorNotice';
import type {AppApiError} from '@/types/api';
import {useAuthStore, selectSessionIdentity} from '@/store/auth-store';
import {formatDateTime} from '@/utils/formatters';
import {decimalDisplay} from '@/utils/decimal-display';
import {platformApi, type SimHistoryPoint} from './api';
import {UserStateAction} from './UserStateAction';
import './platform.css';

/** 所有估值均来自专用只读接口；前端不重算利润、不以零填补缺失。 */
export function Component() {
    const {t} = useTranslation('platform');
    const {tenantId = ''} = useParams();
    const identity = useAuthStore(selectSessionIdentity);
    const summary = useQuery({queryKey: ['platform', identity, 'summary', tenantId],
        queryFn: ({signal}) => platformApi.summary(tenantId, signal), enabled: Boolean(tenantId)});
    const performance = useQuery({queryKey: ['platform', identity, 'performance', tenantId],
        queryFn: ({signal}) => platformApi.performance(tenantId, signal), enabled: Boolean(tenantId), retry: false});
    const user = summary.isError ? undefined : summary.data;
    const data = performance.isError ? undefined : performance.data;
    const value = (number: string | number | null | undefined, percentage = false) => number == null ? t('unknown')
        : typeof number === 'number' ? number : decimalDisplay(number, percentage ? 4 : 8, percentage ? 2 : 0) + (percentage ? '%' : '');
    const bool = (flag: boolean | null | undefined) => flag == null ? t('unknown') : t(flag ? 'yes' : 'no');
    return <section className="platform-page">
        <Link to="/platform/tenants">{t('back')}</Link>
        <Space wrap className="platform-page__heading"><Typography.Title level={2}>{t('detail')}</Typography.Title>
            <Button loading={summary.isFetching || performance.isFetching} onClick={() => {void summary.refetch(); void performance.refetch();}}>{t('refresh')}</Button></Space>
        <Alert type="info" showIcon message={t('boundary')} />
        {summary.error && <ApiErrorNotice error={summary.error as AppApiError} />}
        <Card title={t('accountInformation')} loading={summary.isPending} extra={user && <UserStateAction user={user} />}>
            {user && <Descriptions column={{xs: 1, md: 2}} items={[
                {key: 'username', label: t('username'), children: user.username},
                {key: 'tenant', label: t('tenantId'), children: user.tenantId},
                {key: 'email', label: t('emailVerified'), children: t(user.emailVerified ? 'verified' : 'pending')},
                {key: 'registered', label: t('registeredAt'), children: formatDateTime(user.registeredAt)},
                {key: 'enabled', label: t('accountState'), children: t(user.enabled ? 'enabled' : 'disabled')},
                {key: 'account', label: t('accountType'), children: t(user.simAccountStatus, {defaultValue: t('unknown')})},
                {key: 'sim', label: t('simState'), children: t(user.simStatus, {defaultValue: t('unknown')})},
                {key: 'suspended', label: t('suspended'), children: bool(user.suspended)},
                {key: 'resume', label: t('manualResume'), children: bool(user.requiresManualResume)},
                {key: 'epoch', label: t('epoch'), children: value(user.executionEpoch)},
            ]} />}
        </Card>
        <Card title={t('performance')} loading={performance.isPending}>
            <Typography.Paragraph>{t('fundingNote')}</Typography.Paragraph>
            {performance.error && <ApiErrorNotice error={performance.error as AppApiError} />}
            {data && data.status !== 'AVAILABLE' && <Alert type="warning" showIcon message={t(data.status === 'STALE' ? 'STALE' : 'unavailable')}
                description={t(data.reason ?? data.status, {defaultValue: t('unknown')})} />}
            <Row gutter={[16, 20]}>{(['initialCapital', 'currentCash', 'equity', 'realizedPnl', 'unrealizedPnl', 'netPnl', 'returnRate', 'drawdown', 'orderCount', 'tradeCount'] as const).map((key) =>
                <Col xs={12} lg={6} key={key}><Statistic title={t(key)} value={value(data?.[key], key === 'returnRate' || key === 'drawdown')} /></Col>)}</Row>
            {data && <Typography.Paragraph type="secondary" style={{marginTop: 20}}>
                {t('source')}: {t(data.source, {defaultValue: data.source})} · {t('asOf')}: {data.asOf ? formatDateTime(data.asOf) : t('unknown')}
                {' · '}{t('historyWindow')}: {data.coverageStart ? formatDateTime(data.coverageStart) : t('unknown')} — {data.coverageEnd ? formatDateTime(data.coverageEnd) : t('unknown')}
            </Typography.Paragraph>}
        </Card>
        <Card title={t('history')}>
            <Table<SimHistoryPoint> rowKey="id" dataSource={data?.history ?? []} pagination={{pageSize: 20, showSizeChanger: false}}
                scroll={{x: 620}} locale={{emptyText: t('noHistory')}} columns={[
                    {title: t('at'), dataIndex: 'at', render: (at: string) => formatDateTime(at)},
                    {title: t('equity'), dataIndex: 'equity', render: (v: string | null) => value(v)},
                    {title: t('currentCash'), dataIndex: 'cash', render: (v: string | null) => value(v)},
                    {title: t('realizedPnl'), dataIndex: 'realizedPnl', render: (v: string | null) => value(v)},
                    {title: t('unrealizedPnl'), dataIndex: 'unrealizedPnl', render: (v: string | null) => value(v)},
                    {title: t('drawdown'), dataIndex: 'drawdown', render: (v: string | null) => value(v, true)},
                ]} />
        </Card>
    </section>;
}
