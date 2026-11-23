import {Form, type FormInstance} from 'antd';
import {useEffect, useRef} from 'react';
import {useTranslation} from 'react-i18next';

/** 切换语言时仅重新校验已有错误，保留字段值、触碰状态和所有写操作边界。 */
export function useLocalizedForm<Values = unknown>(): [FormInstance<Values>] {
    const [form] = Form.useForm<Values>();
    const {i18n} = useTranslation();
    const previousLocale = useRef(i18n.resolvedLanguage);
    useEffect(() => {
        if (previousLocale.current === i18n.resolvedLanguage) return;
        previousLocale.current = i18n.resolvedLanguage;
        const invalidFields = form.getFieldsError().filter((field) => field.errors.length > 0).map((field) => field.name);
        if (invalidFields.length > 0) {
            // 这里只更新本地 Form 错误，不触发 onFinish 或任何请求。
            void form.validateFields(invalidFields).catch(() => undefined);
        }
    }, [form, i18n.resolvedLanguage]);
    return [form];
}
