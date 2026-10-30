import {useEffect, useRef, useState} from 'react';
import {useSearchParams, Link} from 'react-router-dom';
import {Alert, Button, Card, Collapse, DatePicker, Descriptions, Empty, Form, Input, Radio, Select, Space, Spin, Table, Tag, Typography} from 'antd';
import {useTranslation} from 'react-i18next';
import dayjs, {type Dayjs} from 'dayjs';
import utc from 'dayjs/plugin/utc';
import {useAuthStore} from '@/store/auth-store';
import {canWriteResearch, isPublicDemo} from '@/utils/distribution';
import {formatApiError} from '@/api/errors';
import type {AppApiError} from '@/types/api';
import {NqAmountText} from '@/components/nq/NqNumericText';
import {NqMetricCard} from '@/components/nq/NqMetricCard';
import {PerformanceStatisticsCards} from '@/features/accounts/components/PerformanceStatisticsCards';
import {decimalDisplay} from '@/utils/decimal-display';
import {confirmTimeWithoutSubmittingForm} from '@/utils/time-range';
import type {SimAnalysisRun} from '../api/analysis';
import {evaluationWindow, observationIdPattern, frozenSimCostRows, type ObservationDecision, type ObservationCreateRequest} from '../api/observations';
import {useForwardSimObservations} from '../hooks/useForwardSimObservations';

dayjs.extend(utc);
const decisions: ObservationDecision[] = ['CONTINUE_OBSERVATION', 'STOP_OBSERVATION', 'RETURN_TO_RESEARCH'];
const utcInput = (value: Dayjs) => dayjs.utc(value.format('YYYY-MM-DDTHH:mm:ss.SSS')).toISOString();
const sameTime = (left: string | null | undefined, right: string | null | undefined) => !!left && !!right && Date.parse(left) === Date.parse(right);
interface RangeDraft {from: Dayjs; to: Dayjs; asOf: Dayjs}

