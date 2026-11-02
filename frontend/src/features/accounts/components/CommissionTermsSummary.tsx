import {Alert, Descriptions, Table, Typography} from 'antd';
import {useTranslation} from 'react-i18next';
import type {CommissionRates, SpotCommissionTerms} from '../api/fees';
import {formatAccountFeePercent} from '../fee-display';

/** 分量表保留方向与优惠边界；没有金额/余额/实际支付资产时只展示来源条件，不生成已发生成本。 */
export function CommissionTermsSummary({terms}: {terms: SpotCommissionTerms}) {
    const {t} = useTranslation('pages');
    const rows = (
        [
            ['standard', terms.standard],
            ['tax', terms.tax],
            ['special', terms.special],
        ] as const
    ).map(([kind, rates]) => ({kind, ...rates}));
    return (
        <>
            <Typography.Paragraph type="secondary">
                {t('accountFees.commissionFormula')}
            </Typography.Paragraph>
            <Table
                size="small"
                rowKey="kind"
                pagination={false}
                dataSource={rows}
                scroll={{x: 580}}
                columns={[
                    {
                        title: t('accountFees.commissionCategory'),
                        dataIndex: 'kind',
                        width: 120,
                        render: (kind: string) => t(`accountFees.commission_${kind}`),
                    },
                    ...(['maker', 'taker', 'buyer', 'seller'] as const).map(
                        (key: keyof CommissionRates) => ({
                            title: t(`accountFees.component_${key}`),
                            dataIndex: key,
                            width: 115,
                            render: formatAccountFeePercent,
                        }),
                    ),
                ]}
            />
            <Descriptions size="small" column={{xs: 1, md: 2}} style={{marginTop: 12}}>
                <Descriptions.Item label={t('accountFees.discountAccount')}>
                    {t(
                        terms.discount.enabledForAccount
                            ? 'accountFees.enabled'
                            : 'accountFees.disabled',
                    )}
                </Descriptions.Item>
                <Descriptions.Item label={t('accountFees.discountSymbol')}>
                    {t(
                        terms.discount.enabledForSymbol
                            ? 'accountFees.enabled'
                            : 'accountFees.disabled',
                    )}
                </Descriptions.Item>
                <Descriptions.Item label={t('accountFees.discountAsset')}>
                    {terms.discount.asset}
                </Descriptions.Item>
                <Descriptions.Item label={t('accountFees.discountMultiplier')}>
                    {terms.discount.multiplier} ×
                </Descriptions.Item>
            </Descriptions>
            <Alert
                type="info"
                showIcon
                message={t('accountFees.discountBoundary')}
                description={t('accountFees.directionalConsumptionPending')}
            />
        </>
    );
}
