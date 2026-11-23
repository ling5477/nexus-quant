import {Alert, Button, Checkbox, Modal, Space} from 'antd';
import {useEffect, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useUserPreferences} from '@/features/preferences/hooks/useUserPreferences';
import {LegalDocuments} from './LegalDocuments';
import {useAuthStore} from '@/store/auth-store';
import {formatApiError} from '@/api/errors';
import {isPublicDemo} from '@/utils/distribution';

/** 只记录本次登录是否已处理自动提示，不把本地标记当作声明接受事实。 */
function readHandled(key: string): boolean {
    try {
        const value = sessionStorage.getItem(key);
        return value === 'read' || value === 'suppressed';
    } catch {
        return false;
    }
}

/** 按每次成功登录和持久化偏好提示声明；偏好读取失败仍提示，保存成功后才关闭。 */
export function LoginDisclosures() {
    const {t} = useTranslation();
    const {query, save} = useUserPreferences();
    const username = useAuthStore((state) => state.currentUser?.username);
    const key = 'nq:login-disclosures:' + (username ?? '');
    const [handled, setHandled] = useState(() => readHandled(key));
    const [suppress, setSuppress] = useState(false);
    useEffect(() => {
        setHandled(readHandled(key));
        setSuppress(false);
    }, [key]);
    useEffect(() => {
        if (!query.isSuccess || !query.data.hideLoginDisclosures) return;
        // 登录时已按偏好跳过提示，设置页重新开启只影响下一次成功登录；刷新仍沿用本次处理结果。
        try {
            sessionStorage.setItem(key, 'suppressed');
        } catch {
            /* 禁用会话存储时，本次挂载仍保留处理结果。 */
        }
        setHandled(true);
    }, [key, query.isSuccess, query.data?.hideLoginDisclosures]);
    const open =
        (isPublicDemo || (query.isSuccess && !query.data.hideLoginDisclosures) || query.isError) && !handled;
    function finish() {
        try {
            sessionStorage.setItem(key, 'read');
        } catch {
            /* 禁用会话存储时，本次挂载仍不重复提示。 */
        }
        setHandled(true);
    }
    // 会话声明必须高于由路由参数自动打开的业务抽屉，否则同层Portal后挂载会遮住阅读确认按钮。
    return (
        <Modal
            open={open}
            title={t('preferences.loginDisclosures')}
            width={780}
            zIndex={1200}
            closable={false}
            maskClosable={false}
            keyboard={false}
            footer={
                <Space wrap>
                    {!isPublicDemo && <Checkbox
                        checked={suppress}
                        disabled={!query.data}
                        onChange={(event) => setSuppress(event.target.checked)}
                    >
                        {t('preferences.hideNextLogin')}
                    </Checkbox>}
                    <Button
                        type="primary"
                        loading={save.isPending}
                        onClick={() => {
                            if (suppress)
                                save.mutate({hideLoginDisclosures: true}, {onSuccess: finish});
                            else finish();
                        }}
                    >
                        {t('preferences.confirmRead')}
                    </Button>
                </Space>
            }
        >
            {query.error && (
                <Alert
                    type="error"
                    showIcon
                    message={formatApiError(query.error)}
                    action={
                        <Button onClick={() => void query.refetch()}>
                            {t('preferences.reload')}
                        </Button>
                    }
                />
            )}
            {save.error && <Alert type="error" showIcon message={formatApiError(save.error)} />}
            <LegalDocuments />
        </Modal>
    );
}
