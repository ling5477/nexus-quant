import {useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {Alert, Button, Collapse, Descriptions, Modal, Space, Tag, Typography} from 'antd';
import {useTranslation} from 'react-i18next';
import {systemVersionQueryKeys} from '@/api/query-keys';
import {selectSessionIdentity, useAuthStore} from '@/store/auth-store';
import {isPublicDemo} from '@/utils/distribution';
import {readRuntimeVersion} from '../api/version';
import {installedRelease, releaseRecords, whatsNewKey, type ReleaseRecord} from '../release-records';

export function ReleaseDetails({record}: {record: ReleaseRecord}) {
    const {t, i18n} = useTranslation();
    const locale = i18n.resolvedLanguage === 'en-US' ? 'en-US' : 'zh-CN';
    return <Space direction="vertical" style={{width: '100%'}}>
        <Tag>{t(`releaseHistory.${record.status}`)}</Tag>
        <Descriptions size="small" column={1}>
            <Descriptions.Item label={t('releaseHistory.version')}>{record.version}</Descriptions.Item>
            <Descriptions.Item label={t('releaseHistory.releaseDate')}>{record.releaseDate ?? t('releaseHistory.unreleased')}</Descriptions.Item>
            <Descriptions.Item label={t('releaseHistory.releaseUrl')}>
                {record.releaseUrl ? <Typography.Link href={record.releaseUrl} target="_blank" rel="noopener noreferrer">{record.releaseUrl}</Typography.Link> : t('releaseHistory.unreleased')}
            </Descriptions.Item>
        </Descriptions>
        {(['features', 'improvements', 'bugFixes', 'breakingChanges', 'knownIssues'] as const).map((field) => <div key={field}>
            <Typography.Text strong>{t(`releaseHistory.${field}`)}</Typography.Text>
            {record[field].length ? <ul>{record[field].map((entry, index) => <li key={index}>{entry[locale]}</li>)}</ul> : <Typography.Paragraph>{t('releaseHistory.none')}</Typography.Paragraph>}
        </div>)}
    </Space>;
}

export function VersionHistory() {
    const {t} = useTranslation();
    return <Collapse items={[{key: 'history', label: t('releaseHistory.title'), children:
        <Collapse items={releaseRecords.map((record) => ({key: record.version, label: `${record.version} · ${t(`releaseHistory.${record.status}`)}`, children: <ReleaseDetails record={record} />}))} />
    }]} />;
}

/** 阅读记录仅影响提示，不证明升级成功；版本始终读取当前登录会话的服务身份。 */
export function WhatsNew() {
    const {t} = useTranslation();
    const token = useAuthStore(selectSessionIdentity);
    const user = useAuthStore((state) => state.currentUser);
    const version = useQuery({queryKey: systemVersionQueryKeys.current(token), queryFn: ({signal}) => readRuntimeVersion(signal), enabled: !isPublicDemo && Boolean(token) && Boolean(user?.userId) && !user?.mustChangePassword, retry: false});
    const [handled, setHandled] = useState<string | null>(null);
    const [failedKey, setFailedKey] = useState<string | null>(null);
    const key = !version.isError && !isPublicDemo ? whatsNewKey(user?.userId, version.data) : null;
    const record = !version.isError ? installedRelease(version.data) : undefined;
    const storageFailed = failedKey === key;
    let read = false;
    try { read = Boolean(key && localStorage.getItem(key) === 'read'); } catch { /* 存储不可用时本次仍展示，确认后提示无法跨登录记忆。 */ }
    if (!key || !record || read || handled === key || user?.mustChangePassword) return null;
    const finish = () => {
        if (storageFailed) { setHandled(key); return; }
        try { localStorage.setItem(key, 'read'); setHandled(key); } catch { setFailedKey(key); }
    };
    return <Modal open title={`${t('releaseHistory.whatsNew')} · ${record.version}`} width={760} styles={{body: {maxHeight: '60vh', overflowY: 'auto'}}} maskClosable={false} onCancel={finish} footer={<Button type="primary" onClick={finish}>{t(storageFailed ? 'releaseHistory.closeSession' : 'releaseHistory.read')}</Button>}>
        {storageFailed && <Alert type="warning" message={t('releaseHistory.storageFailed')} action={<Button onClick={() => setHandled(key)}>{t('releaseHistory.closeSession')}</Button>} />}
        <Typography.Paragraph>{t('releaseHistory.reopen')}</Typography.Paragraph>
        <ReleaseDetails record={record} />
    </Modal>;
}
