import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {describe, expect, it, vi} from 'vitest';
import {FrozenFeeSourceSummary} from '@/features/backtests/FrozenFeeSourceSummary';

vi.mock('react-i18next', async (importOriginal) => ({
    ...(await importOriginal<typeof import('react-i18next')>()),
    useTranslation: () => ({t: (key: string) => key}),
}));

/** 冻结预览中的收到资产率只能代表回落条件；配置预览不宣称优惠币已实际支付。 */
describe('冻结方向费用的标签含义', () => {
    const render = (discount: boolean) => {
        const snapshot = {
            schemaVersion: 'ACCOUNT_FEE_MODEL_V2',
            buyReceivedAssetRate: '0.001',
            sellReceivedAssetRate: '0.002',
            paymentPolicy: discount ? 'DISCOUNT_ASSET_IF_FUNDED' : 'RECEIVED_ASSET',
            symbol: 'BTC-USDT',
            observedAt: '2026-01-01T00:00:00Z',
            frozenAt: '2026-01-01T00:01:00Z',
        };
        const executionSpec = {
            costSource: 'CURRENT_ACCOUNT_OBSERVATION',
            feeSourceSnapshotJson: JSON.stringify(snapshot),
            ...(discount
                ? {
                      discountAssetBudgetFraction: '0.1',
                      discountPriceDatasetId: 'dataset',
                      discountPriceSnapshotId: 'snapshot',
                      discountPriceSnapshotSha256: 'a'.repeat(64),
                  }
                : {}),
        };
        return renderToStaticMarkup(
            createElement(FrozenFeeSourceSummary, {configuration: JSON.stringify({executionSpec})}),
        );
    };
    it('labels received rates as insufficient-balance fallback under funded policy', () => {
        const markup = render(true);
        expect(markup).toContain('discountFee.fallbackBuyRate');
        expect(markup).toContain('discountFee.fallbackSellRate');
        expect(markup).toContain('discountFee.actualPaymentMeaning');
        expect(markup).not.toContain('feeModel.buyTaker');
        expect(markup).not.toContain('feeModel.sellTaker');
    });
    it('retains the existing received-asset labels for that explicit policy', () => {
        const markup = render(false);
        expect(markup).toContain('feeModel.buyTaker');
        expect(markup).toContain('feeModel.sellTaker');
        expect(markup).not.toContain('discountFee.fallbackBuyRate');
    });
});
