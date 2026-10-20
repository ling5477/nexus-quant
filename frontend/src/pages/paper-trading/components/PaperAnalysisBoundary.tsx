import {Alert, Button, Card, Form, Input, Space, Tabs, Typography} from 'antd';
import {useTranslation} from 'react-i18next';
import {Link, useNavigate, useSearchParams} from 'react-router-dom';

export type PaperAnalysisKind = 'portfolio' | 'diagnostics' | 'reviews';

export function paperRunFactsPath(paperRunId: string): string {
    return `/paper-trading/runs?paperRunId=${encodeURIComponent(paperRunId.trim())}`;
}

/** 旧聚合缺少可核验的来源全集，保留能力说明并转到唯一的运行事实入口。 */
export function PaperAnalysisBoundary({kind}: {kind: PaperAnalysisKind}) {
    const {t} = useTranslation('pages');
    const navigate = useNavigate();
    const [params] = useSearchParams();
    const initialRunId = params.get('paperRunId') ?? '';
    const titleKey = {portfolio: 'paperPortfolioDashboard', diagnostics: 'paperExecutionDiagnostics', reviews: 'strategyEvaluationDashboard'}[kind];

    return <Card className="page-section" variant="borderless" title={t(titleKey)} data-testid={`paper-analysis-boundary-${kind}`}>
        <Space direction="vertical" size={16} style={{display: 'flex', minWidth: 0}}>
            <Alert type="warning" showIcon message={t('pages:paperAnalysisBoundaryTitle')}
                description={t('pages:paperAnalysisBoundaryReason')}/>
            <Tabs items={[
                {key: 'facts', label: t('pages:paperAnalysisFactsTab'), children: <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <Typography.Paragraph style={{marginBottom: 0}}>{t('pages:paperAnalysisFactsInstructions')}</Typography.Paragraph>
                    <Form key={`${kind}:${initialRunId}`} name={`paper-analysis-${kind}`} layout="vertical"
                        initialValues={{paperRunId: initialRunId}}
                        onFinish={({paperRunId}: {paperRunId: string}) => navigate(paperRunFactsPath(paperRunId))}>
                        <Form.Item name="paperRunId" label={t('pages:paperRunId')}
                            rules={[{required: true, whitespace: true, message: t('pages:paperAnalysisRunRequired')},
                                {max: 256, message: t('pages:paperAnalysisRunTooLong')}]}>
                            <Input autoComplete="off" maxLength={256} placeholder={t('pages:paperAnalysisRunPlaceholder')}/>
                        </Form.Item>
                        <Space wrap>
                            <Button type="primary" htmlType="submit">{t('pages:paperAnalysisOpenFacts')}</Button>
                            <Link to="/paper-trading/runs">{t('pages:paperAnalysisFindRun')}</Link>
                        </Space>
                    </Form>
                    <Typography.Text type="secondary">{t('pages:paperAnalysisNoWrite')}</Typography.Text>
                </Space>},
                {key: 'history', label: t('pages:paperAnalysisHistoryTab'), children: <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <Typography.Paragraph style={{marginBottom: 0}}>{t(`paperAnalysisHistory_${kind}`)}</Typography.Paragraph>
                    <Alert type="info" showIcon message={t('pages:paperAnalysisHistoryUnavailable')}
                        description={t('pages:paperAnalysisHistoryReason')}/>
                    <Typography.Text type="secondary">{t('pages:paperAnalysisUnknownNotZero')}</Typography.Text>
                </Space>},
            ]}/>
        </Space>
    </Card>;
}
