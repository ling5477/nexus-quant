import {Link} from 'react-router-dom';
import {isPublicDemo} from '@/utils/distribution';
import {isResearchJob, researchFromDatasetLink} from './research-dataset';
import {Alert, Button, Card, Collapse, Descriptions, Space, Tag, Typography} from 'antd';
import {useTranslation} from 'react-i18next';
import {formatApiError} from '@/api/errors';
import type {AppApiError} from '@/types/api';
import {formatDateTime} from '@/utils/formatters';
import type {DatasetOperationState} from './useDatasetOperationState';
import './DatasetOperationRecovery.css';

/** 正式回执显示原次数据质量；本机待确认记录仅负责找回原编号，不生成成功结果。 */
export function DatasetOperationRecovery({state}: {state: DatasetOperationState}) {
    const {t} = useTranslation('pages');
    const {pending, result, query, busy, terminal, originalSelected} = state;
    const mutationError =
        state.create.error ?? state.refresh.error ?? state.resume.error ?? state.acknowledge.error;
    function errorText(error: unknown) {
        const code = (error as AppApiError)?.code;
        if (code?.startsWith('DATASET_OPERATION_'))
            return t('datasetRecovery.errors.' + code.slice('DATASET_OPERATION_'.length), {
                defaultValue: formatApiError(error as AppApiError),
            });
        return formatApiError(error as AppApiError);
    }
    const receipt = result?.state === 'SUCCEEDED' ? result.result : null;
    return (
        <div className="dataset-operation-recovery" data-testid="dataset-operation-recovery">
            {!state.canWrite && (
                <Alert showIcon type="info" message={t('datasetRecovery.readOnly')} />
            )}
            {state.saved.kind === 'unavailable' && (
                <Alert showIcon type="error" message={t('datasetRecovery.storageUnavailable')} />
            )}
            {state.invalid && (
                <Alert
                    showIcon
                    type="warning"
                    message={t('datasetRecovery.invalidLink')}
                    action={
                        <Button onClick={() => state.select(pending?.request.operationId ?? null)}>
                            {t('datasetRecovery.resetLink')}
                        </Button>
                    }
                />
            )}
            {pending && (
                <Card size="small" title={t('datasetRecovery.original')}>
                    <Alert
                        showIcon
                        type={originalSelected && terminal ? 'info' : 'warning'}
                        message={t(
                            originalSelected && terminal
                                ? 'datasetRecovery.resultFound'
                                : 'datasetRecovery.unknown',
                        )}
                    />
                    <Descriptions
                        size="small"
                        column={{xs: 1, md: 2}}
                        items={[
                            {
                                key: 'kind',
                                label: t('datasetRecovery.kind'),
                                children: t('datasetRecovery.kinds.' + pending.request.kind),
                            },
                            {
                                key: 'dataset',
                                label: t('datasetId'),
                                children: (
                                    <Typography.Text copyable>
                                        {pending.request.datasetId}
                                    </Typography.Text>
                                ),
                            },
                            ...(pending.request.kind === 'CREATE'
                                ? [
                                      {
                                          key: 'name',
                                          label: t('datasetName'),
                                          children: pending.request.input.datasetName,
                                      },
                                      {
                                          key: 'object',
                                          label: t('datasetRecovery.scope'),
                                          children: `${pending.request.input.exchangeCode} · ${pending.request.input.marketType} · ${pending.request.input.symbol} · ${pending.request.input.interval}`,
                                      },
                                      {
                                          key: 'start',
                                          label: t('startTime'),
                                          children: pending.request.input.startTime,
                                      },
                                      {
                                          key: 'end',
                                          label: t('endTime'),
                                          children: pending.request.input.endTime,
                                      },
                                  ]
                                : []),
                            {
                                key: 'id',
                                label: t('datasetRecovery.operationId'),
                                children: (
                                    <Typography.Text copyable>
                                        {pending.request.operationId}
                                    </Typography.Text>
                                ),
                            },
                        ]}
                    />
                    <Space wrap className="dataset-recovery-actions">
                        {!originalSelected && (
                            <Button onClick={() => state.select(pending.request.operationId)}>
                                {t('datasetRecovery.locate')}
                            </Button>
                        )}
                        <Button
                            loading={query.isFetching}
                            disabled={busy}
                            onClick={() => {
                                if (!originalSelected) state.select(pending.request.operationId);
                                else void query.refetch();
                            }}
                        >
                            {t('datasetRecovery.lookup')}
                        </Button>
                        <Button
                            disabled={!state.canWrite || busy || !state.continuation}
                            loading={state.resume.isPending}
                            onClick={() => state.resume.mutate()}
                        >
                            {t('datasetRecovery.continue')}
                        </Button>
                        <Button
                            disabled={busy || query.isFetching || !originalSelected || !terminal}
                            loading={state.acknowledge.isPending}
                            onClick={() => state.acknowledge.mutate()}
                        >
                            {t('datasetRecovery.acknowledge')}
                        </Button>
                    </Space>
                    <Typography.Paragraph type="secondary">
                        {t('datasetRecovery.continueHelp')}
                    </Typography.Paragraph>
                </Card>
            )}
            {mutationError && (
                <Alert
                    showIcon
                    type="error"
                    message={t('datasetRecovery.actionError')}
                    description={errorText(mutationError)}
                />
            )}
            {state.selected && !result && (
                <Alert
                    showIcon
                    type={query.isError ? 'warning' : 'info'}
                    message={t(
                        query.isError
                            ? state.notVisible
                                ? 'datasetRecovery.notVisible'
                                : 'datasetRecovery.queryError'
                            : 'datasetRecovery.loading',
                    )}
                    description={
                        query.isError && !state.notVisible ? errorText(query.error) : undefined
                    }
                    action={
                        <Button
                            loading={query.isFetching}
                            disabled={busy}
                            onClick={() => void query.refetch()}
                        >
                            {t('datasetRecovery.lookup')}
                        </Button>
                    }
                />
            )}
            {result && (
                <Card
                    size="small"
                    title={t('datasetRecovery.receipt')}
                    extra={
                        <Tag
                            color={
                                result.state === 'SUCCEEDED'
                                    ? 'green'
                                    : result.state === 'FAILED'
                                      ? 'red'
                                      : 'orange'
                            }
                        >
                            {t('datasetRecovery.states.' + result.state)}
                        </Tag>
                    }
                >
                    {result.state === 'FAILED' && (
                        <Alert
                            showIcon
                            type="error"
                            message={t('datasetRecovery.failures.' + result.failureCode)}
                        />
                    )}
                    <Descriptions
                        size="small"
                        column={{xs: 1, md: 2}}
                        items={[
                            {
                                key: 'kind',
                                label: t('datasetRecovery.kind'),
                                children: t('datasetRecovery.kinds.' + result.kind),
                            },
                            {key: 'dataset', label: t('datasetId'), children: result.datasetId},
                            {
                                key: 'created',
                                label: t('datasetRecovery.createdAt'),
                                children: formatDateTime(result.createdAt),
                            },
                            ...(result.completedAt
                                ? [
                                      {
                                          key: 'completed',
                                          label: t('datasetRecovery.completedAt'),
                                          children: formatDateTime(result.completedAt),
                                      },
                                  ]
                                : []),
                            ...(receipt
                                ? [
                                      {
                                          key: 'name',
                                          label: t('datasetName'),
                                          children: receipt.dataset.datasetName,
                                      },
                                      {
                                          key: 'status',
                                          label: t('status'),
                                          children: t(
                                              'datasetRecovery.dataStates.' +
                                                  receipt.dataset.status,
                                              {defaultValue: receipt.dataset.status},
                                          ),
                                      },
                                      {
                                          key: 'quality',
                                          label: t('quality'),
                                          children: t(
                                              'datasetRecovery.qualities.' +
                                                  receipt.coverage.qualityStatus,
                                              {defaultValue: receipt.coverage.qualityStatus},
                                          ),
                                      },
                                      {
                                          key: 'actual',
                                          label: t('barCount'),
                                          children: receipt.coverage.actualBars,
                                      },
                                      {
                                          key: 'expected',
                                          label: t('datasetRecovery.expected'),
                                          children: receipt.coverage.expectedBars,
                                      },
                                      {
                                          key: 'missing',
                                          label: t('gaps'),
                                          children: receipt.coverage.missingBars,
                                      },
                                      {
                                          key: 'duplicates',
                                          label: t('datasetRecovery.duplicates'),
                                          children: receipt.coverage.duplicateBars,
                                      },
                                      {
                                          key: 'invalid',
                                          label: t('datasetRecovery.invalidBars'),
                                          children: receipt.coverage.invalidBars,
                                      },
                                      {
                                          key: 'start',
                                          label: t('startTime'),
                                          children: receipt.dataset.startTime,
                                      },
                                      {
                                          key: 'end',
                                          label: t('endTime'),
                                          children: receipt.dataset.endTime,
                                      },
                                  ]
                                : []),
                        ]}
                    />
                    {receipt && (
                        <Typography.Paragraph type="secondary">
                            {t('datasetRecovery.qualityHelp')}
                            {!isPublicDemo && isResearchJob(receipt.dataset) && receipt.dataset.source === 'marketdata_bars' && receipt.dataset.status === 'READY' && receipt.coverage.qualityStatus === 'OK' && receipt.coverage.missingBars === 0 && (
                                <><br /><Link to={researchFromDatasetLink(receipt.dataset.datasetId)}>{t('researchDataset.createResearch')}</Link></>
                            )}
                        </Typography.Paragraph>
                    )}
                    <Collapse
                        ghost
                        items={[
                            {
                                key: 'details',
                                label: t('datasetRecovery.details'),
                                children: (
                                    <>
                                        <Typography.Paragraph>
                                            {t('datasetRecovery.operationId')}：
                                            <Typography.Text copyable>
                                                {result.operationId}
                                            </Typography.Text>
                                        </Typography.Paragraph>
                                        {receipt && (
                                            <Typography.Paragraph>
                                                {t('datasetRecovery.coverageId')}：
                                                {receipt.coverage.coverageId}
                                            </Typography.Paragraph>
                                        )}
                                        <pre>{JSON.stringify(result, null, 2)}</pre>
                                    </>
                                ),
                            },
                        ]}
                    />
                </Card>
            )}
        </div>
    );
}
