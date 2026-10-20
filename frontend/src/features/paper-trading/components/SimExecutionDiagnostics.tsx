import {Alert, Button, List, Space, Tag, Typography} from 'antd';
import {useTranslation} from 'react-i18next';
import {Link} from 'react-router-dom';
import {NqMetricCard} from '@/components/nq/NqMetricCard';
import type {SimAnalysis} from '../api/analysis';
import {simExecutionDiagnosticView, type SimDiagnosticTarget} from '../execution-diagnostics';

/** 已知决策代码按业务含义翻译；原文保留于可访问名称，未知代码仍明示原文，避免伪装成正常。 */
export function SimExecutionCode({kind, value}: {kind: 'status' | 'reason'; value: string | null}) {
    const {t, i18n} = useTranslation('pages');
    if (!value) return <>—</>;
    const key = `simDiagnostics.codes.${kind}.${value}`;
    const label = i18n.exists(key, {ns: 'pages'})
        ? t(key)
        : `${t('pages:simDiagnostics.unknownCode')} · ${value}`;
    return (
        <Typography.Text title={value} aria-label={`${label} (${value})`}>
            {label}
        </Typography.Text>
    );
}

/** 诊断先给出已确认的执行事实，再进入同一实例明细；本区域不下单、不恢复或推进运行。 */
export function SimExecutionDiagnostics({
    analysis,
    onInspect,
}: {
    analysis: SimAnalysis;
    onInspect: (target: Exclude<SimDiagnosticTarget, 'run'>) => void;
}) {
    const {t} = useTranslation('pages');
    const view = simExecutionDiagnosticView(analysis);
    const runPath = `/paper-trading/runs?paperRunId=${encodeURIComponent(analysis.run.paperRunId)}`;
    return (
        <Space
            direction="vertical"
            size={12}
            style={{display: 'flex', minWidth: 0}}
            data-testid="sim-execution-diagnostics"
        >
            <div className="sim-analysis-metrics">
                {(['decisions', 'orders', 'trades', 'riskEvents'] as const).map((key) => (
                    <NqMetricCard
                        key={key}
                        label={t(`pages:simAnalysis.${key}`)}
                        value={view.counts?.[key] ?? '—'}
                    />
                ))}
            </div>
            <Typography.Text type="secondary">
                {t('pages:simDiagnostics.evidenceMeaning')}
            </Typography.Text>
            <List
                size="small"
                bordered
                dataSource={view.findings}
                renderItem={(finding) => {
                    const target = finding.target;
                    return (
                        <List.Item key={finding.key} style={{display: 'block'}}>
                            <Space wrap style={{width: '100%', justifyContent: 'space-between'}}>
                                <Space wrap>
                                    <Tag
                                        color={
                                            finding.tone === 'error'
                                                ? 'red'
                                                : finding.tone === 'warning'
                                                  ? 'orange'
                                                  : 'blue'
                                        }
                                    >
                                        {t(`pages:simDiagnostics.findings.${finding.key}.title`)}
                                    </Tag>
                                    {finding.count !== undefined && (
                                        <Typography.Text>{finding.count}</Typography.Text>
                                    )}
                                </Space>
                                {target === 'run' ? (
                                    <Link to={runPath}>{t('pages:simAnalysis.manageRun')}</Link>
                                ) : (
                                    <Button
                                        type="link"
                                        size="small"
                                        onClick={() => onInspect(target)}
                                    >
                                        {t(`pages:simDiagnostics.inspect_${target}`)}
                                    </Button>
                                )}
                            </Space>
                            <Typography.Paragraph type="secondary" style={{margin: '6px 0 0'}}>
                                {t(`pages:simDiagnostics.findings.${finding.key}.detail`)}
                            </Typography.Paragraph>
                        </List.Item>
                    );
                }}
            />
            <Alert type="info" showIcon message={t('pages:simDiagnostics.analysisBoundary')} />
        </Space>
    );
}
