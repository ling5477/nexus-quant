package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.strategy.application.StrategyRunRecoveryService;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Set;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

/** 只为因果回归逐次记录真实恢复调用；正式参数不能启用该诊断instrumentation。 */
final class L6BRecoveryTrace {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path path = Path.of("strategy-recovery-trace-" + ProcessHandle.current().pid() + ".ndjson");
    private final StrategyRunRepository runs;
    private int sequence;

    private L6BRecoveryTrace(StrategyRunRepository runs) { this.runs = runs; }

    static void installIfRequested(ConfigurableApplicationContext context) throws Exception {
        var parameters = JSON.readTree(Files.readAllBytes(Path.of("parameters.json")));
        if (!parameters.path("strategyRecoveryDiagnostic").asBoolean()) return;
        requireDiagnostic(parameters);
        var recovery = context.getBean(StrategyRunRecoveryService.class);
        var trace = new L6BRecoveryTrace(context.getBean(StrategyRunRepository.class));
        ReflectionTestUtils.setField(recovery, "executions", trace.wrap(ReflectionTestUtils.getField(recovery, "executions"),
                Set.of("reserveCandidates", "findCandidates", "project")));
        ReflectionTestUtils.setField(recovery, "gateway", trace.wrap(ReflectionTestUtils.getField(recovery, "gateway"), Set.of("resume")));
    }

    static void requireDiagnostic(JsonNode parameters) {
        L6BContract.require(parameters.path("diagnosticOnly").asBoolean() && parameters.path("probe").asBoolean()
                && !parameters.path("formalTimerStarted").asBoolean() && L6BContract.MODE.equals(parameters.path("mode").asText()));
    }

    private Object wrap(Object target, Set<String> methods) {
        var proxy = new ProxyFactory(target);
        proxy.addAdvice((MethodInterceptor) call -> {
            String method = call.getMethod().getName();
            if (!methods.contains(method)) return call.proceed();
            var row = JSON.createObjectNode().put("method", method).put("phase", "START");
            row.set("arguments", JSON.valueToTree(call.getArguments())); append(row);
            try {
                Object result = call.proceed();
                var completed = row.deepCopy().put("phase", "COMPLETED");
                completed.set("result", JSON.valueToTree(result));
                if ((method.equals("project") || method.equals("resume")) && call.getArguments()[0] instanceof String id) {
                    completed.put("durableStatusAfter", runs.findByStrategyRunId(id).orElseThrow().status().name());
                }
                append(completed); return result;
            } catch (Throwable failure) {
                append(row.deepCopy().put("phase", "FAILED").put("failureType", failure.getClass().getName()));
                throw failure;
            }
        });
        return proxy.getProxy();
    }

    private synchronized void append(ObjectNode row) throws Exception {
        L6BContract.require(++sequence <= 5000);
        row.put("sequence", sequence).put("at", Instant.now().toString()).put("nanoTime", System.nanoTime())
                .put("pid", ProcessHandle.current().pid()).put("thread", Thread.currentThread().getName());
        Files.writeString(path, row + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
