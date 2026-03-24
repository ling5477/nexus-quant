package com.guidinglight.nexusquant.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.core.model.StrategyDefinition;
import com.guidinglight.nexusquant.core.model.StrategySchedule;
import com.guidinglight.nexusquant.core.service.port.StrategyDefinitionRepository;
import com.guidinglight.nexusquant.core.service.port.StrategyTriggerGateway;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

/**
 * StrategyScheduleScanService 实现 GateE-2.1 的最小 schedule scan / dispatch 入口。
 */
@Service
public class StrategyScheduleScanService {

    private final StrategyScheduleService strategyScheduleService;
    private final StrategyDefinitionRepository strategyDefinitionRepository;
    private final StrategyTriggerGateway strategyTriggerGateway;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public StrategyScheduleScanService(
            StrategyScheduleService strategyScheduleService,
            StrategyDefinitionRepository strategyDefinitionRepository,
            StrategyTriggerGateway strategyTriggerGateway,
            ObjectMapper objectMapper
    ) {
        this.strategyScheduleService = Objects.requireNonNull(strategyScheduleService, "strategyScheduleService must not be null");
        this.strategyDefinitionRepository = Objects.requireNonNull(
                strategyDefinitionRepository,
                "strategyDefinitionRepository must not be null"
        );
        this.strategyTriggerGateway = Objects.requireNonNull(strategyTriggerGateway, "strategyTriggerGateway must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Clock.systemUTC();
    }

    public List<StrategyScheduleScanResult> scanOnce(String traceId) {
        List<StrategyScheduleScanResult> results = new ArrayList<>();
        Instant now = Instant.now(clock);
        for (StrategySchedule schedule : strategyScheduleService.listEnabledSchedules()) {
            StrategyDefinition definition = strategyDefinitionRepository.findByStrategyId(schedule.strategyId()).orElse(null);
            if (definition == null) {
                results.add(new StrategyScheduleScanResult(
                        schedule.scheduleJobId(),
                        schedule.strategyId(),
                        false,
                        null,
                        null,
                        "strategy_definition_missing"
                ));
                continue;
            }
            if (!definition.enabled()) {
                results.add(new StrategyScheduleScanResult(
                        schedule.scheduleJobId(),
                        schedule.strategyId(),
                        false,
                        null,
                        null,
                        "strategy_definition_disabled"
                ));
                continue;
            }
            if (!isDue(schedule, now)) {
                results.add(new StrategyScheduleScanResult(
                        schedule.scheduleJobId(),
                        schedule.strategyId(),
                        false,
                        null,
                        null,
                        "not_due"
                ));
                continue;
            }
            StrategyManualTriggerRequest request = buildTriggerRequest(schedule, definition, traceId);
            StrategyManualTriggerResult result = strategyTriggerGateway.trigger(request);
            strategyScheduleService.updateLastTriggeredAt(schedule.scheduleJobId(), now);
            results.add(new StrategyScheduleScanResult(
                    schedule.scheduleJobId(),
                    schedule.strategyId(),
                    true,
                    result.requestId(),
                    result.strategyRunId(),
                    "triggered"
            ));
        }
        return results;
    }

    private boolean isDue(StrategySchedule schedule, Instant now) {
        if (!"CRON".equalsIgnoreCase(schedule.scheduleType())) {
            return false;
        }
        ZoneId zoneId = ZoneId.of(schedule.timezone());
        Instant referenceInstant = schedule.lastTriggeredAt() == null
                ? schedule.createdAt().minusSeconds(1)
                : schedule.lastTriggeredAt();
        ZonedDateTime reference = referenceInstant.atZone(zoneId);
        ZonedDateTime next = CronExpression.parse(schedule.cronExpr()).next(reference);
        return next != null && !next.toInstant().isAfter(now);
    }

    private StrategyManualTriggerRequest buildTriggerRequest(
            StrategySchedule schedule,
            StrategyDefinition definition,
            String traceId
    ) {
        JsonNode config = parseConfig(definition.configSnapshot());
        return new StrategyManualTriggerRequest(
                definition.strategyId(),
                "req-schedule-" + schedule.scheduleJobId() + "-" + UUID.randomUUID(),
                requireText(config.path("symbol").asText(null), "config_snapshot.symbol"),
                OrderSide.valueOf(requireText(config.path("side").asText(null), "config_snapshot.side").toUpperCase()),
                OrderType.valueOf(requireText(config.path("orderType").asText(null), "config_snapshot.orderType").toUpperCase()),
                new BigDecimal(requireText(config.path("quantity").asText(null), "config_snapshot.quantity")),
                parsePrice(config.path("price").asText(null)),
                traceId == null || traceId.isBlank() ? "trc-schedule-scan-" + UUID.randomUUID() : traceId
        );
    }

    private JsonNode parseConfig(String configSnapshot) {
        try {
            return objectMapper.readTree(configSnapshot == null || configSnapshot.isBlank() ? "{}" : configSnapshot);
        } catch (Exception ex) {
            throw new IllegalStateException("strategy definition config_snapshot is not valid JSON", ex);
        }
    }

    private BigDecimal parsePrice(String rawPrice) {
        if (rawPrice == null || rawPrice.isBlank()) {
            return null;
        }
        return new BigDecimal(rawPrice);
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
