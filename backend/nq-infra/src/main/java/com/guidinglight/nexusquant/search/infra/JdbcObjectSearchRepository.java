package com.guidinglight.nexusquant.search.infra;

import com.guidinglight.nexusquant.research.domain.port.ResearchFeeSourcePort;
import com.guidinglight.nexusquant.research.infra.ResearchFeeReadScope;
import com.guidinglight.nexusquant.search.domain.ObjectSearchHit;
import com.guidinglight.nexusquant.search.domain.ObjectSearchRepository;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/** 按每个对象的正式账户或创建身份过滤；不先全局搜索再在浏览器隐藏其他用户对象。 */
public final class JdbcObjectSearchRepository implements ObjectSearchRepository {
    private final JdbcTemplate jdbc;
    private final ResearchFeeReadScope feeScope =
            new ResearchFeeReadScope(ResearchFeeSourcePort.disabled());

    public JdbcObjectSearchRepository(JdbcTemplate jdbc) {
        this.jdbc = new JdbcTemplate(jdbc.getDataSource());
        this.jdbc.setQueryTimeout(5);
    }

    @Override
    public List<ObjectSearchHit> findOwned(
            long owner, String query, String kind, int offset, int limit) {
        if (limit < 1 || limit > 21 || offset < 0 || offset > 980)
            throw new IllegalArgumentException("search limit exceeded");
        String pattern = "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return jdbc.query(
                """
                WITH owned_accounts AS (SELECT * FROM exchange_accounts WHERE owner_user_id=?),
                owned_sim AS (
                    SELECT p.* FROM paper_trading_runs p LEFT JOIN backtest_publish_records published ON published.publish_record_id=p.publish_id
                    LEFT JOIN strategy_versions frozen ON frozen.strategy_version_id=coalesce(p.strategy_version_id,published.strategy_version_id)
                    WHERE (EXISTS(SELECT 1 FROM strategy_definitions source JOIN owned_accounts a
                        ON a.legacy_account_id=source.account_id AND a.trade_env=source.trade_env AND a.exchange_code=source.exchange_code
                        WHERE source.strategy_code=frozen.strategy_code OR source.strategy_id=published.target_strategy_definition_id
                            OR source.strategy_id=published.source_strategy_id)
                    OR (p.created_by<>'system' AND p.created_by=(SELECT username FROM users WHERE id=?)))
                """
                        + " AND "
                        + feeScope.backtest("published.backtest_run_id", owner)
                        + "\n"
                        + """
                ),
                candidates AS (
                    SELECT 'ACCOUNT' kind,a.exchange_account_id::text id,a.account_alias title,a.status,
                        NULL::text strategy_code,a.exchange_account_id,a.trade_env,NULL::text paper_run_id,a.updated_at
                    FROM owned_accounts a
                    UNION ALL
                    SELECT 'STRATEGY',s.strategy_id,s.strategy_name,CASE WHEN s.enabled THEN 'ENABLED' ELSE 'DISABLED' END,
                        s.strategy_code,a.exchange_account_id,s.trade_env,NULL,s.updated_at
                    FROM strategy_definitions s JOIN owned_accounts a ON a.legacy_account_id=s.account_id AND a.trade_env=s.trade_env AND a.exchange_code=s.exchange_code
                    UNION ALL
                    SELECT 'VERSION',v.strategy_version_id,v.version_name||' · v'||v.version,v.status,
                        v.strategy_code,a.exchange_account_id,s.trade_env,NULL,v.updated_at
                    FROM strategy_versions v JOIN strategy_definitions s ON s.strategy_code=v.strategy_code
                    JOIN owned_accounts a ON a.legacy_account_id=s.account_id AND a.trade_env=s.trade_env AND a.exchange_code=s.exchange_code
                    UNION ALL
                    SELECT 'RUN',r.strategy_run_id,s.strategy_name||' · '||r.strategy_run_id,r.status,
                        s.strategy_code,a.exchange_account_id,r.trade_env,p.paper_run_id,coalesce(r.finished_at,r.started_at)
                    FROM strategy_runs r JOIN strategy_definitions s ON s.strategy_id=r.strategy_id
                    LEFT JOIN owned_accounts a ON a.legacy_account_id=r.account_id AND a.trade_env=r.trade_env AND a.exchange_code=r.exchange_code
                    LEFT JOIN owned_sim p ON p.canonical_account_id=r.account_id AND p.trade_env='SIM' AND r.trade_env='SIM' AND r.exchange_code='PAPER'
                    WHERE a.exchange_account_id IS NOT NULL OR p.paper_run_id IS NOT NULL
                    UNION ALL
                    SELECT 'SIM',p.paper_run_id,p.symbol||' · '||p.paper_run_id,p.status,
                        NULL,NULL,p.trade_env,p.paper_run_id,p.updated_at FROM owned_sim p
                    UNION ALL
                    SELECT 'ORDER',o.order_id,o.symbol||' · '||o.order_id,o.status,
                        NULL,a.exchange_account_id,o.trade_env,p.paper_run_id,o.updated_at
                    FROM orders o LEFT JOIN owned_accounts a ON a.legacy_account_id=o.account_id AND a.trade_env=o.trade_env AND a.exchange_code=o.exchange_code
                    LEFT JOIN owned_sim p ON p.canonical_account_id=o.account_id AND p.trade_env='SIM' AND o.trade_env='SIM' AND o.exchange_code='PAPER'
                    WHERE a.exchange_account_id IS NOT NULL OR p.paper_run_id IS NOT NULL
                )
                SELECT * FROM candidates WHERE (?='ALL' OR kind=?)
                    AND (title ILIKE ? ESCAPE '!' OR id ILIKE ? ESCAPE '!' OR strategy_code ILIKE ? ESCAPE '!')
                ORDER BY updated_at DESC,kind,id LIMIT ? OFFSET ?
                """,
                (rs, row) ->
                        new ObjectSearchHit(
                                rs.getString("kind"),
                                rs.getString("id"),
                                rs.getString("title"),
                                rs.getString("status"),
                                rs.getString("strategy_code"),
                                rs.getObject("exchange_account_id", Long.class),
                                rs.getString("trade_env"),
                                rs.getString("paper_run_id"),
                                rs.getTimestamp("updated_at").toInstant()),
                owner,
                owner,
                kind,
                kind,
                pattern,
                pattern,
                pattern,
                limit,
                offset);
    }
}
