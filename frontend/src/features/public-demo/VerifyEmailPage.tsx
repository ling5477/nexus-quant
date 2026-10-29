import {Alert, Button, Form, Input, Spin, Typography} from 'antd';
import {useMutation} from '@tanstack/react-query';
import {useEffect, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import type {AppApiError} from '@/types/api';
import {DemoAuthSurface} from './DemoAuthSurface';
import {demoRegistrationApi} from './api';

export function Component() {
    const {t} = useTranslation('demo');
    const [form] = useLocalizedForm<{email: string}>();
    const [token] = useState(() => new URLSearchParams(window.location.hash.slice(1)).get('token')
        ?? new URLSearchParams(window.location.search).get('token'));
    const started = useRef(false);
    const verify = useMutation({mutationFn: demoRegistrationApi.verify, retry: false});
    const resend = useMutation({mutationFn: demoRegistrationApi.resend, retry: false});
    const verifyToken = verify.mutate;
    useEffect(() => {
        // 使用后立即移除地址栏中的秘密；不存入浏览器持久化存储或诊断信息。
        window.history.replaceState(window.history.state, '', window.location.pathname);
        let active = true;
        // 等待本次挂载完成后提交；开发模式的重挂载清理不会留下失去订阅的写请求。
        void Promise.resolve().then(() => {
            if (active && token && !started.current) { started.current = true; verifyToken(token); }
        });
        return () => {active = false;};
    }, [token, verifyToken]);
    const ready = verify.isSuccess && verify.data?.status === 'READY';
    const failure = verify.error as AppApiError | null;
    const failureKey = failure?.status === 429 ? 'limited' : failure?.status === 410 ? 'expired' : failure?.status === 400 ? 'invalid' : 'failure';
    return <DemoAuthSurface title={t('verifyTitle')} backLabel={ready ? t('goLogin') : undefined}>
        {verify.isPending && <><Spin aria-label={t('verifying')} /><Typography.Paragraph>{t('verifying')}</Typography.Paragraph></>}
        {ready ? <Alert type="success" showIcon message={t('verified')} /> : <>
            {!token && <Alert type="warning" showIcon message={t('missingToken')} description={t('missingTokenDescription')} />}
            {failure && <Alert type="error" showIcon message={t(failureKey)} />}
            {verify.isSuccess && !ready && <Alert type="warning" showIcon message={t('failure')} />}
            <Typography.Paragraph>{t('verifyDescription')}</Typography.Paragraph>
            {token && failure && failure.status !== 400 && failure.status !== 410 && <Button loading={verify.isPending} onClick={() => verify.mutate(token)}>{t('retry')}</Button>}
            <Typography.Title level={4}>{t('resend')}</Typography.Title>
            <Typography.Paragraph>{t('resendDescription')}</Typography.Paragraph>
            {resend.isSuccess && <Alert type="info" showIcon message={t('accepted')} description={t('acceptedDescription')} />}
            {resend.isError && <Alert type="error" showIcon message={t((resend.error as AppApiError).status === 429 ? 'limited' : 'failure')} />}
            <Form form={form} name="demo-resend" layout="vertical" requiredMark={false} onFinish={({email}) => resend.mutate(email.trim())}>
                <Form.Item name="email" label={t('email')} rules={[{required: true, message: t('required')}, {type: 'email', message: t('emailRule')}]}>
                    <Input type="email" autoComplete="email" maxLength={254} />
                </Form.Item>
                <Button htmlType="submit" loading={resend.isPending} disabled={verify.isPending}>{t('resend')}</Button>
            </Form>
        </>}
    </DemoAuthSurface>;
}
