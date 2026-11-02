package com.guidinglight.nexusquant.auth.infra.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.auth.domain.UserPreferences;
import com.guidinglight.nexusquant.auth.domain.WatchlistSymbol;
import com.guidinglight.nexusquant.auth.domain.port.UserPreferencesRepository;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 单条带版本条件的写入保证首次创建和已有记录更新均不会丢失并发变化。 */
public final class JdbcUserPreferencesRepository implements UserPreferencesRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcUserPreferencesRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Optional<UserPreferences> find(long userId) {
        List<UserPreferences> rows =
                jdbc.query(
                        "SELECT * FROM user_preferences WHERE user_id = ?",
                        (rs, row) -> read(rs),
                        userId);
        return rows.stream().findFirst();
    }

    /** 创建以主键排他，更新以用户及版本条件排他；未命中返回空，不通过先读后写覆盖并发变化。 */
    @Override
    public Optional<UserPreferences> save(long userId, UserPreferences p) {
        // 不先读再写：首次保存由主键排他，更新由行锁内的版本条件排他。
        if (p.version() == 0) {
            List<UserPreferences> inserted =
                    jdbc.query(
                            """
                INSERT INTO user_preferences (user_id, version, locale, hide_login_disclosures,
                    default_market, watchlist, notifications_read_at)
                VALUES (?, 1, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?)
                ON CONFLICT (user_id) DO NOTHING
                RETURNING *
                """,
                            (rs, row) -> read(rs),
                            userId,
                            p.locale(),
                            p.hideLoginDisclosures(),
                            json(p.defaultMarket()),
                            json(p.watchlist()),
                            p.notificationsReadAt() == null
                                    ? null
                                    : Timestamp.from(p.notificationsReadAt()));
            return inserted.stream().findFirst();
        }
        List<UserPreferences> rows =
                jdbc.query(
                        """
                UPDATE user_preferences SET version = version + 1, locale = ?,
                    hide_login_disclosures = ?, default_market = CAST(? AS jsonb),
                    watchlist = CAST(? AS jsonb), notifications_read_at = ?, updated_at = now()
                WHERE user_id = ? AND version = ? RETURNING *
                """,
                        (rs, row) -> read(rs),
                        p.locale(),
                        p.hideLoginDisclosures(),
                        json(p.defaultMarket()),
                        json(p.watchlist()),
                        p.notificationsReadAt() == null
                                ? null
                                : Timestamp.from(p.notificationsReadAt()),
                        userId,
                        p.version());
        return rows.stream().findFirst();
    }

    private UserPreferences read(ResultSet rs) throws SQLException {
        try {
            String market = rs.getString("default_market");
            Timestamp readAt = rs.getTimestamp("notifications_read_at");
            return new UserPreferences(
                    rs.getLong("version"),
                    rs.getString("locale"),
                    rs.getBoolean("hide_login_disclosures"),
                    market == null || market.equals("null")
                            ? null
                            : mapper.readValue(market, WatchlistSymbol.class),
                    mapper.readValue(
                            rs.getString("watchlist"),
                            new TypeReference<List<WatchlistSymbol>>() {}),
                    readAt == null ? null : readAt.toInstant());
        } catch (JsonProcessingException ex) {
            throw new SQLException("invalid stored user preferences", ex);
        }
    }

    private String json(Object value) {
        if (value == null) return null;
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("invalid preferences payload", ex);
        }
    }
}
