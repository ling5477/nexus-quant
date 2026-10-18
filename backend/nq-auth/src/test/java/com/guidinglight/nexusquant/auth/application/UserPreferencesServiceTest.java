package com.guidinglight.nexusquant.auth.application;

import static org.junit.jupiter.api.Assertions.*;

import com.guidinglight.nexusquant.auth.application.service.UserPreferencesService;
import com.guidinglight.nexusquant.auth.domain.UserPreferences;
import com.guidinglight.nexusquant.auth.domain.WatchlistSymbol;
import com.guidinglight.nexusquant.auth.domain.port.UserPreferencesRepository;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 内存端口验证偏好边界、失效市场移除与并发版本语义；不替代正式API或PostgreSQL持久化证明。 */
class UserPreferencesServiceTest {
    @Test
    void defaultsDoNotInventMarketsOrSuppressDisclosures() {
        UserPreferences defaults = new UserPreferencesService(new EmptyRepository()).read(12);
        assertEquals(0, defaults.version());
        assertFalse(defaults.hideLoginDisclosures());
        assertNull(defaults.defaultMarket());
        assertTrue(defaults.watchlist().isEmpty());
    }

    @Test
    void malformedDuplicateAndUnboundedWatchlistsAreRejected() {
        WatchlistSymbol market = new WatchlistSymbol("OKX", "SPOT", "BTC-USDT");
        assertThrows(
                IllegalArgumentException.class,
                () -> new WatchlistSymbol("OKX", "SWAP", "BTC-USDT"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new UserPreferences(-1, "zh-CN", false, null, List.of(), null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new UserPreferences(0, "invalid", false, null, List.of(), null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new UserPreferences(0, "zh-CN", false, null, List.of(market, market), null));
        List<WatchlistSymbol> many = new ArrayList<>();
        for (int i = 0; i < 21; i++)
            many.add(new WatchlistSymbol("OKX", "SPOT", "ASSET" + i + "-USDT"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new UserPreferences(0, "zh-CN", false, null, many, null));
    }

    @Test
    void futureNotificationReadTimeCannotHideUnseenEvents() {
        UserPreferencesService service = new UserPreferencesService(new EmptyRepository());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.save(
                                12,
                                new UserPreferences(
                                        0,
                                        "zh-CN",
                                        false,
                                        null,
                                        List.of(),
                                        Instant.now().plusSeconds(60))));
        assertThrows(IllegalArgumentException.class, () -> service.read(0));
    }

    @Test
    void unsupportedAdditionsFailWithoutChangingSavedPreferences() {
        var repository = new SavedRepository();
        var service = new UserPreferencesService(repository, requested -> Set.of());
        var market = new WatchlistSymbol("OKX", "SPOT", "BTC-USDT");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.save(
                                12,
                                new UserPreferences(
                                        0, "zh-CN", false, market, List.of(market), null)));
        assertEquals(UserPreferences.defaults(), service.read(12));
    }

    @Test
    void staleSymbolsRemainRemovableAndSortableWithoutSilentDefaultSwitch() {
        var repository = new SavedRepository();
        var first = new WatchlistSymbol("OKX", "SPOT", "BTC-USDT");
        var second = new WatchlistSymbol("BINANCE", "SPOT", "ETH-USDT");
        var service = new UserPreferencesService(repository, requested -> new HashSet<>(requested));
        service.save(
                12, new UserPreferences(0, "zh-CN", false, first, List.of(first, second), null));
        var stale = new UserPreferencesService(repository, requested -> Set.of());
        stale.save(12, new UserPreferences(1, "zh-CN", false, first, List.of(second, first), null));
        assertEquals(List.of(second, first), stale.read(12).watchlist());
        assertEquals(first, stale.read(12).defaultMarket());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        stale.save(
                                12,
                                new UserPreferences(
                                        2, "zh-CN", false, second, List.of(second), null)));
        stale.save(12, new UserPreferences(2, "zh-CN", false, null, List.of(), null));
        assertNull(stale.read(12).defaultMarket());
    }

    private static final class SavedRepository implements UserPreferencesRepository {
        private UserPreferences saved;

        public Optional<UserPreferences> find(long userId) {
            return Optional.ofNullable(saved);
        }

        public Optional<UserPreferences> save(long userId, UserPreferences p) {
            if ((saved == null ? 0 : saved.version()) != p.version()) return Optional.empty();
            saved =
                    new UserPreferences(
                            p.version() + 1,
                            p.locale(),
                            p.hideLoginDisclosures(),
                            p.defaultMarket(),
                            p.watchlist(),
                            p.notificationsReadAt());
            return Optional.of(saved);
        }
    }

    private static final class EmptyRepository implements UserPreferencesRepository {
        public Optional<UserPreferences> find(long userId) {
            return Optional.empty();
        }

        public Optional<UserPreferences> save(long userId, UserPreferences p) {
            return Optional.empty();
        }
    }
}
