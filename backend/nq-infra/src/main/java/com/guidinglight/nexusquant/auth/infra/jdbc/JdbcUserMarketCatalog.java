package com.guidinglight.nexusquant.auth.infra.jdbc;

import com.guidinglight.nexusquant.auth.domain.WatchlistSymbol;
import com.guidinglight.nexusquant.auth.domain.port.UserMarketCatalog;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 读取市场数据模块已有目录；一次有界查询，不产生第二份标的事实。 */
public final class JdbcUserMarketCatalog implements UserMarketCatalog {
    private final JdbcTemplate jdbc;

    public JdbcUserMarketCatalog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<WatchlistSymbol> supported(List<WatchlistSymbol> requested) {
        if (requested.size() > 21)
            throw new IllegalArgumentException("market preference limit exceeded");
        if (requested.isEmpty()) return Set.of();
        List<Object> args = new ArrayList<>();
        String predicate =
                requested.stream()
                        .map(
                                symbol -> {
                                    args.add(symbol.exchangeCode());
                                    args.add(symbol.symbol());
                                    return "(exchange_code=? AND internal_symbol=?)";
                                })
                        .collect(Collectors.joining(" OR "));
        return new HashSet<>(
                jdbc.query(
                        "SELECT DISTINCT exchange_code, internal_symbol FROM instrument_catalog "
                                + "WHERE instrument_type='SPOT' AND status IN ('TRADING','LIVE','ACTIVE') AND ("
                                + predicate
                                + ")",
                        (rs, row) -> new WatchlistSymbol(rs.getString(1), "SPOT", rs.getString(2)),
                        args.toArray()));
    }
}
