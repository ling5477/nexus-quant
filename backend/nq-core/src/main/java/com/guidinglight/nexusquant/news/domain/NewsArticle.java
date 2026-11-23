package com.guidinglight.nexusquant.news.domain;

import java.time.Instant;

/** 只保留发布者提供的标题、时点与原文定位，不保存或转发文章正文。 */
public record NewsArticle(String title, String url, Instant publishedAt) {}
