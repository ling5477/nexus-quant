package com.guidinglight.nexusquant.news.domain;

import java.time.Instant;
import java.util.List;

/** 保留来源、接收与检查时点以及跳过条数；缓存失败时旧文章必须标为过期，不能伪装本次拉取成功。 */
public record NewsFeedSnapshot(
        String source,
        String sourceUrl,
        String status,
        Instant sourceUpdatedAt,
        Instant receivedAt,
        Instant checkedAt,
        String errorCode,
        int itemsRead,
        int itemsSkipped,
        int maxArticles,
        int refreshSeconds,
        List<NewsArticle> articles) {
    public NewsFeedSnapshot {
        articles = List.copyOf(articles);
    }
}
