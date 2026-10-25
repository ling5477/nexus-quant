import {Alert, Button, Card, Empty, List, Space, Spin, Tag, Typography} from 'antd';
import {useQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {formatApiError} from '@/api/errors';
import {newsQueryKeys} from '@/api/query-keys';
import {formatDateTime} from '@/utils/formatters';
import type {AppApiError} from '@/types/api';
import {publicNewsApi, trustedNewsUrl} from './public-news';

/** 仅显示真实公开RSS标题元数据；失败保留带过期标记的旧记录，原文始终属于发布者。 */
export function PublicNewsCard() {
    const {t} = useTranslation('pages');
    const query = useQuery({
        queryKey: newsQueryKeys.feed(),
        queryFn: ({signal}) => publicNewsApi.read(signal),
        retry: false,
        refetchInterval: 60_000,
        refetchIntervalInBackground: false,
    });
    const snapshot = query.data;
    const stale =
        query.isError ||
        snapshot?.status === 'STALE' ||
        Boolean(snapshot?.receivedAt && Date.now() - Date.parse(snapshot.receivedAt) > 900_000);
    const articles = snapshot?.articles ?? [];
    const status = stale && articles.length ? 'STALE' : snapshot?.status;
    return (
        <Card
            className="page-section"
            variant="borderless"
            title={t('pages:news.title')}
            extra={
                <Button
                    size="small"
                    loading={query.isFetching}
                    onClick={() => void query.refetch()}
                >
                    {t('pages:news.refresh')}
                </Button>
            }
        >
            <Space direction="vertical" size={12} style={{width: '100%'}}>
                {query.isError && (
                    <Alert
                        type={articles.length ? 'warning' : 'error'}
                        showIcon
                        message={formatApiError(query.error as AppApiError)}
                        description={articles.length ? t('pages:news.lastKnown') : undefined}
                    />
                )}
                {query.isPending && (
                    <Spin>
                        <div style={{minHeight: 100}} />
                    </Spin>
                )}
                {snapshot && (
                    <>
                        <Space wrap>
                            <Tag>{snapshot.source}</Tag>
                            <Tag
                                color={
                                    status === 'AVAILABLE'
                                        ? 'blue'
                                        : status === 'STALE'
                                          ? 'orange'
                                          : 'default'
                                }
                            >
                                {t(`pages:news.status_${status}`)}
                            </Tag>
                        </Space>
                        {snapshot.receivedAt && (
                            <Typography.Text type="secondary">
                                {t('pages:news.receivedAt')}：{formatDateTime(snapshot.receivedAt)}
                            </Typography.Text>
                        )}
                        {snapshot.errorCode && !query.isError && (
                            <Alert
                                type="warning"
                                showIcon
                                message={t('pages:news.sourceFailure')}
                                description={
                                    articles.length ? t('pages:news.lastKnown') : undefined
                                }
                            />
                        )}
                        {articles.length ? (
                            <List
                                size="small"
                                dataSource={articles}
                                pagination={{
                                    pageSize: 5,
                                    hideOnSinglePage: true,
                                    showSizeChanger: false,
                                }}
                                renderItem={(article) => (
                                    <List.Item key={article.url}>
                                        <Space
                                            direction="vertical"
                                            size={4}
                                            style={{width: '100%'}}
                                        >
                                            {trustedNewsUrl(article.url) ? (
                                                <Typography.Link
                                                    href={trustedNewsUrl(article.url)!}
                                                    target="_blank"
                                                    rel="noopener noreferrer"
                                                >
                                                    {article.title}
                                                </Typography.Link>
                                            ) : (
                                                <Typography.Text>{article.title}</Typography.Text>
                                            )}
                                            <Typography.Text type="secondary">
                                                <time dateTime={article.publishedAt}>
                                                    {formatDateTime(article.publishedAt)}
                                                </time>{' '}
                                                · {snapshot.source}
                                            </Typography.Text>
                                        </Space>
                                    </List.Item>
                                )}
                            />
                        ) : (
                            <Empty
                                image={Empty.PRESENTED_IMAGE_SIMPLE}
                                description={t(`pages:news.status_${snapshot.status}`)}
                            />
                        )}
                        <Typography.Text type="secondary">
                            {t('pages:news.scope', {
                                limit: snapshot.maxArticles,
                                minutes: snapshot.refreshSeconds / 60,
                            })}
                        </Typography.Text>
                        {snapshot.itemsSkipped > 0 && (
                            <Typography.Text type="secondary">
                                {t('pages:news.partial', {count: snapshot.itemsSkipped})}
                            </Typography.Text>
                        )}
                    </>
                )}
            </Space>
        </Card>
    );
}
