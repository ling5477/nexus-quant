package com.guidinglight.nexusquant.notification.application;

import com.guidinglight.nexusquant.notification.domain.UserNotification;
import com.guidinglight.nexusquant.notification.domain.port.UserNotificationReadPort;
import java.time.Instant;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/** 每页50条、最多1000条；查询失败不作为无通知，也不修改告警或恢复状态。 */
public class UserNotificationService {
  private final UserNotificationReadPort source;

  public UserNotificationService(UserNotificationReadPort source) {
    this.source = source;
  }

  /** 每页50条并多读一条判断窗口；末页明确标记截断，检查时点不代表业务事件发生时点。 */
  @Transactional(readOnly = true, timeout = 5)
  public Page read(long owner, int offset) {
    if (owner <= 0 || offset < 0 || offset > 950 || offset % 50 != 0)
      throw new IllegalArgumentException("INVALID_NOTIFICATION_PAGE");
    var rows = source.latest(owner, offset, 51);
    return new Page(
        rows.stream().limit(50).toList(),
        offset,
        offset < 950 && rows.size() > 50,
        offset == 950 && rows.size() > 50,
        Instant.now(),
        "AUTHORIZED_USER_EVENTS");
  }

  public record Page(
      List<UserNotification> items,
      int offset,
      boolean hasMore,
      boolean windowLimited,
      Instant checkedAt,
      String scope) {}
}
