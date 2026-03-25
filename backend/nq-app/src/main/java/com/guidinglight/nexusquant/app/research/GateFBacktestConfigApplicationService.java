package com.guidinglight.nexusquant.app.research;

import com.guidinglight.nexusquant.research.model.BacktestConfig;
import com.guidinglight.nexusquant.research.service.BacktestConfigCreateRequest;
import com.guidinglight.nexusquant.research.service.BacktestConfigService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Service;

/**
 * GateFBacktestConfigApplicationService 负责把 HTTP 输入映射到回测配置服务。
 */
@Service
public class GateFBacktestConfigApplicationService {

    private final BacktestConfigService backtestConfigService;

    public GateFBacktestConfigApplicationService(BacktestConfigService backtestConfigService) {
        this.backtestConfigService = Objects.requireNonNull(
                backtestConfigService,
                "backtestConfigService must not be null"
        );
    }

    public BacktestConfig create(String researchConfigId, String name, String description,
                                 Instant startTime, Instant endTime, BigDecimal initialCapital,
                                 String executionSpec, String evaluationSpec) {
        return backtestConfigService.create(new BacktestConfigCreateRequest(
                researchConfigId,
                name,
                description,
                startTime,
                endTime,
                initialCapital,
                executionSpec,
                evaluationSpec
        ));
    }
}
