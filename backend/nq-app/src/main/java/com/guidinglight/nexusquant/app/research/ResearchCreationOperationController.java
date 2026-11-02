package com.guidinglight.nexusquant.app.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.app.research.ResearchCreationOperationService.Receipt;
import com.guidinglight.nexusquant.app.research.ResearchCreationOperationService.Result;
import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.paper.api.dto.PaperTradingRunCreateRequestBody;
import com.guidinglight.nexusquant.paper.api.web.StrategySimRunController.CreateRequest;
import com.guidinglight.nexusquant.research.api.dto.BacktestConfigCreateRequestBody;
import com.guidinglight.nexusquant.research.api.dto.BacktestRunStartRequestBody;
import com.guidinglight.nexusquant.research.api.dto.ResearchConfigCreateRequestBody;
import com.guidinglight.nexusquant.research.application.api.ResearchConfigApiService;
import com.guidinglight.nexusquant.research.application.api.backtest.BacktestConfigApiService;
import com.guidinglight.nexusquant.research.application.api.paper.PaperTradingApiService;
import com.guidinglight.nexusquant.research.application.eval.api.BacktestRunApiService;
import com.guidinglight.nexusquant.research.application.paper.command.PaperTradingRunCreateCommand;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimAnalysisQuery;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimRunService;
import com.guidinglight.nexusquant.strategy.api.dto.StrategyDefinitionCreateRequestBody;
import com.guidinglight.nexusquant.strategy.api.dto.StrategyVersionCreateRequestBody;
import com.guidinglight.nexusquant.strategy.application.StrategyDefinitionService;
import com.guidinglight.nexusquant.strategy.application.StrategyVersionService;
import com.guidinglight.nexusquant.strategy.application.command.StrategyDefinitionCreateRequest;
import com.guidinglight.nexusquant.strategy.application.command.StrategyVersionCreateRequest;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.function.Supplier;

/** 本地业务创建的恢复入口；复用原DTO和正式服务，不执行回测、发布或启动交易。 */
@RestController
@RequestMapping("/api/research-creation-operations")
public class ResearchCreationOperationController {
    private final ResearchCreationOperationService operations;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final GatewayAuthFacade auth;
    private final CurrentUserProfileService profiles;
    private final StrategyDefinitionService strategies;
    private final StrategyVersionService versions;
    private final ResearchConfigApiService research;
    private final BacktestConfigApiService backtests;
    private final BacktestRunApiService runs;
    private final PaperTradingApiService paper;
    private StrategySimRunService sim;
    private StrategySimAnalysisQuery simAnalysis;

    /** SIM关闭时保留研究创建能力；创建回执不能反向启用模拟执行器。 */
    @Autowired
    private void bindSim(
            ObjectProvider<StrategySimRunService> sim,
            ObjectProvider<StrategySimAnalysisQuery> analysis) {
        this.sim = sim.getIfAvailable();
        this.simAnalysis = analysis.getIfAvailable();
    }

    public ResearchCreationOperationController(
            ResearchCreationOperationService operations,
            ObjectMapper mapper,
            Validator validator,
            GatewayAuthFacade auth,
            CurrentUserProfileService profiles,
            StrategyDefinitionService strategies,
            StrategyVersionService versions,
            ResearchConfigApiService research,
            BacktestConfigApiService backtests,
            BacktestRunApiService runs,
            PaperTradingApiService paper) {
        this.operations = operations;
        this.mapper = mapper;
        this.validator = validator;
        this.auth = auth;
        this.profiles = profiles;
        this.strategies = strategies;
        this.versions = versions;
        this.research = research;
        this.backtests = backtests;
        this.runs = runs;
        this.paper = paper;
    }

