package com.guidinglight.nexusquant.notification.api;

import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.notification.application.UserNotificationService;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 当前用户可见业务事件；系统任务摘要仅供管理员，已读不调用原业务ack/resolve或执行入口。 */
@RestController
@RequestMapping("/api/user/notifications")
public class UserNotificationController {
  private final UserNotificationService notifications;
  private final GatewayAuthFacade auth;
  private final CurrentUserProfileService users;

  public UserNotificationController(
      UserNotificationService notifications,
      GatewayAuthFacade auth,
      CurrentUserProfileService users) {
    this.notifications = notifications;
    this.auth = auth;
    this.users = users;
  }

  @GetMapping
  public UserNotificationService.Page read(@RequestParam(defaultValue = "0") int offset) {
    var user =
        auth.currentUser()
            .orElseThrow(
                () -> new AuthenticationCredentialsNotFoundException("authentication required"));
    long owner =
        users
            .findByUsername(user.username())
            .orElseThrow(
                () -> new AuthenticationCredentialsNotFoundException("authentication required"))
            .userId();
    return notifications.read(owner, offset);
  }
}
