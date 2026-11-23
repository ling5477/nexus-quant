package com.guidinglight.nexusquant.notification.domain.port;

import com.guidinglight.nexusquant.notification.domain.UserNotification;
import java.util.List;

/** 从已有SIM、Shadow和系统任务事实读取摘要；权限在合并前过滤，不创建第二套事件。 */
public interface UserNotificationReadPort {
  /** 读取用户有权查看的事件摘要；分页须有界，失败不能用空列表代替，也不能改变告警状态。 */
  List<UserNotification> latest(long ownerUserId, int offset, int limit);
}
