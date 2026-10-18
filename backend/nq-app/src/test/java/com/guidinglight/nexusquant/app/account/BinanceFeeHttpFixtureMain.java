package com.guidinglight.nexusquant.app.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCreateCommand;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCredentialUpsertCommand;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountCommandService;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountCredentialCommandService;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountCredentialVerificationService;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.fee.AccountFeeQuote;
import com.guidinglight.nexusquant.account.domain.port.AccountFeeReadPort;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.account.infra.binance.readonly.JdbcBinanceAccountFeeReadPort;
import com.guidinglight.nexusquant.adapter.binance.fee.BinanceCommissionHttpReader;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.app.config.account.AccountCredentialRuntimeProperties;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.application.service.AuthSeedService;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 仅由目标测试启动的真实Servlet进程；随机测试库、合成材料与loopback供应方之外的来源全部关闭。 */
public final class BinanceFeeHttpFixtureMain {
    static final String PASSWORD = "synthetic-http-fee-login-password";
    private static final String API_KEY = "synthetic-http-binance-key",
            SECRET = "synthetic-http-hmac-secret";

    private BinanceFeeHttpFixtureMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3
                || !Set.of("observe-hmac", "observe-ed25519", "reopen").contains(args[0]))
            throw new IllegalArgumentException("bounded fixture mode required");
        String url = System.getenv("TASK_BINANCE_FEE_HTTP_DB_URL");
        if (url == null
                || !url.matches(
                        "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/binance_fee_http_[a-f0-9]{32}"))
            throw new IllegalArgumentException("owned random test database required");
        Path ready = Path.of(args[1]).toAbsolutePath(), stop = Path.of(args[2]).toAbsolutePath();
        if (!ready.getParent().equals(stop.getParent())
                || Files.exists(ready)
                || Files.exists(stop))
            throw new IllegalArgumentException("fresh process identity files required");
        var application =
                new SpringApplication(NexusQuantApplication.class, FixtureConfiguration.class);
        try (var context =
                (ServletWebServerApplicationContext)
                        application.run(
                                "--server.address=127.0.0.1",
                                "--server.port=0",
                                "--spring.profiles.active=local",
                                "--spring.datasource.url=" + url,
                                "--spring.datasource.username="
                                        + System.getenv("TASK_BINANCE_FEE_HTTP_DB_USER"),
                                "--spring.datasource.password="
                                        + System.getenv("TASK_BINANCE_FEE_HTTP_DB_PASSWORD"),
                                "--nq.account.credentials.master-key=synthetic-http-fee-master-key-123456789",
                                "--nq.security.secret=synthetic-http-fee-jwt-secret-123456789",
                                "--nq.security.issuer=synthetic-http-fee",
                                "--nq.env-safety.ci=false",
                                "--nq.env-safety.no-outbound=true",
                                "--nq.env-safety.live-enabled=false",
                                "--nq.env-safety.real-provider-enabled=false",
                                "--nq.env-safety.real-client-enabled=false",
                                "--nq.env-safety.real-exchange-enabled=false",
                                "--nq.auth.bootstrap-admin.enabled=false",
                                "--nq.runtime.trading-components.enabled=false",
                                "--nq.instrument.catalog-sync.enabled=false",
                                "--nq.okx.recovery.enabled=false",
                                "--nq.okx.ws.enabled=false",
                                "--nq.binance.ws.enabled=false",
                                "--nq.account-fees.binance.enabled=false",
                                "--nq.okx.private-readonly-diagnostics.enabled=false",
                                "--nq.strategy-sim.enabled=false",
                                "--nq.continuous-sim.enabled=false",
                                "--nq.account.performance.sampling-enabled=false",
                                "--nq.public-news.enabled=false")) {
            var jdbc = context.getBean(JdbcTemplate.class);
            var source = context.getBean(FixtureSource.class);
            long account = 0, credential = 0, owner = 0;
            if (!args[0].equals("reopen")) {
                var passwords = context.getBean(PasswordEncoder.class);
                context.getBean(AuthSeedService.class)
                        .seedUsers(
                                List.of(
                                        new SeedUserCommand(
                                                "http-fee-owner",
                                                passwords.encode(PASSWORD),
                                                List.of("OPERATOR", "VIEWER"),
                                                true),
                                        new SeedUserCommand(
                                                "http-fee-other",
                                                passwords.encode(PASSWORD),
                                                List.of("OPERATOR", "VIEWER"),
                                                true),
                                        new SeedUserCommand(
                                                "http-fee-viewer",
                                                passwords.encode(PASSWORD),
                                                List.of("VIEWER"),
                                                true)));
                owner =
                        jdbc.queryForObject(
                                "SELECT id FROM users WHERE username='http-fee-owner'", Long.class);
                var summary =
                        context.getBean(ExchangeAccountCommandService.class)
                                .create(
                                        owner,
                                        new ExchangeAccountCreateCommand(
                                                "BINANCE",
                                                "LIVE",
                                                "Synthetic HTTP fee source",
                                                null));
                source.start(args[0].equals("observe-ed25519"));
                String type = source.ed ? "BINANCE_ED25519" : "BINANCE_HMAC";
                var version =
                        context.getBean(ExchangeAccountCredentialCommandService.class)
                                .upsert(
                                        owner,
                                        summary.exchangeAccountId(),
                                        new ExchangeAccountCredentialUpsertCommand(
                                                type,
                                                API_KEY,
                                                source.ed ? null : SECRET,
                                                null,
                                                source.pem()),
                                        1);
                var verified =
                        context.getBean(ExchangeAccountCredentialVerificationService.class)
                                .verifyVersion(
                                        owner, summary.exchangeAccountId(), version.credentialId());
                source.delegate =
                        new JdbcBinanceAccountFeeReadPort(
                                jdbc,
                                context.getBean(AccountCredentialRuntimeProperties.class)
                                        .getMasterKey(),
                                new BinanceCommissionHttpReader(
                                        source.client,
                                        URI.create(
                                                "http://127.0.0.1:"
                                                        + source.server.getAddress().getPort()),
                                        BinanceCommissionHttpReader.Environment.ISOLATED_FIXTURE,
                                        Duration.ofSeconds(4),
                                        Clock.systemUTC()),
                                context.getBean(ExchangeAccountRepository.class),
                                context.getBean(ExchangeAccountCredentialRepository.class),
                                context.getBean(KillSwitchService.class),
                                owner,
                                summary.exchangeAccountId(),
                                verified.credentialId(),
                                type,
                                Set.of("BTC-USDT"),
                                BinanceCommissionHttpReader.PermissionScope.READ_ONLY);
                account = summary.exchangeAccountId();
                credential = verified.credentialId();
            }
            // 就绪文件只包含端口及业务身份，不保存密码、JWT、密文或供应方请求。
            Files.writeString(
                    ready,
                    new ObjectMapper()
                            .writeValueAsString(
                                    Map.of(
                                            "port",
                                            context.getWebServer().getPort(),
                                            "owner",
                                            owner,
                                            "account",
                                            account,
                                            "credential",
                                            credential,
                                            "mode",
                                            args[0],
                                            "pid",
                                            ProcessHandle.current().pid())));
            long deadline = System.nanoTime() + Duration.ofMinutes(2).toNanos();
            while (!Files.exists(stop) && System.nanoTime() < deadline) Thread.sleep(100);
            if (!Files.exists(stop)) throw new IllegalStateException("fixture lifetime exceeded");
            System.out.println(
                    "BINANCE_FEE_HTTP_FIXTURE_STOP mode="
                            + args[0]
                            + " providerGets="
                            + source.gets.get()
                            + " signatures="
                            + source.signatures.get());
        }
    }

    /** 仅测试启动入口显式装配；正式生产组件扫描会排除此配置。 */
    @TestConfiguration
    public static class FixtureConfiguration {
        @Bean(destroyMethod = "close")
        FixtureSource binanceFeeHttpFixtureSource() {
            return new FixtureSource();
        }
    }

    /** 合成供应方自行核验HMAC或Ed25519签名；重开模式不创建HTTP客户端、server或解密委托。 */
    static final class FixtureSource implements AccountFeeReadPort, AutoCloseable {
        HttpServer server;
        HttpClient client;
        KeyPair pair;
        boolean ed;
        AccountFeeReadPort delegate;
        final AtomicInteger gets = new AtomicInteger(), signatures = new AtomicInteger();
        private ExecutorService workers;

        void start(boolean useEd) throws Exception {
            ed = useEd;
            pair = ed ? KeyPairGenerator.getInstance("Ed25519").generateKeyPair() : null;
            client = BinanceCommissionHttpReader.directHttpClient();
            workers = Executors.newVirtualThreadPerTaskExecutor();
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(workers);
            server.createContext("/", this::respond);
            server.start();
        }

        String pem() {
            return ed
                    ? "-----BEGIN PRIVATE KEY-----\n"
                            + Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded())
                            + "\n-----END PRIVATE KEY-----"
                    : null;
        }

        @Override
        public boolean supports(ExchangeAccountSummary account, String symbol) {
            return delegate != null && delegate.supports(account, symbol);
        }

        @Override
        public AccountFeeQuote read(
                ExchangeAccountSummary account,
                ExchangeAccountCredentialSummary credential,
                String symbol) {
            if (delegate == null) throw new IllegalStateException("FEE_PRIVATE_SOURCE_DISABLED");
            return delegate.read(account, credential, symbol);
        }

        private void respond(HttpExchange request) throws IOException {
            try (request) {
                gets.incrementAndGet();
                String path = request.getRequestURI().getPath();
                if (!request.getRequestMethod().equals("GET")
                        || !path.equals("/api/v3/time") && !valid(request)) {
                    request.sendResponseHeaders(401, -1);
                    return;
                }
                String body =
                        switch (path) {
                            case "/api/v3/time" ->
                                    "{\"serverTime\":" + System.currentTimeMillis() + "}";
                            case "/sapi/v1/account/apiRestrictions" ->
                                    """
                            {"ipRestrict":true,"enableReading":true,"enableWithdrawals":false,"enableInternalTransfer":false,
                            "enableSpotAndMarginTrading":false,"enableMargin":false,"enableFutures":false,"enableVanillaOptions":false}
                            """;
                            case "/api/v3/account/commission" ->
                                    """
                            {"symbol":"BTCUSDT","standardCommission":{"maker":"-0.000000000001","taker":"0.001","buyer":"0.0002","seller":"0.0003"},
                            "taxCommission":{"maker":"0","taker":"0","buyer":"0","seller":"0"},
                            "specialCommission":{"maker":"0","taker":"0.00001","buyer":"0","seller":"0"},
                            "discount":{"enabledForAccount":true,"enabledForSymbol":true,"discountAsset":"BNB","discount":"0.25"}}
                            """;
                            default -> throw new IllegalStateException("unexpected fixture path");
                        };
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                request.sendResponseHeaders(200, bytes.length);
                request.getResponseBody().write(bytes);
            }
        }

        private boolean valid(HttpExchange request) {
            try {
                String raw = request.getRequestURI().getRawQuery();
                int split = raw.lastIndexOf("&signature=");
                if (split < 0
                        || !API_KEY.equals(request.getRequestHeaders().getFirst("X-MBX-APIKEY")))
                    return false;
                String text = raw.substring(0, split),
                        signature =
                                URLDecoder.decode(
                                        raw.substring(split + 11), StandardCharsets.UTF_8);
                boolean valid;
                if (ed) {
                    var verifier = Signature.getInstance("Ed25519");
                    verifier.initVerify(pair.getPublic());
                    verifier.update(text.getBytes(StandardCharsets.UTF_8));
                    valid = verifier.verify(Base64.getDecoder().decode(signature));
                } else {
                    var mac = Mac.getInstance("HmacSHA256");
                    mac.init(
                            new SecretKeySpec(
                                    SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                    valid =
                            HexFormat.of()
                                    .formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)))
                                    .equals(signature);
                }
                if (valid) signatures.incrementAndGet();
                return valid;
            } catch (Exception failure) {
                return false;
            }
        }

        @Override
        public void close() {
            if (server != null) server.stop(0);
            if (workers != null) workers.shutdownNow();
            if (client != null) client.close();
            delegate = null;
            pair = null;
        }
    }
}
