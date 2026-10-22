package com.guidinglight.nexusquant.app.research;

import com.guidinglight.nexusquant.app.research.BacktestEvaluationRequestService.Receipt;
import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 原请求查询只读且按当前数据库认证主体隔离；只读角色不能执行新的写操作。 */
@RestController
@RequestMapping("/api/backtest-evaluation-requests")
public final class BacktestEvaluationRequestController {
  private final BacktestEvaluationRequestService requests;
  private final GatewayAuthFacade auth;
  private final CurrentUserProfileService profiles;

  public BacktestEvaluationRequestController(
      BacktestEvaluationRequestService requests,
      GatewayAuthFacade auth,
      CurrentUserProfileService profiles) {
    this.requests = requests;
    this.auth = auth;
    this.profiles = profiles;
  }

  @PostMapping("/{requestId}")
  public Receipt submit(@PathVariable UUID requestId, @RequestParam String target) {
    AuthUserProfile actor = profile();
    boolean demo = actor.roles().contains("DEMO_USER")
        && com.guidinglight.nexusquant.common.tenant.TenantContext.requireCurrent().kind()
            == com.guidinglight.nexusquant.common.tenant.TenantKind.DEMO;
    if (!actor.roles().contains("ADMIN") && !actor.roles().contains("OPERATOR") && !demo)
      throw new AccessDeniedException("operator role required");
    return requests.submit(requestId, target, actor.userId());
  }

  @GetMapping("/{requestId}")
  public ResponseEntity<Receipt> find(@PathVariable UUID requestId) {
    return ResponseEntity.of(requests.find(requestId, profile().userId()));
  }

  private AuthUserProfile profile() {
    var actor =
        auth.currentUser()
            .flatMap(claims -> profiles.findByUsername(claims.username()))
            .orElseThrow(
                () -> new AuthenticationCredentialsNotFoundException("authentication required"));
    if (actor.userId() == null
        || actor.userId() <= 0
        || !actor.enabled()
        || actor.mustChangePassword())
      throw new AuthenticationCredentialsNotFoundException("authentication required");
    return actor;
  }
}
