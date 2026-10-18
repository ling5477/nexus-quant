package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 真正跨JVM的Servlet/JWT/PG证明；关闭来源后只能恢复原观察，不能伪造新的同步成功。 */
@EnabledIfSystemProperty(named = "nq.postgres.smoke.required", matches = "true")
class DiscountAssetStrategySimHttpRestartPostgresIntegrationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void positiveFeesRecoverCanonicalPendingOrderAcrossTwoJvms() throws Exception {
        prove("positive");
    }

    @Test
    void zeroFeesRecoverCanonicalPendingOrderAcrossTwoJvms() throws Exception {
        prove("zero");
    }

    @Test
    void negativeFeesRecoverCanonicalPendingOrderAcrossTwoJvms() throws Exception {
        prove("negative");
    }

    @Test
    void cancelledPositiveFeeOrderReleasesBudgetAndExecutionAcrossRestart() throws Exception {
        prove("positive", true);
    }

    @Test
    void cancelledZeroFeeOrderReleasesBudgetAndExecutionAcrossRestart() throws Exception {
        prove("zero", true);
    }

    @Test
    void cancelledNegativeFeeOrderReleasesBudgetAndExecutionAcrossRestart() throws Exception {
        prove("negative", true);
    }

    @Test
    void negativeFeeBindingFailureRestartsWithoutNewOrder() throws Exception {
        prove("negative", false, true);
    }

    /** 停机前保留真实在途订单；重开后仅查询原冻结观察，正式ADMIN任务撮合并经owner接口读取经济事实。 */
    private void prove(String mode) throws Exception {
        prove(mode, false);
    }

    private void prove(String mode, boolean cancelPending) throws Exception {
        prove(mode, cancelPending, false);
    }

    private void prove(String mode, boolean cancelPending, boolean failBinding) throws Exception {
        String baseUrl = System.getProperty("nq.postgres.smoke.url", "");
        // 与原币费用测试一致：连接材料经环境进入，避免 Maven 参数和 Surefire 属性记录明文。
        String user = databaseSetting("TASK_NATIVE_BACKTEST_DB_USER", "nq.postgres.smoke.user"),
                password =
                        databaseSetting(
                                "TASK_NATIVE_BACKTEST_DB_PASSWORD", "nq.postgres.smoke.password");
        URI controlUri = URI.create(baseUrl.replaceFirst("^jdbc:", ""));
        assertEquals("127.0.0.1", controlUri.getHost());
        assertEquals(
                Integer.getInteger("nq.discount.sim.pg.port", 54466).intValue(),
                controlUri.getPort());
        assertTrue(controlUri.getRawQuery() == null && controlUri.getUserInfo() == null);
        String database = "discount_sim_http_" + UUID.randomUUID().toString().replace("-", "");
        String url = baseUrl.substring(0, baseUrl.lastIndexOf('/') + 1) + database;
        Path evidence =
                Path.of(
                                System.getProperty(
                                        "nq.discount.sim.http.evidence",
                                        System.getProperty("java.io.tmpdir")))
                        .toAbsolutePath()
                        .resolve(database);
        Files.createDirectories(evidence);
        var control =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                baseUrl + "?connectTimeout=5&socketTimeout=30", user, password));
        control.execute("CREATE DATABASE \"" + database + "\"");
        try (var http =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .proxy(new DirectProxy())
                        .build()) {
            var jdbc =
                    new JdbcTemplate(
                            new DriverManagerDataSource(
                                    url + "?connectTimeout=5&socketTimeout=30", user, password));
            JsonNode identity, observed, created, decisions, facts;
            long firstPid, secondPid;
            try (var process = launch(mode, url, user, password, evidence, failBinding)) {
                identity = process.ready();
                firstPid = identity.path("pid").asLong();
                String owner = login(http, process.port, "discount-http-owner"),
                        viewer = login(http, process.port, "discount-http-viewer");
                observed =
                        ok(
                                send(
                                        http,
                                        process.port,
                                        "POST",
                                        path(identity.path("account").asLong(), UUID.randomUUID()),
                                        owner,
                                        mapper.writeValueAsString(
                                                Map.of(
                                                        "credentialId",
                                                        identity.path("credential").asLong(),
                                                        "symbol",
                                                        "BTC-USDT"))));
                var spec =
                        mapper.createObjectNode()
                                .put("costSource", "CURRENT_ACCOUNT_OBSERVATION")
                                .put("feeExchangeCode", "BINANCE")
                                .put("feePaymentPolicy", "DISCOUNT_ASSET_IF_FUNDED")
                                .put("discountAssetBudgetFraction", "0.1")
                                .put(
                                        "discountPriceDatasetId",
                                        identity.path("discountDataset").asText())
                                .put("feeObservationId", observed.path("observationId").asText())
                                .put("feeAccountId", identity.path("account").asText())
                                .put("feeTradeEnv", "LIVE")
                                .put("feeSymbol", "BTC-USDT")
                                .put("quantityStep", "0.00000001")
                                .put("priceTick", "0.00000001")
                                .put("minimumQuantity", "0.00000001")
                                .put("minimumNotional", "5")
                                .put("slippageBps", "0");
                var config =
                        ok(
                                send(
                                        http,
                                        process.port,
                                        "POST",
                                        "/api/backtest-configs",
                                        owner,
                                        mapper.writeValueAsString(
                                                Map.of(
                                                        "researchConfigId",
                                                        identity.path("research").asText(),
                                                        "name",
                                                        "Native canonical HTTP " + mode,
                                                        "description",
                                                        "synthetic isolated recovery",
                                                        "startTime",
                                                        "2026-09-25T00:00:00Z",
                                                        "endTime",
                                                        "2026-09-25T00:07:59Z",
                                                        "initialCapital",
                                                        100,
                                                        "executionSpec",
                                                        spec.toString(),
                                                        "evaluationSpec",
                                                        "{}"))));
                String configPath =
                        "/api/backtest-configs/" + config.path("backtestConfigId").asText();
                ok(
                        send(
                                http,
                                process.port,
                                "PATCH",
                                configPath + "/dataset",
                                owner,
                                mapper.writeValueAsString(
                                        Map.of("datasetId", identity.path("dataset").asText()))));
                ok(
                        send(
                                http,
                                process.port,
                                "PATCH",
                                configPath + "/strategy-version",
                                owner,
                                mapper.writeValueAsString(
                                        Map.of(
                                                "strategyVersionId",
                                                identity.path("strategyVersion").asText()))));
                var run =
                        ok(
                                send(
                                        http,
                                        process.port,
                                        "POST",
                                        "/api/backtest-runs",
                                        owner,
                                        mapper.writeValueAsString(
                                                Map.of(
                                                        "backtestConfigId",
                                                        config.path("backtestConfigId")
                                                                .asText()))));
                String researchPath = runPath(run);
                assertEquals(
                        "SUCCEEDED",
                        ok(send(http, process.port, "POST", researchPath + "/start", owner, null))
                                .path("status")
                                .asText());
                assertEquals(
                        "SUCCEEDED",
                        ok(send(
                                        http,
                                        process.port,
                                        "POST",
                                        researchPath + "/evaluate",
                                        owner,
                                        null))
                                .path("evaluationStatus")
                                .asText());
                var published =
                        ok(
                                send(
                                        http,
                                        process.port,
                                        "POST",
                                        researchPath + "/publish",
                                        owner,
                                        mapper.writeValueAsString(
                                                Map.of(
                                                        "displayName",
                                                        "Native canonical HTTP publish",
                                                        "strategyVersionId",
                                                        identity.path("strategyVersion")
                                                                .asText()))));
                assertEquals("SUCCEEDED", published.path("publishStatus").asText());
                String createBody =
                        mapper.writeValueAsString(
                                Map.of(
                                        "publishId",
                                        published.path("publishRecordId").asText(),
                                        "budget",
                                        "100"));
                assertEquals(
                        403,
                        send(
                                        http,
                                        process.port,
                                        "POST",
                                        "/api/paper-trading/strategy-sim/runs",
                                        viewer,
                                        createBody)
                                .statusCode());
                created =
                        ok(
                                send(
                                        http,
                                        process.port,
                                        "POST",
                                        "/api/paper-trading/strategy-sim/runs",
                                        owner,
                                        createBody));
                String canonicalPath = canonicalPath(created);
                ok(send(http, process.port, "POST", canonicalPath + "/start", owner, null));
                for (int i = 0; i < 2; i++)
                    ok(send(http, process.port, "POST", canonicalPath + "/advance", owner, null));
                var admission =
                        send(http, process.port, "POST", canonicalPath + "/advance", owner, null);
                if (failBinding) assertEquals(409, admission.statusCode());
                else ok(admission);
                decisions =
                        ok(
                                send(
                                        http,
                                        process.port,
                                        "GET",
                                        canonicalPath + "/decisions",
                                        owner,
                                        null));
                assertTrue(decisions.size() >= 3);
                assertTrue(decisions.findValues("feeRate").stream().allMatch(JsonNode::isNull));
                assertTrue(
                        decisions.findValues("feeModelJson").stream()
                                .allMatch(value -> !value.asText().isBlank()));
                assertTrue(
                        decisions.findValues("feeChargeJson").stream()
                                .filter(value -> !value.isNull())
                                .anyMatch(value -> value.asText().contains("DISCOUNT_ASSET_USED")));
                if (failBinding)
                    assertTrue(
                            decisions.findValues("reason").stream()
                                    .anyMatch(value -> "DECIDING".equals(value.asText())));
                facts = ok(send(http, process.port, "GET", canonicalPath + "/facts", owner, null));
                assertEquals(1, facts.path("orders").size());
                assertEquals(0, facts.path("trades").size());
                equal(new BigDecimal("90.000004"), number(facts.path("exactValues"), "cash"));
                equal(new BigDecimal("0.01666666"), number(facts.path("discountAsset"), "balance"));
                assertTrue(
                        number(facts.path("discountAsset"), "reserved").signum()
                                == (mode.equals("positive") ? 1 : 0));
                assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM trades", Long.class));
                assertEquals(
                        4L, jdbc.queryForObject("SELECT count(*) FROM ledger_entries", Long.class));
            }
            assertTrue(
                    Files.readString(evidence.resolve(mode + ".log")).contains("providerGets=3"));
            try (var reopened = launch("reopen", url, user, password, evidence)) {
                secondPid = reopened.ready().path("pid").asLong();
                assertNotEquals(firstPid, secondPid);
                String owner = login(http, reopened.port, "discount-http-owner"),
                        other = login(http, reopened.port, "discount-http-other"),
                        admin = login(http, reopened.port, "discount-http-admin");
                String canonicalPath = canonicalPath(created);
                assertEquals(
                        401,
                        send(http, reopened.port, "GET", canonicalPath + "/facts", null, null)
                                .statusCode());
                assertEquals(
                        decisions,
                        ok(
                                send(
                                        http,
                                        reopened.port,
                                        "GET",
                                        canonicalPath + "/decisions",
                                        owner,
                                        null)));
                assertEquals(
                        facts,
                        ok(
                                send(
                                        http,
                                        reopened.port,
                                        "GET",
                                        canonicalPath + "/facts",
                                        owner,
                                        null)));
                assertDenied(
                        send(http, reopened.port, "GET", canonicalPath + "/facts", other, null));
                assertDenied(
                        send(http, reopened.port, "POST", canonicalPath + "/advance", other, null));
                ok(send(http, reopened.port, "POST", canonicalPath + "/advance", owner, null));
                assertEquals(
                        1,
                        ok(send(http, reopened.port, "GET", canonicalPath + "/facts", owner, null))
                                .path("orders")
                                .size());
                assertEquals(
                        403,
                        send(
                                        http,
                                        reopened.port,
                                        "POST",
                                        "/api/scheduler/jobs/PAPER_MATCHING/run-once",
                                        owner,
                                        null)
                                .statusCode());
                if (cancelPending) {
                    String orderId = finalOrderId(facts);
                    // 通过正式撤单编排形成终态；不能直接改订单或策略执行状态来绕过原生命周期。
                    String cancelPath = canonicalPath + "/orders/" + orderId + "/cancel";
                    assertEquals(
                            404,
                            send(http, reopened.port, "POST", cancelPath, other, null)
                                    .statusCode());
                    String viewer = login(http, reopened.port, "discount-http-viewer");
                    assertEquals(
                            403,
                            send(http, reopened.port, "POST", cancelPath, viewer, null)
                                    .statusCode());
                    if (mode.equals("positive")) {
                        // 普通入口必须有交易所账户映射；独立 SIM 无映射，拒绝后资金与原订单应保持不变。
                        assertEquals(
                                0L,
                                jdbc.queryForObject(
                                        "SELECT count(*) FROM exchange_accounts WHERE legacy_account_id=(SELECT account_id FROM orders WHERE order_id=?)",
                                        Long.class,
                                        orderId));
                        var legacy = send(
                                http, reopened.port, "POST", "/api/trading/orders/cancel", admin,
                                mapper.writeValueAsString(Map.of(
                                        "orderId", orderId, "reason", "synthetic native reserve release")));
                        assertEquals(404, legacy.statusCode());
                        Files.writeString(evidence.resolve("legacy-generic-cancel-rejection.json"), legacy.body());
                        assertEquals("ACCEPTED", jdbc.queryForObject(
                                "SELECT status FROM orders WHERE order_id=?", String.class, orderId));
                        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM trades", Long.class));
                        // 报价币和优惠资产各有一对初始注资分录；拒绝撤单不得增加任何分录。
                        assertEquals(4L, jdbc.queryForObject("SELECT count(*) FROM ledger_entries", Long.class));
                    }
                    // 丢失成功响应后读取同一订单，再重复原取消；新订单和经济分录数量不得增加。
                    observeGui(reopened, evidence, database, created, "cancel");
                    discardCancelResponse(reopened.port, cancelPath, owner);
                    long cancelDeadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
                    while (System.nanoTime() < cancelDeadline
                            && !"CANCELLED"
                                    .equals(
                                            ok(send(
                                                            http,
                                                            reopened.port,
                                                            "GET",
                                                            canonicalPath
                                                                    + "/orders/"
                                                                    + orderId,
                                                            owner,
                                                            null))
                                                    .path("order")
                                                    .path("status")
                                                    .asText())) Thread.sleep(20);
                    assertEquals(
                            "CANCELLED",
                            ok(send(
                                            http,
                                            reopened.port,
                                            "GET",
                                            canonicalPath + "/orders/" + orderId,
                                            owner,
                                            null))
                                    .path("order")
                                    .path("status")
                                    .asText());
                    assertTrue(
                            ok(send(http, reopened.port, "POST", cancelPath, owner, null))
                                    .path("idempotentHit")
                                    .asBoolean());
                    assertEquals(
                            "FAILED",
                            ok(send(
                                            http,
                                            reopened.port,
                                            "POST",
                                            canonicalPath
                                                    + "/orders/"
                                                    + orderId
                                                    + "/refresh-execution",
                                            owner,
                                            null))
                                    .path("status")
                                    .asText());
                    assertEquals(
                            "CANCELLED",
                            jdbc.queryForObject(
                                    "SELECT status FROM orders WHERE order_id=?",
                                    String.class,
                                    orderId));
                    assertEquals(
                            0L, jdbc.queryForObject("SELECT count(*) FROM trades", Long.class));
                    var released =
                            ok(
                                    send(
                                            http,
                                            reopened.port,
                                            "GET",
                                            canonicalPath + "/facts",
                                            owner,
                                            null));
                    equal(
                            new BigDecimal("90.000004"),
                            number(released.path("exactValues"), "cash"));
                    equal(BigDecimal.ZERO, number(released.path("discountAsset"), "reserved"));
                    equal(
                            new BigDecimal("0.01666666"),
                            number(released.path("discountAsset"), "available"));
                    ok(send(http, reopened.port, "POST", canonicalPath + "/advance", owner, null));
                    assertEquals(
                            2,
                            ok(send(
                                            http,
                                            reopened.port,
                                            "GET",
                                            canonicalPath + "/facts",
                                            owner,
                                            null))
                                    .path("orders")
                                    .size());
                    assertEquals(
                            "FAILED",
                            jdbc.queryForObject(
                                    "SELECT r.status FROM strategy_runs r JOIN orders o ON o.strategy_run_id=r.strategy_run_id WHERE o.order_id=?",
                                    String.class,
                                    orderId));
                }
                for (int i = 0; i < 9; i++) {
                    assertEquals(
                            "SUCCESS",
                            ok(send(
                                            http,
                                            reopened.port,
                                            "POST",
                                            "/api/scheduler/jobs/PAPER_MATCHING/run-once",
                                            admin,
                                            null))
                                    .path("result")
                                    .asText());
                    ok(send(http, reopened.port, "POST", canonicalPath + "/advance", owner, null));
                }
                var finalFacts =
                        ok(send(http, reopened.port, "GET", canonicalPath + "/facts", owner, null));
                assertEquals(2, finalFacts.path("trades").size());
                BigDecimal cash = new BigDecimal("90.000004"),
                        base = BigDecimal.ZERO,
                        asset = new BigDecimal("0.01666666"),
                        rate = new BigDecimal(identity.path("signed").asText());
                var fills =
                        jdbc.queryForList(
                                "SELECT t.*,o.side FROM trades t JOIN orders o ON o.order_id=t.order_id ORDER BY t.ts,t.trade_id");
                assertEquals(2, fills.size());
                for (var fill : fills) {
                    boolean buy = "BUY".equals(fill.get("side"));
                    var quantity = (BigDecimal) fill.get("qty");
                    var price = (BigDecimal) fill.get("price");
                    var fee = (BigDecimal) fill.get("fee");
                    assertEquals("BNB", fill.get("fee_currency"));
                    BigDecimal notional =
                            quantity.multiply(price).setScale(8, RoundingMode.HALF_UP);
                    equal(
                            quantity.multiply(price)
                                    .multiply(rate)
                                    .multiply(new BigDecimal("0.25"))
                                    .divide(new BigDecimal("600"), 8, RoundingMode.HALF_UP),
                            fee);
                    cash = buy ? cash.subtract(notional) : cash.add(notional);
                    base = buy ? base.add(quantity) : base.subtract(quantity);
                    asset = asset.subtract(fee);
                }
                equal(cash, number(finalFacts.path("exactValues"), "cash"));
                equal(base, number(finalFacts.path("exactValues"), "positionQuantity"));
                equal(BigDecimal.ZERO, base);
                var performance =
                        ok(
                                send(
                                        http,
                                        reopened.port,
                                        "GET",
                                        "/api/paper-trading/strategy-sim/analysis/runs/"
                                                + created.path("paperRunId").asText()
                                                + "/performance",
                                        owner,
                                        null));
                BigDecimal equity =
                        cash.add(
                                asset.multiply(new BigDecimal("600"))
                                        .setScale(8, RoundingMode.HALF_UP));
                equal(asset, number(finalFacts.path("discountAsset"), "balance"));
                equal(BigDecimal.ZERO, number(finalFacts.path("discountAsset"), "reserved"));
                equal(equity, number(finalFacts.path("exactValues"), "equity"));
                assertEquals("AVAILABLE", performance.path("status").asText());
                equal(equity, number(performance, "finalEquity"));
                if (cancelPending) observeGui(reopened, evidence, database, created, "final");
                assertEquals(
                        0L,
                        jdbc.queryForObject(
                                "SELECT count(*) FROM orders WHERE trade_env='LIVE'", Long.class));
                assertEquals(
                        "ENGAGED",
                        jdbc.queryForObject(
                                "SELECT status FROM kill_switch_states WHERE scope='GLOBAL_TRADING'",
                                String.class));
                assertEquals(
                        0L,
                        jdbc.queryForObject(
                                "SELECT count(*) FROM (SELECT account_id,currency FROM ledger_entries GROUP BY account_id,currency HAVING sum(delta)<>0) invalid",
                                Long.class));
                var saved =
                        ok(
                                send(
                                        http,
                                        reopened.port,
                                        "GET",
                                        canonicalPath + "/decisions",
                                        owner,
                                        null));
                ok(
                        send(
                                http,
                                reopened.port,
                                "POST",
                                "/api/scheduler/jobs/PAPER_MATCHING/run-once",
                                admin,
                                null));
                assertEquals(
                        finalFacts,
                        ok(
                                send(
                                        http,
                                        reopened.port,
                                        "GET",
                                        canonicalPath + "/facts",
                                        owner,
                                        null)));
                assertEquals(
                        saved,
                        ok(
                                send(
                                        http,
                                        reopened.port,
                                        "GET",
                                        canonicalPath + "/decisions",
                                        owner,
                                        null)));
            }
            assertTrue(Files.readString(evidence.resolve("reopen.log")).contains("providerGets=0"));
            Files.writeString(
                    evidence.resolve("proof.json"),
                    mapper.writeValueAsString(
                            Map.of(
                                    "result",
                                    "PASS_SCOPED_CANONICAL_DISCOUNT_RESTART",
                                    "mode",
                                    mode,
                                    "firstPid",
                                    firstPid,
                                    "paperRunId",
                                    created.path("paperRunId").asText(),
                                    "discountTrades",
                                    2,
                                    "reopenProviderGets",
                                    0,
                                    "source",
                                    "BINANCE",
                                    "execution",
                                    "PAPER",
                                    "secondPid",
                                    secondPid,
                                    "cancelResponseUnknown",
                                    cancelPending && !mode.equals("positive"))));
        } finally {
            assertTrue(database.matches("discount_sim_http_[a-f0-9]{32}"));
            control.queryForList(
                    "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname=? AND pid<>pg_backend_pid()",
                    database);
            control.execute("DROP DATABASE \"" + database + "\"");
        }
    }

    /** 优先使用本测试专属环境；旧属性入口仅保留兼容，不在当前验证中传递连接材料。 */
    private static String databaseSetting(String environment, String legacyProperty) {
        String value = System.getenv(environment);
        if (value == null || value.isBlank()) value = System.getProperty(legacyProperty, "");
        if (value.isBlank())
            throw new IllegalArgumentException("isolated database setting required");
        return value;
    }

    private static String canonicalPath(JsonNode created) {
        return "/api/paper-trading/strategy-sim/runs/" + created.path("paperRunId").asText();
    }

    /** 可选真实GUI沿用本次Servlet及随机库；完成文件仅控制夹具等待，不代表业务验收自动成功。 */
    private void observeGui(
            FixtureProcess process, Path evidence, String database, JsonNode created, String point)
            throws Exception {
        if (!Boolean.getBoolean("nq.native.backtest.ui")) return;
        Path ready = evidence.getParent().resolve("native-canonical-" + point + "-ui-ready.json");
        Path done = evidence.getParent().resolve("native-canonical-" + point + "-ui.done");
        assertFalse(Files.exists(ready));
        assertFalse(Files.exists(done));
        Files.writeString(
                ready,
                mapper.writeValueAsString(
                        Map.of(
                                "port",
                                process.port,
                                "database",
                                database,
                                "paperRunId",
                                created.path("paperRunId").asText(),
                                "evidence",
                                evidence.toString(),
                                "point",
                                point)));
        long deadline = System.nanoTime() + Duration.ofMinutes(10).toNanos();
        while (!Files.exists(done) && System.nanoTime() < deadline) Thread.sleep(100);
        assertTrue(Files.exists(done), "bounded native canonical GUI observation did not complete");
    }

    private static String finalOrderId(JsonNode facts) {
        assertEquals(1, facts.path("orders").size());
        return facts.path("orders").get(0).path("order_id").asText();
    }

    private static BigDecimal number(JsonNode node, String key) {
        assertTrue(node.hasNonNull(key), key);
        return new BigDecimal(node.path(key).asText());
    }

    private static void equal(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }

    private static String runPath(JsonNode run) {
        return "/api/backtest-runs/" + run.path("backtestRunId").asText();
    }

    private static void assertDenied(HttpResponse<String> response) {
        assertTrue(
                List.of(400, 404).contains(response.statusCode()),
                "cross owner status=" + response.statusCode());
    }

    private String login(HttpClient http, int port, String username) throws Exception {
        var body =
                mapper.writeValueAsString(
                        Map.of(
                                "username",
                                username,
                                "password",
                                DiscountAssetStrategySimHttpFixtureMain.PASSWORD));
        var result = ok(send(http, port, "POST", "/api/auth/login", null, body));
        assertFalse(result.path("accessToken").asText().isBlank());
        return result.path("accessToken").asText();
    }

    private HttpResponse<String> send(
            HttpClient http, int port, String method, String path, String token, String body)
            throws Exception {
        var request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(Duration.ofSeconds(8));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.header("Content-Type", "application/json");
        request.method(
                method,
                body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        return http.send(
                request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** 请求已完整写入真实socket后主动关闭、不读取响应；结果未知时只查询原订单与原取消身份。 */
    private static void discardCancelResponse(int port, String path, String token)
            throws Exception {
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 5000);
            socket.setSoTimeout(8000);
            String request =
                    "POST "
                            + path
                            + " HTTP/1.1\r\nHost: 127.0.0.1:"
                            + port
                            + "\r\nAuthorization: Bearer "
                            + token
                            + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
            socket.getOutputStream().write(request.getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            socket.shutdownOutput();
        }
    }

    private JsonNode ok(HttpResponse<String> response) throws Exception {
        // 认证响应可能带JWT；失败只报告状态码，禁止把完整body放入断言输出。
        assertEquals(200, response.statusCode());
        return mapper.readTree(response.body());
    }

    private static String path(long account, UUID operation) {
        return "/api/exchange-accounts/" + account + "/fees/operations/" + operation;
    }

    /** JVM参数文件避免Windows命令行长度限制；只含classpath和合成配置，数据库连接材料经环境传递。 */
    private FixtureProcess launch(
            String mode, String url, String user, String password, Path evidence) throws Exception {
        return launch(mode, url, user, password, evidence, false);
    }

    private FixtureProcess launch(
            String mode,
            String url,
            String user,
            String password,
            Path evidence,
            boolean failBinding)
            throws Exception {
        Path args = evidence.resolve(mode + ".args");
        String cp =
                System.getProperty(
                        "surefire.test.class.path", System.getProperty("java.class.path"));
        String escaped = cp.replace("\\", "\\\\").replace("\"", "\\\"");
        // main参数顺序固定；就绪/停止文件路径同样以参数文件传入，避免shell插值。
        Files.writeString(
                args,
                "-Duser.timezone=UTC\n-cp\n\""
                        + escaped
                        + "\"\n"
                        + DiscountAssetStrategySimHttpFixtureMain.class.getName()
                        + "\n"
                        + mode
                        + "\n"
                        + quoted(evidence.resolve(mode + ".ready.json"))
                        + "\n"
                        + quoted(evidence.resolve(mode + ".stop"))
                        + "\n");
        var builder =
                new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                        "@" + args);
        builder.environment().put("TASK_NATIVE_BACKTEST_DB_URL", url);
        builder.environment().put("TASK_NATIVE_BACKTEST_DB_USER", user);
        builder.environment().put("TASK_NATIVE_BACKTEST_DB_PASSWORD", password);
        builder.environment()
                .put(
                        "TASK_NATIVE_BACKTEST_UI",
                        Boolean.toString(Boolean.getBoolean("nq.native.backtest.ui")));
        builder.environment().put("TASK_NATIVE_SIM_FAIL_BINDING", Boolean.toString(failBinding));
        builder.redirectErrorStream(true).redirectOutput(evidence.resolve(mode + ".log").toFile());
        var fixture = new FixtureProcess(builder.start(), evidence, mode, user, password);
        try {
            fixture.awaitReady();
            return fixture;
        } catch (Exception | AssertionError failure) {
            fixture.close();
            throw failure;
        }
    }

    private static String quoted(Path path) {
        return "\"" + path.toString().replace("\\", "\\\\") + "\"";
    }

    /** 有界启动/关闭仅管理本测试启动的子进程；保留日志且擦除连接材料。 */
    private final class FixtureProcess implements AutoCloseable {
        private final Process process;
        private final Path evidence;
        private final String mode, user, password;
        private int port;

        FixtureProcess(Process process, Path evidence, String mode, String user, String password) {
            this.process = process;
            this.evidence = evidence;
            this.mode = mode;
            this.user = user;
            this.password = password;
        }

        JsonNode ready() throws Exception {
            return mapper.readTree(Files.readString(evidence.resolve(mode + ".ready.json")));
        }

        void awaitReady() throws Exception {
            long deadline = System.nanoTime() + Duration.ofSeconds(55).toNanos();
            while (!Files.exists(evidence.resolve(mode + ".ready.json"))
                    && process.isAlive()
                    && System.nanoTime() < deadline) Thread.sleep(100);
            assertTrue(
                    Files.exists(evidence.resolve(mode + ".ready.json")),
                    "Servlet fixture did not become ready: " + mode);
            port = ready().path("port").asInt();
            assertTrue(port > 0);
        }

        @Override
        public void close() throws Exception {
            Files.writeString(evidence.resolve(mode + ".stop"), "stop owned fixture");
            boolean stopped = process.waitFor(15, TimeUnit.SECONDS);
            if (!stopped) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            Path log = evidence.resolve(mode + ".log");
            if (Files.exists(log)) {
                String content =
                        Files.readString(log)
                                .replace(password, "[REDACTED]")
                                .replace(user, "[REDACTED]")
                                .replaceAll(
                                        "(?m)(Using generated security password:\\s*)\\S+",
                                        "$1[REDACTED]");
                Files.writeString(log, content);
            }
            assertTrue(stopped, "Servlet fixture required forced termination: " + mode);
            assertEquals(0, process.exitValue(), "Servlet fixture exit: " + mode);
        }
    }

    /** localhost请求不继承系统代理，保证合成材料不会外发。 */
    private static final class DirectProxy extends ProxySelector {
        @Override
        public List<Proxy> select(URI uri) {
            return List.of(Proxy.NO_PROXY);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress socket, IOException failure) {}
    }
}
