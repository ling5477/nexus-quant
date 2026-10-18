import {Alert, Button, Card, Popover, Select, Space, Typography} from 'antd';
import {useTranslation} from 'react-i18next';
import {useSearchParams} from 'react-router-dom';
import {SimAnalysisWorkspace} from './SimAnalysisWorkspace';
import {LegacyPaperAnalysisWorkspace} from './LegacyPaperAnalysisWorkspace';
import {updateLegacyParams} from '@/features/paper-trading/legacy-analysis';

/** 来源显式决定业务对象和统计口径；无效来源必须解释，不静默切成另一组资产或运行。 */
export function PaperAnalysisWorkspace({kind}: {kind: 'diagnostics' | 'reviews' | 'portfolio'}) {
    const {t} = useTranslation('pages');
    const [params, setParams] = useSearchParams();
    const source = params.get('analysisSource') ?? 'sim';
    const valid = source === 'sim' || source === 'legacy';
    return (
        <Space direction="vertical" size={12} style={{width: '100%', minWidth: 0}}>
            <Card className="page-section" variant="borderless">
                <Space wrap>
                    <Typography.Text strong>{t('pages:legacyAnalysis.source')}</Typography.Text>
                    <Select
                        aria-label={t('pages:legacyAnalysis.source')}
                        value={valid ? source : undefined}
                        style={{minWidth: 220}}
                        onChange={(value: string) =>
                            setParams(
                                updateLegacyParams(params, {
                                    analysisSource: value,
                                    legacyVersion: null,
                                }),
                            )
                        }
                        options={[
                            {value: 'sim', label: t('pages:legacyAnalysis.sim')},
                            {value: 'legacy', label: t('pages:legacyAnalysis.legacy')},
                        ]}
                    />
                    <Popover
                        trigger="click"
                        title={t('pages:legacyAnalysis.sourceMeaning')}
                        content={
                            <Typography.Paragraph style={{maxWidth: 320, margin: 0}}>
                                {t(
                                    `pages:legacyAnalysis.${source === 'legacy' ? 'legacyScope' : 'simScope'}`,
                                )}
                            </Typography.Paragraph>
                        }
                    >
                        <Button type="text" size="small">
                            {t('pages:legacyAnalysis.sourceMeaning')}
                        </Button>
                    </Popover>
                </Space>
            </Card>
            {!valid ? (
                <Alert
                    type="error"
                    showIcon
                    message={t('pages:legacyAnalysis.invalidSource')}
                    action={
                        <Button
                            onClick={() =>
                                setParams(updateLegacyParams(params, {analysisSource: 'sim'}))
                            }
                        >
                            {t('pages:legacyAnalysis.resetSource')}
                        </Button>
                    }
                />
            ) : source === 'legacy' ? (
                <LegacyPaperAnalysisWorkspace kind={kind} />
            ) : (
                <SimAnalysisWorkspace kind={kind} />
            )}
        </Space>
    );
}
