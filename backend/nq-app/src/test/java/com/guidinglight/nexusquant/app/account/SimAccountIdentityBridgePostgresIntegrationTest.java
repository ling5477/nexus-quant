package com.guidinglight.nexusquant.app.account;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCreateCommand;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountCommandService;
import com.guidinglight.nexusquant.account.application.exception.ExchangeAccountNotFoundException;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.account.infra.jdbc.CanonicalLegacyAccountBridgeService;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 全新 V58 数据库上证明正式 SIM 账户可直接供 Strategy 使用，且旧账户补齐可并发重入。 */
@EnabledIfSystemProperty(named = "nq.sim-account.pg.required", matches = "true")
@ActiveProfiles("local")
@SpringBootTest(classes = NexusQuantApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"nq.instrument.catalog-sync.enabled=false", "nq.okx.recovery.enabled=false",
                "nq.okx.ws.enabled=false", "nq.binance.ws.enabled=false",
                "spring.task.scheduling.enabled=false"})
@AutoConfigureMockMvc
class SimAccountIdentityBridgePostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired ExchangeAccountCommandService commands;
    @Autowired ExchangeAccountRepository accounts;
    @Autowired CanonicalLegacyAccountBridgeService bridge;

    @Test
    void freshAccountCreatesStrategyAndExistingAccountResolvesOnceUnderConcurrency() throws Exception {
        assertEquals(0, count("SELECT count(*) FROM accounts"));
        assertEquals(58, count("SELECT max(version::integer) FROM flyway_schema_history WHERE success"));
        String token = mapper.readTree(mvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"admin\",\"password\":\"ChangeMe123!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("accessToken").asText();
        long owner = jdbc.queryForObject("SELECT id FROM users WHERE username='admin'", Long.class);

        JsonNode created = mapper.readTree(mvc.perform(post("/api/exchange-accounts")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"exchangeCode\":\"OKX\",\"tradeEnv\":\"SIM\","
                        + "\"accountAlias\":\"sim-identity-proof\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long exchangeId = created.path("exchangeAccountId").asLong();
        long legacyId = created.path("legacyAccountId").asLong();
        assertTrue(exchangeId > 0 && legacyId > 0);
        assertEquals(legacyId, count("SELECT legacy_account_id FROM exchange_accounts WHERE exchange_account_id=" + exchangeId));
        assertEquals("nq-okx-sim-" + exchangeId,
                jdbc.queryForObject("SELECT account_code FROM accounts WHERE account_id=?", String.class, legacyId));
        assertEquals("OKX|ACTIVE", jdbc.queryForObject(
                "SELECT venue||'|'||status FROM accounts WHERE account_id=?", String.class, legacyId));

        JsonNode strategy = mapper.readTree(mvc.perform(post("/api/strategies")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"strategyCode\":\"sim-identity-proof\",\"strategyName\":\"SIM identity proof\","
                        + "\"strategyType\":\"SPOT_SMA_TARGET_V1\",\"exchangeCode\":\"OKX\","
                        + "\"accountId\":" + legacyId + ",\"tradeEnv\":\"SIM\",\"configSnapshot\":\"{}\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(strategy.path("strategyId").asText().startsWith("str-"));
        assertEquals(legacyId, count("SELECT account_id FROM strategy_definitions WHERE strategy_code='sim-identity-proof'"));

        JsonNode binance = mapper.readTree(mvc.perform(post("/api/exchange-accounts")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"exchangeCode\":\"BINANCE\",\"tradeEnv\":\"SIM\","
                        + "\"accountAlias\":\"binance-sim-identity-proof\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long binanceExchangeId = binance.path("exchangeAccountId").asLong();
        long binanceLegacyId = binance.path("legacyAccountId").asLong();
        assertTrue(binanceExchangeId > 0 && binanceLegacyId > 0);
        assertEquals(binanceLegacyId, count("SELECT legacy_account_id FROM exchange_accounts WHERE exchange_account_id="
                + binanceExchangeId));
        assertEquals("nq-binance-sim-" + binanceExchangeId + "|BINANCE|ACTIVE",
                jdbc.queryForObject("SELECT account_code||'|'||venue||'|'||status FROM accounts WHERE account_id=?",
                        String.class, binanceLegacyId));

        ExchangeAccountSummary old = accounts.create(owner, "OKX", "SIM", "old-unbridged", null, Instant.now());
        assertEquals(null, old.legacyAccountId());
        JsonNode repaired = mapper.readTree(mvc.perform(post("/api/exchange-accounts/" + old.exchangeAccountId()
                        + "/resolve-sim-identity").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long repairedId = repaired.path("legacyAccountId").asLong();
        assertTrue(repairedId > 0);
        assertEquals(repairedId, commands.resolveSimIdentity(owner, old.exchangeAccountId()).legacyAccountId());

        ExchangeAccountSummary concurrent = accounts.create(owner, "OKX", "SIM", "concurrent-unbridged",
                null, Instant.now());
        CountDownLatch start = new CountDownLatch(1);
        List<Long> resolved = new ArrayList<>();
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> { start.await(); return commands.resolveSimIdentity(owner,
                    concurrent.exchangeAccountId()).legacyAccountId(); });
            var second = workers.submit(() -> { start.await(); return commands.resolveSimIdentity(owner,
                    concurrent.exchangeAccountId()).legacyAccountId(); });
            start.countDown();
            resolved.add(first.get(15, TimeUnit.SECONDS));
            resolved.add(second.get(15, TimeUnit.SECONDS));
        }
        assertEquals(resolved.getFirst(), resolved.getLast());
        assertEquals(1, count("SELECT count(*) FROM accounts WHERE account_code='nq-okx-sim-"
                + concurrent.exchangeAccountId() + "'"));
        assertEquals(resolved.getFirst(), commands.resolveSimIdentity(owner,
                concurrent.exchangeAccountId()).legacyAccountId());

        assertThrows(ExchangeAccountNotFoundException.class,
                () -> commands.resolveSimIdentity(owner + 1000, concurrent.exchangeAccountId()));
        assertThrows(LiveControlException.class, () -> commands.create(owner,
                new ExchangeAccountCreateCommand("UNSUPPORTED", "SIM", "rollback-proof", null)));
        assertEquals(0, count("SELECT count(*) FROM exchange_accounts WHERE account_alias='rollback-proof'"));

        ExchangeAccountSummary wrongBinding = accounts.create(owner, "OKX", "SIM", "wrong-binding", null,
                Instant.now());
        long wrongLegacy = jdbc.queryForObject("""
                INSERT INTO accounts(account_code,venue,status) VALUES ('wrong-code','OKX','ACTIVE')
                RETURNING account_id
                """, Long.class);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE exchange_accounts SET legacy_account_id=? WHERE exchange_account_id=?",
                wrongLegacy, wrongBinding.exchangeAccountId()));
        assertEquals(null, accounts.findById(wrongBinding.exchangeAccountId()).orElseThrow().legacyAccountId());

        assertThrows(LiveControlException.class, () -> bridge.resolveOrCreate(
                createdAccount(exchangeId, legacyId, owner), "live-reject-sim", Instant.now()));
        ExchangeAccountSummary live = accounts.create(owner, "OKX", "LIVE", "live-scope-proof", null,
                Instant.now());
        assertThrows(LiveControlException.class,
                () -> commands.resolveSimIdentity(owner, live.exchangeAccountId()));
        long liveLegacy = bridge.resolveOrCreate(live, "live-positive-proof", Instant.now());
        assertEquals("nq-okx-live-" + live.exchangeAccountId(), jdbc.queryForObject(
                "SELECT account_code FROM accounts WHERE account_id=?", String.class, liveLegacy));
        assertEquals(0, count("SELECT count(*) FROM exchange_account_credentials"));
    }

    private ExchangeAccountSummary createdAccount(long exchangeId, long legacyId, long owner) {
        return new ExchangeAccountSummary(exchangeId, legacyId, owner, "OKX", "SIM",
                "sim-identity-proof", null, false, "ACTIVE");
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        assertNotNull(value);
        return value;
    }
}
