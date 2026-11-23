package com.guidinglight.nexusquant.app.observability;

import java.time.Instant;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 更新后由正式前端独立读取现有Actuator聚合状态；不暴露组件、连接信息或配置详情。 */
@RestController
public final class RuntimeHealthController {
  private final HealthEndpoint health;

  public RuntimeHealthController(HealthEndpoint health) {
    this.health = health;
  }

  /** 只有明确UP才作为健康证据；未知及失败不能被成功HTTP或安装回执替代。 */
  @GetMapping("/api/system/health")
  public ResponseEntity<Snapshot> read() {
    String status = health.health().getStatus().getCode();
    return ResponseEntity.status("UP".equals(status) ? 200 : 503)
        .body(new Snapshot(status, Instant.now()));
  }

  public record Snapshot(String status, Instant checkedAt) {}
}