/** URL只拥有选择和固定观察截止；冻结评价与人工决定必须由显式按钮提交原操作身份。 */
export function ForwardSimObservationPanel({run}: {run: SimAnalysisRun}) {
    const {t} = useTranslation('pages');
    const [params, setParams] = useSearchParams();
    const roles = useAuthStore(state => state.currentUser?.roles);
    const writable = canWriteResearch(roles) && !isPublicDemo;
    const [form] = Form.useForm<RangeDraft>();
    const [decisionForm] = Form.useForm<{decision: ObservationDecision; reason: string}>();
    const [offset, setOffset] = useState(0);
    const [reviewsOffset, setReviewsOffset] = useState(0);
    const [kind, setKind] = useState<ObservationCreateRequest['kind']>('RETROSPECTIVE');
    const [localError, setLocalError] = useState<string | null>(null);
    const draftState = useRef({context: '', edited: false});
    const from = params.get('simFrom'), to = params.get('simTo'), asOf = params.get('simAsOf');
    const range = evaluationWindow(from, to, asOf);
    const requestedId = params.get('observationId'), requestedReview = params.get('reviewId');
    const invalidIdentity = (requestedId !== null && !observationIdPattern.test(requestedId))
        || (requestedReview !== null && (!requestedId || !observationIdPattern.test(requestedReview)));
    const observationId = requestedId && observationIdPattern.test(requestedId) ? requestedId : null;
    const reviewId = !invalidIdentity && requestedReview ? requestedReview : null;
    const invalidRange = [from, to, asOf].some(value => value !== null) && !range;
    const onSaved = (id: string, review?: string) => {setReviewsOffset(0); setParams(previous => {
        const next = new URLSearchParams(previous); next.set('observationId', id);
        if (review) next.set('reviewId', review); else next.delete('reviewId');
        return next;
    });};
    const query = useForwardSimObservations(run, invalidRange ? null : range, observationId, reviewId, offset, reviewsOffset, onSaved);
    const detail = !query.detail.isError ? query.detail.data : undefined;
    const savedReview = !query.review.isError && !invalidIdentity ? query.review.data : undefined;
    const observation = detail?.observation;
    const evaluation = invalidIdentity || invalidRange ? undefined : reviewId ? savedReview?.evaluation : !query.evaluation.isError ? query.evaluation.data : undefined;
    const performance = evaluation?.performance ?? (!reviewId && !invalidRange && !invalidIdentity && !range && !query.rawPerformance.isError
        && query.rawPerformance.data?.paperRunId === run.paperRunId ? query.rawPerformance.data : undefined);
    const method = evaluation?.methodDetails;
    const validationId = observation?.validationId ?? params.get('validationId'), trialId = observation?.selectedTrialId ?? params.get('selectedTrialId');
    const sourceExperiment = observation?.policy?.sourceExperimentId;
    const experimentId = typeof sourceExperiment === 'string' && observationIdPattern.test(sourceExperiment) ? sourceExperiment : params.get('experimentId');
    const contextValid = !!validationId && observationIdPattern.test(validationId) && !!trialId && observationIdPattern.test(trialId)
        && (!params.get('strategyVersionId') || params.get('strategyVersionId') === run.strategyVersionId)
        && (!params.get('publishId') || params.get('publishId') === run.publishId);
    const blockWrite = !writable || query.storageError || !!query.pending || query.mutation.isPending;
    const rawFrom = query.rawPerformance.data?.coverageStart, rawTo = query.rawPerformance.data?.coverageEnd;
    useEffect(() => {
        const context = JSON.stringify([from, to, asOf, observation?.observationId]);
        if (draftState.current.context !== context) draftState.current = {context, edited: false};
        if (draftState.current.edited) return;
        form.setFieldsValue({from: dayjs.utc(from ?? observation?.observationFrom ?? rawFrom ?? run.createdAt),
            to: dayjs.utc(to ?? observation?.observationTo ?? rawTo ?? undefined), asOf: dayjs.utc(asOf ?? undefined)});
    }, [form, from, to, asOf, observation?.observationFrom, observation?.observationTo, rawFrom, rawTo, run.createdAt]);
    const value = (unknown: string | number | null | undefined) => unknown == null ? '—' : String(unknown);
    const state = (status: string) => t(`forwardObservation.states.${status}`, {defaultValue: status});
    const percent = (amount: string | null) => amount === null ? '—' : decimalDisplay(amount, 4, 2) + '%';
    const visibleError = localError ?? (invalidIdentity ? t('forwardObservation.invalidIdentity') : invalidRange ? t('forwardObservation.invalidRange') : null);
    const errors = [query.list.error, query.detail.error, query.review.error, query.mutation.error,
        ...(range ? [query.evaluation.error] : [query.rawPerformance.error])].filter(Boolean);
    const applyRange = (draft: RangeDraft) => {
        const nextRange = evaluationWindow(utcInput(draft.from), utcInput(draft.to), utcInput(draft.asOf));
        if (!nextRange) {setLocalError(t('forwardObservation.invalidRange')); return;}
        setLocalError(null);
        setParams(previous => {const next = new URLSearchParams(previous); next.set('simFrom', nextRange.from);
            next.set('simTo', nextRange.to); next.set('simAsOf', nextRange.asOf); next.delete('reviewId'); return next;});
    };
    const createObservation = async () => {
        try {
            const draft = await form.validateFields();
            const start = utcInput(draft.from), end = utcInput(draft.to), duration = Date.parse(end) - Date.parse(start);
            if (!contextValid || Date.parse(start) % 3_600_000 !== 0 || Date.parse(end) % 3_600_000 !== 0
                || duration < 86_400_000 || duration > 90 * 86_400_000) throw new Error(t('forwardObservation.planRange'));
            if (kind === 'RETROSPECTIVE' && Date.parse(end) > Date.now()) throw new Error(t('forwardObservation.retroPastOnly'));
            setLocalError(null);
            query.mutation.mutate({kind: 'OBSERVATION', input: {paperRunId: run.paperRunId, canonicalAccountId: run.canonicalAccountId,
                strategyVersionId: run.strategyVersionId, publishId: run.publishId, validationId: validationId!, selectedTrialId: trialId!,
                kind, observationFrom: start, observationTo: end,
                ...(observation ? {supersedesObservationId: observation.observationId} : {})}});
        } catch (error) {if (error instanceof Error) setLocalError(error.message);}
    };
    const reviewRangeMatches = !!observation && !!evaluation && sameTime(observation.observationFrom, evaluation.observationFrom)
        && sameTime(observation.observationTo, evaluation.observationTo);
    const selectObservation = (id?: string) => {setReviewsOffset(0); setParams(previous => {
        const next = new URLSearchParams(previous); if (id) next.set('observationId', id); else next.delete('observationId');
        next.delete('reviewId'); return next;
    });};
    return <Card title={t('forwardObservation.title')} data-testid="forward-sim-observation">
        <Space direction="vertical" size={16} style={{width: '100%', minWidth: 0}}>
            <Alert type="info" showIcon message={t('forwardObservation.boundary')} description={t('forwardObservation.rangeMeaning')}/>
            {visibleError && <Alert type="error" showIcon message={visibleError}/>}
            {errors.map((error, index) => <Alert key={index} type="error" showIcon message={formatApiError(error as AppApiError)}/>)}
            {query.storageError && <Alert type="error" message={t('forwardObservation.storageError')}/>}
            {query.pending && <Alert type="warning" message={t('forwardObservation.pending')} description={query.pending.operationId} action={<Space wrap>
                <Button disabled={query.mutation.isPending} onClick={() => query.mutation.mutate({kind: 'QUERY', original: query.pending!})}>{t('forwardObservation.queryOriginal')}</Button>
                <Button disabled={!writable || query.mutation.isPending} onClick={() => query.mutation.mutate({kind: 'RESEND', original: query.pending!})}>{t('forwardObservation.resendOriginal')}</Button>
                {query.pending.rejected && <Button onClick={query.discardRejected}>{t('forwardObservation.discardRejected')}</Button>}
            </Space>}/>}
            <Form form={form} layout="vertical" onFinish={applyRange} onValuesChange={() => {draftState.current.edited = true;}} data-testid="forward-observation-range">
                <Space wrap align="start">
                    {(['from', 'to', 'asOf'] as const).map(field => <Form.Item key={field} name={field} label={t(`forwardObservation.${field}`)} rules={[{required: true}]}>
                        <DatePicker showTime={{format: 'HH:mm:ss'}} format="YYYY-MM-DD HH:mm:ss" allowClear={false} onKeyDown={confirmTimeWithoutSubmittingForm}/>
                    </Form.Item>)}
                </Space>
                <Space wrap><Button type="primary" htmlType="submit" loading={query.evaluation.isFetching}>{t('forwardObservation.evaluate')}</Button>
                    <Button onClick={() => {draftState.current.edited = true; form.setFieldValue('asOf', dayjs.utc());}}>{t('forwardObservation.now')}</Button>
                    <Button onClick={() => {setLocalError(null); draftState.current.edited = false; form.resetFields();
                        form.setFieldsValue({from: dayjs.utc(observation?.observationFrom ?? rawFrom ?? run.createdAt),
                            to: dayjs.utc(observation?.observationTo ?? rawTo ?? undefined), asOf: dayjs.utc()});
                        setParams(previous => {const next = new URLSearchParams(previous);
                        for (const key of ['simFrom', 'simTo', 'simAsOf', 'reviewId']) next.delete(key); return next;});}}>{t('forwardObservation.allEvents')}</Button>
                    {range && <Button onClick={() => {if (reviewId) void query.review.refetch(); else void query.evaluation.refetch();}}>{t('forwardObservation.refreshFixed')}</Button>}
                </Space>
            </Form>
            {(query.evaluation.isFetching || query.rawPerformance.isPending || query.review.isFetching) && <Spin/>}
            {reviewId && <Alert type="info" message={t('forwardObservation.frozenView')}/>}
            {evaluation && <>
                <Space wrap><Tag>{state(evaluation.status)}</Tag><Tag>{evaluation.timeBasis}</Tag>{evaluation.reason && <Typography.Text>{evaluation.reason}</Typography.Text>}</Space>
                <Descriptions size="small" column={{xs: 1, md: 2}} items={[
                    {key: 'requested', label: t('forwardObservation.requestedRange'), children: `${evaluation.observationFrom} → ${evaluation.observationTo}`},
                    {key: 'due', label: t('forwardObservation.dueRange'), children: `${value(evaluation.dueFrom)} → ${value(evaluation.dueTo)}`},
                    {key: 'coverage', label: t('forwardObservation.coverage'), children: `${value(evaluation.coverageStart)} → ${value(evaluation.coverageEnd)}`},
                    {key: 'asOf', label: t('forwardObservation.asOf'), children: evaluation.observedAt},
                ]}/>
                <div className="sim-analysis-metrics">{(['expected', 'observed', 'missing', 'unknown'] as const).map(field =>
                    <NqMetricCard key={field} label={t(`forwardObservation.${field}`)} value={evaluation.timeBasis === 'ACTUAL_EVENT_TIME' ? value(evaluation.samples[field]) : '—'} footer={state(evaluation.samples.status)}/>)}</div>
                {(evaluation.samples.windowLimited || evaluation.metrics.windowLimited) && <Alert type="warning" message={t('forwardObservation.windowLimited')}/>}
                {evaluation.samples.gapRanges.length > 0 && <Table size="small" pagination={{pageSize: 10}} rowKey={row => `${row.from}/${row.to}`}
                    dataSource={evaluation.samples.gapRanges} columns={[{title: t('forwardObservation.from'), dataIndex: 'from'}, {title: t('forwardObservation.to'), dataIndex: 'to'}]} scroll={{x: 560}}/>}
                <Table size="small" pagination={{pageSize: 10}} rowKey={(row, index) => `${row.code}/${index}`} dataSource={evaluation.findings} scroll={{x: 850}} columns={[
                    {title: t('forwardObservation.finding'), dataIndex: 'code', render: code => state(code)},
                    {title: t('experiments.status'), dataIndex: 'status', render: status => state(status)},
                    {title: t('forwardObservation.reason'), dataIndex: 'reason'}, {title: t('forwardObservation.from'), dataIndex: 'from'},
                    {title: t('forwardObservation.to'), dataIndex: 'to'}, {title: t('forwardObservation.count'), dataIndex: 'count', render: value},
                ]}/>
            </>}
            {performance && <>
                {!evaluation && <Typography.Text type="secondary">{t('forwardObservation.originalPerformance')} · {performance.timeBasis}</Typography.Text>}
                {performance.timeBasis === 'REPLAY_EVENT_TIME' && <Alert type="warning" message={t('forwardObservation.replay')}/>}
                <div className="sim-analysis-metrics">
                    <NqMetricCard label={t('common:accountPerformance.pnl')} value={<NqAmountText exact value={performance.pnl}/>} footer={performance.quoteCurrency}/>
                    <NqMetricCard label={t('common:accountPerformance.return')} value={percent(performance.returnRatio)}/>
                    <NqMetricCard label={t('common:accountPerformance.drawdown')} value={<NqAmountText exact value={performance.maxDrawdown}/>} footer={performance.quoteCurrency}/>
                    <NqMetricCard label={t('common:accountPerformance.drawdownRatio')} value={percent(performance.maxDrawdownRatio)}/>
                </div>
                <PerformanceStatisticsCards sharpe={performance.sharpe} outcomes={performance.outcomes}/>
                {performance.tradesAfterCoverage > 0 && <Alert type="warning" message={t('common:performanceStatistics.afterCoverage', {count: performance.tradesAfterCoverage})}/>}
            </>}
            {evaluation && <>
                <div className="sim-analysis-metrics">{([
                    ['tradeCount', evaluation.metrics.tradeCount, evaluation.metrics.tradeCountStatus],
                    ['tradeFrequency', evaluation.metrics.tradeFrequencyPerDay, evaluation.metrics.tradeFrequencyStatus],
                    ['turnover', evaluation.metrics.twoWayTurnover, evaluation.metrics.turnoverStatus],
                    ['riskCount', evaluation.metrics.riskEventCount, evaluation.metrics.riskStatus],
                    ['executionCount', evaluation.metrics.executionAnomalyCount, evaluation.metrics.executionStatus],
                ] as const).map(([key, amount, status]) => <NqMetricCard key={key} label={t(`forwardObservation.${key}`)} value={value(amount)} footer={state(status)}/>)}</div>
                <Typography.Paragraph type="secondary">{t('forwardObservation.frequencyMeaning')}</Typography.Paragraph>
                <Collapse items={[{key: 'method', label: t('forwardObservation.methodDetails'), children: <Space direction="vertical" style={{width: '100%'}}>
                    <Typography.Paragraph type="secondary">{t('forwardObservation.methodMeaning')}</Typography.Paragraph>
                    <Descriptions size="small" column={1} items={[
                        {key: 'elapsed', label: t('forwardObservation.methodElapsed'), children: `${value(method?.frequencyElapsedSeconds)} · ${state(method?.frequencyStatus ?? 'UNAVAILABLE')}`},
                        {key: 'days', label: t('forwardObservation.methodNaturalDays'), children: value(method?.frequencyNaturalDays)},
                        {key: 'completeDays', label: t('forwardObservation.methodCompleteDays'), children: `${value(method?.completeObservationDays)} · ${state(method?.completeObservationDaysStatus ?? 'UNAVAILABLE')}`},
                        {key: 'equityCount', label: t('forwardObservation.methodEquityCount'), children: value(method?.turnoverEquitySampleCount)},
                        {key: 'meanEquity', label: t('forwardObservation.methodMeanEquity'), children: `${value(method?.turnoverMeanHourlyEquity)} · ${state(method?.turnoverStatus ?? 'UNAVAILABLE')}`},
                        {key: 'turnover', label: t('forwardObservation.methodTurnover'), children: method?.turnoverDefinition === 'ABSOLUTE_TRADED_NOTIONAL_DIVIDED_BY_MEAN_SIGNAL_CLOSE_EQUITY'
                            ? t('forwardObservation.methodTurnoverFormula') : value(method?.turnoverDefinition)},
                        {key: 'rawCount', label: t('forwardObservation.methodRawCount'), children: `${value(method?.rawValuationSampleCount)} · ${state(method?.valuationSamplesStatus ?? 'UNAVAILABLE')}`},
                        {key: 'displayedCount', label: t('forwardObservation.methodDisplayedCount'), children: value(method?.displayedSampleCount)},
                        {key: 'downsampled', label: t('forwardObservation.methodDownsampled'), children: method?.chartDownsampled == null ? '—'
                            : t(method.chartDownsampled ? 'forwardObservation.yes' : 'forwardObservation.no')},
                    ]}/>
                </Space>}]} />
                <Space wrap>{evaluation.metrics.orderStatuses.map(row => <Tag key={row.status}>{row.status} · {row.count}</Tag>)}<Tag>{state(evaluation.metrics.orderStatusStatus)}</Tag></Space>
                <Table size="small" rowKey={row => row.currency ?? 'UNKNOWN'} pagination={false} dataSource={evaluation.metrics.fees} locale={{emptyText: t('forwardObservation.feeUnavailable')}} columns={[
                    {title: t('simColumn_fee_currency'), dataIndex: 'currency', render: value},
                    {title: t('forwardObservation.recordedFee'), dataIndex: 'totalFee', render: amount => <NqAmountText exact value={amount}/>},
                    {title: t('forwardObservation.costCoverage'), render: (_, row) => `${row.measuredFeeCount}/${row.tradeCount}`},
                ]}/>
                <Typography.Text type="secondary">{t('forwardObservation.feeMeaning')} · {evaluation.metrics.feeSource} · {state(evaluation.metrics.feeStatus)}</Typography.Text>
                <Collapse items={[{key: 'simCosts', label: t('forwardObservation.frozenSimCosts'), children: <Space direction="vertical" style={{width: '100%'}}>
                    <Typography.Paragraph type="secondary">{t('forwardObservation.frozenSimCostsMeaning')}</Typography.Paragraph>
                    <Table size="small" rowKey="field" pagination={false} scroll={{x: 600}} dataSource={frozenSimCostRows(evaluation.costAssumptions)} columns={[
                        {title: t('forwardObservation.costField'), dataIndex: 'field', render: field => t(`forwardObservation.costFields.${field}`)},
                        {title: t('forwardObservation.frozenCostValue'), dataIndex: 'value', render: amount => <Typography.Text style={{overflowWrap: 'anywhere'}}>{value(amount)}</Typography.Text>},
                        {title: t('experiments.status'), dataIndex: 'status', render: status => state(status)},
                    ]}/>
                </Space>}]} />
                <Collapse items={[{key: 'identity', label: t('forwardObservation.provenance'), children: <Descriptions column={1} size="small" styles={{content: {overflowWrap: 'anywhere'}}}
                    items={(['paperRunId', 'canonicalAccountId', 'strategyVersionId', 'publishId', 'timeBasis', 'methodVersion', 'watermark', 'checksum'] as const).map(field =>
                        ({key: field, label: field, children: String(evaluation[field])}))}/>}]} />
            </>}
            <Typography.Title level={5}>{t('forwardObservation.records')}</Typography.Title>
            <Select aria-label={t('forwardObservation.records')} allowClear style={{width: '100%'}} value={observationId ?? undefined}
                onChange={selectObservation} options={query.list.data?.map(record => ({value: record.observationId, label: `${t(`forwardObservation.kinds.${record.kind}`)} · ${state(record.status)} · ${record.observationFrom} → ${record.observationTo} · ${record.observationId}`}))}/>
            <Space wrap><Button disabled={!offset} onClick={() => setOffset(Math.max(0, offset - 20))}>{t('experiments.previous')}</Button>
                <Button disabled={(query.list.data?.length ?? 0) < 20} onClick={() => setOffset(offset + 20)}>{t('experiments.next')}</Button></Space>
            {!query.list.isPending && query.list.data?.length === 0 && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('forwardObservation.noRecords')}/>}
            <Collapse items={[{key: 'create', label: t('forwardObservation.create'), children: <Space direction="vertical" style={{width: '100%'}}>
                <Radio.Group value={kind} onChange={event => setKind(event.target.value)} options={['RETROSPECTIVE', 'FORWARD'].map(choice => ({value: choice, label: t(`forwardObservation.kinds.${choice}`)}))}/>
                <Typography.Paragraph>{t(kind === 'FORWARD' ? 'forwardObservation.forwardPlan' : 'forwardObservation.retroPlan')}</Typography.Paragraph>
                {!contextValid && <Alert type="info" message={t('forwardObservation.researchRequired')}/>}
                <Button disabled={blockWrite || !contextValid || (evaluation?.timeBasis ?? performance?.timeBasis) !== 'ACTUAL_EVENT_TIME'} onClick={() => void createObservation()}>{t('forwardObservation.create')}</Button>
            </Space>}]}/>
            {observation && <>
                <Descriptions size="small" column={1} items={[
                    {key: 'id', label: t('forwardObservation.records'), children: observation.observationId},
                    {key: 'revision', label: t('forwardObservation.revision'), children: `${observation.revision} · ${observation.status} · ${observation.kind}`},
                    {key: 'range', label: t('forwardObservation.requestedRange'), children: `${observation.observationFrom} → ${observation.observationTo}`},
                    {key: 'registration', label: t('forwardObservation.registeredAt'), children: observation.registeredAt},
                ]}/>
                <Button onClick={() => applyRange({from: dayjs.utc(observation.observationFrom), to: dayjs.utc(observation.observationTo), asOf: dayjs.utc()})}>{t('forwardObservation.evaluateRecord')}</Button>
                <Alert type="warning" message={t('forwardObservation.decisionBoundary')}/>
                <Form form={decisionForm} layout="vertical" initialValues={{decision: 'CONTINUE_OBSERVATION'}} onFinish={draft => {
                    if (!evaluation || !reviewRangeMatches) return;
                    query.mutation.mutate({kind: 'OBSERVATION_REVIEW', observationId: observation.observationId,
                        input: {expectedRevision: observation.revision, decision: draft.decision, reason: draft.reason.trim(),
                            observedAt: evaluation.observedAt, expectedEvaluationChecksum: evaluation.checksum}});
                }}>
                    <Form.Item name="decision" label={t('forwardObservation.decision')} rules={[{required: true}]}>
                        <Select options={decisions.map(decision => ({value: decision, label: t(`forwardObservation.decisions.${decision}`)}))}/>
                    </Form.Item>
                    <Form.Item name="reason" label={t('forwardObservation.reason')} rules={[{required: true, whitespace: true}, {max: 2000}]}><Input.TextArea rows={3} maxLength={2000}/></Form.Item>
                    <Button type="primary" htmlType="submit" loading={query.mutation.isPending} disabled={blockWrite || observation.status !== 'OPEN' || !evaluation || !!reviewId || !reviewRangeMatches}>{t('forwardObservation.saveReview')}</Button>
                </Form>
                {!reviewRangeMatches && <Typography.Text type="secondary">{t('forwardObservation.sameWindowRequired')}</Typography.Text>}
                <Table size="small" rowKey="reviewId" dataSource={detail?.reviews ?? []} pagination={{pageSize: 10}} scroll={{x: 850}} columns={[
                    {title: t('forwardObservation.decision'), dataIndex: 'decision', render: decision => t(`forwardObservation.decisions.${decision}`, {defaultValue: decision})},
                    {title: t('forwardObservation.reason'), dataIndex: 'reason'}, {title: t('forwardObservation.reviewedAt'), dataIndex: 'reviewedAt'},
                    {title: t('forwardObservation.reviewer'), dataIndex: 'reviewerUserId'},
                    {title: t('experiments.details'), render: (_, review) => <Button type="link" onClick={() => onSaved(observation.observationId, review.reviewId)}>{t('forwardObservation.viewFrozen')}</Button>},
                ]}/>
                {detail?.hasMoreReviews && <Typography.Text type="warning">{t('forwardObservation.moreReviews')}</Typography.Text>}
                <Space wrap><Button disabled={!reviewsOffset} onClick={() => setReviewsOffset(Math.max(0, reviewsOffset - 20))}>{t('experiments.previous')}</Button>
                    <Button disabled={!detail?.hasMoreReviews || reviewsOffset >= 9980} onClick={() => setReviewsOffset(reviewsOffset + 20)}>{t('experiments.next')}</Button></Space>
            </>}
            <Space wrap>
                <Link to={`/paper-trading/runs?paperRunId=${encodeURIComponent(run.paperRunId)}`}>{t('simAnalysis.manageRun')}</Link>
                <Link to={`/publishes?publishId=${encodeURIComponent(run.publishId)}`}>{t('simAnalysis.publishedSource')}</Link>
                {run.strategyCode && <Link to={`/strategies?strategyCode=${encodeURIComponent(run.strategyCode)}&versionId=${encodeURIComponent(run.strategyVersionId)}`}>{t('strategyVersion')}</Link>}
                {run.backtestConfigId && <Link to={`/backtests/${encodeURIComponent(run.backtestConfigId)}${run.backtestRunId ? `?backtestRunId=${encodeURIComponent(run.backtestRunId)}` : ''}`}>{t('simAnalysis.frozenBacktest')}</Link>}
                {experimentId && <Link to={`/experiments?experimentId=${encodeURIComponent(experimentId)}${validationId ? `&validationId=${encodeURIComponent(validationId)}` : ''}`}>{t('forwardObservation.backToResearch')}</Link>}
            </Space>
        </Space>
    </Card>;
}
