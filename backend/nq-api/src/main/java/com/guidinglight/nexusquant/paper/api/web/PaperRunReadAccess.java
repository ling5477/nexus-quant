package com.guidinglight.nexusquant.paper.api.web;

import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.research.application.api.paper.PaperTradingApiService;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimAnalysisQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.server.ResponseStatusException;

/** 旧只读别名在进入数据查询前统一授权；不改变命令或legacy Paper读取合同。 */
@Component
public class PaperRunReadAccess implements WebMvcConfigurer, HandlerInterceptor {
    private final GatewayAuthFacade auth;
    private final CurrentUserProfileService profiles;
    private final ObjectProvider<StrategySimAnalysisQuery> queries;
    private final PaperTradingApiService paper;

    public PaperRunReadAccess(GatewayAuthFacade auth, CurrentUserProfileService profiles,
            ObjectProvider<StrategySimAnalysisQuery> queries, PaperTradingApiService paper) {
        this.auth = auth;
        this.profiles = profiles;
        this.queries = queries;
        this.paper = paper;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/paper-trading/runs/**",
                "/api/paper-trading/strategy-sim/runs/**", "/api/paper-trading/schedules/**");
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())) return true;
        var variables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (variables == null) return true;
        String run = variables.get("paperRunId");
        if (run == null && variables.containsKey("scheduleId"))
            run = paper.getScheduleById(variables.get("scheduleId")).paperRunId();
        if (run != null) {
            boolean canonicalPath = request.getRequestURI().contains("/strategy-sim/runs/");
            if (canonicalPath || paper.getById(run).canonicalAccountId() != null) requireOwned(run);
        } else if (request.getParameter("paperRunId") != null) {
            run = request.getParameter("paperRunId");
            if (paper.getById(run).canonicalAccountId() != null) requireOwned(run);
        }
        return true;
    }

    private long owner() {
        return auth.currentUser().flatMap(user -> profiles.findByUsername(user.username()))
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("authentication required")).userId();
    }

    private void requireOwned(String run) {
        var query = queries.getIfAvailable();
        if (query == null || query.readIdentity(owner(), run).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SIM_RUN_NOT_FOUND");
    }

    /** 目录先批量求可读身份，再由原控制器投影；不把其他账户的快照返回给浏览器。 */
    public Set<String> readableIds(List<String> ids) {
        if (ids.isEmpty()) return Set.of();
        var query = queries.getIfAvailable();
        if (query == null) {
            var legacy = new HashSet<String>();
            paper.list(null, null).stream().filter(run -> run.canonicalAccountId() == null)
                    .forEach(run -> legacy.add(run.paperRunId()));
            legacy.retainAll(ids);
            return legacy;
        }
        long user = owner();
        var readable = new HashSet<String>();
        var distinct = ids.stream().distinct().toList();
        for (int i = 0; i < distinct.size(); i += 100)
            readable.addAll(query.readableRunIds(user, distinct.subList(i, Math.min(i + 100, distinct.size()))));
        return readable;
    }
}
