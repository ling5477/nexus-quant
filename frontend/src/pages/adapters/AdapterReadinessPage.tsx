import {Alert, Button, Card, Space, Table, Tag, Typography} from 'antd';
import type {ColumnsType} from 'antd/es/table';

import {formatApiError} from '@/api/errors';
import {PageHero} from '@/components/page/PageHero';
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
            return {label: '就绪 READY', color: 'green'};
        case 'NOT_READY':
            return {label: '未就绪 NOT_READY', color: 'red'};
        case 'NO_REAL':
            return {label: '模拟·无真实 NO_REAL', color: 'default'};
        case 'DISABLED_SENTINEL':
            return {label: '端点禁用 DISABLED_SENTINEL', color: 'orange'};
        case 'CREDENTIAL_UNCONFIGURED':
            return {label: '凭证未配置 CREDENTIAL_UNCONFIGURED', color: 'orange'};
        case 'LIVE_NOT_AUTHORIZED':
            return {label: 'LIVE 未授权 LIVE_NOT_AUTHORIZED', color: 'red'};
        case 'CAPABILITY_NOT_IMPLEMENTED':
            return {label: '能力未实现 CAPABILITY_NOT_IMPLEMENTED', color: 'orange'};
        case 'UNKNOWN_REQUIRES_REVIEW':
            return {label: '未知需复核 UNKNOWN_REQUIRES_REVIEW', color: 'red'};
        default:
            return {label: `未就绪 ${status}`, color: 'red'};
    }
}

const REASON_LABELS: Record<string, string> = {
    NO_REAL_DISABLED: 'Noop 无真实能力',
    ENDPOINT_DISABLED_SENTINEL: '端点禁用 sentinel',
    CREDENTIALS_MISSING: '凭证未配置',
    REAL_PROVIDER_NOT_IMPLEMENTED: '真实 provider 未实现',
    LIVE_DISABLED: 'LIVE 已禁用',
    CAPABILITY_FORBIDDEN_IN_GATEL: '当前阶段禁止',
    RAW_PAYLOAD_SUPPRESSED: '原始负载已抑制',
    UNKNOWN_REQUIRES_REVIEW: '未知需复核',
};

function reasonLabel(reason: string): string {
    return REASON_LABELS[reason] ?? reason;
}

function buildColumns(): ColumnsType<AdapterReadinessItem> {
    return [
        {
            title: 'Venue',
            dataIndex: 'venue',
            key: 'venue',
            width: 130,
            render: (venue: string) => (
                <Tag color={EXCHANGE_VENUES.has(venue) ? 'blue' : 'default'}>{venue}</Tag>
            ),
        },
        {
            title: 'Capability',
            dataIndex: 'capability',
            key: 'capability',
            width: 200,
            render: (capability: string) => <Text code>{capability}</Text>,
        },
        {
            title: '状态 Status',
            dataIndex: 'status',
            key: 'status',
            width: 280,
            render: (status: string) => {
                const {label, color} = statusPresentation(status);
                return <Tag color={color}>{label}</Tag>;
            },
        },
        {
            title: '可用 Allowed',
            dataIndex: 'allowed',
            key: 'allowed',
            width: 130,
            render: (allowed: boolean) => (
                allowed
                    ? <Tag color="green">可用</Tag>
                    : <Tag color="red">不可用</Tag>
            ),
        },
        {
            title: 'LIVE 授权',
            dataIndex: 'liveAuthorized',
            key: 'liveAuthorized',
            width: 140,
            render: (liveAuthorized: boolean) => (
                liveAuthorized
                    ? <Tag color="green">LIVE 已授权</Tag>
                    : <Tag color="red">LIVE 未授权</Tag>
            ),
        },
        {
            title: '原因 Reasons',
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
            title: '说明 Message',
            dataIndex: 'message',
            key: 'message',
            render: (message: string) => <Text type="secondary">{message}</Text>,
        },
    ];
}

/**
 * AdapterReadinessPage 是 GateM-5B 的只读 adapter readiness 面板。
 *
 * Why:
 * 给运维 / 操作者一个明确入口，确认当前 OKX / Binance / Noop 各能力是否可实盘及原因。页面表达的核心事实：
 * 当前不是 ready、不是 real-trading enabled、不是 LIVE。所有 `allowed=false` 一律显示为「不可用」，
 * 失败态显示 "readiness API unavailable" 并按 fail-closed 处理，绝不回退成可用 / 可交易。
 */
export function AdapterReadinessPage() {
    const readinessQuery = useAdapterReadinessQuery();
    const items = readinessQuery.data?.items ?? [];

    return (
        <Space direction="vertical" size={16} style={{display: 'flex'}}>
            <Card className="page-card" bordered={false}>
                <PageHero
                    title="Adapter Readiness"
                    description="只读查看各交易所适配器与能力的运行时 readiness。当前为 No-Real / LIVE 禁用基线：所有交易所能力均未就绪、不可真实交易、不可真实下单/撤单/行情订阅。"
                    badge="只读 · 安全边界"
                />
            </Card>

            <Alert
                type="warning"
                showIcon
                message="当前所有交易所适配器未就绪（NOT READY / NOT FROZEN / NOT AUTHORIZED）"
                description="LIVE 已禁用，OKX / Binance 不可真实下单、撤单或行情订阅；Noop / Paper / Sim 为模拟无真实能力。本页仅展示只读 readiness 状态，不提供任何真实交易入口。"
            />

            <Card
                className="page-section"
                bordered={false}
                title="Adapter readiness 状态"
                extra={(
                    <Space size={12}>
                        {readinessQuery.data?.generatedAt ? (
                            <Text type="secondary">生成时间：{formatDateTime(readinessQuery.data.generatedAt)}</Text>
                        ) : null}
                        <Button
                            onClick={() => readinessQuery.refetch()}
                            loading={readinessQuery.isFetching}
                        >
                            刷新
                        </Button>
                    </Space>
                )}
            >
                {readinessQuery.isError ? (
                    <Alert
                        type="error"
                        showIcon
                        message="readiness API unavailable"
                        description={(
                            <Paragraph style={{marginBottom: 0}}>
                                未能获取 adapter readiness；按安全策略一律视为<strong>未就绪（fail-closed）</strong>，
                                不代表任何 venue 已就绪或允许下单。请稍后重试或联系运维。
                                <br />
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
                        locale={{emptyText: '暂无 adapter readiness 数据'}}
                    />
                )}
            </Card>
        </Space>
    );
}
