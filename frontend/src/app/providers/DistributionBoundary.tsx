import {Alert, Button, Space} from 'antd';
import {useQuery} from '@tanstack/react-query';
import type {PropsWithChildren} from 'react';
import {useTranslation} from 'react-i18next';
import {AppLoadingScreen} from '@/components/app/AppLoadingScreen';
import {appEnv} from '@/utils/env';

/** 先比较不可变构建身份再恢复认证；失败关闭入口，配置不能使两个发行物互相提升。 */
export function DistributionBoundary({children}: PropsWithChildren) {
    const {t} = useTranslation();
    const expected = import.meta.env.VITE_DISTRIBUTION === 'PUBLIC_DEMO' ? 'NQ_PUBLIC_DEMO' : 'NQ_SELF_HOSTED';
    const query = useQuery({queryKey: ['distribution-identity'], retry: false, staleTime: Infinity,
        queryFn: async () => {
            const response = await fetch(`${appEnv.apiBaseUrl.replace(/\/$/, '')}/distribution`, {
                signal: AbortSignal.timeout(10000), cache: 'no-store', credentials: 'omit',
            });
            if (!response.ok) throw new Error('DISTRIBUTION_IDENTITY_UNAVAILABLE');
            const identity: unknown = await response.json();
            if (!identity || typeof identity !== 'object' || !('distribution' in identity) || identity.distribution !== expected)
                throw new Error('DISTRIBUTION_IDENTITY_MISMATCH');
            return expected;
        }});
    if (query.isPending) return <AppLoadingScreen message={t('distribution.checking')} />;
    if (query.isError) return <Space direction="vertical" style={{padding: 32, width: '100%'}}>
        <Alert type="error" showIcon message={t('distribution.unavailable')} description={t('distribution.description')} />
        <Button onClick={() => void query.refetch()} loading={query.isFetching}>{t('distribution.retry')}</Button>
    </Space>;
    return children;
}
