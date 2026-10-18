package com.guidinglight.nexusquant.currency.infra;

import com.guidinglight.nexusquant.common.currency.CurrencyDefinition;
import com.guidinglight.nexusquant.common.currency.CurrencyRegistry;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/** 只读币种目录；不包含账户金额、汇率或用户写入入口。 */
public final class JdbcCurrencyRegistry implements CurrencyRegistry {
    private final JdbcTemplate jdbc;

    public JdbcCurrencyRegistry(JdbcTemplate template) {
        jdbc = new JdbcTemplate(template.getDataSource());
        jdbc.setQueryTimeout(5);
    }

    @Override
    public List<CurrencyDefinition> list() {
        var rows =
                jdbc.query(
                        "SELECT currency_code,currency_kind,display_name FROM currency_definitions"
                                + " ORDER BY currency_code LIMIT 1001",
                        (rs, row) ->
                                new CurrencyDefinition(
                                        rs.getString(1), rs.getString(2), rs.getString(3)));
        if (rows.size() > 1000) throw new IllegalStateException("CURRENCY_REGISTRY_LIMIT_EXCEEDED");
        return List.copyOf(rows);
    }
}
