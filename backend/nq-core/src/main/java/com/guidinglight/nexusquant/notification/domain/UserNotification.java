package com.guidinglight.nexusquant.notification.domain;

import java.time.Instant;

/** 通知引用原业务对象；Shadow和系统任务没有Paper身份，不能用它们的编号冒充运行或交易环境。 */
public record UserNotification(
    String id,
    String kind,
    String paperRunId,
    String runKind,
    String tradeEnv,
    String title,
    String severity,
    String status,
    Instant occurredAt,
    String sourceType,
    String sourceId) {
  /** 原SIM调用者继续使用原对象身份；新增来源必须显式指定对应的正式对象类型。 */
  public UserNotification(
      String id,
      String kind,
      String paperRunId,
      String runKind,
      String tradeEnv,
      String title,
      String severity,
      String status,
      Instant occurredAt) {
    this(
        id,
        kind,
        paperRunId,
        runKind,
        tradeEnv,
        title,
        severity,
        status,
        occurredAt,
        "PAPER_RUN",
        paperRunId);
  }
}
