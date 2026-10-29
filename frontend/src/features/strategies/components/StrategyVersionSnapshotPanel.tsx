import {ObjectConfigurationSummary} from '@/components/configuration/ObjectConfigurationSummary';
import {VersionPublicationRelations} from './StrategyRelations';
import {Alert, Button, Descriptions, Space, Spin, Typography} from 'antd';
import {useTranslation} from 'react-i18next';
import {formatApiError} from '@/api/errors';
import {useStrategyVersionDetailQuery} from '@/features/strategies/hooks/useStrategyListQuery';
import type {AppApiError} from '@/types/api';

/** 冻结JSON保留服务端原字符串，避免解析重排时损失大整数与小数精度。 */
export function StrategyVersionSnapshotPanel({
    strategyCode,
    versionId,
}: {
    strategyCode: string;
    versionId: string;
}) {
    const {t} = useTranslation('pages');
    const query = useStrategyVersionDetailQuery(strategyCode, versionId);
    if (query.isError)
        return (
            <Alert
                type="error"
                showIcon
                message={t('versionSnapshotLoadFailed')}
                description={formatApiError(query.error as AppApiError)}
                action={<Button onClick={() => void query.refetch()}>{t('retry')}</Button>}
            />
        );
    if (!query.data) return <Spin />;
    const row = query.data;
    return (
        <Space direction="vertical" style={{display: 'flex'}}>
            <Descriptions size="small" column={1}>
                <Descriptions.Item label={t('versionId')}>
                    <Typography.Text copyable>{row.strategyVersionId}</Typography.Text>
                </Descriptions.Item>
                <Descriptions.Item label={t('checksum')}>
                    <Typography.Text copyable style={{overflowWrap: 'anywhere'}}>
                        {row.checksum}
                    </Typography.Text>
                </Descriptions.Item>
            </Descriptions>
            <VersionPublicationRelations key={versionId} versionId={versionId} />
            {(['paramSnapshotJson', 'configSnapshotJson', 'sourceSnapshotJson'] as const).map(
                (key) => (
                    <div key={key}>
                        <Typography.Text strong>
                            {t(
                                key === 'paramSnapshotJson'
                                    ? 'parameterSnapshotJson'
                                    : key === 'configSnapshotJson'
                                      ? 'configurationSnapshotJson'
                                      : 'sourceSnapshotJson',
                            )}
                        </Typography.Text>
                        <ObjectConfigurationSummary value={row[key]} />
                    </div>
                ),
            )}
        </Space>
    );
}
