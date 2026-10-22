package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 所有网络地址均限定本机；邮箱验证凭据仅在内存流转，不进入测试日志或证据。 */
final class PublicDemoTestHttp implements AutoCloseable {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String PASSWORD = "SyntheticDemoPassword123!";
    private static final Pattern TOKEN = Pattern.compile("[?&#]token=([A-Za-z0-9_.~%+-]+)");
    final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    final String base;

    PublicDemoTestHttp(int port) { base = "http://127.0.0.1:" + port; }

    static ServletWebServerApplicationContext start(TenantIsolationTestDatabase db, String... additional) {
        return start(db.url(), db.runtime, db.runtimePassword, additional);
    }

    static ServletWebServerApplicationContext start(String url, String username, String password, String... additional) {
        isolatedProcessConfiguration();
        var application = new SpringApplication(NexusQuantApplication.class);
        application.setRegisterShutdownHook(false);
        var args = new ArrayList<>(List.of(
                "--server.address=127.0.0.1", "--server.port=0", "--spring.profiles.active=ci",
                "--spring.main.banner-mode=off", "--logging.level.root=WARN",
                "--spring.datasource.url=" + url, "--spring.datasource.username=" + username,
                "--spring.datasource.password=" + password, "--spring.datasource.hikari.maximum-pool-size=8",
                "--spring.flyway.enabled=false", "--spring.sql.init.mode=never",
                "--nq.env-safety.no-outbound=true", "--nq.env-safety.live-enabled=false",
                "--nq.demo.tenant-business-enabled=true", "--nq.demo.registration-enabled=true",
                "--nq.demo.token-secret=synthetic-demo-verification-secret-20261022",
                "--nq.demo.verification-base-url=http://127.0.0.1:18080/verify-email",
                "--nq.demo.mail-dispatch-enabled=true", "--nq.demo.mail-poll-ms=100",
                "--spring.mail.host=127.0.0.1", "--spring.mail.port=" + System.getProperty("nq.demo.smtp.port", "11025"),
                "--spring.mail.properties.mail.smtp.connectiontimeout=1000", "--spring.mail.properties.mail.smtp.timeout=1000",
                "--spring.mail.properties.mail.smtp.writetimeout=1000",
                "--nq.strategy-sim.enabled=true", "--nq.continuous-sim.enabled=false",
                "--nq.instrument.catalog-sync.enabled=false", "--nq.okx.ws.enabled=false",
                "--nq.binance.ws.enabled=false", "--nq.okx.recovery.enabled=false",
                "--nq.auth.bootstrap-admin.enabled=false", "--nq.public-news.enabled=false",
                "--nq.account.performance.sampling-enabled=false",
                "--nq.account.credentials.master-key=synthetic-demo-key-1234567890123456789",
                "--nq.security.issuer=public-demo-test",
                "--nq.security.secret=synthetic-public-demo-signing-secret-123456789"));
        for (String override : additional) {
            String key = override.substring(0, override.indexOf('=') + 1);
            args.removeIf(value -> value.startsWith(key));
            args.add(override);
        }
        return (ServletWebServerApplicationContext) application.run(args.toArray(String[]::new));
    }

    /** 只在测试 JVM 覆盖原适配器读取视图；不读取、改写或复用工作区真实配置。 */
    static void isolatedProcessConfiguration() {
        System.setProperty("NQ_OKX_ENV", "dome"); System.setProperty("NQ_BINANCE_ENV", "dome");
        for (String key : List.of("NQ_OKX_BASE_URL", "NQ_OKX_WS_URL", "NQ_BINANCE_DOME_BASE_URL", "NQ_BINANCE_DOME_WS_URL",
                "NQ_BINANCE_REAL_BASE_URL", "NQ_BINANCE_REAL_WS_URL")) System.setProperty(key, "disabled://public-demo-test");
        for (String key : List.of("NQ_LIVE_ENABLED", "NQ_REAL_PROVIDER_ENABLED", "NQ_REAL_CLIENT_ENABLED",
                "NQ_REAL_EXCHANGE_ENABLED", "NQ_OKX_WS_ENABLED", "NQ_BINANCE_WS_ENABLED", "NQ_OKX_RECOVERY_ENABLED",
                "NQ_AI_ENABLED", "NQ_DH_RUNTIME_ENABLED")) System.setProperty(key, "false");
        for (String key : List.of("NQ_OKX_API_KEY", "NQ_OKX_API_SECRET", "NQ_OKX_API_PASSPHRASE",
                "NQ_BINANCE_API_KEY", "NQ_BINANCE_API_SECRET")) System.setProperty(key, "");
    }

    HttpResponse<String> send(String method, String path, String token, Object body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(40))
                .header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    JsonNode ok(String method, String path, String token, Object body) throws Exception {
        var response = send(method, path, token, body);
        assertEquals(200, response.statusCode(), method + " " + path + ": " + response.body());
        return JSON.readTree(response.body());
    }

    String login(String username) throws Exception {
        return ok("POST", "/api/auth/login", null, Map.of("username", username, "password", PASSWORD))
                .path("accessToken").asText();
    }

    static Map<String, Object> registration(UUID id, String username, String email) {
        return Map.of("registrationId", id.toString(), "username", username, "email", email,
                "password", PASSWORD, "confirmPassword", PASSWORD);
    }

    String mailToken(String recipient) throws Exception { return mailToken(recipient, null); }

    String mailToken(String recipient, String excluded) throws Exception {
        String api = System.getProperty("nq.demo.mail-api", "http://127.0.0.1:18025");
        assertTrue(api.matches("http://127\\.0\\.0\\.1:[0-9]+"), "loopback mail fixture required");
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            var listing = client.send(HttpRequest.newBuilder(URI.create(api + "/api/v1/messages?limit=100"))
                    .timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (listing.statusCode() == 200) {
                for (var message : JSON.readTree(listing.body()).path("messages")) {
                    boolean addressed = false;
                    for (var to : message.path("To")) if (recipient.equalsIgnoreCase(to.path("Address").asText())) addressed = true;
                    if (!addressed) continue;
                    var full = client.send(HttpRequest.newBuilder(URI.create(api + "/api/v1/message/" + message.path("ID").asText()))
                            .timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.ofString());
                    var text = JSON.readTree(full.body());
                    var matcher = TOKEN.matcher(text.path("Text").asText() + " " + text.path("HTML").asText());
                    if (matcher.find()) {
                        String candidate = URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
                        if (!candidate.equals(excluded)) return candidate;
                    }
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("local verification email was not delivered within bounded timeout");
    }

    @Override public void close() { client.close(); }
}
