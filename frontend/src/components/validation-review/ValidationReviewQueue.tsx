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
        title: 'Case ID',
        dataIndex: 'id',
        key: 'id',
        width: 210,
        render: (value: string) => <Text code copyable>{value}</Text>,
    },
    {
        title: '状态',
        dataIndex: 'state',
        key: 'state',
        width: 140,
        render: (value: string) => <NqStatusTag status={value}/>,
    },
    {
        title: '严重度',
        dataIndex: 'severity',
        key: 'severity',
        width: 120,
        render: (value: string) => <NqStatusTag status={value}/>,
    },
    {title: 'Owner', dataIndex: 'ownerId', key: 'ownerId', width: 100},
    {
        title: '诊断来源',
        key: 'source',
        width: 230,
        render: (_, record) => (
            <Space direction="vertical" size={0}>
                <Text>{record.evidenceType}</Text>
                <Text type="secondary" ellipsis={{tooltip: record.evidenceSource}}>{record.evidenceSource}</Text>
            </Space>
        ),
    },
    {title: '标题', dataIndex: 'title', key: 'title', ellipsis: true},
    {
        title: '创建时间',
        dataIndex: 'createdAt',
        key: 'createdAt',
        width: 180,
        render: formatDateTime,
    },
    {
        title: '更新时间',
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
    const permissionDenied = props.error?.status === 403;
    const pageNumber = Math.floor(props.offset / props.limit) + 1;

    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}} data-testid="validation-review-queue">
            <Space wrap>
                <Select
                    aria-label="Review state"
                    allowClear
                    placeholder="全部状态"
                    value={props.state}
                    style={{width: 180}}
                    options={['OPEN', 'ACKNOWLEDGED', 'ESCALATED', 'RESOLVED', 'CLOSED'].map((value) => ({value}))}
                    onChange={props.onStateChange}
                />
                <Select
                    aria-label="Review severity"
                    allowClear
                    placeholder="全部严重度"
                    value={props.severity}
                    style={{width: 160}}
                    options={['INFO', 'WARNING', 'HIGH', 'CRITICAL'].map((value) => ({value}))}
                    onChange={props.onSeverityChange}
                />
                {props.isAdmin ? (
                    <InputNumber
                        aria-label="Owner ID"
                        min={1}
                        precision={0}
                        placeholder="Owner ID"
                        value={props.ownerId}
                        onChange={(value) => props.onOwnerChange(typeof value === 'number' ? value : undefined)}
                    />
                ) : null}
                <Button icon={<ReloadOutlined/>} loading={props.isFetching} onClick={props.onRefresh}>
                    刷新队列
                </Button>
            </Space>

            {permissionDenied ? (
                <Alert type="error" showIcon message="无权访问 review queue" description="当前身份没有 validation review 权限。"/>
            ) : props.error ? (
                <Alert type="error" showIcon message="Review queue 加载失败" description="请稍后重试；失败不会被解释为无待办。"/>
            ) : null}

            <Table<ValidationReviewCase>
                rowKey="id"
                size="small"
                loading={props.isLoading}
                columns={columns}
                dataSource={props.data}
                pagination={false}
                scroll={{x: 1450}}
                locale={{emptyText: '当前筛选条件下没有 review case。'}}
                rowClassName={(record) => record.id === props.selectedCaseId ? 'ant-table-row-selected' : ''}
                onRow={(record) => ({onClick: () => props.onSelectCase(record.id)})}
            />

            <Space style={{justifyContent: 'space-between', width: '100%'}}>
                <Text type="secondary">第 {pageNumber} 页 · 当前 {props.data.length} 条 · 后端未提供 total</Text>
                <Space>
                    <Button disabled={props.offset === 0 || props.isFetching}
                            onClick={() => props.onPageChange(Math.max(0, props.offset - props.limit))}>
                        上一页
                    </Button>
                    <Button disabled={props.data.length < props.limit || props.isFetching}
                            onClick={() => props.onPageChange(props.offset + props.limit)}>
                        下一页
                    </Button>
                </Space>
            </Space>
        </Space>
    );
}
