import {Alert, Button, Form, Input, Typography} from 'antd';
import {useMutation} from '@tanstack/react-query';
import {useRef} from 'react';
import {useTranslation} from 'react-i18next';
import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import {DemoAuthSurface} from './DemoAuthSurface';
import {demoRegistrationApi, type DemoRegistrationInput} from './api';
import type {AppApiError} from '@/types/api';

type Values = Omit<DemoRegistrationInput, 'registrationId'>;

export function Component() {
    const {t} = useTranslation('demo');
    const [form] = useLocalizedForm<Values>();
    const lastRequest = useRef<DemoRegistrationInput | null>(null);
    const registration = useMutation({mutationFn: demoRegistrationApi.register, retry: false,
        onSuccess: () => { form.resetFields(); lastRequest.current = null; }});
    const submit = (values: Values) => {
        const input = {...values, username: values.username.trim(), email: values.email.trim()};
        const previous = lastRequest.current;
        // 响应未知时，相同输入复用同一注册身份；密码仅保存在当前页面内存。
        const same = previous && Object.entries(input).every(([key, value]) => previous[key as keyof Values] === value);
        const request = {...input, registrationId: same ? previous.registrationId : crypto.randomUUID()};
        lastRequest.current = request;
        registration.mutate(request);
    };
    return <DemoAuthSurface title={t('register')}>
        <Typography.Paragraph>{t('registrationDescription')}</Typography.Paragraph>
        {registration.isSuccess ? <Alert type="info" showIcon message={t('accepted')} description={t('acceptedDescription')} /> : <>
            {registration.isError && <Alert type="error" showIcon message={t((registration.error as AppApiError).status === 429 ? 'limited' : 'failure')} />}
            <Form form={form} name="demo-registration" layout="vertical" onFinish={submit} requiredMark={false}>
                <Form.Item name="username" label={t('username')} rules={[{required: true, message: t('required')}, {pattern: /^[A-Za-z0-9][A-Za-z0-9_.-]{2,63}$/, message: t('usernameRule')}]}>
                    <Input autoComplete="username" maxLength={64} />
                </Form.Item>
                <Form.Item name="email" label={t('email')} rules={[{required: true, message: t('required')}, {type: 'email', message: t('emailRule')}]}>
                    <Input type="email" autoComplete="email" maxLength={254} />
                </Form.Item>
                <Form.Item name="password" label={t('password')} rules={[{required: true, message: t('required')}, {min: 12, max: 72, message: t('passwordRule')}, {validator: (_, value: string) => !value || new TextEncoder().encode(value).length <= 72 ? Promise.resolve() : Promise.reject(new Error(t('passwordRule')))}]}>
                    <Input.Password autoComplete="new-password" maxLength={72} />
                </Form.Item>
                <Form.Item name="confirmPassword" label={t('confirmPassword')} dependencies={['password']} rules={[{required: true, message: t('required')}, ({getFieldValue}) => ({validator: (_, value: string) => !value || value === getFieldValue('password') ? Promise.resolve() : Promise.reject(new Error(t('passwordsDiffer')))})]}>
                    <Input.Password autoComplete="new-password" maxLength={72} />
                </Form.Item>
                <Button type="primary" htmlType="submit" loading={registration.isPending} block>{t('register')}</Button>
            </Form>
        </>}
    </DemoAuthSurface>;
}
