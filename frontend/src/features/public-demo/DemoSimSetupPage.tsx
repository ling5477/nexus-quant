import {Alert, Button, Space, Spin} from 'antd';
import {useQuery} from '@tanstack/react-query';
import {Navigate, useNavigate} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {useAuthStore} from '@/store/auth-store';
import {StrategySimPanel} from '@/features/paper-trading/components/StrategySimPanel';
import {demoRegistrationApi, demoRegistrationKeys} from './api';
import {registrationStatus} from './registration-state';
import './i18n';

/** Demo 首次准入只复用 Continuous 操作，不装配旧 replay 创建及非 canonical 运行列表。 */
export function Component() {
    const {t} = useTranslation('demo');
    const userId = useAuthStore((state) => state.currentUser?.userId ?? 0);
    const navigate = useNavigate();
    const result = useQuery({queryKey: demoRegistrationKeys.result(userId),
        queryFn: ({signal}) => demoRegistrationApi.result(signal), enabled: userId > 0,
        retry: false, staleTime: 0});
    if (result.isPending) return <Spin aria-label={t('INITIALIZING')} />;
    const status = result.isError ? 'UNKNOWN' : registrationStatus(result.data);
    const analysisPath = (id: string) => `/paper-trading/portfolio?paperRunId=${encodeURIComponent(id)}&analysisSource=sim`;
    if (['SIM_RUNNING', 'SIM_STOPPED'].includes(status) && result.data?.boundPaperRunId)
        return <Navigate to={analysisPath(result.data.boundPaperRunId)} replace />;
    if (!['INITIALIZED', 'SIM_NOT_BOUND'].includes(status)) return <Alert type="warning" showIcon
        message={t(status)} action={<Button onClick={() => void result.refetch()}>{t('refresh')}</Button>} />;
    return <Space direction="vertical" size={16} style={{width: '100%'}}>
        <Alert type="info" showIcon message={t('bindingDescription')} />
        <StrategySimPanel selectedRun={null} onCreated={(paperRunId) => navigate(analysisPath(paperRunId))} />
    </Space>;
}
