package com.guidinglight.nexusquant.app.smoke;

import java.util.Base64;

/** 从独立 JVM 通过正式 HTTP 入口提交或读取同一隔离数据库中的恢复操作。 */
public final class SimProjectionRecoveryProbeMain {
    public static void main(String[] args) throws Exception {
        String url = System.getenv("TASK_BOUNDED_DB_URL");
        if (args.length != 3 || url == null
                || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/nq_baseline_test_[a-f0-9]{32}")) {
            throw new IllegalArgumentException("isolated recovery fixture required");
        }
        var command = ForwardSimEvaluationFixture.JSON.readTree(Base64.getDecoder().decode(args[2]));
        String path = BoundedContinuousSimPostgresIntegrationTest.RUNS + args[0] + "/projection-recovery";
        try (var app = ForwardSimEvaluationFixture.start(url,
                     System.getenv("TASK_BOUNDED_DB_USER"), System.getenv("TASK_BOUNDED_DB_PASSWORD"));
                var http = new TenantIsolationHttpIntegrationTest.Http(app.getWebServer().getPort())) {
            String token = http.login(BoundedContinuousSimPostgresIntegrationTest.USER);
            if ("end-receipt".equals(args[1])) {
                String operation = command.required("operationId").asText();
                var receipt = http.ok("GET", BoundedContinuousSimPostgresIntegrationTest.OPS + "/" + operation,
                        token, null);
                if (!operation.equals(receipt.required("operationId").asText())
                        || !command.required("manualEndAt").equals(receipt.required("result").required("bounded").required("manualEndAt"))) {
                    throw new AssertionError("original END receipt changed across process restart");
                }
                System.out.println("TERMINAL_INTENT_RESTART_VERIFIED pid=" + ProcessHandle.current().pid());
                return;
            }
            if ("recover".equals(args[1])) {
                http.ok("POST", path, token, command);
                return;
            }
            var recovery = http.ok("GET", path + "/operations/" + command.required("recoveryOperationId").asText(),
                    token, null);
            if (!"UNCHANGED".equals(recovery.required("ledger").asText())) {
                throw new AssertionError("projection receipt lacks verified unchanged ledger");
            }
            var finalized = http.ok("POST", path + "/finalization", token, command).required("finalization");
            if (!"VERIFIED".equals(finalized.required("finalValuationStatus").asText())) {
                throw new AssertionError("finalization did not recover in a new process");
            }
            System.out.println("PROJECTION_RECHECK_RESTART_VERIFIED pid=" + ProcessHandle.current().pid());
        }
    }
}
