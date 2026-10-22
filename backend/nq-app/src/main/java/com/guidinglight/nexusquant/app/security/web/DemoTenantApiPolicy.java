package com.guidinglight.nexusquant.app.security.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.regex.Pattern;

/** 逐方法列举已隔离的研究与 SIM 面；新增接口不会因目录前缀而自动获得 Demo 权限。 */
public final class DemoTenantApiPolicy {
    private DemoTenantApiPolicy() {}
    private record Route(String method, Pattern path) {}
    private static Route route(String method, String path) { return new Route(method, Pattern.compile(path)); }
    private static final List<Route> ROUTES = List.of(
            route("GET", "/api/demo/registration-result"),
            route("GET", "/api/demo/sim-account"),
            route("GET", "/api/marketdata/datasets(?:/[^/]+)?"),
            route("GET", "/api/paper-trading/strategy-sim/runs/continuous-capabilities"),
            route("GET", "/api/backtest-evaluation-requests/[0-9a-fA-F-]{36}"),
            route("POST", "/api/backtest-evaluation-requests/[0-9a-fA-F-]{36}"),
            route("POST", "/api/publishes"),
            route("GET", "/api/publishes/[^/]+/sim-runs/page"),
            route("GET", "/api/research-creation-operations/[0-9a-fA-F-]{36}"),
            route("POST", "/api/research-creation-operations/[0-9a-fA-F-]{36}"),
            route("GET", "/api/paper-trading/strategy-sim/continuous-operations/[0-9a-fA-F-]{36}"),
            route("POST", "/api/paper-trading/strategy-sim/continuous-operations"),
            route("POST", "/api/paper-trading/strategy-sim/runs/continuous"),
            route("GET", "/api/strategies(?:/[^/]+(?:/versions(?:/[^/]+)?)?)?"),
            route("POST", "/api/strategies(?:/[^/]+/versions)?"),
            route("GET", "/api/research-configs(?:/[^/]+)?"),
            route("POST", "/api/research-configs(?:/[^/]+/archive)?"),
            route("GET", "/api/backtest-configs(?:/[^/]+)?"),
            route("POST", "/api/backtest-configs(?:/[^/]+/archive)?"),
            route("PATCH", "/api/backtest-configs/[^/]+/(?:dataset|strategy-version)"),
            route("GET", "/api/backtest-runs(?:/[^/]+(?:/(?:evaluation|publish|sim-orders|sim-trades|sim-positions|pnl-snapshots))?)?"),
            route("POST", "/api/backtest-runs(?:/[^/]+/(?:start|evaluate|publish))?"),
            route("GET", "/api/(?:evaluations|publishes)(?:/[^/]+)?"),
            route("GET", "/api/paper-trading/strategy-sim/analysis/runs(?:/[^/]+(?:/performance)?)?"),
            // 只开放既有 canonical 派生快照；服务仍逐次校验运行所有者及冻结估值事件。
            route("POST", "/api/paper-trading/runs/[^/]+/equity-snapshots"),
            route("GET", "/api/paper-trading/runs/[^/]+/equity-snapshots/[^/]+"),
            route("GET", "/api/paper-trading/strategy-sim/runs/[^/]+/(?:continuous|decisions|facts)"),
            route("POST", "/api/paper-trading/strategy-sim/runs/[^/]+/continuous/(?:stop|resume)")
    );

    public static boolean allows(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return ROUTES.stream().anyMatch(route -> route.method().equals(request.getMethod()) && route.path().matcher(path).matches());
    }
}
