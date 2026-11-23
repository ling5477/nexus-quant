import {Space, Table, Tag, Typography} from 'antd';
import {Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {formatDateTime} from '@/utils/formatters';
import type {TenantItem} from './api';
import {UserStateAction} from './UserStateAction';

export function TenantTable({items, loading, pagination, management = true}: {
    items: TenantItem[]; loading: boolean; management?: boolean;
    pagination?: {current: number; pageSize: number; total: number; onChange: (page: number, size: number) => void};
}) {
    const {t} = useTranslation('platform');
    return <Table<TenantItem> rowKey="userId" dataSource={items} loading={loading}
        scroll={{x: 1000}} pagination={pagination ? {...pagination, showSizeChanger: true, pageSizeOptions: [10, 20, 50]} : false}
        locale={{emptyText: t('empty')}} columns={[
            {title: t('username'), dataIndex: 'username', render: (name: string) => <Typography.Text>{name}</Typography.Text>},
            {title: t('emailVerified'), dataIndex: 'emailVerified', render: (verified: boolean) => t(verified ? 'verified' : 'pending')},
            {title: t('registeredAt'), dataIndex: 'registeredAt', render: (value: string) => formatDateTime(value)},
            {title: t('accountState'), dataIndex: 'enabled', render: (enabled: boolean) => <Tag color={enabled ? 'green' : 'default'}>{t(enabled ? 'enabled' : 'disabled')}</Tag>},
            {title: t('accountType'), dataIndex: 'simAccountStatus', render: (value: string) => t(value, {defaultValue: t('unknown')})},
            {title: t('simState'), dataIndex: 'simStatus', render: (value: string) => t(value, {defaultValue: t('unknown')})},
            {title: t('actions'), key: 'actions', render: (_, item) => <Space wrap>
                {item.tenantId && <Link to={`/platform/tenants/${encodeURIComponent(item.tenantId)}`}>{t('view')}</Link>}
                {management && <UserStateAction user={item} />}
            </Space>},
        ]} />;
}
