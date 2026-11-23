import {apiClient} from '@/api/client';

export interface WatchlistSymbol {
    exchangeCode: string;
    marketType: 'SPOT';
    symbol: string;
}

/** 用户偏好独立于交易权限；version用于并发冲突，通知已读时点与业务告警状态分别保存。 */
export interface UserPreferences {
    version: number;
    locale: 'zh-CN' | 'en-US';
    hideLoginDisclosures: boolean;
    defaultMarket: WatchlistSymbol | null;
    watchlist: WatchlistSymbol[];
    notificationsReadAt: string | null;
}

/** 请求绑定当前登录令牌；保存使用服务端版本约束，冲突时不得自动覆盖其他会话。 */
export const preferencesApi = {
    async read(token: string, signal?: AbortSignal): Promise<UserPreferences> {
        const {data} = await apiClient.get<UserPreferences>('/user/preferences', {
            signal,
            headers: {Authorization: `Bearer ${token}`},
        });
        return data;
    },
    async save(value: UserPreferences, token: string): Promise<UserPreferences> {
        const {data} = await apiClient.put<UserPreferences>('/user/preferences', value, {
            headers: {Authorization: `Bearer ${token}`},
        });
        return data;
    },
};
