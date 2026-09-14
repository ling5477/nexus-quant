package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.strategy.application.StrategyManualTriggerRequest;
import com.guidinglight.nexusquant.strategy.application.StrategyManualTriggerService;
import java.math.BigDecimal;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/** 测试 producer 只调用既有 canonical trigger；slot 重复按失败处理，绝不盲重发。 */
final class L6FormalTrigger {
    static String emit(ConfigurableApplicationContext context, String command) throws Exception {
        L6StorageCalibrationContract.runManifest();
        B0Fixture.require(command.matches("L6_EMIT [0-9]{1,4} -?[0-9]{1,19}"));
        String[] parts = command.split(" ");
        int slot = Integer.parseInt(parts[1]); long deadline = Long.parseLong(parts[2]);
        requireBeforeDeadline(System.nanoTime(), deadline);
        B0Fixture.require(slot >= 0 && slot < 3000);
        String request = String.format(java.util.Locale.ROOT, "l6p-%04d", slot);
        var jdbc = context.getBean(JdbcTemplate.class);
        B0Fixture.require(jdbc.queryForObject("SELECT count(*) FROM strategy_runs WHERE request_id=?", Integer.class, request) == 0);
        requireBeforeDeadline(System.nanoTime(), deadline);
        var result = context.getBean(StrategyManualTriggerService.class).trigger(new StrategyManualTriggerRequest(
                "l6-strategy-" + (slot % 2 + 1), request, "BTC-USDT", OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("0.1005"), new BigDecimal("100"), "l6-pacing-" + slot));
        B0Fixture.require(result.orderId() != null && !result.idempotentHit());
        return new ObjectMapper().writeValueAsString(new ObjectMapper().createObjectNode()
                .put("slotIndex", slot).put("logicalOrderId", result.orderId()).put("strategyRunId", result.strategyRunId()));
    }
    static void requireBeforeDeadline(long now, long deadline) {
        if (now >= deadline) throw new IllegalStateException("L6_ADMISSION_PHASE_CLOSED");
    }
}
