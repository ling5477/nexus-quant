import {
    Alert,
    App,
    Button,
    Collapse,
    Descriptions,
    Drawer,
    Empty,
    Select,
    Space,
    Table,
    Tag,
    Typography,
} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useEffect, useState} from 'react';
import {useQuery, useQueryClient} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {useAuthStore} from '@/store/auth-store';
import {instrumentsApi} from '@/features/instruments/api/instruments';
import {instrumentQueryKeys} from '@/api/query-keys';
import {formatApiError} from '@/api/errors';
import {formatAccountFeePercent} from '../fee-display';
import {formatDateTimeMilliseconds} from '@/utils/formatters';
import type {AppApiError} from '@/types/api';
import type {ExchangeAccountSummary, ExchangeCredentialType} from '../types/accounts';
import {CommissionTermsSummary} from './CommissionTermsSummary';
import {
    accountFeesApi,
    feeSyncChanged,
    hasFeeSyncRecord,
    readPendingFeeSync,
    type AccountFeeObservation,
} from '../api/fees';

/** 切换用户后只读取该用户身份；刷新及重新挂载仅恢复待查询状态，不重放同步请求。 */
function usePendingFeeSync() {
    const owner = useAuthStore((state) => state.currentUser?.userId);
    const [pending, setPending] = useState(() => readPendingFeeSync(owner));
    useEffect(() => {
        const update = () => setPending(readPendingFeeSync(owner));
        update();
        window.addEventListener(feeSyncChanged, update);
        return () => window.removeEventListener(feeSyncChanged, update);
    }, [owner]);
    return pending?.ownerUserId === owner ? pending : null;
}

