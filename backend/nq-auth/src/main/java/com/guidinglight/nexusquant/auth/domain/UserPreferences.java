package com.guidinglight.nexusquant.auth.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;

/** 偏好属于认证用户；账户默认关系仍由账户模块唯一持有。 */
public record UserPreferences(
        long version,
        String locale,
        boolean hideLoginDisclosures,
        WatchlistSymbol defaultMarket,
        List<WatchlistSymbol> watchlist,
        Instant notificationsReadAt) {
    public UserPreferences {
        if (version < 0 || !("zh-CN".equals(locale) || "en-US".equals(locale))) {
            throw new IllegalArgumentException("invalid user preferences");
        }
        watchlist = watchlist == null ? List.of() : List.copyOf(watchlist);
        if (watchlist.size() > 20 || new HashSet<>(watchlist).size() != watchlist.size()) {
            throw new IllegalArgumentException("watchlist must contain at most 20 unique symbols");
        }
    }

    public static UserPreferences defaults() {
        return new UserPreferences(0, "zh-CN", false, null, List.of(), null);
    }
}
