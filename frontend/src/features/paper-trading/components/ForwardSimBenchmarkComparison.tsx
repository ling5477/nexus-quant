import {Alert, Card, Collapse, Descriptions, Space, Table, Tag, Typography} from 'antd';
import {useTranslation} from 'react-i18next';
import {NqAmountText} from '@/components/nq/NqNumericText';
import {decimalDisplay} from '@/utils/decimal-display';
import {closedBarObservationMethod, type ForwardEvaluation} from '../api/observations';

/** 金额、差值及完整路径统计只展示服务端结果，不在浏览器补价或重算收益。 */
export function ForwardSimBenchmarkComparison({evaluation}: {evaluation: ForwardEvaluation}) {
    const {t} = useTranslation('pages');
    if (evaluation.methodVersion !== closedBarObservationMethod) return null;
    const valuation = evaluation.methodDetails?.closedBarValuation;
    const comparison = evaluation.methodDetails?.benchmarks;
    const state = (value?: string) => t(`forwardObservation.states.${value ?? 'UNAVAILABLE'}`, {defaultValue: value ?? 'UNAVAILABLE'});
    const percent = (value?: string | null) => value == null ? '—' : decimalDisplay(value, 4, 2) + '%';
    const available = evaluation.status === 'AVAILABLE' && valuation?.status === 'AVAILABLE' && comparison?.status === 'AVAILABLE';
    const rows = [
        {key: 'strategy', label: t('forwardValuation.strategy'), status: evaluation.status,
            finalEquity: evaluation.performance.finalEquity, returnRatio: evaluation.performance.returnRatio, excessReturn: null},
        {key: 'cash', label: t('forwardValuation.cash'), ...comparison?.cash},
        {key: 'buyHold', label: t('forwardValuation.buyHold'), ...comparison?.buyHold},
    ];
    return <Card size="small" title={<span style={{whiteSpace: 'normal'}}>{t('forwardValuation.title')}</span>} data-testid="forward-benchmark-comparison">
        <Space direction="vertical" style={{width: '100%', minWidth: 0}}>
            <Alert type="info" showIcon message={t('forwardValuation.meaning')} description={t('forwardValuation.costMeaning')}/>
            <Space wrap><Tag>{state(evaluation.status)}</Tag><Tag style={{whiteSpace: 'normal', overflowWrap: 'anywhere'}}>{closedBarObservationMethod}</Tag></Space>
            {!available && <Alert type="warning" showIcon message={t('forwardValuation.unavailable')}
                description={comparison?.reason ?? valuation?.reason ?? evaluation.reason ?? 'VALUATION_EVIDENCE_UNAVAILABLE'}/>}
            <Table size="small" pagination={false} rowKey="key" dataSource={rows} scroll={{x: 650}} columns={[
                {title: t('forwardValuation.comparison'), dataIndex: 'label'},
                {title: t('experiments.status'), dataIndex: 'status', render: value => state(value)},
                {title: t('forwardValuation.finalEquity'), dataIndex: 'finalEquity', render: value => <NqAmountText exact value={available ? value : null}/>},
                {title: t('common:accountPerformance.return'), dataIndex: 'returnRatio', render: value => percent(available ? value : null)},
                {title: t('forwardValuation.excessReturn'), dataIndex: 'excessReturn', render: value => percent(available ? value : null)},
            ]}/>
            <Typography.Paragraph type="secondary">{t('forwardValuation.sampleMeaning')}</Typography.Paragraph>
            <Descriptions size="small" layout="vertical" column={{xs: 1, md: 2}} styles={{content: {overflowWrap: 'anywhere'}}} items={[
                {key: 'symbol', label: t('forwardValuation.symbol'), children: comparison?.symbol ?? '—'},
                {key: 'baseline', label: t('forwardValuation.initialEquity'), children: <NqAmountText exact value={available ? comparison?.initialEquity : null}/>},
                {key: 'window', label: t('forwardObservation.requestedRange'), children: `${comparison?.from ?? evaluation.observationFrom} → ${comparison?.to ?? evaluation.observationTo}`},
                {key: 'cutoff', label: t('forwardObservation.asOf'), children: valuation?.knowledgeCutoff ?? evaluation.observedAt},
                {key: 'quality', label: t('forwardValuation.dataQuality'), children: state(valuation?.status)},
                {key: 'grid', label: t('forwardValuation.gridPoints'), children: valuation?.gridPointCount ?? '—'},
                {key: 'source', label: t('forwardValuation.source'), children: valuation?.source ?? '—'},
                {key: 'digest', label: t('forwardValuation.priceDigest'), children: valuation?.priceDigest ?? '—'},
                {key: 'boundary', label: t('forwardValuation.boundary'), children: t('forwardValuation.boundaryMeaning')},
                {key: 'freshness', label: t('forwardValuation.freshness'), children: t('forwardValuation.freshnessMeaning', {lateness: valuation?.maximumLatenessSeconds ?? '—', skew: valuation?.maximumClockOffsetSeconds ?? '—'})},
            ]}/>
            {valuation && <Collapse items={[{key: 'prices', label: t('forwardValuation.prices'), children: <Table size="small" rowKey="valuationBoundary"
                dataSource={valuation.prices} pagination={{pageSize: 10}} scroll={{x: 1000}} columns={[
                    {title: t('forwardValuation.valuationBoundary'), dataIndex: 'valuationBoundary'},
                    {title: t('forwardValuation.sourceCloseTime'), dataIndex: 'sourceCloseTime'},
                    {title: t('forwardValuation.availableAt'), dataIndex: 'availableAt'},
                    {title: t('forwardValuation.receivedAt'), dataIndex: 'receivedAt'},
                    {title: t('forwardValuation.price'), dataIndex: 'price', render: value => <NqAmountText exact value={value}/>},
                    {title: t('forwardValuation.barDigest'), dataIndex: 'barSha256'},
                ]}/>}]} />}
        </Space>
    </Card>;
}
