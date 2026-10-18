package com.guidinglight.nexusquant.app.research;

import com.guidinglight.nexusquant.research.application.BacktestPublishService;
import com.guidinglight.nexusquant.research.infra.ResearchFeeReadScope;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 版本发布下的真实SIM身份窗口；沿用正式费用读取范围，不推断定义启用即运行。 */
@RestController
public class PublishedSimRelationController {
    private final JdbcTemplate jdbc;
    private final ResearchFeeReadScope scope;
    private final BacktestPublishService publications;

    public PublishedSimRelationController(
            JdbcTemplate jdbc, ResearchFeeReadScope scope, BacktestPublishService publications) {
        this.jdbc = jdbc;
        this.scope = scope;
        this.publications = publications;
    }

    @GetMapping("/api/publishes/{publishId}/sim-runs/page")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public Page page(
            @PathVariable String publishId,
            @RequestParam(defaultValue = "5") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        if (limit < 1 || limit > 50 || offset < 0)
            throw new IllegalArgumentException("invalid SIM relation window");
        // 先走正式发布详情的权限与不存在合同，再读取同一来源的身份，不返回资金或快照副本。
        publications.getByPublishRecordId(publishId);
        String where = " WHERE publish_id=? AND " + scope.paper();
        long total =
                jdbc.queryForObject(
                        "SELECT count(*) FROM paper_trading_runs" + where, Long.class, publishId);
        var items =
                jdbc.query(
                        "SELECT paper_run_id,publish_id,strategy_version_id,status FROM"
                            + " paper_trading_runs"
                                + where
                                + " ORDER BY created_at DESC,paper_run_id DESC LIMIT ? OFFSET ?",
                        (row, index) ->
                                new Item(
                                        row.getString(1),
                                        row.getString(2),
                                        row.getString(3),
                                        row.getString(4)),
                        publishId,
                        limit,
                        offset);
        return new Page(items, total, limit, offset);
    }

    public record Item(
            String paperRunId, String publishId, String strategyVersionId, String status) {}

    public record Page(List<Item> items, long total, int limit, int offset) {}
}
