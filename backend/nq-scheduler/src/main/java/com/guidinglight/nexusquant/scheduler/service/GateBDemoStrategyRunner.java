package com.guidinglight.nexusquant.scheduler.service;

import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.core.model.OrderRecord;
import com.guidinglight.nexusquant.core.service.OrderCommandService;
import com.guidinglight.nexusquant.core.service.PlaceOrderRequest;
import com.guidinglight.nexusquant.core.service.PlaceOrderResult;
import com.guidinglight.nexusquant.core.service.port.AuditLogRepository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * GateBDemoStrategyRunner 提供 Gate B 最小策略触发入口。
 * <p>
 * Why:
 * Gate B 目标是“可一键跑通闭环”，因此需要一个稳定且可重启恢复的触发器，
 * 在没有复杂策略引擎前先以固定参数触发单笔下单链路。
 */
@Component
public class GateBDemoStrategyRunner {

    private static final Logger log = LoggerFactory.getLogger(GateBDemoStrategyRunner.class);

    private static final Long DEMO_ACCOUNT_ID = 1001L;
    private static final String DEMO_ACCOUNT_CODE = "PAPER-DEMO-1001";
    private static final String DEMO_RUN_ID = "run-gateb-demo-001";
    private static final String DEMO_STRATEGY_ID = "strategy-gateb-demo";
    private static final String DEMO_CLIENT_ORDER_ID = "coid-gateb-demo-001";
    private static final String DEMO_TRACE_ID = "trc-gateb-demo-001";
    private static final String DEMO_SYMBOL = "BTC-USDT";
    private static final BigDecimal DEMO_QTY = new BigDecimal("0.01000000");

    private final JdbcTemplate jdbcTemplate;
    private final OrderCommandService orderCommandService;
    private final AuditLogRepository auditLogRepository;
    private final AtomicBoolean startupProbeLogged;
    private volatile boolean demoRunnerBlocked;

