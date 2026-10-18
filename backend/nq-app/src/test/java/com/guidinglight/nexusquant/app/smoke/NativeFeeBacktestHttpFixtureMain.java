package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCreateCommand;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCredentialUpsertCommand;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountCommandService;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountCredentialCommandService;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountCredentialVerificationService;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.account.infra.binance.readonly.JdbcBinanceAccountFeeReadPort;
import com.guidinglight.nexusquant.adapter.binance.fee.BinanceCommissionHttpReader;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.app.config.account.AccountCredentialRuntimeProperties;
import com.guidinglight.nexusquant.auth.application.command.LoginRequest;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.application.port.AuthService;
import com.guidinglight.nexusquant.auth.application.service.AuthSeedService;
import com.guidinglight.nexusquant.marketdata.application.command.CreateMarketdataDatasetCommand;
import com.guidinglight.nexusquant.marketdata.application.service.MarketdataDatasetService;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataBarRepository;
import com.guidinglight.nexusquant.research.application.ResearchConfigService;
import com.guidinglight.nexusquant.research.application.command.ResearchConfigCreateRequest;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.security.token.port.TokenService;
import com.guidinglight.nexusquant.strategy.application.StrategyVersionService;
import com.guidinglight.nexusquant.strategy.application.command.StrategyVersionCreateRequest;
import com.guidinglight.nexusquant.strategy.domain.SpotSmaTargetStrategy;
import com.guidinglight.nexusquant.strategy.domain.StrategyDefinition;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyDefinitionRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 真实Servlet子进程只装配合成来源；回测配置、执行和评估必须由外层测试经正式HTTP完成。 */
public final class NativeFeeBacktestHttpFixtureMain {
    static final String PASSWORD = "native-http-synthetic-login";

    private NativeFeeBacktestHttpFixtureMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3
                || !Set.of("positive", "zero", "negative", "minute-gain", "reopen")
                        .contains(args[0]))
            throw new IllegalArgumentException("bounded native backtest fixture mode required");
        String url = System.getenv("TASK_NATIVE_BACKTEST_DB_URL");
        if (url == null
                || !url.matches(
                        "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/native_backtest_http_[a-f0-9]{32}"))
            throw new IllegalArgumentException("owned random database required");
        Path ready = Path.of(args[1]).toAbsolutePath(), stop = Path.of(args[2]).toAbsolutePath();
        if (!ready.getParent().equals(stop.getParent())
                || Files.exists(ready)
                || Files.exists(stop))
            throw new IllegalArgumentException("fresh process identity required");
        var application =
                new SpringApplication(
                        NexusQuantApplication.class,
                        NativeFeeBacktestPostgresIntegrationTest.FixtureConfiguration.class);
        try (var context =
                (ServletWebServerApplicationContext)
                        application.run(
                                "--server.address=127.0.0.1",
                                "--server.port=0",
                                "--spring.profiles.active=local",
                                "--spring.datasource.url=" + url,
                                "--spring.datasource.username="
                                        + System.getenv("TASK_NATIVE_BACKTEST_DB_USER"),
                                "--spring.datasource.password="
                                        + System.getenv("TASK_NATIVE_BACKTEST_DB_PASSWORD"),
                                "--nq.account.credentials.master-key=native-http-synthetic-master-key-123456789",
                                "--nq.security.secret=native-http-synthetic-jwt-secret-123456789",
                                "--nq.security.issuer=native-http-synthetic",
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
            var source =
                    context.getBean(NativeFeeBacktestPostgresIntegrationTest.FixtureSource.class);
            NativeProvider provider = null;
            try {
                var identity =
                        new ObjectMapper()
                                .createObjectNode()
                                .put("pid", ProcessHandle.current().pid())
                                .put("port", context.getWebServer().getPort());
                if (!args[0].equals("reopen")) {
                    var passwords = context.getBean(PasswordEncoder.class);
                    context.getBean(AuthSeedService.class)
                            .seedUsers(
                                    List.of(
                                            new SeedUserCommand(
                                                    "native-http-admin",
                                                    passwords.encode(PASSWORD),
                                                    List.of("ADMIN"),
                                                    true),
                                            new SeedUserCommand(
                                                    "native-http-owner",
                                                    passwords.encode(PASSWORD),
                                                    List.of("OPERATOR", "VIEWER"),
                                                    true),
                                            new SeedUserCommand(
                                                    "native-http-other",
                                                    passwords.encode(PASSWORD),
                                                    List.of("OPERATOR", "VIEWER"),
                                                    true),
                                            new SeedUserCommand(
                                                    "native-http-viewer",
                                                    passwords.encode(PASSWORD),
                                                    List.of("VIEWER"),
                                                    true)));
                    long owner =
                            jdbc.queryForObject(
                                    "SELECT id FROM users WHERE username='native-http-owner'",
                                    Long.class);
                    var accounts = context.getBean(ExchangeAccountCommandService.class);
                    var account =
                            accounts.create(
                                    owner,
                                    new ExchangeAccountCreateCommand(
                                            "BINANCE",
                                            "LIVE",
                                            "Synthetic native fee HTTP source",
                                            null));
                    String signed =
                            args[0].equals("positive")
                                    ? "0.001"
                                    : args[0].equals("negative") ? "-0.001" : "0";
                    provider = new NativeProvider(signed);
                    var version =
                            context.getBean(ExchangeAccountCredentialCommandService.class)
                                    .upsert(
                                            owner,
                                            account.exchangeAccountId(),
                                            new ExchangeAccountCredentialUpsertCommand(
                                                    "BINANCE_HMAC",
                                                    NativeProvider.KEY,
                                                    NativeProvider.SECRET,
                                                    null,
                                                    null),
                                            1);
                    var verified =
                            context.getBean(ExchangeAccountCredentialVerificationService.class)
                                    .verifyVersion(
                                            owner,
                                            account.exchangeAccountId(),
                                            version.credentialId());
                    source.delegate =
                            new JdbcBinanceAccountFeeReadPort(
                                    jdbc,
                                    context.getBean(AccountCredentialRuntimeProperties.class)
                                            .getMasterKey(),
                                    new BinanceCommissionHttpReader(
                                            provider.client,
                                            provider.base(),
                                            BinanceCommissionHttpReader.Environment
                                                    .ISOLATED_FIXTURE,
                                            Duration.ofSeconds(4),
                                            Clock.systemUTC()),
                                    context.getBean(ExchangeAccountRepository.class),
                                    context.getBean(ExchangeAccountCredentialRepository.class),
                                    context.getBean(KillSwitchService.class),
                                    owner,
                                    account.exchangeAccountId(),
                                    verified.credentialId(),
                                    "BINANCE_HMAC",
                                    Set.of("BTC-USDT"),
                                    BinanceCommissionHttpReader.PermissionScope.READ_ONLY);
                    var researchAccount =
                            accounts.create(
                                    owner,
                                    new ExchangeAccountCreateCommand(
                                            "BINANCE",
                                            "SIM",
                                            "Synthetic research HTTP account",
                                            null));
                    List<HistoricalBar> bars = bars(args[0]);
                    for (var bar : bars)
                        context.getBean(MarketdataBarRepository.class)
                                .upsertBars(
                                        List.of(bar),
                                        "SYNTHETIC_NATIVE_HTTP_BARS",
                                        bar.availableAt());
                    var datasets = context.getBean(MarketdataDatasetService.class);
                    var dataset =
                            datasets.createDataset(
                                    new CreateMarketdataDatasetCommand(
                                            "Synthetic HTTP bars",
                                            "BINANCE",
                                            "SPOT",
                                            "BTC-USDT",
                                            "1m",
                                            bars.getFirst().openTime(),
                                            bars.getLast().closeTime(),
                                            "synthetic-test"));
                    String strategy = "native-http-" + UUID.randomUUID(),
                            code = "native-http-sma-" + UUID.randomUUID();
                    Instant now = Instant.now();
                    context.getBean(StrategyDefinitionRepository.class)
                            .insert(
                                    new StrategyDefinition(
                                            strategy,
                                            code,
                                            "Synthetic HTTP SMA",
                                            SpotSmaTargetStrategy.EXECUTABLE,
                                            "BINANCE",
                                            researchAccount.legacyAccountId(),
                                            "SIM",
                                            true,
                                            "{}",
                                            1,
                                            now,
                                            now));
                    // 夹具准备研究身份仍由正式登录解析owner，不能用无owner服务调用生成可跨用户访问的配置。
                    var tokens = context.getBean(TokenService.class);
                    var login =
                            context.getBean(AuthService.class)
                                    .login(new LoginRequest("native-http-owner", PASSWORD));
                    var security = SecurityContextHolder.createEmptyContext();
                    security.setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    tokens.parse(login.accessToken()).orElseThrow(),
                                    null,
                                    List.of()));
                    SecurityContextHolder.setContext(security);
                    try {
                        var strategyVersion =
                                context.getBean(StrategyVersionService.class)
                                        .create(
                                                new StrategyVersionCreateRequest(
                                                        code,
                                                        "v1",
                                                        "ACTIVE",
                                                        "{\"window\":3,\"investedExposure\":\"1\"}",
                                                        "{}",
                                                        "{\"executable\":\"SPOT_SMA_TARGET_V1\"}",
                                                        "synthetic-test"));
                        var research =
                                context.getBean(ResearchConfigService.class)
                                        .create(
                                                new ResearchConfigCreateRequest(
                                                        strategy,
                                                        "Synthetic HTTP research",
                                                        "synthetic",
                                                        "{}",
                                                        "{}",
                                                        datasets.buildDatasetSnapshot(
                                                                dataset.datasetId())));
                        identity.put("research", research.researchConfigId())
                                .put("strategyVersion", strategyVersion.strategyVersionId())
                                .put("dataset", dataset.datasetId().toString());
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                    identity.put("owner", owner)
                            .put("account", account.exchangeAccountId())
                            .put("credential", verified.credentialId())
                            .put("signed", signed);
                }
                // 只保存可核对的业务身份；JWT、连接材料和供应方签名不进入artifact。
                Files.writeString(ready, identity.toString());
                // 人工浏览器验收使用较长但仍有界的窗口；普通跨进程证明继续采用3分钟上限。
                long deadline =
                        System.nanoTime()
                                + Duration.ofMinutes(
                                                "true"
                                                                .equals(
                                                                        System.getenv(
                                                                                "TASK_NATIVE_BACKTEST_UI"))
                                                        ? 30
                                                        : 3)
                                        .toNanos();
                while (!Files.exists(stop) && System.nanoTime() < deadline) Thread.sleep(100);
                if (!Files.exists(stop))
                    throw new IllegalStateException("native backtest fixture lifetime exceeded");
                System.out.println(
                        "NATIVE_BACKTEST_HTTP_STOP mode="
                                + args[0]
                                + " providerGets="
                                + (provider == null ? 0 : provider.gets.get()));
            } finally {
                source.delegate = null;
                if (provider != null) provider.close();
            }
        }
    }

    /** 收盘时间可见的有界合成bar；固定价格包含买卖信号，禁止借本次写入时点伪造历史可见性。 */
    private static List<HistoricalBar> bars(String mode) {
        // 分钟正收益用于年化边界证明；原三个费用符号场景的输入保持原样。
        String[] prices =
                mode.equals("minute-gain")
                        ? new String[] {
                            "98", "99", "99.9", "100", "90", "100.20", "100.20", "100.20"
                        }
                        : new String[] {"100", "101", "102", "103", "104", "80", "81", "82"};
        var output = new ArrayList<HistoricalBar>();
        for (int i = 0; i < prices.length; i++) {
            Instant start = Instant.parse("2026-09-25T00:00:00Z").plusSeconds(i * 60L);
            var price = new BigDecimal(prices[i]);
            output.add(
                    new HistoricalBar(
                            "BINANCE",
                            "SPOT",
                            "BTC-USDT",
                            BarInterval.ONE_MINUTE,
                            start,
                            start.plusSeconds(59),
                            price,
                            price,
                            price,
                            price,
                            BigDecimal.ONE,
                            null,
                            null,
                            "OK",
                            "{\"fixture\":\"synthetic-native-http-bars\"}",
                            start.plusSeconds(59)));
        }
        return output;
    }

    /** 三个固定GET的loopback供应方自行校验HMAC；重开进程不会创建此对象或重新读取私有来源。 */
    private static final class NativeProvider implements AutoCloseable {
        static final String KEY = "synthetic-native-http-key",
                SECRET = "synthetic-native-http-secret";
        final HttpClient client = BinanceCommissionHttpReader.directHttpClient();
        final AtomicInteger gets = new AtomicInteger();
        final HttpServer server;
        final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        final String signed;

        NativeProvider(String signed) throws IOException {
            this.signed = signed;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(workers);
            server.createContext("/", this::respond);
            server.start();
        }

        URI base() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
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
                                    "{\"ipRestrict\":true,\"enableReading\":true,\"enableWithdrawals\":false,\"enableInternalTransfer\":false,\"enableSpotAndMarginTrading\":false,\"enableMargin\":false,\"enableFutures\":false,\"enableVanillaOptions\":false}";
                            case "/api/v3/account/commission" ->
                                    "{\"symbol\":\"BTCUSDT\",\"standardCommission\":{\"maker\":\"0\",\"taker\":\""
                                            + signed
                                            + "\",\"buyer\":\"0\",\"seller\":\"0\"},\"taxCommission\":{\"maker\":\"0\",\"taker\":\"0\",\"buyer\":\"0\",\"seller\":\"0\"},\"specialCommission\":{\"maker\":\"0\",\"taker\":\"0\",\"buyer\":\"0\",\"seller\":\"0\"},\"discount\":{\"enabledForAccount\":true,\"enabledForSymbol\":true,\"discountAsset\":\"BNB\",\"discount\":\"0.25\"}}";
                            default ->
                                    throw new IllegalStateException(
                                            "unexpected native HTTP fixture path");
                        };
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                request.sendResponseHeaders(200, bytes.length);
                request.getResponseBody().write(bytes);
            }
        }

        private boolean valid(HttpExchange request) {
            try {
                String raw = request.getRequestURI().getRawQuery();
                if (raw == null) return false;
                int split = raw.lastIndexOf("&signature=");
                if (split < 0 || !KEY.equals(request.getRequestHeaders().getFirst("X-MBX-APIKEY")))
                    return false;
                var mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                return HexFormat.of()
                        .formatHex(
                                mac.doFinal(
                                        raw.substring(0, split).getBytes(StandardCharsets.UTF_8)))
                        .equals(raw.substring(split + 11));
            } catch (Exception failure) {
                return false;
            }
        }

        @Override
        public void close() {
            server.stop(0);
            workers.shutdownNow();
            client.close();
        }
    }
}
