import {Alert, Collapse, Descriptions, Tag, Typography} from 'antd';
import {useTranslation} from 'react-i18next';
import {formatDateTimeMilliseconds} from '@/utils/formatters';
import {formatAccountFeePercent} from '@/features/accounts/fee-display';
import {
    accountFeeMode,
    directionalFeeVersion,
    discountAssetPolicy,
    executionObject,
    validDiscountFraction,
} from './fee-source';
import {formatRatioPercent} from '@/utils/formatters';

/** 只读运行的冻结来源，不能用当前配置或最新费率覆盖历史结果。 */
export function FrozenFeeSourceSummary({configuration}: {configuration?: string | null}) {
    const {t} = useTranslation('pages');
    if (!configuration) return null;
    let spec: Record<string, unknown>;
    try {
        const config = executionObject(configuration);
        spec = (config.executionSpec ?? config.feeExecutionSpec) as Record<string, unknown>;
        if (!spec || spec.costSource !== accountFeeMode) return null;
    } catch {
        return <Alert type="warning" message={t('feeModel.frozenUnavailable')} />;
    }
    let snapshot: Record<string, unknown>;
    try {
        snapshot = executionObject(String(spec.feeSourceSnapshotJson));
        const native = snapshot.schemaVersion === directionalFeeVersion;
        if (
            typeof snapshot.frozenAt !== 'string' ||
            (native
                ? typeof snapshot.buyReceivedAssetRate !== 'string' ||
                  typeof snapshot.sellReceivedAssetRate !== 'string' ||
                  !['RECEIVED_ASSET', discountAssetPolicy].includes(String(snapshot.paymentPolicy))
                : typeof snapshot.selectedRate !== 'string')
        )
            throw new Error();
        if (
            snapshot.paymentPolicy === discountAssetPolicy &&
            (!validDiscountFraction(spec.discountAssetBudgetFraction) ||
                typeof spec.discountPriceDatasetId !== 'string' ||
                typeof spec.discountPriceSnapshotId !== 'string' ||
                typeof spec.discountPriceSnapshotSha256 !== 'string')
        )
            throw new Error();
    } catch {
        return <Alert type="warning" message={t('feeModel.frozenUnavailable')} />;
    }
    const native = snapshot.schemaVersion === directionalFeeVersion;
    const discount = native && snapshot.paymentPolicy === discountAssetPolicy;
    const assets = String(snapshot.symbol).split('-');
    return (
        <div>
            <Typography.Text strong>{t('feeModel.frozenTitle')}</Typography.Text>{' '}
            <Tag>{t('feeModel.current')}</Tag>
            <Typography.Paragraph type="secondary" style={{margin: '6px 0 10px'}}>
                {t(native ? 'feeModel.directionalFrozenMeaning' : 'feeModel.frozenMeaning')}
            </Typography.Paragraph>
            <Descriptions
                size="small"
                column={{xs: 1, sm: 2}}
                items={[
                    {
                        key: 'scope',
                        label: t('feeModel.account'),
                        children:
                            '#' +
                            snapshot.exchangeAccountId +
                            ' · ' +
                            snapshot.tradeEnv +
                            ' · ' +
                            snapshot.symbol,
                    },
                    ...(native
                        ? [
                              {
                                  key: 'buy',
                                  label: t(
                                      discount
                                          ? 'discountFee.fallbackBuyRate'
                                          : 'feeModel.buyTaker',
                                  ),
                                  children:
                                      formatAccountFeePercent(
                                          String(snapshot.buyReceivedAssetRate),
                                      ) +
                                      ' · ' +
                                      assets[0],
                              },
                              {
                                  key: 'sell',
                                  label: t(
                                      discount
                                          ? 'discountFee.fallbackSellRate'
                                          : 'feeModel.sellTaker',
                                  ),
                                  children:
                                      formatAccountFeePercent(
                                          String(snapshot.sellReceivedAssetRate),
                                      ) +
                                      ' · ' +
                                      assets[1],
                              },
                              {
                                  key: 'payment',
                                  label: t('feeModel.paymentPolicy'),
                                  children: t(
                                      discount
                                          ? 'discountFee.fundedPolicy'
                                          : 'discountFee.receivedPolicy',
                                  ),
                              },
                              ...(discount
                                  ? [
                                        {
                                            key: 'fraction',
                                            label: t('discountFee.budgetFraction'),
                                            children: formatRatioPercent(
                                                String(spec.discountAssetBudgetFraction),
                                            ),
                                        },
                                        {
                                            key: 'conversion',
                                            label: t('discountFee.priceDataset'),
                                            children: 'BNB-USDT · 1m',
                                        },
                                    ]
                                  : []),
                          ]
                        : [
                              {
                                  key: 'rate',
                                  label: t('feeModel.taker'),
                                  children:
                                      formatAccountFeePercent(String(snapshot.selectedRate)) +
                                      ' · ' +
                                      snapshot.feeCurrency,
                              },
                          ]),
                    {
                        key: 'observed',
                        label: t('accountFees.observedAt'),
                        children: formatDateTimeMilliseconds(String(snapshot.observedAt)),
                    },
                    {
                        key: 'frozen',
                        label: t('feeModel.frozenAt'),
                        children: formatDateTimeMilliseconds(String(snapshot.frozenAt)),
                    },
                ]}
            />
            {discount && (
                <Typography.Paragraph type="secondary" style={{marginBottom: 10}}>
                    {t('discountFee.frozenMeaning')}
                </Typography.Paragraph>
            )}
            {discount && (
                <Typography.Paragraph type="secondary" style={{marginBottom: 10}}>
                    {t('discountFee.actualPaymentMeaning')}
                </Typography.Paragraph>
            )}
            <Collapse
                size="small"
                items={[
                    {
                        key: 'source',
                        label: t('accountFees.sourceDetails'),
                        children: (
                            <Descriptions
                                size="small"
                                column={1}
                                items={[
                                    {
                                        key: 'id',
                                        label: t('accountFees.observation'),
                                        children: (
                                            <Typography.Text copyable>
                                                {String(snapshot.observationId)}
                                            </Typography.Text>
                                        ),
                                    },
                                    {
                                        key: 'credential',
                                        label: t('accountFees.credential'),
                                        children: String(snapshot.credentialId),
                                    },
                                    ...(native
                                        ? [
                                              {
                                                  key: 'terms',
                                                  label: t('feeModel.fullCommissionTerms'),
                                                  children: (
                                                      <Typography.Text
                                                          style={{
                                                              whiteSpace: 'pre-wrap',
                                                              wordBreak: 'break-word',
                                                          }}
                                                      >
                                                          {JSON.stringify(
                                                              snapshot.commissionTerms,
                                                              null,
                                                              2,
                                                          )}
                                                      </Typography.Text>
                                                  ),
                                              },
                                          ]
                                        : [
                                              {
                                                  key: 'maker',
                                                  label: t('accountFees.originalMaker'),
                                                  children: String(snapshot.makerProviderRate),
                                              },
                                              {
                                                  key: 'taker',
                                                  label: t('accountFees.originalTaker'),
                                                  children: String(snapshot.takerProviderRate),
                                              },
                                          ]),
                                    {
                                        key: 'sha',
                                        label: t('accountFees.digest'),
                                        children: (
                                            <Typography.Text
                                                copyable
                                                style={{wordBreak: 'break-all'}}
                                            >
                                                {String(spec.feeSourceSnapshotSha256)}
                                            </Typography.Text>
                                        ),
                                    },
                                    ...(discount
                                        ? [
                                              {
                                                  key: 'dataset',
                                                  label: t('discountFee.datasetIdentity'),
                                                  children: (
                                                      <Typography.Text copyable>
                                                          {String(spec.discountPriceDatasetId)}
                                                      </Typography.Text>
                                                  ),
                                              },
                                              {
                                                  key: 'price-id',
                                                  label: t('discountFee.snapshotIdentity'),
                                                  children: (
                                                      <Typography.Text copyable>
                                                          {String(spec.discountPriceSnapshotId)}
                                                      </Typography.Text>
                                                  ),
                                              },
                                              {
                                                  key: 'price-sha',
                                                  label: t('discountFee.snapshotDigest'),
                                                  children: (
                                                      <Typography.Text
                                                          copyable
                                                          style={{wordBreak: 'break-all'}}
                                                      >
                                                          {String(spec.discountPriceSnapshotSha256)}
                                                      </Typography.Text>
                                                  ),
                                              },
                                          ]
                                        : []),
                                ]}
                            />
                        ),
                    },
                ]}
            />
        </div>
    );
}
