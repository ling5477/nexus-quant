import {Alert, Button, Space, Table} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {formatApiError} from '@/api/errors';
import {useStrategyVersionPageQuery} from '@/features/strategies/hooks/useStrategyListQuery';
import {StrategyVersionQueryFields, type VersionFilters} from './StrategyVersionQueryFields';
import {StrategyVersionSnapshotPanel} from './StrategyVersionSnapshotPanel';
import type {StrategyVersionListItem} from '@/features/strategies/types/strategies';
import type {AppApiError} from '@/types/api';

/** 列表保持旧字段，分页在服务端完成；只展开一个版本并按身份读取快照。 */
export function StrategyVersionList({
    strategyCode,
    columns,
    locatedVersionId,
}: {
    strategyCode: string;
    columns: ColumnsType<StrategyVersionListItem>;
    locatedVersionId: string | null;
}) {
    const {t} = useTranslation('pages');
    const [filters, setFilters] = useState<VersionFilters>({});
    const [page, setPage] = useState(1);
    const [revision, setRevision] = useState(0);
    const [expanded, setExpanded] = useState<string | null>(null);
    const results = useStrategyVersionPageQuery(
        strategyCode,
        {...filters, limit: 5, offset: (page - 1) * 5},
        true,
        revision,
    );
    const search = (values: VersionFilters) => {
        setFilters(values);
        setPage(1);
        setExpanded(null);
        setRevision((v) => v + 1);
    };
    return (
        <Space direction="vertical" style={{display: 'flex'}}>
            <StrategyVersionQueryFields onSearch={search} onReset={() => search({})} />
            <Button onClick={() => void results.refetch()} loading={results.isFetching}>
                {t('refreshVersions')}
            </Button>
            {results.isError ? (
                <Alert
                    type="error"
                    showIcon
                    message={t('failedToQueryStrategyVersions')}
                    description={formatApiError(results.error as AppApiError)}
                    action={<Button onClick={() => void results.refetch()}>{t('retry')}</Button>}
                />
            ) : (
                <Table<StrategyVersionListItem>
                    rowKey="strategyVersionId"
                    size="small"
                    columns={columns}
                    dataSource={results.data?.items ?? []}
                    loading={results.isFetching}
                    rowClassName={(row) =>
                        row.strategyVersionId === locatedVersionId ? 'nq-located-object' : ''
                    }
                    pagination={{
                        current: page,
                        pageSize: 5,
                        total: results.data?.total ?? 0,
                        showSizeChanger: false,
                        onChange: (next) => {
                            setPage(next);
                            setExpanded(null);
                        },
                        showTotal: (total) => t('versionPageTotal', {total}),
                    }}
                    expandable={{
                        expandedRowKeys: expanded ? [expanded] : [],
                        onExpand: (open, row) => setExpanded(open ? row.strategyVersionId : null),
                        expandedRowRender: (row) =>
                            expanded === row.strategyVersionId ? (
                                <StrategyVersionSnapshotPanel
                                    strategyCode={strategyCode}
                                    versionId={row.strategyVersionId}
                                />
                            ) : null,
                    }}
                    scroll={{x: 1000}}
                    locale={{emptyText: t('versionPickerEmpty')}}
                />
            )}
        </Space>
    );
}
