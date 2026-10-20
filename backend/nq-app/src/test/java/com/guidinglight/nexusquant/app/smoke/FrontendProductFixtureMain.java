package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.application.service.AuthSeedService;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataBarRepository;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimRunService;
import com.guidinglight.nexusquant.scheduler.paper.PaperMatchingService;
import com.guidinglight.nexusquant.research.application.paper.service.PaperTradingRunService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 仅在本任务新建PG数据库中装配可追溯的合成输入，业务事实由正式Java服务生成。 */
public final class FrontendProductFixtureMain {
    public static void main(String[] args) throws Exception {
        String url = System.getenv("TASK_PRODUCT_DB_URL");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/nq_product_[a-f0-9]{16}"))
            throw new IllegalArgumentException("owned product database required");
        Path ready = Path.of(args[0]), stop = Path.of(args[1]);
        if (Files.exists(ready) || Files.exists(stop)) throw new IllegalArgumentException("fresh runtime paths required");
        try (var context = (ServletWebServerApplicationContext) new SpringApplication(NexusQuantApplication.class).run(
                "--server.address=127.0.0.1", "--server.port=18488", "--spring.profiles.active=local",
                "--spring.datasource.url=" + url, "--spring.datasource.username=nq_product",
                "--spring.datasource.password=isolated-product-only", "--nq.env-safety.no-outbound=true",
                "--nq.env-safety.live-enabled=false", "--nq.strategy-sim.enabled=true",
                "--nq.continuous-sim.enabled=false", "--nq.instrument.catalog-sync.enabled=false",
                "--nq.okx.ws.enabled=false", "--nq.binance.ws.enabled=false", "--nq.okx.recovery.enabled=false",
                "--nq.auth.bootstrap-admin.enabled=false", "--nq.public-news.enabled=false",
                "--nq.account.performance.sampling-enabled=false")) {
            var jdbc = context.getBean(JdbcTemplate.class);
            var mapper = context.getBean(ObjectMapper.class);
            // local启动会应用开发用户；在任务库恢复受控登录，绝不重新生成交易事实。
            context.getBean(AuthSeedService.class).seedUsers(List.of(new SeedUserCommand(
                    System.getenv("TASK_PRODUCT_LOGIN_USER"), context.getBean(PasswordEncoder.class).encode(System.getenv("TASK_PRODUCT_LOGIN_PASSWORD")),
                    List.of("ADMIN", "OPERATOR", "VIEWER"), true), new SeedUserCommand("product-other-viewer",
                    context.getBean(PasswordEncoder.class).encode("synthetic-other-viewer"), List.of("VIEWER"), true)));
            if (args.length == 3) {
                var previous = mapper.readTree(Files.readString(Path.of(args[2])));
                if (!url.equals(previous.path("database").asText())) throw new IllegalArgumentException("reopen database mismatch");
                var facts = context.getBean(StrategySimRunService.class).facts(previous.path("paperRunId").asText());
                if (facts.canonicalAccountId() != previous.path("canonicalAccountId").asLong() || facts.trades().size() != previous.path("trades").asInt())
                    throw new IllegalStateException("reopen durable facts mismatch");
                Files.writeString(ready, previous.toPrettyString());
                long deadline = System.nanoTime() + java.time.Duration.ofHours(12).toNanos();
                while (!Files.exists(stop) && System.nanoTime() < deadline) Thread.sleep(500);
                return;
            }
            // 复用既有合成研究输入；它们是测试夹具，不冒充历史发行或真实研究收益。
            var seed = new StrategySimPostgresIntegrationTest();
            ReflectionTestUtils.setField(seed, "jdbc", jdbc);
            ReflectionTestUtils.setField(seed, "mapper", mapper);
            Object fixture = ReflectionTestUtils.invokeMethod(seed, "seed");
            String publishId = ReflectionTestUtils.invokeMethod(fixture, "publishId");
            // 交易证明的旧合成输入不读取配置列表；浏览器夹具补齐正式配置读侧的必需字段。
            jdbc.update("UPDATE backtest_configs SET config_json=?::jsonb WHERE backtest_config_id=(SELECT backtest_config_id FROM backtest_publish_records WHERE publish_record_id=?)",
                    "{\"startTime\":\"2026-09-25T00:00:00Z\",\"endTime\":\"2026-09-25T00:04:59Z\",\"initialCapital\":\"100\",\"executionSpec\":{}}", publishId);
            // 仅本任务新数据库的SIM夹具使用该状态；共享环境及LIVE默认边界完全不变。
            jdbc.update("UPDATE kill_switch_states SET status='DISENGAGED',version=version+1,reason_code='PRODUCT_FIXTURE_ONLY',source='ISOLATED_PRODUCT_FIXTURE',updated_at=now(),updated_by='fixture',trace_id='product-fixture' WHERE scope='GLOBAL_TRADING'");
            var sim = context.getBean(StrategySimRunService.class);
            var run = sim.create(publishId, new BigDecimal("100.00000000"), System.getenv("TASK_PRODUCT_LOGIN_USER"));
            context.getBean(PaperTradingRunService.class).start(run.paperRunId());
            for (int i=0;i<3;i++) sim.advance(run.paperRunId());
            context.getBean(PaperMatchingService.class).matchOnce(100);
            var facts = sim.facts(run.paperRunId());
            if (facts.trades().isEmpty() || facts.positionQuantity().signum() <= 0)
                throw new IllegalStateException("canonical fixture did not generate durable trade and position");
            var bars = new ArrayList<HistoricalBar>();
            Instant from = Instant.parse("2026-10-18T00:00:00Z");
            for (int i=0;i<120;i++) {
                BigDecimal open = new BigDecimal("100").add(BigDecimal.valueOf(i).divide(new BigDecimal("10")));
                BigDecimal close = open.add(i%3 == 0 ? new BigDecimal("-0.2") : new BigDecimal("0.3"));
                Instant time = from.plusSeconds(60L*i);
                bars.add(new HistoricalBar("OKX","SPOT","BTC-USDT",BarInterval.ONE_MINUTE,time,time.plusSeconds(59),
                        open,open.max(close).add(BigDecimal.ONE),open.min(close).subtract(BigDecimal.ONE),close,
                        BigDecimal.valueOf(10+i),null,null,"OK","{\"fixture\":\"PRODUCT_REVIEW_SYNTHETIC\"}",time.plusSeconds(59)));
            }
            context.getBean(MarketdataBarRepository.class).upsertBars(bars,"PRODUCT_REVIEW_SYNTHETIC",from.plusSeconds(7200));
            jdbc.update("INSERT INTO instrument_catalog(exchange_code,instrument_type,exchange_symbol,internal_symbol,base_asset,quote_asset,status,source,synced_at) VALUES ('OKX','SPOT','BTC-USDT','BTC-USDT','BTC','USDT','TRADING','PRODUCT_REVIEW_SYNTHETIC',now()) ON CONFLICT DO NOTHING");
            var identity = mapper.createObjectNode().put("port",18488).put("database",url).put("paperRunId",run.paperRunId())
                    .put("canonicalAccountId",facts.canonicalAccountId()).put("publishId",publishId).put("marketSource","PRODUCT_REVIEW_SYNTHETIC")
                    .put("trades",facts.trades().size()).put("ledgerEntries",facts.ledgerEntries().size()).put("bars",bars.size());
            Files.writeString(ready,identity.toPrettyString());
            long deadline = System.nanoTime()+java.time.Duration.ofHours(12).toNanos();
            while (!Files.exists(stop) && System.nanoTime()<deadline) Thread.sleep(500);
        }
    }
}
