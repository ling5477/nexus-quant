package com.guidinglight.nexusquant.news.api.web;

import com.guidinglight.nexusquant.news.application.PublicNewsService;
import com.guidinglight.nexusquant.news.domain.NewsFeedSnapshot;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 沿用JWT GET守卫，仅返回公开标题元数据，不提供任意URL代理或文章正文。 */
@RestController
@RequestMapping("/api/news")
public class PublicNewsController {
    private final PublicNewsService news;

    public PublicNewsController(PublicNewsService news) {
        this.news = news;
    }

    @GetMapping
    public NewsFeedSnapshot read() {
        return news.read();
    }
}