/** 当前账户费率观察与来源历史；不把私有来源关闭、空历史或过期证据显示成零费率或历史真实费用。 */
export function AccountFeeWorkspace({
    account,
    onClose,
}: {
    account: ExchangeAccountSummary;
    onClose: () => void;
}) {
    const {t} = useTranslation('pages');
    const {modal, message} = App.useApp();
    const client = useQueryClient();
    const user = useAuthStore((state) => state.currentUser);
    const pending = usePendingFeeSync();
    const scopedPending = pending?.accountId === account.exchangeAccountId ? pending : null;
    const [symbol, setSymbol] = useState<string>();
    const [binanceCredentialType, setBinanceCredentialType] =
        useState<ExchangeCredentialType>('BINANCE_HMAC');
    const [before, setBefore] = useState<string>();
    const [selected, setSelected] = useState<AccountFeeObservation | null>(null);
    const [busy, setBusy] = useState(false),
        [lookupBusy, setLookupBusy] = useState(false);
    const [notRecorded, setNotRecorded] = useState(false),
        [error, setError] = useState<AppApiError | null>(null);
    const [now, setNow] = useState(Date.now);
    // 只更新观察过期标签，不轮询供应方或自动生成新费率；关闭抽屉后释放计时器。
    useEffect(() => {
        const timer = window.setInterval(() => setNow(Date.now()), 1000);
        return () => window.clearInterval(timer);
    }, []);
    const selectedSymbol = scopedPending?.symbol ?? symbol;
    const owner = user?.userId;
    const prefix = ['account-fees', owner, account.exchangeAccountId] as const;
    const catalog = useQuery({
        queryKey: instrumentQueryKeys.list(account.exchangeCode),
        queryFn: ({signal}) => instrumentsApi.list(account.exchangeCode, signal),
    });
    const symbols = [
        ...new Set(
            (catalog.data ?? [])
                .filter(
                    (row) =>
                        row.instrumentType === 'SPOT' &&
                        ['TRADING', 'LIVE', 'ACTIVE'].includes(row.status),
                )
                .map((row) => row.internalSymbol),
        ),
    ];
    // 历史与来源能力按用户/账户/标的/游标隔离；只有显式选择或恢复原身份后才查询费率。
    const history = useQuery({
        queryKey: [...prefix, 'history', selectedSymbol ?? 'none', before ?? 'latest'],
        queryFn: ({signal}) =>
            accountFeesApi.history(account.exchangeAccountId, selectedSymbol!, before, signal),
        enabled: Boolean(owner && selectedSymbol),
    });
    const supportedVenue = ['OKX', 'BINANCE'].includes(account.exchangeCode);
    const credentialType = account.exchangeCode === 'OKX' ? 'OKX_API_V5' : binanceCredentialType;
    // 类型是明确选择，活动版本来自正式响应；HMAC与Ed25519不互相回退，也不伪造OKX引用。
    const credential = useQuery({
        queryKey: [...prefix, 'active-credential', credentialType],
        queryFn: ({signal}) =>
            accountFeesApi.activeCredential(account.exchangeAccountId, signal, credentialType),
        enabled: Boolean(owner && supportedVenue),
    });
    const active = credential.data?.activeCredential;
    const canWrite = user?.roles.some((role) => ['ADMIN', 'OPERATOR'].includes(role));
    const canSync =
        canWrite &&
        account.status === 'ACTIVE' &&
        history.data?.synchronizationAvailable &&
        active?.isActive &&
        active.credentialStatus === 'ACTIVE' &&
        !pending &&
        !hasFeeSyncRecord(owner) &&
        !busy &&
        !lookupBusy;
    useEffect(() => {
        setBefore(undefined);
        setSelected(null);
    }, [selectedSymbol]);
    useEffect(() => {
        setNotRecorded(false);
        setError(null);
    }, [pending?.operationId]);
    const confirmed = async (row: AccountFeeObservation) => {
        setSelected(row);
        setSymbol(row.symbol);
        setBefore(undefined);
        await client.invalidateQueries({queryKey: prefix});
        message.success(t('pages:accountFees.confirmed'));
    };
    // 恢复须先查询未记录并由用户显式确认；首次同步的可用性不能用于替换原身份。
    const sync = async (recover = false) => {
        if (
            busy ||
            lookupBusy ||
            (recover ? !scopedPending || !notRecorded : !canSync || !active || !selectedSymbol)
        )
            return;
        setBusy(true);
        setError(null);
        try {
            const row = recover
                ? await accountFeesApi.recover(scopedPending!)
                : await accountFeesApi.submit(
                      account.exchangeAccountId,
                      active!.credentialId,
                      selectedSymbol!,
                  );
            await confirmed(row);
        } catch (failure) {
            setError(failure as AppApiError);
        } finally {
            setBusy(false);
        }
    };
    // 只读原回执；404只改变恢复提示，不清身份、不自动重发，也不表示同步失败。
    const lookup = async () => {
        if (!scopedPending || busy || lookupBusy) return;
        setLookupBusy(true);
        setError(null);
        setNotRecorded(false);
        try {
            const row = await accountFeesApi.result(scopedPending);
            accountFeesApi.confirm(scopedPending);
            await confirmed(row);
        } catch (failure) {
            if ((failure as AppApiError).status === 404) setNotRecorded(true);
            else setError(failure as AppApiError);
        } finally {
            setLookupBusy(false);
        }
    };
    const rate = formatAccountFeePercent;
    const columns: ColumnsType<AccountFeeObservation> = [
        {
            title: t('pages:accountFees.observedAt'),
            dataIndex: 'observedAt',
            width: 190,
            render: formatDateTimeMilliseconds,
        },
        {
            title: t('pages:accountFees.maker'),
            dataIndex: 'makerCostRate',
            width: 130,
            render: (value, row) =>
                row.commissionTerms ? t('accountFees.conditional') : rate(value),
        },
        {
            title: t('pages:accountFees.taker'),
            dataIndex: 'takerCostRate',
            width: 130,
            render: (value, row) =>
                row.commissionTerms ? t('accountFees.conditional') : rate(value),
        },
        {
            title: t('pages:accountFees.freshness'),
            width: 110,
            render: (_, row) => (
                <Tag color={now >= Date.parse(row.expiresAt) ? 'orange' : 'blue'}>
                    {t(
                        now >= Date.parse(row.expiresAt)
                            ? 'pages:accountFees.expired'
                            : 'pages:accountFees.observed',
                    )}
                </Tag>
            ),
        },
        {
            title: t('pages:actions'),
            width: 90,
            render: (_, row) => (
                <Button type="link" onClick={() => setSelected(row)}>
                    {t('pages:accountFees.details')}
                </Button>
            ),
        },
    ];
    return (
        <Drawer
            open
            onClose={onClose}
            width="min(960px, 100vw)"
            destroyOnHidden
            title={
                <Space wrap>
                    <span>{t('pages:accountFees.title')}</span>
                    <Tag>{account.accountAlias}</Tag>
                    <Tag>
                        {account.exchangeCode} · {account.tradeEnv}
                    </Tag>
                </Space>
            }
        >
            <Space direction="vertical" size={16} style={{width: '100%'}}>
                <Typography.Paragraph type="secondary">
                    {t('pages:accountFees.scope')}
                </Typography.Paragraph>
                {pending && !scopedPending && (
                    <Alert
                        type="warning"
                        showIcon
                        message={t('pages:accountFees.otherPending', {account: pending.accountId})}
                    />
                )}
                {!pending && hasFeeSyncRecord(owner) && (
                    <Alert type="error" message={t('pages:accountFees.unreadable')} />
                )}
                {scopedPending && (
                    <Alert
                        type="warning"
                        showIcon
                        message={t('pages:accountFees.unknown')}
                        description={
                            <Space direction="vertical" size={12}>
                                <Typography.Text>
                                    {t('pages:accountFees.unknownMeaning')}
                                </Typography.Text>
                                <Typography.Text>
                                    {scopedPending.symbol} · {t('pages:accountFees.credential')} #
                                    {scopedPending.credentialId}
                                </Typography.Text>
                                <Typography.Text copyable>
                                    {scopedPending.operationId}
                                </Typography.Text>
                                {notRecorded && (
                                    <Typography.Text>
                                        {t('pages:accountFees.notRecorded')}
                                    </Typography.Text>
                                )}
                                <Space wrap>
                                    <Button
                                        disabled={
                                            busy ||
                                            lookupBusy ||
                                            scopedPending.phase === 'SUBMITTING'
                                        }
                                        loading={lookupBusy}
                                        onClick={() => void lookup()}
                                    >
                                        {t('pages:accountFees.lookup')}
                                    </Button>
                                    <Button
                                        disabled={!canWrite || !notRecorded || busy || lookupBusy}
                                        loading={busy}
                                        onClick={() =>
                                            modal.confirm({
                                                title: t('pages:accountFees.recover'),
                                                content: t('pages:accountFees.recoverMeaning'),
                                                okText: t('pages:accountFees.recover'),
                                                cancelText: t('pages:cancel'),
                                                onOk: () => sync(true),
                                            })
                                        }
                                    >
                                        {t('pages:accountFees.recover')}
                                    </Button>
                                </Space>
                            </Space>
                        }
                    />
                )}
                <Space wrap style={{width: '100%'}}>
                    {account.exchangeCode === 'BINANCE' && (
                        <label>
                            {t('accountFees.credentialType')}{' '}
                            <Select
                                aria-label={t('accountFees.credentialType')}
                                value={binanceCredentialType}
                                style={{width: 170}}
                                disabled={Boolean(scopedPending) || busy || lookupBusy}
                                options={(['BINANCE_HMAC', 'BINANCE_ED25519'] as const).map(
                                    (value) => ({
                                        value,
                                        label: t(`credentialManager.type_${value}`),
                                    }),
                                )}
                                onChange={setBinanceCredentialType}
                            />
                        </label>
                    )}
                    <label>
                        {t('pages:accountFees.symbol')}{' '}
                        <Select
                            showSearch
                            aria-label={t('pages:accountFees.symbol')}
                            style={{width: 210, maxWidth: '100%'}}
                            placeholder={t('pages:accountFees.chooseSymbol')}
                            value={selectedSymbol}
                            disabled={Boolean(scopedPending) || busy || lookupBusy}
                            loading={catalog.isFetching}
                            options={symbols.map((value) => ({value, label: value}))}
                            onChange={setSymbol}
                        />
                    </label>
                    <Button
                        disabled={!selectedSymbol || history.isFetching}
                        onClick={() => void history.refetch()}
                    >
                        {t('pages:accountFees.refresh')}
                    </Button>
                    <Button
                        type="primary"
                        loading={busy}
                        disabled={!canSync}
                        onClick={() =>
                            modal.confirm({
                                title: t('pages:accountFees.sync'),
                                content: t('pages:accountFees.syncMeaning', {
                                    account: account.accountAlias,
                                    symbol: selectedSymbol,
                                    credential: active?.credentialId,
                                }),
                                okText: t('pages:accountFees.sync'),
                                cancelText: t('pages:cancel'),
                                onOk: () => sync(),
                            })
                        }
                    >
                        {t('pages:accountFees.sync')}
                    </Button>
                </Space>
                {catalog.error && (
                    <Alert type="error" message={formatApiError(catalog.error as AppApiError)} />
                )}
                {credential.error && (
                    <Alert type="error" message={formatApiError(credential.error as AppApiError)} />
                )}
                {error && <Alert type="error" message={formatApiError(error)} />}
                {!supportedVenue ? (
                    <Alert type="info" message={t('pages:accountFees.unsupportedVenue')} />
                ) : (
                    credential.isSuccess &&
                    !active && <Alert type="info" message={t('pages:accountFees.noCredential')} />
                )}
                {account.exchangeCode === 'BINANCE' && account.tradeEnv === 'SIM' && (
                    <Alert
                        type="info"
                        showIcon
                        message={t('accountFees.binanceTestnetUnavailable')}
                    />
                )}
                {active && (
                    <Typography.Text type="secondary">
                        {t('accountFees.credential')} #{active.credentialId} ·{' '}
                        {t(`credentialManager.type_${active.credentialType}`)}
                    </Typography.Text>
                )}
                {history.error && (
                    <Alert
                        type="error"
                        message={formatApiError(history.error as AppApiError)}
                        action={
                            <Button onClick={() => void history.refetch()}>
                                {t('pages:accountFees.refresh')}
                            </Button>
                        }
                    />
                )}
                {history.isSuccess && !history.data.synchronizationAvailable && (
                    <Alert type="info" message={t('pages:accountFees.sourceDisabled')} />
                )}
                {selectedSymbol ? (
                    <Table
                        size="small"
                        columns={columns}
                        rowKey="observationId"
                        loading={history.isFetching}
                        dataSource={history.data?.items ?? []}
                        pagination={false}
                        scroll={{x: 650}}
                        locale={{
                            emptyText: history.isError
                                ? t('pages:accountFees.readFailed')
                                : t('pages:accountFees.noHistory'),
                        }}
                    />
                ) : (
                    <Empty description={t('pages:accountFees.chooseSymbol')} />
                )}
                <Space wrap>
                    <Button
                        disabled={!before || history.isFetching}
                        onClick={() => setBefore(undefined)}
                    >
                        {t('pages:accountFees.latest')}
                    </Button>
                    <Button
                        disabled={!history.data?.hasMore || history.isFetching}
                        onClick={() => setBefore(history.data?.nextBefore ?? undefined)}
                    >
                        {t('pages:accountFees.next')}
                    </Button>
                </Space>
                {selected && (
                    <Descriptions
                        size="small"
                        bordered
                        column={{xs: 1, md: 2}}
                        title={t('pages:accountFees.details')}
                    >
                        <Descriptions.Item label={t('pages:accountFees.symbol')}>
                            {selected.symbol} · {selected.exchangeCode} · {selected.tradeEnv}
                        </Descriptions.Item>
                        <Descriptions.Item label={t('pages:accountFees.credential')}>
                            #{selected.credentialId}
                        </Descriptions.Item>
                        <Descriptions.Item label={t('pages:accountFees.maker')}>
                            {rate(selected.makerCostRate)}
                        </Descriptions.Item>
                        <Descriptions.Item label={t('pages:accountFees.taker')}>
                            {rate(selected.takerCostRate)}
                        </Descriptions.Item>
                        <Descriptions.Item
                            label={
                                selected.providerTimeBasis === 'HTTP_RESPONSE_DATE'
                                    ? t('accountFees.providerResponseAt')
                                    : t('pages:accountFees.providerAt')
                            }
                        >
                            {selected.providerAt
                                ? formatDateTimeMilliseconds(selected.providerAt)
                                : t('accountFees.notProvided')}
                        </Descriptions.Item>
                        <Descriptions.Item label={t('pages:accountFees.observedAt')}>
                            {formatDateTimeMilliseconds(selected.observedAt)}
                        </Descriptions.Item>
                        <Descriptions.Item label={t('pages:accountFees.expiresAt')}>
                            {formatDateTimeMilliseconds(selected.expiresAt)}
                        </Descriptions.Item>
                        <Descriptions.Item label={t('pages:accountFees.tier')}>
                            {selected.tier ?? t('accountFees.notProvided')}
                        </Descriptions.Item>
                    </Descriptions>
                )}
                {selected?.commissionTerms && (
                    <CommissionTermsSummary terms={selected.commissionTerms} />
                )}
                <Collapse
                    size="small"
                    items={[
                        {
                            key: 'meaning',
                            label: t('pages:accountFees.meaning'),
                            children: (
                                <Typography.Paragraph>
                                    {t('pages:accountFees.signMeaning')}
                                </Typography.Paragraph>
                            ),
                        },
                        ...(selected
                            ? [
                                  {
                                      key: 'source',
                                      label: t('pages:accountFees.sourceDetails'),
                                      children: (
                                          <Descriptions size="small" column={1}>
                                              <Descriptions.Item
                                                  label={t('pages:accountFees.observation')}
                                              >
                                                  <Typography.Text copyable>
                                                      {selected.observationId}
                                                  </Typography.Text>
                                              </Descriptions.Item>
                                              <Descriptions.Item
                                                  label={t('pages:accountFees.operation')}
                                              >
                                                  <Typography.Text copyable>
                                                      {selected.operationId}
                                                  </Typography.Text>
                                              </Descriptions.Item>
                                              <Descriptions.Item
                                                  label={t('pages:accountFees.source')}
                                              >
                                                  {selected.source}
                                              </Descriptions.Item>
                                              <Descriptions.Item label={t('accountFees.timeBasis')}>
                                                  {selected.providerTimeBasis ??
                                                      'PROVIDER_FEE_TIMESTAMP'}
                                              </Descriptions.Item>
                                              <Descriptions.Item
                                                  label={t('pages:accountFees.digest')}
                                              >
                                                  <Typography.Text
                                                      style={{overflowWrap: 'anywhere'}}
                                                      copyable
                                                  >
                                                      {selected.sourceSha256}
                                                  </Typography.Text>
                                              </Descriptions.Item>
                                              {selected.commissionTerms ? (
                                                  <>
                                                      <Descriptions.Item
                                                          label={t('accountFees.nativeSymbol')}
                                                      >
                                                          {selected.commissionTerms.nativeSymbol}
                                                      </Descriptions.Item>
                                                      <Descriptions.Item
                                                          label={t('accountFees.responseDigest')}
                                                      >
                                                          <Typography.Text
                                                              style={{overflowWrap: 'anywhere'}}
                                                              copyable
                                                          >
                                                              {
                                                                  selected.commissionTerms
                                                                      .responseSha256
                                                              }
                                                          </Typography.Text>
                                                      </Descriptions.Item>
                                                      <Descriptions.Item
                                                          label={t('accountFees.exactComponents')}
                                                      >
                                                          <pre
                                                              style={{
                                                                  whiteSpace: 'pre-wrap',
                                                                  overflowWrap: 'anywhere',
                                                              }}
                                                          >
                                                              {JSON.stringify(
                                                                  selected.commissionTerms,
                                                                  null,
                                                                  2,
                                                              )}
                                                          </pre>
                                                      </Descriptions.Item>
                                                  </>
                                              ) : (
                                                  <>
                                                      <Descriptions.Item
                                                          label={t(
                                                              'pages:accountFees.originalMaker',
                                                          )}
                                                      >
                                                          {selected.makerProviderRate}
                                                      </Descriptions.Item>
                                                      <Descriptions.Item
                                                          label={t(
                                                              'pages:accountFees.originalTaker',
                                                          )}
                                                      >
                                                          {selected.takerProviderRate}
                                                      </Descriptions.Item>
                                                      <Descriptions.Item
                                                          label={t('pages:accountFees.exactCost')}
                                                      >
                                                          {selected.makerCostRate} /{' '}
                                                          {selected.takerCostRate}
                                                      </Descriptions.Item>
                                                  </>
                                              )}
                                          </Descriptions>
                                      ),
                                  },
                              ]
                            : []),
                    ]}
                />
            </Space>
        </Drawer>
    );
}
