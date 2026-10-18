package com.guidinglight.nexusquant.app.account;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCreateCommand;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountCommandService;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.application.service.AuthSeedService;
import com.guidinglight.nexusquant.notification.application.UserNotificationService;
import java.net.URI;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;

/** 随机可丢弃PG库验证所有权、窗口及已读与告警状态分离；夹具不是交易事实证明。 */
@EnabledIfSystemProperty(named = "nq.postgres.smoke.required", matches = "true")
@ActiveProfiles("local")
@SpringBootTest(
    classes = NexusQuantApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "nq.strategy-sim.enabled=true",
      "nq.instrument.catalog-sync.enabled=false",
      "nq.okx.recovery.enabled=false",
      "nq.okx.ws.enabled=false",
      "nq.binance.ws.enabled=false",
      "nq.env-safety.no-outbound=true",
      "nq.account.performance.sampling-enabled=false",
      "nq.okx.adapter.stub-on-bootstrap-failure=false"
    })
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestExecutionListeners(
    listeners = UserNotificationPostgresIntegrationTest.Cleanup.class,
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class UserNotificationPostgresIntegrationTest {
  private static JdbcTemplate control;
  private static String database;
  private static final String PASSWORD = "synthetic-notification-fixture-password";
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;
  @Autowired AuthSeedService seeds;
  @Autowired PasswordEncoder passwords;
  @Autowired ExchangeAccountCommandService accounts;
  @Autowired UserNotificationService notifications;
  private long owner, other;
  private String ownerName, otherName;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    String url = System.getProperty("nq.postgres.smoke.url", ""),
        user =
            System.getProperty(
                "nq.postgres.smoke.user",
                System.getenv().getOrDefault("TASK_NATIVE_BACKTEST_DB_USER", "")),
        password =
            System.getProperty(
                "nq.postgres.smoke.password",
                System.getenv().getOrDefault("TASK_NATIVE_BACKTEST_DB_PASSWORD", ""));
    assertTrue(url.startsWith("jdbc:postgresql://") && !user.isBlank() && !password.isBlank());
    URI uri = URI.create(url.substring("jdbc:".length()));
    assertTrue(
        Set.of("127.0.0.1", "localhost").contains(uri.getHost())
            && uri.getRawQuery() == null
            && uri.getFragment() == null
            && uri.getPath().matches("/[A-Za-z0-9_]+"));
    control =
        new JdbcTemplate(
            new DriverManagerDataSource(
                url + "?connectTimeout=5&socketTimeout=30", user, password));
    assertEquals(
        16,
        control.queryForObject(
            "SELECT current_setting('server_version_num')::integer/10000", Integer.class));
    database = "user_notifications_" + UUID.randomUUID().toString().replace("-", "");
    control.execute("CREATE DATABASE \"" + database + "\"");
    registry.add(
        "spring.datasource.url", () -> url.substring(0, url.lastIndexOf('/') + 1) + database);
    registry.add("spring.datasource.username", () -> user);
    registry.add("spring.datasource.password", () -> password);
  }

  @BeforeEach
  void identities() {
    ownerName = "notice-owner-" + UUID.randomUUID();
    otherName = "notice-other-" + UUID.randomUUID();
    seeds.seedUsers(
        List.of(
            new SeedUserCommand(ownerName, passwords.encode(PASSWORD), List.of("VIEWER"), true),
            new SeedUserCommand(otherName, passwords.encode(PASSWORD), List.of("VIEWER"), true)));
    owner = jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, ownerName);
    other = jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, otherName);
  }

  private String run(long user, String creator, boolean canonical) {
    var account =
        accounts.create(
            user,
            new ExchangeAccountCreateCommand("OKX", "SIM", "notice-" + UUID.randomUUID(), null));
    String suffix = UUID.randomUUID().toString(),
        strategy = "ns-" + suffix,
        version = "nv-" + suffix,
        research = "nr-" + suffix,
        config = "nc-" + suffix,
        backtest = "nb-" + suffix,
        publish = "np-" + suffix,
        paper = "nrun-" + suffix;
    jdbc.update(
        "INSERT INTO"
            + " strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,exchange_code,account_id,trade_env,enabled,config_snapshot,version)"
            + " VALUES(?,?,?,'SPOT_SMA_TARGET_V1','OKX',?,'SIM',true,'{}',1)",
        strategy,
        strategy,
        strategy,
        account.legacyAccountId());
    jdbc.update(
        "INSERT INTO"
            + " strategy_versions(strategy_version_id,strategy_code,version,version_name,status,param_snapshot_json,config_snapshot_json,source_snapshot_json,checksum)"
            + " VALUES(?,?,1,'notification','ACTIVE','{}','{}','{}',?)",
        version,
        strategy,
        "a".repeat(64));
    jdbc.update(
        "INSERT INTO research_configs(research_config_id,source_strategy_id,name,strategy_snapshot)"
            + " VALUES(?,?,?,'{}')",
        research,
        strategy,
        research);
    jdbc.update(
        "INSERT INTO backtest_configs(backtest_config_id,research_config_id,name) VALUES(?,?,?)",
        config,
        research,
        config);
    jdbc.update(
        "INSERT INTO"
            + " backtest_runs(backtest_run_id,backtest_config_id,research_config_id,source_strategy_id,status,strategy_snapshot,backtest_config_snapshot,requested_at)"
            + " VALUES(?,?,?,?,'CREATED','{}','{}',now())",
        backtest,
        config,
        research,
        strategy);
    jdbc.update(
        "INSERT INTO"
            + " backtest_publish_records(publish_record_id,backtest_run_id,research_config_id,backtest_config_id,source_strategy_id,publish_status,publish_name,strategy_version_id)"
            + " VALUES(?,?,?,?,?,'SUCCEEDED','notification',?)",
        publish,
        backtest,
        research,
        config,
        strategy,
        version);
    Long canonicalId =
        canonical
            ? jdbc.queryForObject(
                "INSERT INTO accounts(account_code,venue,status) VALUES(?,'PAPER','ACTIVE')"
                    + " RETURNING account_id",
                Long.class,
                "nca-" + suffix)
            : null;
    jdbc.update(
        "INSERT INTO"
            + " paper_trading_runs(paper_run_id,publish_id,strategy_version_id,status,trade_env,exchange_code,market_type,symbol,interval_code,created_by,created_at,updated_at,canonical_account_id)"
            + " VALUES(?,?,?,'CREATED','SIM','OKX','SPOT','BTC-USDT','1h',?,now(),now(),?)",
        paper,
        publish,
        version,
        creator,
        canonicalId);
    return paper;
  }

  private String alert(String run) {
    String id = "na-" + UUID.randomUUID();
    jdbc.update(
        "INSERT INTO"
            + " paper_run_alerts(alert_id,paper_run_id,alert_type,severity,status,title,message,created_at,updated_at)"
            + " VALUES(?,?,'MANUAL','MEDIUM','OPEN','隔离通知测试','sensitive-fixture-message-not-exposed',now()-interval"
            + " '1 minute',now()-interval '1 minute')",
        id,
        run);
    return id;
  }

  @Test
  void ownerAndCreatorScopesDoNotLeakAndResultOmitsInternalPayload() throws Exception {
    String owned = run(owner, "system", true),
        legacy = run(owner, ownerName, false),
        hidden = run(other, otherName, true);
    alert(owned);
    alert(legacy);
    alert(hidden);
    jdbc.update(
        "INSERT INTO"
            + " paper_run_recovery_events(recovery_event_id,paper_run_id,recovery_type,status,reason,request_json,started_at,finished_at,created_at)"
            + " VALUES(?,?,'MANUAL_RECOVER','FAILED','sensitive-reason','{\"secret\":\"not-exposed\"}',now()-interval"
            + " '2 minutes',now(),now()-interval '2 minutes')",
        "nrec-" + UUID.randomUUID(),
        owned);
    var visible = notifications.read(owner, 0);
    assertEquals(3, visible.items().size());
    assertFalse(visible.items().stream().anyMatch(row -> row.paperRunId().equals(hidden)));
    assertEquals("RECOVERY", visible.items().getFirst().kind());
    assertEquals(
        Set.of("CANONICAL_SIM", "LEGACY_PAPER"),
        visible.items().stream().map(row -> row.runKind()).collect(Collectors.toSet()));
    assertEquals(1, notifications.read(other, 0).items().size());
    String body =
        mvc.perform(
                get("/api/user/notifications")
                    .header("Authorization", "Bearer " + login(ownerName)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertFalse(body.contains("sensitive"));
    assertFalse(body.contains("not-exposed"));
    assertFalse(body.contains("requestJson"));
    mvc.perform(get("/api/user/notifications")).andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/user/notifications")
                .param("offset", "1")
                .header("Authorization", "Bearer " + login(ownerName)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/user/notifications")
                .param("offset", "1000")
                .header("Authorization", "Bearer " + login(ownerName)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void paginationIsBoundedAndDoesNotDuplicateEqualTimeRows() {
    String own = run(owner, ownerName, true);
    jdbc.update(
        "INSERT INTO"
            + " paper_run_alerts(alert_id,paper_run_id,alert_type,severity,status,title,created_at,updated_at)"
            + " SELECT ?||lpad(i::text,4,'0'),?,'MANUAL','LOW','OPEN','window"
            + " fixture',now()-interval '1 minute',now()-interval '1 minute' FROM"
            + " generate_series(1,1001) i",
        "nwin-" + UUID.randomUUID() + "-",
        own);
    var first = notifications.read(owner, 0);
    var second = notifications.read(owner, 50);
    var last = notifications.read(owner, 950);
    assertEquals(50, first.items().size());
    assertTrue(first.hasMore());
    assertEquals(50, second.items().size());
    assertEquals(
        100,
        Stream.concat(first.items().stream(), second.items().stream())
            .map(row -> row.id())
            .distinct()
            .count());
    assertEquals(50, last.items().size());
    assertFalse(last.hasMore());
    assertTrue(last.windowLimited());
    assertThrows(IllegalArgumentException.class, () -> notifications.read(owner, -50));
    assertThrows(IllegalArgumentException.class, () -> notifications.read(owner, 1000));
  }

  @Test
  void readPreferenceDoesNotAcknowledgeOrResolveBusinessAlerts() throws Exception {
    String own = run(owner, ownerName, true), id = alert(own);
    String token = login(ownerName);
    long beforeOrders = jdbc.queryForObject("SELECT count(*) FROM orders", Long.class);
    var prefs =
        mapper.readTree(
            mvc.perform(get("/api/user/preferences").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    ((ObjectNode) prefs)
        .put(
            "notificationsReadAt",
            notifications.read(owner, 0).items().getFirst().occurredAt().toString());
    mvc.perform(
            put("/api/user/preferences")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(mapper.writeValueAsString(prefs)))
        .andExpect(status().isOk());
    mvc.perform(
            put("/api/user/preferences")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(mapper.writeValueAsString(prefs)))
        .andExpect(status().isConflict());
    assertEquals(
        "OPEN",
        jdbc.queryForObject(
            "SELECT status FROM paper_run_alerts WHERE alert_id=?", String.class, id));
    assertNull(
        jdbc.queryForObject(
            "SELECT acknowledged_at FROM paper_run_alerts WHERE alert_id=?", Timestamp.class, id));
    assertNull(
        jdbc.queryForObject(
            "SELECT resolved_at FROM paper_run_alerts WHERE alert_id=?", Timestamp.class, id));
    assertEquals(beforeOrders, jdbc.queryForObject("SELECT count(*) FROM orders", Long.class));
    assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM trades", Long.class));
    assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM ledger_entries", Long.class));
  }

  @Test
  void systemActorCannotBecomeUserOwnershipForNotificationsAnalysisSearchOrOrderRefresh()
      throws Exception {
    seeds.seedUsers(
        List.of(
            new SeedUserCommand(
                "system", passwords.encode(PASSWORD), List.of("OPERATOR", "VIEWER"), true)));
    long systemUser =
        jdbc.queryForObject("SELECT id FROM users WHERE username='system'", Long.class);
    assertEquals(
        0L,
        jdbc.queryForObject(
            "SELECT count(*) FROM exchange_accounts WHERE owner_user_id=?",
            Long.class,
            systemUser));
    String hidden = run(owner, "system", true);
    alert(hidden);
    // 历史系统哨兵只能走来源账户归属，不能因真人账号恰好同名而授予他人的对象。
    assertTrue(notifications.read(systemUser, 0).items().isEmpty());
    String token = login("system"), own = run(systemUser, "system", true);
    alert(own);
    assertEquals(
        List.of(own),
        notifications.read(systemUser, 0).items().stream().map(row -> row.paperRunId()).toList());
    String creatorVisible = run(other, ownerName, false);
    alert(creatorVisible);
    assertTrue(
        notifications.read(owner, 0).items().stream()
            .anyMatch(row -> row.paperRunId().equals(creatorVisible)));
    mvc.perform(
            get("/api/search")
                .header("Authorization", "Bearer " + token)
                .param("q", hidden)
                .param("kind", "SIM"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0));
    mvc.perform(
            get("/api/search")
                .header("Authorization", "Bearer " + token)
                .param("q", own)
                .param("kind", "SIM"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1));
    mvc.perform(
            get("/api/paper-trading/strategy-sim/analysis/runs/" + hidden)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
    var catalog =
        mapper.readTree(
            mvc.perform(
                    get("/api/paper-trading/strategy-sim/analysis/runs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertEquals(1, catalog.path("items").size());
    assertEquals(own, catalog.path("items").get(0).path("paperRunId").asText());
    long canonical =
        jdbc.queryForObject(
            "SELECT canonical_account_id FROM paper_trading_runs WHERE paper_run_id=?",
            Long.class,
            hidden);
    String order = "nsorder-" + UUID.randomUUID();
    jdbc.update(
        "INSERT INTO"
            + " orders(order_id,account_id,symbol,client_order_id,side,type,qty,status,trace_id,venue,exchange_code,trade_env,created_at)"
            + " VALUES(?,?,'BTC-USDT',?,'BUY','MARKET',0.00000001,'REJECTED','notice-isolation-fixture','PAPER','PAPER','SIM',now())",
        order,
        canonical,
        order);
    long ownCanonical =
        jdbc.queryForObject(
            "SELECT canonical_account_id FROM paper_trading_runs WHERE paper_run_id=?",
            Long.class,
            own);
    String ownOrder = "nsorder-" + UUID.randomUUID();
    jdbc.update(
        "INSERT INTO"
            + " orders(order_id,account_id,symbol,client_order_id,side,type,qty,status,trace_id,venue,exchange_code,trade_env,created_at)"
            + " VALUES(?,?,'BTC-USDT',?,'BUY','MARKET',0.00000001,'REJECTED','notice-isolation-fixture','PAPER','PAPER','SIM',now())",
        ownOrder,
        ownCanonical,
        ownOrder);
    mvc.perform(
            get("/api/paper-trading/strategy-sim/runs/" + own + "/orders/" + ownOrder)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.order.order_id").value(ownOrder));
    String path = "/api/paper-trading/strategy-sim/runs/" + hidden + "/orders/" + order;
    var before = jdbc.queryForList("SELECT status,updated_at FROM orders WHERE order_id=?", order);
    mvc.perform(get(path).header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
    mvc.perform(post(path + "/refresh-execution").header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
    assertEquals(
        before, jdbc.queryForList("SELECT status,updated_at FROM orders WHERE order_id=?", order));
    assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM trades", Long.class));
    assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM ledger_entries", Long.class));
  }

  private String login(String name) throws Exception {
    String body =
        mvc.perform(
                post("/api/auth/login")
                    .contentType("application/json")
                    .content(
                        mapper.writeValueAsString(Map.of("username", name, "password", PASSWORD))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(body).path("accessToken").asText();
  }

  /** 仅在本测试随机隔离库建立原事件夹具；摘要不得复制事件正文、metadata或别人的回执。 */
  @Test
  void shadowAndSystemSourcesKeepRealIdentitiesPermissionsAndReadOnlyBoundary() throws Exception {
    String adminName = "notice-admin-" + UUID.randomUUID();
    seeds.seedUsers(
        List.of(
            new SeedUserCommand(
                adminName, passwords.encode(PASSWORD), List.of("ADMIN", "VIEWER"), true)));
    long adminId =
        jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, adminName);
    String adminPaper = run(adminId, "system", true), hiddenPaper = run(other, "system", true);
    UUID ownShadow = notificationShadow(adminPaper, adminName),
        hiddenShadow = notificationShadow(hiddenPaper, otherName);
    UUID ownOperation = notificationOperation(adminId, adminName),
        hiddenOperation = notificationOperation(other, otherName);
    jdbc.update(
        "UPDATE scheduled_job_controls SET last_status='FAILED',last_started_at=now()-interval '1"
            + " second',last_finished_at=now(),last_error_code='ISOLATED_FIXTURE',consecutive_failures=1"
            + " WHERE job_key='PAPER_MATCHING'");
    var before =
        jdbc.queryForList(
            "SELECT job_key,last_status,last_finished_at FROM scheduled_job_controls ORDER BY"
                + " job_key");
    long ordersBefore = jdbc.queryForObject("SELECT count(*) FROM orders", Long.class);
    long ledgerBefore = jdbc.queryForObject("SELECT count(*) FROM ledger_entries", Long.class);
    var visible = notifications.read(adminId, 0);
    assertEquals("AUTHORIZED_USER_EVENTS", visible.scope());
    assertTrue(
        visible.items().stream()
            .anyMatch(
                row ->
                    "SHADOW_EVENT".equals(row.kind())
                        && ownShadow.toString().equals(row.sourceId())
                        && row.paperRunId() == null
                        && row.tradeEnv() == null));
    assertTrue(
        visible.items().stream()
            .anyMatch(
                row ->
                    "SYSTEM_OPERATION".equals(row.kind())
                        && ownOperation.toString().equals(row.sourceId())));
    assertTrue(
        visible.items().stream()
            .anyMatch(
                row ->
                    "SYSTEM_HEALTH".equals(row.kind()) && "PAPER_MATCHING".equals(row.sourceId())));
    assertFalse(
        visible.items().stream()
            .anyMatch(
                row ->
                    hiddenShadow.toString().equals(row.sourceId())
                        || hiddenOperation.toString().equals(row.sourceId())));
    assertTrue(
        notifications.read(other, 0).items().stream()
            .anyMatch(row -> hiddenShadow.toString().equals(row.sourceId())));
    assertFalse(
        notifications.read(other, 0).items().stream()
            .anyMatch(row -> row.kind().startsWith("SYSTEM_")));
    String body =
        mvc.perform(
                get("/api/user/notifications")
                    .header("Authorization", "Bearer " + login(adminName)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertFalse(body.contains("never-copy"));
    assertFalse(body.contains("metadata"));
    assertFalse(body.contains(hiddenOperation.toString()));
    assertEquals(
        before,
        jdbc.queryForList(
            "SELECT job_key,last_status,last_finished_at FROM scheduled_job_controls ORDER BY"
                + " job_key"));
    assertEquals(
        2L,
        jdbc.queryForObject(
            "SELECT count(*) FROM shadow_run_events WHERE shadow_run_id IN (?,?)",
            Long.class,
            ownShadow,
            hiddenShadow));
    assertEquals(
        "COMPLETED",
        jdbc.queryForObject(
            "SELECT status FROM scheduled_job_operations WHERE operation_id=?",
            String.class,
            ownOperation));
    assertEquals(ordersBefore, jdbc.queryForObject("SELECT count(*) FROM orders", Long.class));
    assertEquals(
        ledgerBefore, jdbc.queryForObject("SELECT count(*) FROM ledger_entries", Long.class));
    mvc.perform(
            get("/api/scheduler/operations/" + hiddenOperation)
                .header("Authorization", "Bearer " + login(otherName)))
        .andExpect(status().isForbidden());
  }

  private UUID notificationShadow(String paper, String creator) {
    UUID dataset = UUID.randomUUID(), shadow = UUID.randomUUID();
    String version =
        jdbc.queryForObject(
            "SELECT strategy_version_id FROM paper_trading_runs WHERE paper_run_id=?",
            String.class,
            paper);
    jdbc.update(
        "INSERT INTO"
            + " marketdata_datasets(dataset_id,dataset_name,exchange_code,market_type,symbol,\"interval\",start_time,end_time,status,quality_status,source,created_by)"
            + " VALUES(?,?,'OKX','SPOT','BTC-USDT','1h',now()-interval '2 hours',now()-interval '1"
            + " hour','CREATED','INCOMPLETE','NOTIFICATION_FIXTURE',?)",
        dataset,
        "notice-" + dataset,
        creator);
    jdbc.update(
        "INSERT INTO shadow_runs(id,strategy_version_id,dataset_id,status,idempotency_key,trace_id)"
            + " VALUES(?,?,?,'CREATED',?,'notification-fixture')",
        shadow,
        version,
        dataset,
        "notice-shadow-" + shadow);
    jdbc.update(
        "INSERT INTO"
            + " shadow_run_events(id,shadow_run_id,event_type,to_status,message,metadata,trace_id)"
            + " VALUES(?,?,'CREATED','CREATED','never-copy-body','{\"privatePayload\":\"never-copy-metadata\"}','notification-fixture')",
        UUID.randomUUID(),
        shadow);
    return shadow;
  }

  private UUID notificationOperation(long user, String actor) {
    UUID operation = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO"
            + " scheduled_job_operations(operation_id,owner_user_id,actor,operation_type,job_key,input_sha256,trace_id,status,execution_result,created_at,started_at,completed_at)"
            + " VALUES(?,?,?,'RUN_ONCE','PAPER_MATCHING',?,'notification-fixture','COMPLETED','FAILED',now()-interval"
            + " '1 second',now()-interval '1 second',now())",
        operation,
        user,
        actor,
        "a".repeat(64));
    return operation;
  }

  public static class Cleanup extends AbstractTestExecutionListener {
    @Override
    public int getOrder() {
      return 999;
    }

    @Override
    public void afterTestClass(TestContext context) {
      if (control != null && database != null) {
        assertTrue(database.matches("user_notifications_[0-9a-f]{32}"));
        control.execute("DROP DATABASE \"" + database + "\" WITH (FORCE)");
      }
    }
  }
}
