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
    if (variant === 'paper-boundary') {
        return (
            <Alert
                data-testid="paper-real-boundary-banner"
                type="warning"
                showIcon
                message="Paper-only boundary"
                description={(
                    <Space direction="vertical" size={8} style={{display: 'flex'}}>
                        <Typography.Text>Paper Trading is simulated.</Typography.Text>
                        <Space size={[8, 8]} wrap>
                            <Tag color="warning">Paper order ≠ real order.</Tag>
                            <Tag color="warning">Paper fill ≠ real fill.</Tag>
                            <Tag color="default">Paper balance/position ≠ real account balance/position.</Tag>
                            <Tag color="error">Paper risk pass ≠ LIVE authorization.</Tag>
                        </Space>
                        <Typography.Text type="secondary">
                            Published strategy, Paper risk pass, readiness rows, and permission probe SKIPPED do not authorize LIVE trading.
                        </Typography.Text>
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
            message="Runtime guarded: LIVE disabled"
            description={(
                <Space direction="vertical" size={8} style={{display: 'flex'}}>
                    <Space size={[8, 8]} wrap>
                        <NqStatusTag status="LIVE_DISABLED" tone="danger"/>
                        <NqStatusTag status="REAL_PROVIDER_NOT_IMPLEMENTED" tone="warning"/>
                        <NqStatusTag status="PERMISSION_PROBE_DISABLED / SKIPPED" tone="neutral"/>
                        <NqStatusTag status="NO_REAL" tone="danger"/>
                    </Space>
                    <Typography.Text>LIVE disabled.</Typography.Text>
                    <Typography.Text>Real provider not implemented.</Typography.Text>
                    <Typography.Text>NoReal/Fake/Stub/FutureReal not live-ready.</Typography.Text>
                    <Typography.Text>Permission probe SKIPPED / disabled is not verified.</Typography.Text>
                </Space>
            )}
        />
    );
}
