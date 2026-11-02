import {BellOutlined} from '@ant-design/icons';
import {
    Alert,
    App,
    Badge,
    Button,
    Drawer,
    Empty,
    List,
    Space,
    Spin,
    Tag,
    Tooltip,
    Typography,
} from 'antd';
import {useQuery} from '@tanstack/react-query';
import {useEffect, useState} from 'react';
import {Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {useAuthStore} from '@/store/auth-store';
import {useUserPreferences} from '@/features/preferences/hooks/useUserPreferences';
import {formatApiError} from '@/api/errors';
import {formatDateTime} from '@/utils/formatters';
import type {AppApiError} from '@/types/api';
import {notificationsApi} from './api/notifications';
import {notificationIsUnread, notificationSourcePath} from './read-state';
import './NotificationCenter.css';

/** 已读只保存用户阅读边界；进入来源详情后仍须独立完成业务确认或解决。 */
export function NotificationCenter() {
    const {t} = useTranslation();
    const {message} = App.useApp();
    const owner = useAuthStore((state) => state.currentUser?.userId);
    const [open, setOpen] = useState(false);
    const [offset, setOffset] = useState(0);
    const [visible, setVisible] = useState(document.visibilityState === 'visible');
    const preferences = useUserPreferences();
    useEffect(() => {
        const update = () => setVisible(document.visibilityState === 'visible');
        document.addEventListener('visibilitychange', update);
        return () => document.removeEventListener('visibilitychange', update);
    }, []);
    useEffect(() => {
        setOpen(false);
        setOffset(0);
    }, [owner]);
    const latest = useQuery({
        queryKey: ['user-notifications', owner, 0],
        queryFn: ({signal}) => notificationsApi.read(0, signal),
        enabled: Boolean(owner && visible),
        staleTime: 60000,
        refetchInterval: visible ? 60000 : false,
        refetchIntervalInBackground: false,
    });
    const page = useQuery({
        queryKey: ['user-notifications', owner, offset],
        queryFn: ({signal}) => notificationsApi.read(offset, signal),
        enabled: Boolean(owner && open && offset > 0 && visible),
        staleTime: 60000,
    });
    const active = offset === 0 ? latest : page;
    const readAt = preferences.query.data?.notificationsReadAt ?? null;
    const preferenceKnown = preferences.query.isSuccess && !preferences.query.isError;
    const unread =
        preferenceKnown && latest.isSuccess && !latest.isError
            ? latest.data.items.map((item) => notificationIsUnread(item.occurredAt, readAt))
            : null;
    const unreadKnown = unread !== null && !unread.includes(null);
    const unreadCount = unreadKnown ? unread.filter(Boolean).length : null;
    const cutoff = latest.data?.items[0]?.occurredAt;
    const saveStatus = preferences.save.error?.status ?? 0;
    const saveUnknown = saveStatus === 0 || saveStatus >= 500;
    const rereadPreference = async () => {
        const result = await preferences.query.refetch();
        if (result.isSuccess) preferences.save.reset();
    };
    const markRead = async () => {
        if (!cutoff || !preferenceKnown || !unreadKnown || !unreadCount) return;
        try {
            await preferences.save.mutateAsync({notificationsReadAt: cutoff});
            message.success(t('notifications.saved'));
        } catch (error) {
            message.error(formatApiError(error as AppApiError));
        }
    };
    return (
        <>
            <Tooltip
                title={
                    unreadCount === null
                        ? t('notifications.unknown')
                        : t('notifications.badge', {count: unreadCount})
                }
            >
                <Badge count={unreadCount ?? '?'} size="small" offset={[-3, 3]}>
                    <Button
                        type="text"
                        icon={<BellOutlined />}
                        aria-label={t('notifications.open')}
                        onClick={() => {
                            setOffset(0);
                            setOpen(true);
                        }}
                    />
                </Badge>
            </Tooltip>
            <Drawer
                title={t('notifications.title')}
                open={open}
                onClose={() => setOpen(false)}
                width="min(560px, 100vw)"
                className="nq-notifications"
            >
                <Space direction="vertical" size={16} style={{width: '100%'}}>
                    <Typography.Text type="secondary">{t('notifications.scope')}</Typography.Text>
                    <Space wrap>
                        <Button
                            loading={active.isFetching}
                            onClick={() => {
                                void active.refetch();
                            }}
                        >
                            {t('refresh')}
                        </Button>
                        <Button
                            disabled={
                                !unreadKnown ||
                                !unreadCount ||
                                !cutoff ||
                                preferences.save.isPending ||
                                preferences.save.isError ||
                                latest.isError
                            }
                            loading={preferences.save.isPending}
                            onClick={() => {
                                void markRead();
                            }}
                        >
                            {t('notifications.markRead')}
                        </Button>
                    </Space>
                    <Typography.Text type="secondary">
                        {t('notifications.readMeaning')}
                    </Typography.Text>
                    {cutoff && (
                        <Typography.Text type="secondary">
                            {t('notifications.through', {time: formatDateTime(cutoff)})}
                        </Typography.Text>
                    )}
                    {preferences.query.isError && (
                        <Alert
                            type="warning"
                            showIcon
                            message={t('notifications.preferencesUnknown')}
                            description={formatApiError(preferences.query.error)}
                            action={
                                <Button
                                    onClick={() => {
                                        void preferences.query.refetch();
                                    }}
                                >
                                    {t('retry')}
                                </Button>
                            }
                        />
                    )}
                    {preferences.save.isError && (
                        <Alert
                            type="warning"
                            showIcon
                            message={t(
                                saveUnknown
                                    ? 'notifications.saveUnknown'
                                    : 'notifications.saveRejected',
                            )}
                            description={formatApiError(preferences.save.error)}
                            action={
                                <Button
                                    onClick={() => {
                                        void rereadPreference();
                                    }}
                                >
                                    {t('notifications.reread')}
                                </Button>
                            }
                        />
                    )}
                    {active.isError && (
                        <Alert
                            type="error"
                            showIcon
                            message={active.data ? t('notifications.stale') : t('queryFailed')}
                            description={formatApiError(active.error as AppApiError)}
                        />
                    )}
                    {active.data && (
                        <Typography.Text type="secondary">
                            {t('notifications.checked', {
                                time: formatDateTime(active.data.checkedAt),
                            })}
                        </Typography.Text>
                    )}
                    {active.isPending ? (
                        <Spin />
                    ) : (
                        active.data && (
                            <List
                                dataSource={active.data.items}
                                locale={{
                                    emptyText: <Empty description={t('notifications.empty')} />,
                                }}
                                renderItem={(item) => (
                                    <List.Item className="nq-notifications__item">
                                        <Space
                                            direction="vertical"
                                            size={6}
                                            style={{width: '100%'}}
                                        >
                                            <Space wrap size={6}>
                                                <Tag
                                                    color={
                                                        item.severity === 'CRITICAL' ||
                                                        item.severity === 'HIGH'
                                                            ? 'red'
                                                            : 'blue'
                                                    }
                                                >
                                                    {t(`notifications.kind_${item.kind}`)}
                                                </Tag>
                                                {item.tradeEnv && <Tag>{item.tradeEnv}</Tag>}
                                                <Tag>
                                                    {item.runKind
                                                        ? t(`notifications.run_${item.runKind}`)
                                                        : t(
                                                              `notifications.source_${item.sourceType}`,
                                                          )}
                                                </Tag>
                                                {preferenceKnown &&
                                                    notificationIsUnread(
                                                        item.occurredAt,
                                                        readAt,
                                                    ) === true && (
                                                        <Tag color="gold">
                                                            {t('notifications.unread')}
                                                        </Tag>
                                                    )}
                                                <Typography.Text type="secondary">
                                                    {formatDateTime(item.occurredAt)}
                                                </Typography.Text>
                                            </Space>
                                            <Typography.Paragraph
                                                style={{margin: 0}}
                                                ellipsis={{
                                                    rows: 2,
                                                    expandable: true,
                                                    symbol: t('notifications.more'),
                                                }}
                                            >
                                                {item.kind === 'ALERT'
                                                    ? item.title
                                                    : item.kind === 'RECOVERY'
                                                      ? t(`notifications.recovery_${item.title}`, {
                                                            defaultValue: t(
                                                                'notifications.recoveryTitle',
                                                            ),
                                                        })
                                                      : item.kind === 'SHADOW_EVENT'
                                                        ? t(`notifications.shadow_${item.title}`, {
                                                              defaultValue: item.title,
                                                          })
                                                        : t(
                                                              item.kind === 'SYSTEM_HEALTH'
                                                                  ? 'notifications.systemHealth'
                                                                  : 'notifications.systemOperation',
                                                              {
                                                                  job: t(
                                                                      `notifications.jobs.${item.title}`,
                                                                      {defaultValue: item.title},
                                                                  ),
                                                              },
                                                          )}
                                            </Typography.Paragraph>
                                            <Space wrap>
                                                <Typography.Text>
                                                    {t(`notifications.status_${item.status}`, {
                                                        defaultValue: item.status,
                                                    })}
                                                </Typography.Text>
                                                <Link
                                                    to={notificationSourcePath(item)}
                                                    onClick={() => setOpen(false)}
                                                >
                                                    {t('notifications.source')}
                                                </Link>
                                            </Space>
                                            <Typography.Text
                                                type="secondary"
                                                className="nq-notifications__identity"
                                            >
                                                {item.sourceId ?? item.paperRunId}
                                            </Typography.Text>
                                        </Space>
                                    </List.Item>
                                )}
                            />
                        )
                    )}
                    {active.data && (
                        <Space wrap>
                            <Button
                                disabled={offset === 0 || active.isFetching}
                                onClick={() => setOffset((value) => Math.max(0, value - 50))}
                            >
                                {t('notifications.previous')}
                            </Button>
                            <Typography.Text>
                                {t('notifications.page', {page: offset / 50 + 1})}
                            </Typography.Text>
                            <Button
                                disabled={!active.data.hasMore || active.isFetching}
                                onClick={() => setOffset((value) => value + 50)}
                            >
                                {t('notifications.next')}
                            </Button>
                        </Space>
                    )}
                    {active.data?.windowLimited && (
                        <Alert type="info" message={t('notifications.limit')} />
                    )}
                </Space>
            </Drawer>
        </>
    );
}
