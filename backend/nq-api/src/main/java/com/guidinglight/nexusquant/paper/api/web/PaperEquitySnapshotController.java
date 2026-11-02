package com.guidinglight.nexusquant.paper.api.web;

import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.paper.api.dto.EquityCurveSnapshotResponse;
import com.guidinglight.nexusquant.scheduler.paper.PaperEquitySnapshotService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仅按已保存估值事件生成派生快照；客户端不能指定资金、价格、币种或收益。 */
@Validated
@RestController
@ConditionalOnBean(PaperEquitySnapshotService.class)
@RequestMapping("/api/paper-trading/runs/{paperRunId}/equity-snapshots")
public class PaperEquitySnapshotController {
  private final PaperEquitySnapshotService snapshots;
  private final GatewayAuthFacade auth;
  private final CurrentUserProfileService profiles;

  public PaperEquitySnapshotController(
      PaperEquitySnapshotService snapshots,
      GatewayAuthFacade auth,
      CurrentUserProfileService profiles) {
    this.snapshots = snapshots;
    this.auth = auth;
    this.profiles = profiles;
  }

  @PostMapping
  public Result create(
      @PathVariable @NotBlank @Size(max = 64) String paperRunId,
      @Valid @RequestBody Request request) {
    var result =
        snapshots.generate(owner(), paperRunId, request.decisionId(), request.snapshotTime());
    return new Result(
        result.status(),
        result.reason(),
        result.snapshot() == null ? null : EquityCurveSnapshotResponse.from(result.snapshot()));
  }

  @GetMapping("/{snapshotId}")
  public ResponseEntity<EquityCurveSnapshotResponse> find(
      @PathVariable @NotBlank @Size(max = 64) String paperRunId,
      @PathVariable @NotBlank @Size(max = 64) String snapshotId) {
    return ResponseEntity.of(
        snapshots.find(owner(), paperRunId, snapshotId).map(EquityCurveSnapshotResponse::from));
  }

  private long owner() {
    return auth.currentUser()
        .flatMap(user -> profiles.findByUsername(user.username()))
        .orElseThrow(
            () -> new AuthenticationCredentialsNotFoundException("authentication required"))
        .userId();
  }

  public record Request(
      @NotBlank @Size(max = 64) String decisionId, @NotNull Instant snapshotTime) {}

  public record Result(String status, String reason, EquityCurveSnapshotResponse snapshot) {}
}