    /**
     * @param jdbcTemplate        JDBC 执行器
     * @param orderCommandService 下单编排服务
     * @param auditLogRepository  审计仓储
     */
    public GateBDemoStrategyRunner(
            JdbcTemplate jdbcTemplate,
            OrderCommandService orderCommandService,
            AuditLogRepository auditLogRepository
    ) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.orderCommandService = Objects.requireNonNull(orderCommandService, "orderCommandService must not be null");
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository must not be null");
        this.startupProbeLogged = new AtomicBoolean(false);
        this.demoRunnerBlocked = false;
    }

    /**
     * 定时触发 Gate B 演示策略。
     */
    @Scheduled(
            fixedDelayString = "${nq.strategy.demo.fixed-delay-ms:5000}",
            initialDelayString = "${nq.strategy.demo.initial-delay-ms:1000}"
    )
    public void runDemoTick() {
        if (!ensureDatabaseReadyForGateBDemo()) {
            return;
        }
        ensureDemoAccount();
        ensureDemoStrategyRun();

        Optional<OrderRecord> existing = orderCommandService.findByAccountAndClientOrderId(
                DEMO_ACCOUNT_ID,
                DEMO_CLIENT_ORDER_ID
        );
        if (existing.isPresent() && isTerminal(existing.get().status())) {
            return;
        }

        PlaceOrderResult result = orderCommandService.placeOrder(new PlaceOrderRequest(
                DEMO_ACCOUNT_ID,
                DEMO_RUN_ID,
                DEMO_CLIENT_ORDER_ID,
                DEMO_SYMBOL,
                OrderSide.BUY,
                OrderType.MARKET,
                null,
                DEMO_QTY,
                DEMO_TRACE_ID
        ));
        auditLogRepository.append(
                "STRATEGY",
                "DEMO_STRATEGY_TRIGGERED",
                DEMO_RUN_ID,
                DEMO_TRACE_ID,
                java.util.Map.of(
                        "order_id", result.orderId(),
                        "status", result.status().name(),
                        "idempotent_hit", result.idempotentHit(),
                        "ts", Instant.now().toString()
                )
        );
    }

    private void ensureDemoAccount() {
        jdbcTemplate.update(
                """
                        INSERT INTO accounts (account_id, account_code, venue, status)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT (account_id) DO NOTHING
                        """,
                DEMO_ACCOUNT_ID,
                DEMO_ACCOUNT_CODE,
                "PAPER",
                "ACTIVE"
        );
    }

    private void ensureDemoStrategyRun() {
        jdbcTemplate.update(
                """
                        INSERT INTO strategy_runs (run_id, strategy_id, account_id, status, started_at, trace_id)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT (run_id) DO NOTHING
                        """,
                DEMO_RUN_ID,
                DEMO_STRATEGY_ID,
                DEMO_ACCOUNT_ID,
                "RUNNING",
                Timestamp.from(Instant.now()),
                DEMO_TRACE_ID
        );
    }

    /**
     * 运行前做数据库“连接指纹 + 关键表存在性”探测。
     * <p>
     * Why:
     * 本地常见“宿主 postgres 与 docker postgres 共用 5432”场景会导致应用连错库。
     * 若不在业务入口前做显式探测，runner 会在错误实例持续重试，难以及时发现。
     *
     * @return true 表示数据库指向与 schema 至少满足 Gate B 最小执行前提；false 表示已阻断 runner
     */
    private boolean ensureDatabaseReadyForGateBDemo() {
        if (demoRunnerBlocked) {
            return false;
        }
        if (!startupProbeLogged.compareAndSet(false, true)) {
            return true;
        }

        try {
            Map<String, Object> fingerprint = jdbcTemplate.queryForMap(
                    "SELECT inet_server_addr() AS server_addr, inet_server_port() AS server_port, current_database() AS db_name"
            );
            String datasourceUrl = resolveDatasourceUrl();
            boolean ordersTableExists = Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                    """
                            SELECT EXISTS (
                                SELECT 1
                                FROM information_schema.tables
                                WHERE table_schema = current_schema()
                                  AND table_name = 'orders'
                            )
                            """,
                    Boolean.class
            ));
            log.info(
                    "GateB demo database fingerprint: datasource_url={}, server_addr={}, server_port={}, current_database={}",
                    datasourceUrl,
                    fingerprint.get("server_addr"),
                    fingerprint.get("server_port"),
                    fingerprint.get("db_name")
            );

            if (!ordersTableExists) {
                demoRunnerBlocked = true;
                log.error(
                        "GateB demo runner blocked: required table 'orders' not found. datasource_url={}, server_addr={}, server_port={}, current_database={}",
                        datasourceUrl,
                        fingerprint.get("server_addr"),
                        fingerprint.get("server_port"),
                        fingerprint.get("db_name")
                );
                return false;
            }
            return true;
        } catch (RuntimeException ex) {
            demoRunnerBlocked = true;
            log.error(
                    "GateB demo runner blocked: failed to probe database fingerprint or schema readiness, reason={}",
                    ex.getMessage(),
                    ex
            );
            return false;
        }
    }

    /**
     * 解析当前 DataSource URL 并做最小脱敏输出。
     * <p>
     * Why:
     * 连接指纹需要可追踪的 URL，但不能把潜在凭证参数写入日志。
     *
     * @return 可用于日志输出的 datasource URL；无法获取时返回 unknown
     */
    private String resolveDatasourceUrl() {
        DataSource dataSource = jdbcTemplate.getDataSource();
        if (dataSource == null) {
            return "unknown";
        }
        try (Connection connection = dataSource.getConnection()) {
            return sanitizeDatasourceUrl(connection.getMetaData().getURL());
        } catch (SQLException ex) {
            return "unknown";
        }
    }

    /**
     * 移除 JDBC URL 中可能存在的密码参数。
     * <p>
     * Why:
     * 允许输出定位信息（host/port/db），但必须避免敏感信息泄露。
     */
    private String sanitizeDatasourceUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "unknown";
        }
        return rawUrl.replaceAll("(?i)(password=)[^&;]+", "$1***");
    }

    private boolean isTerminal(OrderStatus status) {
        return status == OrderStatus.FILLED
                || status == OrderStatus.RISK_REJECTED
                || status == OrderStatus.CANCELLED
                || status == OrderStatus.REJECTED
                || status == OrderStatus.FAILED;
    }
}
