import {apiClient} from '@/api/client';

export interface NewsArticle {
    title: string;
    url: string;
    publishedAt: string;
}
export interface NewsFeedSnapshot {
    source: string;
    sourceUrl: string;
    status: 'AVAILABLE' | 'STALE' | 'DISABLED' | 'SOURCE_UNAVAILABLE' | 'BUSY';
    sourceUpdatedAt: string | null;
    receivedAt: string | null;
    checkedAt: string;
    errorCode: string | null;
    itemsRead: number;
    itemsSkipped: number;
    maxArticles: number;
    refreshSeconds: number;
    articles: NewsArticle[];
}
export const publicNewsApi = {
    async read(signal?: AbortSignal): Promise<NewsFeedSnapshot> {
        return (await apiClient.get<NewsFeedSnapshot>('/news', {signal})).data;
    },
};

/** 发布者链接必须使用固定HTTPS主机；不把外部源的任意地址作为可执行跳转。 */
export function trustedNewsUrl(value: string): string | null {
    try {
        const url = new URL(value);
        return url.protocol === 'https:' &&
            url.hostname === 'www.coindesk.com' &&
            !url.username &&
            !url.password &&
            (!url.port || url.port === '443')
            ? url.href
            : null;
    } catch {
        return null;
    }
}