    @PostMapping("/{operationId}")
    public Receipt create(@PathVariable UUID operationId, @Valid @RequestBody Request request) {
        AuthUserProfile actor = profile();
        boolean demo = actor.roles().contains("DEMO_USER")
                && com.guidinglight.nexusquant.common.tenant.TenantContext.requireCurrent().kind()
                    == com.guidinglight.nexusquant.common.tenant.TenantKind.DEMO;
        if (!actor.roles().contains("ADMIN") && !actor.roles().contains("OPERATOR") && !demo)
            throw new AccessDeniedException("operator role required");
        // 旧模拟入口不具有首次绑定的运行合同；Demo 只能经 canonical SIM 和稳定操作回执创建。
        if (demo && (request.kind() == Kind.PAPER_RUN || request.kind() == Kind.SIM_RUN))
            throw new AccessDeniedException("legacy paper creation is unavailable for Demo");
        String parent = request.parentKey() == null ? "" : request.parentKey();
        if ((request.kind() == Kind.VERSION && parent.isBlank())
                || (request.kind() != Kind.VERSION && !parent.isEmpty()))
            throw new IllegalArgumentException("invalid creation parent identity");
        Supplier<Result> creation =
                switch (request.kind()) {
                    case STRATEGY -> {
                        var input =
                                input(request.input(), StrategyDefinitionCreateRequestBody.class);
                        yield () -> {
                            var result =
                                    strategies.create(
                                            new StrategyDefinitionCreateRequest(
                                                    input.strategyCode(),
                                                    input.strategyName(),
                                                    input.strategyType(),
                                                    input.exchangeCode(),
                                                    input.accountId(),
                                                    input.tradeEnv(),
                                                    input.configSnapshot()));
                            return new Result(result.strategyId(), result.strategyCode());
                        };
                    }
                    case VERSION -> {
                        var input = input(request.input(), StrategyVersionCreateRequestBody.class);
                        yield () -> {
                            var result =
                                    versions.create(
                                            new StrategyVersionCreateRequest(
                                                    parent,
                                                    input.versionName(),
                                                    input.status(),
                                                    input.paramSnapshotJson(),
                                                    input.configSnapshotJson(),
                                                    input.sourceSnapshotJson(),
                                                    actor.username()));
                            return new Result(
                                    result.strategyVersionId(), result.strategyVersionId());
                        };
                    }
                    case RESEARCH -> {
                        var input = input(request.input(), ResearchConfigCreateRequestBody.class);
                        yield () -> {
                            var result =
                                    research.create(
                                            input.sourceStrategyId(),
                                            input.name(),
                                            input.description(),
                                            input.parameterSchema(),
                                            input.parameterDefaults(),
                                            input.datasetSpec());
                            return new Result(result.researchConfigId(), result.researchConfigId());
                        };
                    }
                    case BACKTEST -> {
                        var input = input(request.input(), BacktestConfigCreateRequestBody.class);
                        yield () -> {
                            var result =
                                    backtests.create(
                                            input.researchConfigId(),
                                            input.name(),
                                            input.description(),
                                            input.startTime(),
                                            input.endTime(),
                                            input.initialCapital(),
                                            input.executionSpec(),
                                            input.evaluationSpec());
                            return new Result(result.backtestConfigId(), result.backtestConfigId());
                        };
                    }
                    case BACKTEST_RUN -> {
                        var input = input(request.input(), BacktestRunStartRequestBody.class);
                        yield () -> {
                            var result = runs.create(input.backtestConfigId());
                            return new Result(result.backtestRunId(), result.backtestRunId());
                        };
                    }
                    case PAPER_RUN -> {
                        var input = input(request.input(), PaperTradingRunCreateRequestBody.class);
                        yield () -> {
                            // 只为原Research Paper创建绑定回执；不注资、不启动，不改变旧system创建人语义。
                            var result =
                                    paper.create(
                                            new PaperTradingRunCreateCommand(
                                                    input.publishId(),
                                                    input.tradeEnv(),
                                                    input.exchangeCode(),
                                                    input.marketType(),
                                                    input.symbol(),
                                                    input.intervalCode(),
                                                    input.configSnapshotJson(),
                                                    "system"));
                            return new Result(result.paperRunId(), result.paperRunId());
                        };
                    }
                    case SIM_RUN -> {
                        var input = input(request.input(), CreateRequest.class);
                        if (sim == null || simAnalysis == null)
                            throw new ResponseStatusException(
                                    HttpStatus.SERVICE_UNAVAILABLE, "STRATEGY_SIM_UNAVAILABLE");
                        yield () -> {
                            // 保留原system actor及来源账户归属规则。归属不成立时整个创建、注资和回执回滚。
                            var result = sim.create(input.publishId(), input.budget(), "system");
                            if (simAnalysis.read(actor.userId(), result.paperRunId()).isEmpty())
                                throw new AccessDeniedException(
                                        "SIM publication ownership required");
                            return new Result(result.paperRunId(), result.paperRunId());
                        };
                    }
                };
        return operations.execute(
                operationId,
                actor.userId(),
                request.kind().name(),
                parent,
                request.input(),
                creation);
    }

    @GetMapping("/{operationId}")
    public ResponseEntity<Receipt> find(@PathVariable UUID operationId) {
        return ResponseEntity.of(operations.find(operationId, profile().userId()));
    }

    private <T> T input(JsonNode source, Class<T> type) {
        T result = mapper.convertValue(source, type);
        var violations = validator.validate(result);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        return result;
    }

    private AuthUserProfile profile() {
        var result =
                auth.currentUser()
                        .flatMap(current -> profiles.findByUsername(current.username()))
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"));
        if (result.userId() == null
                || result.userId() <= 0
                || !result.enabled()
                || result.mustChangePassword())
            throw new AuthenticationCredentialsNotFoundException("authentication required");
        return result;
    }

    public enum Kind {
        STRATEGY,
        VERSION,
        RESEARCH,
        BACKTEST,
        BACKTEST_RUN,
        SIM_RUN,
        PAPER_RUN
    }

    public record Request(
            @NotNull Kind kind, @Size(max = 128) String parentKey, @NotNull JsonNode input) {}
}
