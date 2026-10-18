import {Alert, Button, Grid, Input, Modal, Space, Table, Typography} from 'antd';
import type {InputProps} from 'antd';
import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {formatApiError} from '@/api/errors';
import {
    useStrategyVersionDetailQuery,
    useStrategyVersionPageQuery,
} from '@/features/strategies/hooks/useStrategyListQuery';
import {StrategyVersionQueryFields, type VersionFilters} from './StrategyVersionQueryFields';
import {StrategyVersionSnapshotPanel} from './StrategyVersionSnapshotPanel';
import type {StrategyVersionListItem} from '@/features/strategies/types/strategies';
import type {AppApiError} from '@/types/api';

interface Props
    extends Pick<
        InputProps,
        'id' | 'disabled' | 'aria-label' | 'aria-describedby' | 'aria-invalid' | 'aria-required'
    > {
    strategyCode: string | null | undefined;
    value?: string;
    active: boolean;
    onChange?: (id: string | undefined) => void;
}

/** 固定父策略的有界选择；取消保留原绑定草稿，选择与绑定仍是两个独立动作。 */
export function StrategyVersionPicker({
    strategyCode,
    value,
    active,
    onChange,
    ...inputProps
}: Props) {
    const {t} = useTranslation('pages');
    const compact = !Grid.useBreakpoint().lg;
    const [open, setOpen] = useState(false);
    const [filters, setFilters] = useState<VersionFilters>({});
    const [revision, setRevision] = useState(0);
    const [session, setSession] = useState(0);
    const [page, setPage] = useState(1);
    const [expanded, setExpanded] = useState<string | null>(null);
    const showing = active && open && Boolean(strategyCode);
    const selected = useStrategyVersionDetailQuery(strategyCode, value, active);
    const results = useStrategyVersionPageQuery(
        strategyCode ?? '',
        {...filters, limit: 20, offset: (page - 1) * 20},
        showing,
        revision,
    );
    const disabled = inputProps.disabled || !strategyCode;
    const choose = () => {
        setFilters({});
        setPage(1);
        setExpanded(null);
        setRevision((v) => v + 1);
        setSession((v) => v + 1);
        setOpen(true);
    };
    const label = selected.data
        ? `${selected.data.versionName} · v${selected.data.version} · ${selected.data.status}`
        : (value ?? '');
    return (
        <Space direction="vertical" size={8} style={{display: 'flex'}}>
            <Space.Compact block>
                <Input
                    {...inputProps}
                    disabled={disabled}
                    readOnly
                    value={label}
                    title={label}
                    placeholder={t('selectStrategyVersion')}
                    onKeyDown={(event) => {
                        if (event.key === 'Enter' && !disabled) {
                            event.preventDefault();
                            choose();
                        }
                    }}
                />
                <Button disabled={disabled} onClick={choose}>
                    {t('versionPickerChoose')}
                </Button>
                {value && (
                    <Button disabled={inputProps.disabled} onClick={() => onChange?.(undefined)}>
                        {t('researchPickerClear')}
                    </Button>
                )}
            </Space.Compact>
            {!strategyCode && (
                <Typography.Text type="secondary">
                    {t('versionPickerParentRequired')}
                </Typography.Text>
            )}
            {active && selected.isError && (
                <Alert
                    type="error"
                    showIcon
                    message={t('versionPickerSelectedUnavailable')}
                    description={formatApiError(selected.error as AppApiError)}
                    action={<Button onClick={() => void selected.refetch()}>{t('retry')}</Button>}
                />
            )}
            {/* 第二层选择窗高于配置抽屉，关闭时查询身份改变并取消目录及展开的快照读取。 */}
            <Modal
                open={showing}
                title={t('versionPickerTitle')}
                width={1000}
                zIndex={1100}
                destroyOnHidden
                footer={null}
                onCancel={() => setOpen(false)}
            >
                <Typography.Paragraph type="secondary">
                    {t('versionPickerHelp', {code: strategyCode})}
                </Typography.Paragraph>
                <StrategyVersionQueryFields
                    key={session}
                    onSearch={(values) => {
                        setFilters(values);
                        setPage(1);
                        setExpanded(null);
                        setRevision((v) => v + 1);
                    }}
                    onReset={() => {
                        setFilters({});
                        setPage(1);
                        setExpanded(null);
                        setRevision((v) => v + 1);
                    }}
                />
                {results.isError ? (
                    <Alert
                        type="error"
                        showIcon
                        message={t('failedToQueryStrategyVersions')}
                        description={formatApiError(results.error as AppApiError)}
                        action={
                            <Button onClick={() => void results.refetch()}>{t('retry')}</Button>
                        }
                    />
                ) : (
                    <Table<StrategyVersionListItem>
                        size="small"
                        tableLayout="fixed"
                        rowKey="strategyVersionId"
                        loading={results.isFetching}
                        dataSource={results.data?.items ?? []}
                        columns={[
                            {
                                title: t('strategyVersion'),
                                key: 'version',
                                render: (_, row) => (
                                    <div style={{overflowWrap: 'anywhere'}}>
                                        <Typography.Text strong>{row.versionName}</Typography.Text>
                                        <br />
                                        <Typography.Text type="secondary">
                                            v{row.version} · {row.status}
                                        </Typography.Text>
                                        <br />
                                        <Typography.Text type="secondary">
                                            {row.strategyVersionId}
                                        </Typography.Text>
                                    </div>
                                ),
                            },
                            {
                                title: t('actions'),
                                key: 'select',
                                width: 80,
                                render: (_, row) => (
                                    <Button
                                        type="link"
                                        size="small"
                                        onClick={() => {
                                            onChange?.(row.strategyVersionId);
                                            setOpen(false);
                                        }}
                                    >
                                        {t('researchPickerUse')}
                                    </Button>
                                ),
                            },
                        ]}
                        expandable={{
                            expandedRowKeys: expanded ? [expanded] : [],
                            onExpand: (expanded, row) =>
                                setExpanded(expanded ? row.strategyVersionId : null),
                            expandedRowRender: (row) =>
                                showing && expanded === row.strategyVersionId ? (
                                    <StrategyVersionSnapshotPanel
                                        strategyCode={strategyCode!}
                                        versionId={row.strategyVersionId}
                                    />
                                ) : null,
                        }}
                        pagination={{
                            current: page,
                            pageSize: 20,
                            total: results.data?.total ?? 0,
                            showSizeChanger: false,
                            onChange: (next) => {
                                setPage(next);
                                setExpanded(null);
                            },
                            showTotal: (total) => t('versionPageTotal', {total}),
                        }}
                        scroll={{y: compact ? 320 : 420}}
                        locale={{emptyText: t('versionPickerEmpty')}}
                    />
                )}
            </Modal>
        </Space>
    );
}
