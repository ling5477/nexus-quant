import {useTranslation} from 'react-i18next';
import {describeApiError, formatApiError} from '@/api/errors';
import {t} from '@/i18n';
import {ReloadOutlined} from '@ant-design/icons';
import {Alert, Button, InputNumber, Select, Space, Table, Typography} from 'antd';
import type {ColumnsType} from 'antd/es/table';

import {NqStatusTag} from '@/components/nq';
import type {AppApiError} from '@/types/api';
import type {
    ValidationReviewCase,
    ValidationReviewSeverity,
    ValidationReviewState,
} from '@/types/validation-review';
import {formatDateTime} from '@/utils/formatters';

const {Text} = Typography;

interface ValidationReviewQueueProps {
    data: ValidationReviewCase[];
    error: AppApiError | null;
    isLoading: boolean;
    isFetching: boolean;
    isAdmin: boolean;
    selectedCaseId: string | null;
    state?: ValidationReviewState;
    severity?: ValidationReviewSeverity;
    ownerId?: number;
    limit: number;
    offset: number;
    onStateChange: (value?: ValidationReviewState) => void;
    onSeverityChange: (value?: ValidationReviewSeverity) => void;
    onOwnerChange: (value?: number) => void;
    onSelectCase: (caseId: string) => void;
    onPageChange: (offset: number) => void;
    onRefresh: () => void;
}

const columns: ColumnsType<ValidationReviewCase> = [
    {
        get title() { return t('pages:caseId'); },
        dataIndex: 'id',
        key: 'id',
        width: 210,
        render: (value: string) => <Text code copyable>{value}</Text>,
    },
    {
        get title() { return t('pages:status'); },
        dataIndex: 'state',
        key: 'state',
        width: 140,
        render: (value: string) => <NqStatusTag status={value}/>,
    },
    {
        get title() { return t('pages:severity2'); },
        dataIndex: 'severity',
        key: 'severity',
        width: 120,
        render: (value: string) => <NqStatusTag status={value}/>,
    },
    {get title() { return t('pages:owner'); }, dataIndex: 'ownerId', key: 'ownerId', width: 100},
    {
        get title() { return t('pages:diagnosticSource'); },
        key: 'source',
        width: 230,
        render: (_, record) => (
            <Space direction="vertical" size={0}>
                <Text>{record.evidenceType}</Text>
                <Text type="secondary" ellipsis={{tooltip: record.evidenceSource}}>{record.evidenceSource}</Text>
            </Space>
        ),
    },
    {get title() { return t('pages:title'); }, dataIndex: 'title', key: 'title', ellipsis: true},
    {
        get title() { return t('pages:createdAt'); },
        dataIndex: 'createdAt',
        key: 'createdAt',
        width: 180,
        render: formatDateTime,
    },
    {
        get title() { return t('pages:updatedAt'); },
        dataIndex: 'updatedAt',
        key: 'updatedAt',
        width: 180,
        render: formatDateTime,
    },
];

/**
 * Review queue 使用后端 limit/offset 和稳定排序，不伪造 total。
 * OPERATOR 看不到 owner filter，避免形成跨 owner 查询暗示；服务端仍是最终权限边界。
 */
export function ValidationReviewQueue(props: ValidationReviewQueueProps) {
    useTranslation('pages');
    const pageNumber = Math.floor(props.offset / props.limit) + 1;

    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}} data-testid="validation-review-queue">
            <Space wrap>
                <Select
                    aria-label={t('pages:reviewState')}
                    allowClear
                    placeholder={t('pages:allStatuses')}
                    value={props.state}
                    style={{width: 180}}
                    options={['OPEN', 'ACKNOWLEDGED', 'ESCALATED', 'RESOLVED', 'CLOSED'].map((value) => ({value}))}
                    onChange={props.onStateChange}
                />
                <Select
                    aria-label={t('pages:reviewSeverity')}
                    allowClear
                    placeholder={t('pages:allSeverities')}
                    value={props.severity}
                    style={{width: 160}}
                    options={['INFO', 'WARNING', 'HIGH', 'CRITICAL'].map((value) => ({value}))}
                    onChange={props.onSeverityChange}
                />
                {props.isAdmin ? (
                    <InputNumber
                        aria-label={t('pages:ownerId')}
                        min={1}
                        precision={0}
                        placeholder={t('pages:ownerId')}
                        value={props.ownerId}
                        onChange={(value) => props.onOwnerChange(typeof value === 'number' ? value : undefined)}
                    />
                ) : null}
                <Button icon={<ReloadOutlined/>} loading={props.isFetching} onClick={props.onRefresh}>
                    {t('pages:refreshQueue')}</Button>
            </Space>

            {props.error ? (
                <Alert type="error" showIcon message={describeApiError(props.error).title} description={formatApiError(props.error)}/>
            ) : null}

            <Table<ValidationReviewCase>
                rowKey="id"
                size="small"
                loading={props.isLoading}
                columns={columns}
                dataSource={props.data}
                pagination={false}
                scroll={{x: 1450}}
                locale={{emptyText: t('pages:noReviewCasesMatchTheseFilters')}}
                rowClassName={(record) => record.id === props.selectedCaseId ? 'ant-table-row-selected' : ''}
                onRow={(record) => ({onClick: () => props.onSelectCase(record.id)})}
            />

            <Space style={{justifyContent: 'space-between', width: '100%'}}>
                <Text type="secondary">{t('pages:page')}{pageNumber} {t('pages:current2')}{props.data.length} {t('pages:itemsTotalUnavailableFromBackend')}</Text>
                <Space>
                    <Button disabled={props.offset === 0 || props.isFetching}
                            onClick={() => props.onPageChange(Math.max(0, props.offset - props.limit))}>
                        {t('pages:previousPage')}</Button>
                    <Button disabled={props.data.length < props.limit || props.isFetching}
                            onClick={() => props.onPageChange(props.offset + props.limit)}>
                        {t('pages:nextPage')}</Button>
                </Space>
            </Space>
        </Space>
    );
}
