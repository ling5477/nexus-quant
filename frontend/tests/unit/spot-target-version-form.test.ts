import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {Form, type FormProps} from 'antd';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import i18n from '@/i18n';
import type {StrategyVersionCreateRequest} from '@/features/strategies/types/strategies';

const fixture = vi.hoisted(() => ({submit: undefined as FormProps['onFinish']}));
vi.mock('antd', async (original) => {
    const actual = await original<typeof import('antd')>();
    return {...actual, Form: Object.assign((props: FormProps) => {
        fixture.submit = props.onFinish;
        return createElement(actual.Form, props);
    }, actual.Form)};
});
import {SpotTargetVersionForm} from '@/features/strategies/components/SpotTargetVersionForm';

beforeEach(async () => {fixture.submit = undefined; await i18n.changeLanguage('zh-CN');});

describe('正式策略参数与冻结来源', () => {
    it('突破版本只发送通道参数及固定收盘合同，不携带SMA窗口', async () => {
        const create = vi.fn<(request: StrategyVersionCreateRequest) => Promise<boolean>>().mockResolvedValue(false);
        const html = renderToStaticMarkup(createElement(SpotTargetVersionForm,
            {strategyType: 'SPOT_CHANNEL_BREAKOUT_V1', onCreate: create, busy: false}));
        expect(html).toContain('HOLD不交易');
        expect(html).toContain('入场回看根数');
        await fixture.submit!({versionName: 'Channel baseline', status: 'ACTIVE', window: 99,
            entryLookbackBars: 20, exitLookbackBars: 10, investedExposure: ' 0.5 '});
        expect(create).toHaveBeenCalledOnce();
        const request = create.mock.calls[0][0];
        expect(JSON.parse(request.paramSnapshotJson!)).toEqual({entryLookbackBars: 20, exitLookbackBars: 10, investedExposure: '0.5'});
        expect(JSON.parse(request.sourceSnapshotJson!)).toEqual({executable: 'SPOT_CHANNEL_BREAKOUT_V1',
            timeframe: '1h', confirmation: 'BAR_CLOSE', currentBarExcluded: true, pyramiding: false, allowShort: false});
    });

    it('原SMA版本仍发送原参数及原执行标识', async () => {
        const create = vi.fn<(request: StrategyVersionCreateRequest) => Promise<boolean>>().mockResolvedValue(false);
        renderToStaticMarkup(createElement(SpotTargetVersionForm,
            {strategyType: 'SPOT_SMA_TARGET_V1', onCreate: create, busy: false}));
        await fixture.submit!({versionName: 'SMA', status: 'ACTIVE', window: 20,
            entryLookbackBars: 55, exitLookbackBars: 20, investedExposure: '1'});
        expect(JSON.parse(create.mock.calls[0][0].paramSnapshotJson!)).toEqual({window: 20, investedExposure: '1'});
        expect(JSON.parse(create.mock.calls[0][0].sourceSnapshotJson!)).toEqual({executable: 'SPOT_SMA_TARGET_V1'});
    });

    it('英文说明明确HOLD不再平衡且收益未证明', async () => {
        await i18n.changeLanguage('en-US');
        const html = renderToStaticMarkup(createElement(SpotTargetVersionForm,
            {strategyType: 'SPOT_CHANNEL_BREAKOUT_V1', onCreate: async () => false, busy: false}));
        expect(html).toContain('HOLD creates no trade');
        expect(html).toContain('profitability and optimal parameters are unproven');
    });
});
