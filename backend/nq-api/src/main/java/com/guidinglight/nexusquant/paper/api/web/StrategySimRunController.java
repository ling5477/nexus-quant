package com.guidinglight.nexusquant.paper.api.web;

import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.paper.api.dto.PaperTradingRunResponse;
import com.guidinglight.nexusquant.research.application.paper.service.PaperTradingRunService;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimDecisionRepository;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimRunService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 隔离策略 SIM 入口；客户端只能提交发布记录与预算，不能提交交易方向和数量。 */
@Validated
@RestController
@ConditionalOnBean(StrategySimRunService.class)
@RequestMapping("/api/paper-trading/strategy-sim/runs")
public class StrategySimRunController {
    private final StrategySimRunService sim;
    private final PaperTradingRunService runs;

    public StrategySimRunController(StrategySimRunService sim, PaperTradingRunService runs) {
        this.sim = Objects.requireNonNull(sim);
        this.runs = Objects.requireNonNull(runs);
    }

    @PostMapping
    public StrategySimRunService.RunView create(@Valid @RequestBody CreateRequest request) {
        TraceIdContext.getOrCreate();
        return sim.create(request.publishId(), request.budget(), "system");
    }

    @PostMapping("/{paperRunId}/start")
    public PaperTradingRunResponse start(@PathVariable @NotBlank String paperRunId) {
        TraceIdContext.getOrCreate();
        return PaperTradingRunResponse.from(runs.start(paperRunId));
    }

    @PostMapping("/{paperRunId}/stop")
    public PaperTradingRunResponse stop(@PathVariable @NotBlank String paperRunId) {
        TraceIdContext.getOrCreate();
        return PaperTradingRunResponse.from(runs.stop(paperRunId));
    }

    @PostMapping("/{paperRunId}/advance")
    public StrategySimDecisionRepository.DecisionView advance(@PathVariable @NotBlank String paperRunId) {
        TraceIdContext.getOrCreate();
        return sim.advance(paperRunId);
    }

    @GetMapping("/{paperRunId}/decisions")
    public List<StrategySimDecisionRepository.DecisionView> decisions(@PathVariable @NotBlank String paperRunId) {
        TraceIdContext.getOrCreate();
        return sim.decisions(paperRunId);
    }

    @GetMapping("/{paperRunId}/facts")
    public StrategySimRunService.FactsView facts(@PathVariable @NotBlank String paperRunId) {
        TraceIdContext.getOrCreate();
        return sim.facts(paperRunId);
    }

    public record CreateRequest(@NotBlank String publishId, @DecimalMin("0.00000001") BigDecimal budget) { }
}
