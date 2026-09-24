import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {Alert, Button, Card, Space, Tag, Typography} from 'antd';
import {useNavigate} from 'react-router-dom';

interface PaperTradingPlaceholderPageProps {
    title: string;
}

/**
 * PaperTradingPlaceholderPage 是 K5-B 的静态占位页。
 *
 * Why:
 * Portfolio / Diagnostics / Reviews 的真实业务面板仍保留在 Runs 兼容页，避免改变既有查询、缓存、
 * selectedRow / factTab / focusRunId 状态和 mutation 行为。placeholder 不调用任何 API/query，只提示 K5-C
 * 后续迁移路径，并重复展示 Paper-only 安全边界。
 */
export function PaperTradingPlaceholderPage({title}: PaperTradingPlaceholderPageProps) {
    useTranslation('pages');
    const navigate = useNavigate();

    return (
        <Card className="page-section" variant="borderless">
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <Space direction="vertical" size={4}>
                    <Typography.Title level={4} style={{margin: 0}}>
                        {title}
                    </Typography.Title>
                    <Typography.Paragraph type="secondary" style={{margin: 0}}>
                        {t('pages:thisModuleWillMoveHereInK5CItsFullViewRemainsAvailableOnTheCompatibleRunsPage')}</Typography.Paragraph>
                </Space>

                <Space size={6} wrap>
                    <Tag color="blue">{t('pages:simPaperOnly')}</Tag>
                    <Tag color="red">{t('pages:liveDisabled2')}</Tag>
                    <Tag color="default">{t('pages:noRealExchangeConnection')}</Tag>
                    <Tag color="default">{t('pages:notInvestmentAdvice')}</Tag>
                </Space>

                <Alert
                    type="info"
                    showIcon
                    message={t('pages:paperOnlyPlaceholder')}
                    description={t('pages:thisK5BRouteShellDoesNotReadCredentialsAccessRealExchangesOrIssueNewQueriesItIsNotInvestmentAdvice')}
                />

                <div>
                    <Button type="primary" onClick={() => navigate('/paper-trading/runs')}>
                        {t('pages:backToRuns')}</Button>
                </div>
            </Space>
        </Card>
    );
}
