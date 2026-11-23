import {SearchOutlined} from '@ant-design/icons';
import {Alert, Button, Input, List, Modal, Select, Space, Spin, Tag, Typography} from 'antd';
import {useQuery} from '@tanstack/react-query';
import {useEffect, useRef, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {apiClient} from '@/api/client';
import {formatApiError} from '@/api/errors';
import {accountsApi} from '@/features/accounts/api/accounts';
import {useAuthStore} from '@/store/auth-store';
import {useAccountContextStore} from '@/store/account-context-store';
import type {AppApiError} from '@/types/api';

interface Hit {
    kind: string;
    id: string;
    title: string;
    status: string;
    strategyCode: string | null;
    exchangeAccountId: number | null;
    paperRunId: string | null;
    tradeEnv: string;
}
interface Results {
    items: Hit[];
    hasMore: boolean;
    offset: number;
}
const kinds = ['ALL', 'ACCOUNT', 'STRATEGY', 'VERSION', 'RUN', 'SIM', 'ORDER'];

/** 只读搜索用当前用户范围的请求与缓存，点击结果只定位对象，不修改默认账户或执行交易。 */
export function ObjectSearch() {
    const {t} = useTranslation();
    const navigate = useNavigate();
    const userId = useAuthStore((s) => s.currentUser?.userId);
    const setSelectedAccount = useAccountContextStore((s) => s.setSelectedAccount);
    const [open, setOpen] = useState(false),
        [text, setText] = useState(''),
        [debounced, setDebounced] = useState('');
    const [restoreTriggerFocus, setRestoreTriggerFocus] = useState(true);
    const [kind, setKind] = useState('ALL'),
        [offset, setOffset] = useState(0),
        [navigationError, setNavigationError] = useState('');
    const navigationSequence = useRef(0);
    useEffect(() => {
        const id = window.setTimeout(() => setDebounced(text.trim()), 250);
        return () => clearTimeout(id);
    }, [text]);
    const ready = text.trim() === debounced && debounced.length >= 2 && debounced.length <= 80;
    const query = useQuery<Results, AppApiError>({
        queryKey: ['object-search', userId, debounced, kind, offset],
        queryFn: async ({signal}) =>
            (
                await apiClient.get<Results>('/search', {
                    params: {q: debounced, kind, offset},
                    signal,
                })
            ).data,
        enabled: Boolean(open && userId && ready),
    });
    const close = () => {
        navigationSequence.current++;
        setOpen(false);
        setNavigationError('');
    };
    const locate = async (hit: Hit) => {
        const sequence = ++navigationSequence.current,
            identity = userId;
        setNavigationError('');
        try {
            if (hit.kind === 'ORDER' && hit.exchangeAccountId && !hit.paperRunId) {
                const account = await accountsApi.detail(hit.exchangeAccountId);
                if (
                    sequence !== navigationSequence.current ||
                    useAuthStore.getState().currentUser?.userId !== identity
                )
                    return;
                if (account.status !== 'ACTIVE') {
                    setNavigationError(t('objectSearch.unavailableAccount'));
                    return;
                }
                setSelectedAccount(account);
            }
            const params = new URLSearchParams();
            let path: string;
            switch (hit.kind) {
                case 'ACCOUNT':
                    path = '/accounts';
                    params.set('accountId', hit.id);
                    break;
                case 'STRATEGY':
                case 'VERSION':
                    path = '/strategies';
                    params.set('strategyCode', hit.strategyCode!);
                    if (hit.kind === 'VERSION') params.set('versionId', hit.id);
                    break;
                case 'RUN':
                    path = '/runs';
                    params.set('runId', hit.id);
                    break;
                case 'SIM':
                    path = '/paper-trading/runs';
                    params.set('paperRunId', hit.id);
                    break;
                case 'ORDER':
                    if (hit.paperRunId) {
                        path = '/paper-trading/runs';
                        params.set('paperRunId', hit.paperRunId);
                        params.set('factTab', 'orders');
                    } else {
                        path = '/trading';
                        params.set('exchangeAccountId', String(hit.exchangeAccountId));
                    }
                    params.set('orderId', hit.id);
                    break;
                default:
                    return;
            }
            if (
                sequence !== navigationSequence.current ||
                useAuthStore.getState().currentUser?.userId !== identity
            )
                return;
            // 新详情抽屉接管焦点；旧搜索框退出时不能再把焦点抢回顶栏，否则Esc和键盘导航失效。
            setRestoreTriggerFocus(false);
            close();
            navigate(path + '?' + params.toString());
        } catch (error) {
            if (sequence === navigationSequence.current)
                setNavigationError(formatApiError(error as AppApiError));
        }
    };
    return (
        <>
            <Button
                className="app-shell__object-search"
                icon={<SearchOutlined />}
                aria-label={t('objectSearch.open')}
                onClick={() => {
                    // 普通取消仍回到搜索入口，只有成功定位时由目标页面接管焦点。
                    setRestoreTriggerFocus(true);
                    setOpen(true);
                    setOffset(0);
                }}
            >
                <span>{t('objectSearch.open')}</span>
            </Button>
            <Modal
                title={t('objectSearch.title')}
                open={open}
                onCancel={close}
                focusTriggerAfterClose={restoreTriggerFocus}
                footer={null}
                width={760}
                destroyOnHidden
            >
                <Space.Compact style={{width: '100%'}}>
                    <Select
                        aria-label={t('objectSearch.kind')}
                        value={kind}
                        onChange={(value) => {
                            setKind(value);
                            setOffset(0);
                        }}
                        // 类型选项少，使用内容宽度，避免弹窗缩放动画把匹配宽度冻结成窄条。
                        popupMatchSelectWidth={false}
                        style={{width: 150, flexShrink: 0}}
                        options={kinds.map((value) => ({
                            value,
                            label: t('objectSearch.kinds.' + value),
                        }))}
                    />
                    <Input
                        autoFocus
                        aria-label={t('objectSearch.query')}
                        maxLength={80}
                        style={{flex: 1, minWidth: 0}}
                        value={text}
                        placeholder={t('objectSearch.placeholder')}
                        onChange={(e) => {
                            setText(e.target.value);
                            setOffset(0);
                        }}
                        allowClear
                    />
                </Space.Compact>
                <Typography.Paragraph type="secondary" style={{margin: '10px 0'}}>
                    {t('objectSearch.scope')}
                </Typography.Paragraph>
                {navigationError && <Alert type="error" message={navigationError} />}
                {!ready ? (
                    <Typography.Text type="secondary">{t('objectSearch.minimum')}</Typography.Text>
                ) : query.error ? (
                    <Alert type="error" message={formatApiError(query.error)} />
                ) : query.isPending ? (
                    <Spin />
                ) : (
                    <List
                        dataSource={query.data?.items ?? []}
                        locale={{emptyText: t('objectSearch.empty')}}
                        renderItem={(hit) => (
                            <List.Item key={hit.kind + hit.id}>
                                <Button
                                    block
                                    type="text"
                                    className="object-search-result"
                                    onClick={() => void locate(hit)}
                                >
                                    <Space wrap>
                                        <Tag>{t('objectSearch.kinds.' + hit.kind)}</Tag>
                                        <strong>{hit.title}</strong>
                                        <Tag>{hit.tradeEnv}</Tag>
                                    </Space>
                                    <small>
                                        {hit.id} · {hit.status}
                                    </small>
                                </Button>
                            </List.Item>
                        )}
                    />
                )}
                <Space style={{display: 'flex', justifyContent: 'flex-end', marginTop: 12}}>
                    <Button disabled={offset === 0} onClick={() => setOffset(offset - 20)}>
                        {t('objectSearch.previous')}
                    </Button>
                    <span>{t('objectSearch.page', {page: offset / 20 + 1})}</span>
                    <Button
                        disabled={!ready || !query.data?.hasMore}
                        onClick={() => setOffset(offset + 20)}
                    >
                        {t('objectSearch.next')}
                    </Button>
                </Space>
            </Modal>
        </>
    );
}
