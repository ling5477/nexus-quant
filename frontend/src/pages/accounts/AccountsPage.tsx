import {Alert, Button, Card, Empty, Space, Table, Tag, Typography} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useQuery} from '@tanstack/react-query';

import {accountsApi} from '@/api/accounts';
import {accountQueryKeys} from '@/api/query-keys';
import {PageHero} from '@/components/page/PageHero';
import {useAuthStore} from '@/store/auth-store';
import {useAccountContextStore} from '@/store/account-context-store';
import type {ExchangeAccountSummary} from '@/types/accounts';

export function AccountsPage() {
    const accessToken = useAuthStore((state) => state.accessToken);
    const selectedExchangeAccountId = useAccountContextStore((state) => state.selectedExchangeAccountId);
    const setSelectedAccount = useAccountContextStore((state) => state.setSelectedAccount);
    const accountsQuery = useQuery({
        queryKey: accountQueryKeys.list(accessToken),
        queryFn: accountsApi.list,
        enabled: Boolean(accessToken),
    });

    const columns: ColumnsType<ExchangeAccountSummary> = [
        {
            title: 'Account ID',
            dataIndex: 'exchangeAccountId',
            key: 'exchangeAccountId',
            width: 120,
        },
        {
            title: '交易所',
            dataIndex: 'exchangeCode',
            key: 'exchangeCode',
            width: 120,
        },
        {
            title: '环境',
            dataIndex: 'tradeEnv',
            key: 'tradeEnv',
            width: 100,
            render: (value: string) => <Tag color={value === 'LIVE' ? 'red' : 'blue'}>{value}</Tag>,
        },
        {
            title: '账户别名',
            dataIndex: 'accountAlias',
            key: 'accountAlias',
            width: 180,
        },
        {
            title: '兼容 legacyAccountId',
            dataIndex: 'legacyAccountId',
            key: 'legacyAccountId',
            width: 160,
            render: (value: number | null) => value ?? '-',
        },
        {
            title: '默认账户',
            dataIndex: 'isDefault',
            key: 'isDefault',
            width: 120,
            render: (value: boolean) => value ? <Tag color="success">默认</Tag> : '-',
        },
        {
            title: '状态',
            dataIndex: 'status',
            key: 'status',
            width: 120,
        },
        {
            title: '操作',
            key: 'action',
            width: 140,
            render: (_, record) => (
                <Button type="link" onClick={() => setSelectedAccount(record)}>
                    设为当前上下文
                </Button>
            ),
        },
    ];

    return (
        <Space direction="vertical" size={16} style={{display: 'flex'}}>
            <Card className="page-card" bordered={false}>
                <PageHero
                    title="账户与凭证管理"
                    description="RC1 先落账户上下文与账户管理骨架；凭证管理在本页后续抽屉中逐步补齐。"
                    badge="RC1-4"
                />
            </Card>
            <Card className="page-section" bordered={false} title="当前上下文">
                {selectedExchangeAccountId ? (
                    <Typography.Text>当前已选择 exchangeAccountId: {selectedExchangeAccountId}</Typography.Text>
                ) : (
                    <Alert type="info" showIcon message="当前尚未选择账户上下文。"/>
                )}
            </Card>
            <Card className="page-section" bordered={false} title="账户列表">
                {accountsQuery.isLoading ? (
                    <Alert type="info" showIcon message="正在加载账户列表..."/>
                ) : accountsQuery.error ? (
                    <Alert type="error" showIcon message="账户列表加载失败"/>
                ) : (accountsQuery.data?.length ?? 0) === 0 ? (
                    <Empty description="当前用户尚未绑定任何 exchange account。"/>
                ) : (
                    <Table
                        rowKey="exchangeAccountId"
                        columns={columns}
                        dataSource={accountsQuery.data}
                        pagination={{pageSize: 10, showSizeChanger: false}}
                    />
                )}
            </Card>
        </Space>
    );
}
