package com.guidinglight.nexusquant.app.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;

/** 聚合状态读取不泄漏组件内容；DOWN和UNKNOWN始终保持非成功健康结果。 */
class RuntimeHealthControllerTest {
  @Test
  void exposesOnlyStatusAndObservationTime() {
    HealthEndpoint endpoint = mock(HealthEndpoint.class);
    when(endpoint.health()).thenReturn(Health.up().withDetail("private", "never-export").build());
    var response = new RuntimeHealthController(endpoint).read();
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody().status()).isEqualTo("UP");
    assertThat(response.getBody().checkedAt()).isNotNull();
    assertThat(response.getBody().toString()).doesNotContain("never-export");
  }

  @Test
  void unavailableAggregateIsNotHealthy() {
    HealthEndpoint endpoint = mock(HealthEndpoint.class);
    for (String state : new String[] {"DOWN", "UNKNOWN", "OUT_OF_SERVICE"}) {
      when(endpoint.health()).thenReturn(Health.status(state).build());
      var response = new RuntimeHealthController(endpoint).read();
      assertThat(response.getStatusCode().value()).isEqualTo(503);
      assertThat(response.getBody().status()).isEqualTo(state);
    }
  }
}
