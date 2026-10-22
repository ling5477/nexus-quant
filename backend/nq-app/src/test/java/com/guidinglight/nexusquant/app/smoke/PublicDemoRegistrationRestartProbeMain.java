package com.guidinglight.nexusquant.app.smoke;

import java.util.Map;
import java.util.List;
import java.math.BigDecimal;

/** 独立 JVM 恢复邮箱队列和注册回执；凭据只经进程环境传递，不写证据日志。 */
public final class PublicDemoRegistrationRestartProbeMain {
    public static void main(String[] args) throws Exception {
        String url = System.getenv("TASK_DEMO_URL");
        if (args.length != 0 || url == null || !url.startsWith("jdbc:postgresql://127.0.0.1:"))
            throw new IllegalArgumentException("isolated loopback database required");
        try (var app = PublicDemoTestHttp.start(url, System.getenv("TASK_DEMO_USER"), System.getenv("TASK_DEMO_PASSWORD"));
                var http = new PublicDemoTestHttp(app.getWebServer().getPort())) {
            if ("READ_BOUND".equals(System.getenv("TASK_DEMO_MODE"))) {
                bound(http);
                System.out.println("DEMO_BOUND_RESTART_VERIFIED pid=" + ProcessHandle.current().pid());
                return;
            }
            String email = System.getenv("TASK_DEMO_EMAIL");
            if ("VERIFY_PENDING".equals(System.getenv("TASK_DEMO_MODE"))) {
                var verified = http.ok("POST", "/api/auth/verify-email", null, Map.of("token", http.mailToken(email)));
                if (!"READY".equals(verified.path("status").asText())) throw new AssertionError("pending registration not recovered");
            }
            String session = http.login("restart-demo");
            var result = http.ok("GET", "/api/demo/registration-result", session, null);
            if (!"SIM_NOT_BOUND".equals(result.path("status").asText()) || !"10000.00000000".equals(result.path("initialCapital").asText()))
                throw new AssertionError("registration result changed after process restart");
            http.ok("POST", "/api/auth/register", null, PublicDemoTestHttp.registration(
                    java.util.UUID.fromString(System.getenv("TASK_DEMO_REGISTRATION")), "restart-demo", email));
            System.out.println("DEMO_REGISTRATION_RESTART_VERIFIED pid=" + ProcessHandle.current().pid());
        }
    }

    private static void bound(PublicDemoTestHttp http) throws Exception {
        String runA = System.getenv("TASK_DEMO_RUN_A"), runB = System.getenv("TASK_DEMO_RUN_B");
        for (var entry : List.of(new String[]{"demo-flow-a", runA, runB}, new String[]{"demo-flow-b", runB, runA})) {
            String session = http.login(entry[0]);
            var result = http.ok("GET", "/api/demo/registration-result", session, null);
            if (!entry[1].equals(result.path("boundPaperRunId").asText())
                    || !entry[1].equals(result.path("reservedPaperRunId").asText())
                    || !"SIM_RUNNING".equals(result.path("status").asText())) throw new AssertionError("registered bound identity lost after restart");
            var facts = http.ok("GET", "/api/paper-trading/strategy-sim/runs/" + entry[1] + "/facts", session, null);
            if (facts.path("canonicalAccountId").asLong() != result.path("accountId").asLong()
                    || facts.path("orders").size() != 1 || facts.path("trades").size() != 1)
                throw new AssertionError("registered canonical facts lost after restart");
            int funding = 0;
            for (var ledger : facts.path("ledgerEntries")) if (ledger.path("ref_type").asText().startsWith("SIM_FUNDING_")) funding++;
            if (funding != 2) throw new AssertionError("registered funding changed after restart");
            var performance = http.ok("GET", "/api/paper-trading/strategy-sim/analysis/runs/" + entry[1] + "/performance", session, null);
            if (new BigDecimal("10000").compareTo(performance.path("fundingBudget").decimalValue()) != 0)
                throw new AssertionError("registered funding basis changed after restart");
            if (http.send("GET", "/api/paper-trading/strategy-sim/runs/" + entry[2] + "/facts", session, null).statusCode() != 404)
                throw new AssertionError("registered tenant isolation lost after restart");
        }
    }
}
