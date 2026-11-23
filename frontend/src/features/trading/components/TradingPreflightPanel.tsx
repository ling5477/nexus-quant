import {useEffect, useRef, type ReactNode} from 'react';
import {Alert, Button, Collapse, Descriptions, List, Space, Typography} from 'antd';
import {useQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {formatApiError} from '@/api/errors';
import {tradingWorkbenchQueryKeys} from '@/api/query-keys';
import {useAuthStore} from '@/store/auth-store';
import {formatDateTime} from '@/utils/formatters';
import type {AppApiError} from '@/types/api';
import {tradingWorkbenchApi} from '../api/trading-workbench';

/** 此面板只读取本地诊断，不探活、不提交委托，也不能把诊断结果作为交易授权。 */
export function TradingPreflightPanel({
    accountId,
    exchangeCode,
}: {
    accountId: number;
    exchangeCode: string;
}) {
    const {t, i18n} = useTranslation('pages');
    const userId = useAuthStore((state) => state.currentUser?.userId);
    const query = useQuery({
        queryKey: tradingWorkbenchQueryKeys.readiness(userId, accountId, exchangeCode),
        queryFn: () =>
            tradingWorkbenchApi.readPreflight({accountId, exchangeCode, marketType: 'SPOT'}),
        enabled: Boolean(userId),
        retry: false,
    });
    const panelRef = useRef<HTMLDivElement>(null);
    useEffect(() => {
        // 异常与重试会替换按钮；只有焦点已掉回body时才接回面板，不抢走关闭按钮等现有焦点。
        if (document.activeElement === document.body) panelRef.current?.focus();
    }, [query.isFetching, query.error, query.dataUpdatedAt]);
    const frame = (content: ReactNode) => (
        <div ref={panelRef} tabIndex={-1} role="region" aria-label={t('preOrderChecks')}>
            {content}
        </div>
    );
    const codeLabel = (code: string) => {
        const key = `tradingActions.preflightCodes.${code}`;
        return i18n.exists(key, {ns: 'pages'}) ? t(key) : t('tradingActions.unknownCode', {code});
    };
    if (query.error)
        return frame(
            <Alert
                type="error"
                showIcon
                message={t('tradingActions.preflightFailed')}
                description={formatApiError(query.error as AppApiError)}
                action={
                    <Button onClick={() => void query.refetch()} loading={query.isFetching}>
                        {t('retry')}
                    </Button>
                }
            />,
        );
    if (!query.data)
        return frame(<Alert type="info" showIcon message={t('tradingActions.preflightLoading')} />);
    const data = query.data;
    return frame(
        <Space
            direction="vertical"
            size={16}
            style={{display: 'flex', minWidth: 0}}
            data-testid="trading-preflight"
        >
            <Alert
                type="info"
                showIcon
                message={t('tradingActions.preflightReadOnly')}
                description={t('tradingActions.preflightBoundary')}
            />
            <Descriptions bordered size="small" column={1}>
                <Descriptions.Item label={t('accountId')}>
                    {data.accountId ?? '—'}
                </Descriptions.Item>
                <Descriptions.Item label={t('venue')}>{data.exchangeCode}</Descriptions.Item>
                <Descriptions.Item label={t('tradingActions.accountStatus')}>
                    <span title={data.accountStatus}>{codeLabel(data.accountStatus)}</span>
                </Descriptions.Item>
                <Descriptions.Item label={t('tradingActions.marketQuality')}>
                    <span title={data.dataQualityStatus}>{codeLabel(data.dataQualityStatus)}</span>
                </Descriptions.Item>
                <Descriptions.Item label={t('tradingActions.riskBaseline')}>
                    <span title={data.riskPreflightStatus}>
                        {codeLabel(data.riskPreflightStatus)}
                    </span>
                </Descriptions.Item>
                <Descriptions.Item label={t('tradingActions.checkedAt')}>
                    {formatDateTime(data.generatedAt)}
                </Descriptions.Item>
            </Descriptions>
            <Typography.Title level={5} style={{margin: 0}}>
                {t('tradingActions.preflightBlockers')}
            </Typography.Title>
            <List
                size="small"
                bordered
                dataSource={data.blockers}
                locale={{emptyText: t('tradingActions.noBlockersReported')}}
                renderItem={(row) => (
                    <List.Item>
                        <Typography.Text title={`${row.code}: ${row.message}`}>
                            {codeLabel(row.code)}
                        </Typography.Text>
                    </List.Item>
                )}
            />
            <Button onClick={() => void query.refetch()} loading={query.isFetching}>
                {t('tradingActions.refreshPreflight')}
            </Button>
            <Collapse
                size="small"
                items={[
                    {
                        key: 'raw',
                        label: t('tradingActions.originalReceipt'),
                        children: (
                            <pre
                                style={{
                                    whiteSpace: 'pre-wrap',
                                    overflowWrap: 'anywhere',
                                    maxHeight: 360,
                                    overflow: 'auto',
                                }}
                            >
                                {JSON.stringify(data, null, 2)}
                            </pre>
                        ),
                    },
                ]}
            />
        </Space>,
    );
}
