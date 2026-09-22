import {t} from '@/i18n';
import {useTranslation} from 'react-i18next';
import {Alert, Space, Tag, Typography} from 'antd';

import {NqStatusTag} from '@/components/nq/NqStatusTag';

export type RuntimeGuardBannerVariant = 'paper-boundary' | 'trading-workbench';

interface RuntimeGuardBannerProps {
    variant: RuntimeGuardBannerVariant;
}

/**
 * RuntimeGuardBanner renders read-only GateM runtime boundary copy.
 *
 * Boundary: this component is informational only. It must not hide controls,
 * unlock controls, call APIs, or become a dismissible substitute for backend
 * Paper-to-Real fail-closed guards.
 */
export function RuntimeGuardBanner({variant}: RuntimeGuardBannerProps) {
    useTranslation();
    if (variant === 'paper-boundary') {
        return (
            <Alert
                data-testid="paper-real-boundary-banner"
                type="warning"
                showIcon
                message={t('boundary.paper')}
                description={(
                    <Space direction="vertical" size={8} style={{display: 'flex'}}>
                        <Typography.Text>{t('boundary.simulated')}</Typography.Text>
                        <Space size={[8, 8]} wrap>
                            <Tag color="warning">{t('boundary.order')}</Tag>
                            <Tag color="warning">{t('boundary.fill')}</Tag>
                            <Tag color="default">{t('boundary.balance')}</Tag>
                            <Tag color="error">{t('boundary.risk')}</Tag>
                        </Space>
                        <Typography.Text type="secondary">{t('boundary.authority')}</Typography.Text>
                    </Space>
                )}
            />
        );
    }

    return (
        <Alert
            data-testid="runtime-guarded-live-disabled-banner"
            type="warning"
            showIcon
            message={t('boundary.runtime')}
            description={(
                <Space direction="vertical" size={8} style={{display: 'flex'}}>
                    <Space size={[8, 8]} wrap>
                        <NqStatusTag status="LIVE_DISABLED" tone="danger"/>
                        <NqStatusTag status="REAL_PROVIDER_NOT_IMPLEMENTED" tone="warning"/>
                        <NqStatusTag status="PERMISSION_PROBE_DISABLED / SKIPPED" tone="neutral"/>
                        <NqStatusTag status="NO_REAL" tone="danger"/>
                    </Space>
                    <Typography.Text>{t('boundary.live')}</Typography.Text>
                    <Typography.Text>{t('boundary.provider')}</Typography.Text>
                    <Typography.Text>{t('boundary.notReady')}</Typography.Text>
                    <Typography.Text>{t('boundary.probe')}</Typography.Text>
                </Space>
            )}
        />
    );
}
