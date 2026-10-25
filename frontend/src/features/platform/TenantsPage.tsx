import {Alert, Button, Card, Input, Select, Space, Typography} from 'antd';
import {useQuery} from '@tanstack/react-query';
import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {ApiErrorNotice} from '@/errors/ApiErrorNotice';
import type {AppApiError} from '@/types/api';
import {useAuthStore, selectSessionIdentity} from '@/store/auth-store';
import {platformApi, type TenantFilters} from './api';
import {TenantTable} from './TenantTable';
import './platform.css';

export function Component() {
    const {t} = useTranslation('platform');
    const identity = useAuthStore(selectSessionIdentity);
    const [filters, setFilters] = useState<TenantFilters>({page: 0, size: 20, search: '', sort: 'registeredAt,desc'});
    const query = useQuery({queryKey: ['platform', identity, 'tenants', filters], queryFn: ({signal}) => platformApi.tenants(filters, signal)});
    const data = query.isError ? undefined : query.data;
    return <section className="platform-page">
        <Space wrap className="platform-page__heading"><Typography.Title level={2}>{t('tenants')}</Typography.Title>
            <Button loading={query.isFetching} onClick={() => void query.refetch()}>{t('refresh')}</Button></Space>
        <Alert type="info" showIcon message={t('boundary')} />
        <Card>
            <Space wrap className="platform-page__filters">
                <Input.Search allowClear placeholder={t('search')} aria-label={t('search')} maxLength={64}
                    onSearch={(search) => setFilters({...filters, search: search.trim(), page: 0})} style={{width: 260}} />
                <Select value={filters.enabled === undefined ? 'all' : String(filters.enabled)} aria-label={t('accountState')}
                    options={[{value: 'all', label: t('allStates')}, {value: 'true', label: t('enabled')}, {value: 'false', label: t('disabled')}]}
                    onChange={(value) => setFilters({...filters, enabled: value === 'all' ? undefined : value === 'true', page: 0})} style={{width: 180}} />
                <Select value={filters.sort} aria-label={t('registeredAt')}
                    options={[{value: 'registeredAt,desc', label: t('newest')}, {value: 'registeredAt,asc', label: t('oldest')}]}
                    onChange={(sort) => setFilters({...filters, sort, page: 0})} style={{width: 260}} />
            </Space>
            {query.error && <ApiErrorNotice error={query.error as AppApiError} />}
            <TenantTable items={data?.items ?? []} loading={query.isPending} pagination={{current: filters.page + 1,
                pageSize: filters.size, total: data?.total ?? 0, onChange: (page, size) => setFilters({...filters, page: page - 1, size})}} />
        </Card>
    </section>;
}
