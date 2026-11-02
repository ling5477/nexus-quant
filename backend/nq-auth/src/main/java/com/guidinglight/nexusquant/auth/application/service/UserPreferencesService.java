package com.guidinglight.nexusquant.auth.application.service;

import com.guidinglight.nexusquant.auth.domain.UserPreferences;
import com.guidinglight.nexusquant.auth.domain.WatchlistSymbol;
import com.guidinglight.nexusquant.auth.domain.port.UserMarketCatalog;
import com.guidinglight.nexusquant.auth.domain.port.UserPreferencesRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 保存用户个人偏好及正式目录内的自选；不授予交易权限，版本冲突留给调用方处理。 */
public final class UserPreferencesService {
    private final UserPreferencesRepository repository;
    private final UserMarketCatalog markets;

    public UserPreferencesService(UserPreferencesRepository repository) {
        this(repository, requested -> Set.of());
    }

    public UserPreferencesService(UserPreferencesRepository repository, UserMarketCatalog markets) {
        this.repository = Objects.requireNonNull(repository);
        this.markets = Objects.requireNonNull(markets);
    }

    /** 没有持久化偏好时返回产品默认值；纯读取不落盘、不同步标的。 */
    public UserPreferences read(long userId) {
        requireUser(userId);
        return repository.find(userId).orElseGet(UserPreferences::defaults);
    }

    /** 校验未来已读时点和新增市场，再按版本保存；空结果表示并发冲突，不静默覆盖另一会话。 */
    public Optional<UserPreferences> save(long userId, UserPreferences preferences) {
        requireUser(userId);
        Objects.requireNonNull(preferences);
        // 只校验新命令；已存事实不因宿主时钟校正而变为不可读取。
        if (preferences.notificationsReadAt() != null
                && preferences.notificationsReadAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("notification read time cannot be in the future");
        }
        UserPreferences previous = read(userId);
        // 失效自选仍可读取、移除或排序；新增和改选必须来自当前正式目录。
        var additions = new ArrayList<WatchlistSymbol>();
        preferences.watchlist().stream()
                .filter(item -> !previous.watchlist().contains(item))
                .forEach(additions::add);
        if (preferences.defaultMarket() != null
                && !Objects.equals(preferences.defaultMarket(), previous.defaultMarket())
                && !additions.contains(preferences.defaultMarket()))
            additions.add(preferences.defaultMarket());
        if (!markets.supported(additions).containsAll(additions)) {
            throw new IllegalArgumentException(
                    "market is unavailable in the current instrument catalog");
        }
        return repository.save(userId, Objects.requireNonNull(preferences));
    }

    private static void requireUser(long userId) {
        if (userId <= 0) throw new IllegalArgumentException("user identity is required");
    }
}
