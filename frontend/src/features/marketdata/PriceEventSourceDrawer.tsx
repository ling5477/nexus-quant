import {Alert, Button, Collapse, Descriptions, Drawer, Space, Typography} from 'antd';
import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {formatApiError} from '@/api/errors';
import {useAuthStore} from '@/store/auth-store';
import {formatDateTime} from '@/utils/formatters';
import {NqAmountText} from '@/components/nq/NqNumericText';
import type {AppApiError} from '@/types/api';
import type {MarketdataBar, PriceEventSourceSubmission} from '@/types/marketdata';
import {
    clearPriceEventPending,
    loadPriceEventPending,
    persistPriceEventPending,
    priceEventPendingKey,
    priceEventSourceApi,
    previewPriceEventDocument,
} from './price-event-source';

/** 所选bar及待确认请求保持固定；网络未知只核对同一完整请求，不把404或重新选择文件当未写入证明。 */
export function PriceEventSourceDrawer({
    bar,
    onClose,
    onSaved,
}: {
    bar: MarketdataBar | null;
    onClose: () => void;
    onSaved: (barId: string, sha: string) => void;
}) {
    const userId = useAuthStore((s) => s.currentUser?.userId);
    return (
        <Drawer
            open={Boolean(bar)}
            onClose={onClose}
            title={<PriceEventTitle />}
            width={680}
            destroyOnClose
        >
            {bar && (
                <SourceContents
                    key={`${userId}:${bar.barId}:${bar.barIdentitySha256}`}
                    bar={bar}
                    onSaved={onSaved}
                />
            )}
        </Drawer>
    );
}
function PriceEventTitle() {
    const {t} = useTranslation();
    return t('priceEventTimes.title');
}
function SourceContents({
    bar,
    onSaved,
}: {
    bar: MarketdataBar;
    onSaved: (barId: string, sha: string) => void;
}) {
    const {t} = useTranslation();
    const user = useAuthStore((s) => s.currentUser);
    const key = user && user.userId > 0 ? priceEventPendingKey(user.userId) : null;
    const [pending, setPending] = useState<PriceEventSourceSubmission | null>(() => {
        try {
            return key ? loadPriceEventPending(localStorage.getItem(key)) : null;
        } catch {
            return null;
        }
    });
    const [file, setFile] = useState<{
        name: string;
        text: string;
        preview: ReturnType<typeof previewPriceEventDocument>;
    } | null>(null);
    const [busy, setBusy] = useState(false);
    const [note, setNote] = useState<string | null>(pending ? 'unknown' : null);
    const [error, setError] = useState<string | null>(null);
    const writable = Boolean(
        user &&
            user.userId > 0 &&
            user.roles.some((role) => role === 'ADMIN' || role === 'OPERATOR') &&
            bar.barId &&
            bar.barIdentitySha256 &&
            bar.qualityStatus === 'OK' &&
            Date.parse(bar.closeTime) <= Date.now(),
    );
    const source = bar.priceEventTimes;
    const otherPending =
        pending &&
        (pending.barId !== bar.barId || pending.expectedBarSha256 !== bar.barIdentitySha256);
    const pendingDocument = pending ? previewPriceEventDocument(pending.documentJson) : null;
    const timeKeys = ['openPriceAt', 'highPriceAt', 'lowPriceAt', 'closePriceAt'] as const;
    const record = async (request: PriceEventSourceSubmission) => {
        if (!key) throw Error('storage unavailable');
        await persistPriceEventPending(key, request);
        setPending(request);
    };
    const release = async (request: PriceEventSourceSubmission) => {
        if (!key) return;
        await clearPriceEventPending(key, request);
        // 锁内清理后再读取当前槽；仍存在的新请求继续占用，不能因旧结果到达而允许覆盖。
        setPending(loadPriceEventPending(localStorage.getItem(key)));
    };
    const confirmed = async (request: PriceEventSourceSubmission) => {
        try {
            await release(request);
        } catch {
            setError(t('priceEventTimes.storageUnavailable'));
        }
        setNote('saved');
        onSaved(request.barId, request.expectedBarSha256);
    };
    const choose = async (selected: File | undefined) => {
        if (!selected) return;
        setError(null);
        try {
            if (selected.size > 32768 || selected.name.length > 128) throw Error('invalid source');
            const text = (await selected.text()).replace(/^\uFEFF/, '');
            const preview = previewPriceEventDocument(text);
            if (
                ['exchangeCode', 'marketType', 'symbol', 'interval'].some(
                    (key) => preview[key as 'exchangeCode'] !== bar[key as 'exchangeCode'],
                )
            )
                throw Error('source scope mismatch');
            setFile({name: selected.name, text, preview});
            setNote(null);
        } catch {
            setFile(null);
            setError(t('priceEventTimes.invalidDocument'));
        }
    };
    const save = async () => {
        if (!file || !writable || pending || busy) return;
        const request: PriceEventSourceSubmission = {
            sourceId: crypto.randomUUID(),
            barId: bar.barId!,
            expectedBarSha256: bar.barIdentitySha256!,
            sourceName: file.name,
            documentJson: file.text,
        };
        try {
            await record(request);
        } catch {
            setError(t('priceEventTimes.storageUnavailable'));
            return;
        }
        setBusy(true);
        setError(null);
        try {
            await priceEventSourceApi.submit(request);
            await confirmed(request);
        } catch (caught) {
            const failure = caught as AppApiError;
            setError(formatApiError(failure));
            if ([400, 401, 403, 404, 409, 413, 422].includes(failure.status)) {
                try {
                    await release(request);
                } catch {
                    setError(t('priceEventTimes.storageUnavailable'));
                }
                setNote('rejected');
            } else setNote('unknown');
        } finally {
            setBusy(false);
        }
    };
    const verify = async () => {
        if (!pending || busy) return;
        const request = pending;
        setBusy(true);
        setError(null);
        try {
            const result = await priceEventSourceApi.verify(request);
            if (result.status === 'MATCH_FOUND') await confirmed(request);
            else setNote(result.status === 'NOT_FOUND' ? 'notFound' : 'conflict');
        } catch (caught) {
            setError(formatApiError(caught as AppApiError));
            setNote('unknown');
        } finally {
            setBusy(false);
        }
    };
    return (
        <Space
            direction="vertical"
            size={12}
            style={{width: '100%', minWidth: 0}}
            data-testid="price-event-source-drawer"
        >
            <Typography.Text strong>
                {bar.exchangeCode} · {bar.symbol} · {bar.interval}
            </Typography.Text>
            <Descriptions size="small" column={1}>
                <Descriptions.Item label={t('priceEventTimes.candleRange')}>
                    {formatDateTime(bar.openTime)} — {formatDateTime(bar.closeTime)}
                </Descriptions.Item>
                <Descriptions.Item label={t('priceEventTimes.status')}>
                    {t(
                        source?.status === 'SOURCE_PROVIDED'
                            ? 'priceEventTimes.provided'
                            : 'priceEventTimes.missing',
                    )}
                </Descriptions.Item>
                {timeKeys.map((key) => (
                    <Descriptions.Item key={key} label={t(`priceEventTimes.${key}`)}>
                        {source?.[key] ? (
                            <div
                                style={{
                                    display: 'flex',
                                    flexDirection: 'column',
                                    gap: 4,
                                    minWidth: 0,
                                    overflowWrap: 'anywhere',
                                }}
                            >
                                <span>{formatDateTime(source[key])}</span>
                                <Typography.Text type="secondary" copyable>
                                    {source[key]}
                                </Typography.Text>
                            </div>
                        ) : (
                            '—'
                        )}
                    </Descriptions.Item>
                ))}
                {source?.sourceName && (
                    <Descriptions.Item label={t('priceEventTimes.sourceName')}>
                        {source.sourceName}
                    </Descriptions.Item>
                )}
                {source?.recordedAt && (
                    <Descriptions.Item label={t('priceEventTimes.recordedAt')}>
                        {formatDateTime(source.recordedAt)}
                    </Descriptions.Item>
                )}
            </Descriptions>
            <Alert type="info" message={t('priceEventTimes.boundary')} />
            {note && (
                <Alert
                    type={note === 'saved' ? 'success' : 'warning'}
                    message={t(`priceEventTimes.notes.${note}`)}
                />
            )}
            {otherPending && pendingDocument && (
                <Alert
                    type="warning"
                    message={t('priceEventTimes.otherPending')}
                    description={
                        <>
                            <div>
                                {pendingDocument.exchangeCode} · {pendingDocument.symbol} ·{' '}
                                {pendingDocument.interval}
                            </div>
                            <div>
                                {formatDateTime(pendingDocument.openTime)} —{' '}
                                {formatDateTime(pendingDocument.closeTime)}
                            </div>
                            <Typography.Text type="secondary">
                                {pending!.sourceName}
                            </Typography.Text>
                        </>
                    }
                />
            )}
            {error && <Alert type="error" message={error} />}
            {writable ? (
                <>
                    <Typography.Text strong>{t('priceEventTimes.importTitle')}</Typography.Text>
                    <Typography.Paragraph type="secondary" style={{margin: 0}}>
                        {t('priceEventTimes.importDescription')}
                    </Typography.Paragraph>
                    <input
                        type="file"
                        accept=".json,application/json"
                        aria-label={t('priceEventTimes.selectFile')}
                        disabled={busy || Boolean(pending)}
                        onChange={(event) => void choose(event.target.files?.[0])}
                    />
                    {file && (
                        <Descriptions size="small" column={1} title={file.name}>
                            {timeKeys.map((key) => (
                                <Descriptions.Item key={key} label={t(`priceEventTimes.${key}`)}>
                                    <Typography.Text copyable>{file.preview[key]}</Typography.Text>
                                </Descriptions.Item>
                            ))}
                            <Descriptions.Item label={t('priceEventTimes.sourcePrices')}>
                                <Space wrap>
                                    {(
                                        [
                                            'openPrice',
                                            'highPrice',
                                            'lowPrice',
                                            'closePrice',
                                        ] as const
                                    ).map((key) => (
                                        <NqAmountText key={key} exact value={file.preview[key]} />
                                    ))}
                                </Space>
                            </Descriptions.Item>
                        </Descriptions>
                    )}
                    <Space wrap>
                        <Button
                            type="primary"
                            disabled={!file || Boolean(pending)}
                            loading={busy}
                            onClick={() => void save()}
                        >
                            {t('priceEventTimes.save')}
                        </Button>
                        {pending && (
                            <Button loading={busy} onClick={() => void verify()}>
                                {t('priceEventTimes.verify')}
                            </Button>
                        )}
                    </Space>
                </>
            ) : (
                <Typography.Text type="secondary">{t('priceEventTimes.readonly')}</Typography.Text>
            )}
            <Collapse
                size="small"
                items={[
                    {
                        key: 'format',
                        label: t('priceEventTimes.formatTitle'),
                        children: (
                            <>
                                <Typography.Paragraph>
                                    {t('priceEventTimes.formatDescription')}
                                </Typography.Paragraph>
                                <pre style={{whiteSpace: 'pre-wrap', overflowWrap: 'anywhere'}}>
                                    {JSON.stringify(
                                        {
                                            exchangeCode: bar.exchangeCode,
                                            marketType: bar.marketType,
                                            symbol: bar.symbol,
                                            interval: bar.interval,
                                            openTime: bar.openTime,
                                            closeTime: bar.closeTime,
                                            openPrice: String(bar.openPrice),
                                            highPrice: String(bar.highPrice),
                                            lowPrice: String(bar.lowPrice),
                                            closePrice: String(bar.closePrice),
                                            openPriceAt: null,
                                            highPriceAt: null,
                                            lowPriceAt: null,
                                            closePriceAt: null,
                                        },
                                        null,
                                        2,
                                    )}
                                </pre>
                            </>
                        ),
                    },
                ]}
            />
        </Space>
    );
}
