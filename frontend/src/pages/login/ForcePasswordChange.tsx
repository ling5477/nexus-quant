import {Alert, Button, Form, Input} from 'antd';
import {useMutation, useQueryClient} from '@tanstack/react-query';
import {useNavigate} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {authApi} from '@/api/auth';
import {useAuthStore} from '@/store/auth-store';
import {StandaloneSurface} from '@/components/standalone/StandaloneSurface';
import {ApiErrorNotice} from '@/errors/ApiErrorNotice';
import type {AppApiError} from '@/types/api';
import './LoginPage.css';

/** 强制改密页面独立于业务布局，成功后清除旧令牌并要求重新登录。 */
export function ForcePasswordChange() {
    const {t} = useTranslation();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const clearAuth = useAuthStore((state) => state.clearAuth);
    const mutation = useMutation({
        mutationFn: authApi.changePassword,
        onSuccess: () => {
            clearAuth();
            queryClient.clear();
            // 清令牌后的导航同步提交，避免受保护路由守卫覆盖改密成功目的地。
            navigate('/login?passwordChanged=true', {replace: true, flushSync: true});
        },
    });
    return <StandaloneSurface className="nq-login" ariaLabel={t('auth.changePasswordTitle')}>
        <div className="nq-login__inner" style={{display: 'flex', justifyContent: 'center'}}>
            <section className="nq-login__card" style={{maxWidth: 480, width: '100%', margin: 'auto'}}>
                <h1>{t('auth.changePasswordTitle')}</h1>
                <Alert type="warning" showIcon message={t('auth.changePasswordRequired')}/>
                {mutation.error && <ApiErrorNotice error={mutation.error as AppApiError}/>}
                <Form layout="vertical" onFinish={(values: {currentPassword: string; newPassword: string}) => mutation.mutate(values)}>
                    <Form.Item name="currentPassword" label={t('auth.currentPassword')} rules={[{required: true}]}>
                        <Input.Password autoComplete="current-password" maxLength={72}/>
                    </Form.Item>
                    <Form.Item name="newPassword" label={t('auth.newPassword')} rules={[{required: true}, {min: 8, max: 72}]}>
                        <Input.Password autoComplete="new-password" maxLength={72}/>
                    </Form.Item>
                    <Form.Item name="confirmPassword" label={t('auth.confirmPassword')} dependencies={['newPassword']}
                        rules={[{required: true}, ({getFieldValue}) => ({validator: (_, value) =>
                            value === getFieldValue('newPassword') ? Promise.resolve() : Promise.reject(new Error(t('auth.passwordMismatch')))})]}>
                        <Input.Password autoComplete="new-password" maxLength={72}/>
                    </Form.Item>
                    <Button type="primary" htmlType="submit" loading={mutation.isPending} block>{t('auth.changePasswordSubmit')}</Button>
                    <Button block onClick={async () => {
                        try { await authApi.logout(); } finally { clearAuth(); queryClient.clear(); navigate('/login', {replace: true, flushSync: true}); }
                    }}>{t('auth.returnToLogin')}</Button>
                </Form>
            </section>
        </div>
    </StandaloneSurface>;
}
