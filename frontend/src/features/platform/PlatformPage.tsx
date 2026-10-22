import {Alert, Button, Card, Col, Row, Select, Space, Statistic, Typography} from 'antd';
import {useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {ApiErrorNotice} from '@/errors/ApiErrorNotice';
import type {AppApiError} from '@/types/api';
import {useAuthStore, selectSessionIdentity} from '@/store/auth-store';
import {formatDateTime} from '@/utils/formatters';
import {platformApi} from './api';
import {TenantTable} from './TenantTable';
import './platform.css';

export function Component() {
    const {t} = useTranslation('platform');
    const identity = useAuthStore(selectSessionIdentity);
    const query = useQuery({queryKey: ['platform', identity, 'overview'],
        queryFn: ({signal}) => platformApi.tenants({page: 0, size: 10, search: '', sort: 'registeredAt,desc'}, signal)});
    const [selectedTenant, setSelectedTenant] = useState<string>();
    const performance = useQuery({queryKey: ['platform', identity, 'overview-performance', selectedTenant],
        queryFn: ({signal}) => platformApi.performance(selectedTenant!, signal), enabled: Boolean(selectedTenant), retry: false});
    const selectedPerformance = performance.isError ? undefined : performance.data;
    const data = query.isError ? undefined : query.data;
    return <section className="platform-page">
        <Space wrap className="platform-page__heading"><Typography.Title level={2}>{t('overview')}</Typography.Title>
            <Button loading={query.isFetching} onClick={() => void query.refetch()}>{t('refresh')}</Button></Space>
        <Alert showIcon type="info" message={t('boundary')} />
        {query.error && <ApiErrorNotice error={query.error as AppApiError} />}
        <Row gutter={[16, 16]}>{(['registeredUsers', 'enabledUsers', 'disabledUsers', 'runningSimCount', 'capacity'] as const).map((key) =>
            <Col key={key} xs={12} lg={key === 'registeredUsers' ? 8 : 4}>
                <Card><Statistic title={t(key)} value={data?.overview[key] ?? '—'} /></Card>
            </Col>)}</Row>
        {data && <Card title={t('dataScope')}>
            <Typography.Paragraph>{t('registeredScope')}</Typography.Paragraph>
            <Typography.Paragraph>{t('source')}: {t(data.overview.source, {defaultValue: data.overview.source})} · {t('asOf')}: {formatDateTime(data.overview.asOf)}</Typography.Paragraph>
            <Typography.Paragraph>{t('performanceScope')}</Typography.Paragraph>
        </Card>}
        <Card title={t('performance')}>
            <Typography.Paragraph>{t('performanceScope')}</Typography.Paragraph>
            <Select allowClear value={selectedTenant} aria-label={t('selectTenant')} placeholder={t('selectTenant')}
                style={{width: '100%', maxWidth: 360}} onChange={setSelectedTenant}
                options={(data?.items ?? []).filter((item) => item.tenantId).map((item) => ({value: item.tenantId!, label: item.username}))} />
            {performance.error && <ApiErrorNotice error={performance.error as AppApiError} />}
            {selectedPerformance && <Space direction="vertical" style={{marginTop: 16, display: 'flex'}}>
                <Typography.Text>{t('netPnl')}: {selectedPerformance.netPnl ?? t('unknown')} USDT</Typography.Text>
                <Typography.Text>{t('equity')}: {selectedPerformance.equity ?? t('unknown')} USDT</Typography.Text>
                {selectedPerformance.status !== 'AVAILABLE' && <Alert showIcon type="warning" message={t(selectedPerformance.reason ?? selectedPerformance.status, {defaultValue: t('unavailable')})} />}
                <Typography.Text>{t('source')}: {t(selectedPerformance.source, {defaultValue: selectedPerformance.source})} · {t('asOf')}: {selectedPerformance.asOf ? formatDateTime(selectedPerformance.asOf) : t('unknown')}</Typography.Text>
                <Link to={`/platform/tenants/${selectedTenant}`}>{t('history')}</Link>
            </Space>}
        </Card>
        <Card title={t('recent')} extra={<Link to="/platform/tenants">{t('viewAll')}</Link>}>
            <TenantTable items={data?.items ?? []} loading={query.isPending} management={false} />
        </Card>
    </section>;
}
