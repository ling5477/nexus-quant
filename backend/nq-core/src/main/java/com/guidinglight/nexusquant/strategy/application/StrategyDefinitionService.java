package com.guidinglight.nexusquant.strategy.application;

import com.guidinglight.nexusquant.strategy.domain.StrategyDefinition;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyDefinitionRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * StrategyDefinitionService 提供 GateE-1.1 的策略定义管理能力。
 */
@Service
public class StrategyDefinitionService {

    private final StrategyDefinitionRepository strategyDefinitionRepository;
    private final Clock clock;

    public StrategyDefinitionService(StrategyDefinitionRepository strategyDefinitionRepository) {
        this.strategyDefinitionRepository = Objects.requireNonNull(
                strategyDefinitionRepository,
                "strategyDefinitionRepository must not be null"
        );
        this.clock = Clock.systemUTC();
    }

    public StrategyDefinition create(StrategyDefinitionCreateRequest request) {
        validateCreateRequest(request);
        Instant now = Instant.now(clock);
        StrategyDefinition definition = new StrategyDefinition(
                "str-" + UUID.randomUUID(),
                request.strategyCode().trim(),
                request.strategyName().trim(),
                request.strategyType().trim(),
                request.exchangeCode().trim().toUpperCase(),
                request.accountId(),
                normalizeTradeEnv(request.tradeEnv()),
                false,
                normalizeConfigSnapshot(request.configSnapshot()),
                1,
                now,
                now
        );
        try {
            strategyDefinitionRepository.insert(definition);
        } catch (DuplicateKeyException ex) {
            throw new IllegalStateException("strategy_code already exists: " + definition.strategyCode(), ex);
        }
        return definition;
    }

    public List<StrategyDefinition> listAll() {
        return strategyDefinitionRepository.listAll();
    }

    public StrategyDefinition getByStrategyId(String strategyId) {
        return strategyDefinitionRepository.findByStrategyId(requireText(strategyId, "strategyId"))
                .orElseThrow(() -> new IllegalArgumentException("strategy definition not found: " + strategyId));
    }

    /**
     * 按策略编码查询策略定义。
     *
     * Why:
     * 正式 HTTP API 的路径变量是 `strategyCode`，不能把它误当成内部 `strategyId`。
     * 本方法只读策略定义事实，不改变策略算法、调度或交易状态。
     *
     * @param strategyCode 策略编码，必须非空
     * @return 匹配的策略定义
     * @throws IllegalArgumentException 当策略编码为空或不存在时抛出
     */
    public StrategyDefinition getByStrategyCode(String strategyCode) {
        return strategyDefinitionRepository.findByStrategyCode(requireText(strategyCode, "strategyCode"))
                .orElseThrow(() -> new IllegalArgumentException("strategy definition not found: " + strategyCode));
    }

    public StrategyDefinition enable(String strategyId) {
        StrategyDefinition current = getByStrategyId(strategyId);
        if (!strategyDefinitionRepository.updateEnabled(current.strategyId(), true, Instant.now(clock))) {
            throw new IllegalStateException("failed to enable strategy definition: " + strategyId);
        }
        return current.withEnabled(true, Instant.now(clock));
    }

    public StrategyDefinition disable(String strategyId) {
        StrategyDefinition current = getByStrategyId(strategyId);
        if (!strategyDefinitionRepository.updateEnabled(current.strategyId(), false, Instant.now(clock))) {
            throw new IllegalStateException("failed to disable strategy definition: " + strategyId);
        }
        return current.withEnabled(false, Instant.now(clock));
    }

    /**
     * 按策略编码启用策略定义。
     *
     * Why:
     * 前端和外部 API 使用 `strategyCode` 作为稳定业务标识，持久化更新仍必须落到内部
     * `strategyId`，避免把业务编码写入 ID 字段导致找不到记录。
     *
     * @param strategyCode 策略编码，必须非空
     * @return 启用后的策略定义快照
     */
    public StrategyDefinition enableByStrategyCode(String strategyCode) {
        StrategyDefinition current = getByStrategyCode(strategyCode);
        if (!strategyDefinitionRepository.updateEnabled(current.strategyId(), true, Instant.now(clock))) {
            throw new IllegalStateException("failed to enable strategy definition: " + strategyCode);
        }
        return current.withEnabled(true, Instant.now(clock));
    }

    /**
     * 按策略编码停用策略定义。
     *
     * @param strategyCode 策略编码，必须非空
     * @return 停用后的策略定义快照
     */
    public StrategyDefinition disableByStrategyCode(String strategyCode) {
        StrategyDefinition current = getByStrategyCode(strategyCode);
        if (!strategyDefinitionRepository.updateEnabled(current.strategyId(), false, Instant.now(clock))) {
            throw new IllegalStateException("failed to disable strategy definition: " + strategyCode);
        }
        return current.withEnabled(false, Instant.now(clock));
    }

    private void validateCreateRequest(StrategyDefinitionCreateRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        requireText(request.strategyCode(), "strategyCode");
        requireText(request.strategyName(), "strategyName");
        requireText(request.strategyType(), "strategyType");
        requireText(request.exchangeCode(), "exchangeCode");
        if (request.accountId() == null || request.accountId() <= 0) {
            throw new IllegalArgumentException("accountId must be positive");
        }
        normalizeTradeEnv(request.tradeEnv());
    }

    private String normalizeTradeEnv(String tradeEnv) {
        String normalized = requireText(tradeEnv, "tradeEnv").toUpperCase();
        if (!"SIM".equals(normalized) && !"LIVE".equals(normalized)) {
            throw new IllegalArgumentException("tradeEnv must be SIM or LIVE");
        }
        return normalized;
    }

    private String normalizeConfigSnapshot(String configSnapshot) {
        return configSnapshot == null || configSnapshot.isBlank() ? "{}" : configSnapshot.trim();
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}


