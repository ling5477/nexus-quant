import {
    Alert,
    Button,
    Card,
    Collapse,
    Descriptions,
    Drawer,
    Space,
    Spin,
    Tabs,
    Typography,
} from 'antd';
import {useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {formatApiError} from '@/api/errors';
import {systemVersionQueryKeys} from '@/api/query-keys';
import {useAuthStore, selectSessionIdentity} from '@/store/auth-store';
import {readRuntimeVersion} from '../api/version';
import {UpdateHandoffPanel} from './UpdateHandoffPanel';
import {VersionHistory} from './VersionHistory';
import {isPublicDemo} from '@/utils/distribution';

const windowsCheck = String.raw`& '<安装目录>\runtime\nexusquant.ps1' -Action check-update -InstallRoot '<安装目录>' -TargetPackageRoot '<目标包目录>' -TrustedManifestSha256 '<独立可信清单SHA256>'`;
const windowsUpdate = windowsCheck.replace('-Action check-update', '-Action update');
const linuxCheck =
    "NQ_INSTALL_ROOT='<安装目录>' sh '<安装目录>/runtime/nexusquant.sh' check-update --package '<目标包目录>' --manifest-sha256 '<独立可信清单SHA256>'";

/** 当前版本来自运行服务，更新步骤复用宿主安装器；按钮只打开说明，不虚报已检查或已更新。 */
export function SystemVersionPanel() {
    const {t} = useTranslation();
    const token = useAuthStore(selectSessionIdentity);
    const version = useQuery({
        queryKey: systemVersionQueryKeys.current(token),
        queryFn: ({signal}) => readRuntimeVersion(signal),
        enabled: Boolean(token),
        retry: false,
    });
    const [open, setOpen] = useState(false);
    return (
        <Card
            title={t('systemVersion.title')}
            extra={
                <Button loading={version.isFetching} onClick={() => void version.refetch()}>
                    {t('systemVersion.refresh')}
                </Button>
            }
        >
            <Space direction="vertical" size={12} style={{width: '100%'}}>
                {version.isPending ? (
                    <Spin />
                ) : version.error ? (
                    <Alert
                        type="error"
                        message={t('systemVersion.readFailed')}
                        description={formatApiError(version.error)}
                    />
                ) : (
                    version.data && (
                        <>
                            <Descriptions column={{xs: 1, sm: 2}} size="small">
                                <Descriptions.Item label={t('systemVersion.service')}>
                                    {version.data.serviceVersion ?? t('systemVersion.missing')}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('systemVersion.package')}>
                                    {version.data.installedPackageVersion ??
                                        t('systemVersion.missing')}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('systemVersion.mode')}>
                                    {t('systemVersion.manual')}
                                </Descriptions.Item>
                            </Descriptions>
                            {version.data.packageIdentityStatus !== 'INSTALLER_DECLARED' && (
                                <Alert
                                    type={
                                        version.data.packageIdentityStatus === 'INVALID'
                                            ? 'error'
                                            : 'info'
                                    }
                                    message={t(
                                        version.data.packageIdentityStatus === 'INVALID'
                                            ? 'systemVersion.invalid'
                                            : 'systemVersion.noPackage',
                                    )}
                                />
                            )}
                            <Collapse
                                size="small"
                                items={[
                                    {
                                        key: 'identity',
                                        label: t('systemVersion.identity'),
                                        children: (
                                            <Descriptions column={1} size="small">
                                                {(
                                                    [
                                                        'serviceArtifact',
                                                        'sourceCommit',
                                                        'releaseSourceHash',
                                                        'packageManifestSha256',
                                                        'packagedSchemaVersion',
                                                    ] as const
                                                ).map((key) => (
                                                    <Descriptions.Item
                                                        key={key}
                                                        label={t(`systemVersion.fields.${key}`)}
                                                    >
                                                        <Typography.Text
                                                            style={{overflowWrap: 'anywhere'}}
                                                        >
                                                            {version.data?.[key] ??
                                                                t('systemVersion.missing')}
                                                        </Typography.Text>
                                                    </Descriptions.Item>
                                                ))}
                                                <Descriptions.Item
                                                    label={t('systemVersion.identityStatus')}
                                                >
                                                    {t(
                                                        `systemVersion.statuses.${version.data.packageIdentityStatus}`,
                                                    )}
                                                </Descriptions.Item>
                                            </Descriptions>
                                        ),
                                    },
                                ]}
                            />
                        </>
                    )
                )}
                <VersionHistory />
                {!isPublicDemo && <><Typography.Text type="secondary">
                    {t('systemVersion.installerScope')}
                </Typography.Text>
                <Button onClick={() => setOpen(true)}>{t('systemVersion.steps')}</Button>
                <UpdateHandoffPanel runtime={version.isError ? undefined : version.data} /></>}
            </Space>
            <Drawer
                open={open}
                onClose={() => setOpen(false)}
                title={t('systemVersion.steps')}
                width={740}
                destroyOnClose
            >
                <Space
                    direction="vertical"
                    size={16}
                    style={{width: '100%', overflowWrap: 'anywhere'}}
                >
                    <Typography.Paragraph>{t('systemVersion.prepare')}</Typography.Paragraph>
                    <Tabs
                        items={[
                            {
                                key: 'windows',
                                label: 'Windows',
                                children: (
                                    <Space direction="vertical" size={12} style={{width: '100%'}}>
                                        <Typography.Text strong>
                                            {t('systemVersion.checkStep')}
                                        </Typography.Text>
                                        <Typography.Text>
                                            {t('systemVersion.checkDescription')}
                                        </Typography.Text>
                                        <pre
                                            style={{
                                                whiteSpace: 'pre-wrap',
                                                overflowWrap: 'anywhere',
                                            }}
                                        >
                                            {windowsCheck}
                                        </pre>
                                        <Typography.Text strong>
                                            {t('systemVersion.updateStep')}
                                        </Typography.Text>
                                        <Typography.Text>
                                            {t('systemVersion.confirmDescription')}
                                        </Typography.Text>
                                        <pre
                                            style={{
                                                whiteSpace: 'pre-wrap',
                                                overflowWrap: 'anywhere',
                                            }}
                                        >
                                            {windowsUpdate}
                                        </pre>
                                    </Space>
                                ),
                            },
                            {
                                key: 'linux',
                                label: 'POSIX',
                                children: (
                                    <Space direction="vertical" size={12}>
                                        <Typography.Text>
                                            {t('systemVersion.otherPlatform')}
                                        </Typography.Text>
                                        <pre
                                            style={{
                                                whiteSpace: 'pre-wrap',
                                                overflowWrap: 'anywhere',
                                            }}
                                        >
                                            {linuxCheck}
                                        </pre>
                                    </Space>
                                ),
                            },
                        ]}
                    />
                    <Alert type="info" message={t('systemVersion.recovery')} />
                    <Button loading={version.isFetching} onClick={() => void version.refetch()}>
                        {t('systemVersion.refresh')}
                    </Button>
                </Space>
            </Drawer>
        </Card>
    );
}
