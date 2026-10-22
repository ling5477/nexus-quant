import {Alert, Button, Card, Descriptions, Space, Spin, Typography} from 'antd';
import {useQuery} from '@tanstack/react-query';
import {Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {useAuthStore} from '@/store/auth-store';
import {NqPageHeader} from '@/components/nq';
import {demoRegistrationApi, demoRegistrationKeys} from './api';
import {registrationStatus} from './registration-state';
import {DemoContinuousControls} from './DemoContinuousControls';
import './i18n';
import './public-demo.css';

export function Component() {
    const {t} = useTranslation('demo');
    const userId = useAuthStore((state) => state.currentUser?.userId ?? 0);
    const result = useQuery({queryKey: demoRegistrationKeys.result(userId),
        queryFn: ({signal}) => demoRegistrationApi.result(signal), enabled: userId > 0,
        retry: false, staleTime: 0,
        refetchInterval: (query) => query.state.data?.status === 'INITIALIZING' ? 3000 : 30000});
    // 失败后不继续展示旧的成功状态，避免缓存掩盖账户状态未知。
    const status = result.isError ? 'UNKNOWN' : registrationStatus(result.data);
    const initialized = ['INITIALIZED', 'SIM_NOT_BOUND', 'SIM_RUNNING', 'SIM_STOPPED'].includes(status);
    const bound = initialized && Boolean(result.data?.boundPaperRunId);
    return <Space direction="vertical" size={20} className="nq-demo-account">
        <NqPageHeader title={t('accountTitle')} description={t('accountDescription')} />
        {result.isPending && <Spin aria-label={t('INITIALIZING')} />}
        {result.isError && <Alert type="error" showIcon message={t('requestFailed')} action={<Button onClick={() => void result.refetch()}>{t('retry')}</Button>} />}
        {!result.isPending && <>
            <Alert type={status === 'UNKNOWN' || status === 'INITIALIZATION_FAILED' ? 'error' : status === 'PENDING_EMAIL_VERIFICATION' ? 'warning' : 'info'}
                showIcon message={t(status)} action={<Button loading={result.isFetching} onClick={() => void result.refetch()}>{t('refresh')}</Button>} />
            {initialized && <Card variant="borderless" className="page-section">
                <Descriptions column={1}>
                    <Descriptions.Item label={t('initialCapital')}>10,000 USDT</Descriptions.Item>
                    <Descriptions.Item label={t('accountType')}>{t('independentAccount')}</Descriptions.Item>
                    <Descriptions.Item label={t('strategyRun')}>{t(bound ? status : 'SIM_NOT_BOUND')}</Descriptions.Item>
                    {bound && <Descriptions.Item label="PaperRunId"><Typography.Text copyable>{result.data?.boundPaperRunId}</Typography.Text></Descriptions.Item>}
                </Descriptions>
                <Typography.Paragraph>{t('bindingDescription')}</Typography.Paragraph>
                <Typography.Title level={4}>{t('next')}</Typography.Title>
                <Space wrap>
                    {bound ? <Link to={`/paper-trading/portfolio?paperRunId=${encodeURIComponent(result.data!.boundPaperRunId!)}&analysisSource=sim`}>{t('analyze')}</Link> : <>
                        <Link to="/strategies">{t('createStrategy')}</Link>
                        <Link to="/publishes">{t('publications')}</Link>
                    </>}
                </Space>
            </Card>}
            {bound && <Card variant="borderless" className="page-section"><DemoContinuousControls paperRunId={result.data!.boundPaperRunId!} /></Card>}
        </>}
    </Space>;
}
