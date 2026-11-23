package com.guidinglight.nexusquant.demo.platform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.account.application.service.AccountPerformanceCalculator;
import com.guidinglight.nexusquant.account.domain.performance.AccountEquitySample;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 平台只能读取既有冻结估值与最小身份投影；账户从未被拼成一个虚构组合。 */
@Service
public final class PlatformReadService {
    private final PlatformAccess access;
    private final PlatformReadSource source;
    private final ObjectMapper mapper;
    private final AccountPerformanceCalculator calculator = new AccountPerformanceCalculator();

    public PlatformReadService(PlatformAccess access,PlatformReadSource source,ObjectMapper mapper) {
        this.access=access; this.source=source; this.mapper=mapper;
    }

    public JsonNode list(int page,int size,String search,Boolean enabled,String sort) {
        var actor=access.require();
        if (page<0 || page>100000 || size<1 || size>100 || search==null || search.length()>64
                || sort==null || !Set.of("registeredAt,asc","registeredAt,desc").contains(sort)) throw invalid();
        return source.read(jdbc -> parse(jdbc.queryForObject(
                "SELECT nq_platform_tenants(?,?,?,?,?,?,?,?)::text",String.class,
                actor.userId(),actor.sessionId(),actor.authVersion(),page,size,search,enabled,sort.endsWith(",asc"))));
    }

    public JsonNode summary(UUID tenantId) {
        var actor=access.require();
        return source.read(jdbc -> required(parse(jdbc.queryForObject(
                "SELECT nq_platform_tenant_summary(?,?,?,?)::text",String.class,
                actor.userId(),actor.sessionId(),actor.authVersion(),tenantId))));
    }

    public JsonNode performance(UUID tenantId) {
        var actor=access.require();
        return source.read(jdbc -> {
            JsonNode summary=required(parse(jdbc.queryForObject("SELECT nq_platform_tenant_summary(?,?,?,?)::text",
                    String.class,actor.userId(),actor.sessionId(),actor.authVersion(),tenantId)));
            JsonNode facts=parse(jdbc.queryForObject("SELECT nq_platform_sim_facts(?,?,?,?)::text",String.class,
                    actor.userId(),actor.sessionId(),actor.authVersion(),tenantId));
            return projectPerformance(tenantId,summary,facts);
        });
    }

    /** 只做状态与既有金额的呈现；净收益、比例和回撤继续使用 canonical calculator。 */
    ObjectNode projectPerformance(UUID tenantId,JsonNode summary,JsonNode facts) {
        ObjectNode result=mapper.createObjectNode();
        result.put("tenantId",tenantId.toString()).put("status","UNAVAILABLE")
                .put("reason","VERIFIED_CANONICAL_VALUATION_UNAVAILABLE")
                .put("source","CANONICAL_SIM_PROJECTION").put("valuationCurrency","USDT");
        result.set("simStatus",summary.path("simStatus"));
        for (String field: new String[]{"initialCapital","currentCash","equity","realizedPnl","unrealizedPnl",
                "netPnl","returnRate","drawdown","orderCount","tradeCount","asOf","coverageStart","coverageEnd"})
            result.putNull(field);
        result.putArray("history");
        if (facts==null || facts.isNull()) return result;
        for (String field: new String[]{"initialCapital","currentCash","orderCount","tradeCount","asOf"})
            result.set(field,facts.path(field));
        if (facts.path("initialCapital").isNull() || !facts.path("fundedAt").isTextual()) {
            result.put("reason","CANONICAL_FUNDING_UNAVAILABLE"); return result;
        }
        var history=facts.path("snapshots");
        if (!history.isArray() || history.isEmpty()) return result;
        if (history.size()>999) { result.put("reason","HISTORY_WINDOW_LIMITED"); return result; }
        BigDecimal capital=new BigDecimal(facts.path("initialCapital").asText());
        Instant fundedAt=Instant.parse(facts.path("fundedAt").asText());
        Instant readAt=Instant.parse(facts.path("asOf").asText());
        long account=facts.path("exchangeAccountId").asLong();
        var samples=new ArrayList<AccountEquitySample>();
        for (JsonNode snapshot:history) {
            samples.add(new AccountEquitySample(UUID.nameUUIDFromBytes(snapshot.path("id").asText()
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)),account,"USDT",
                    Instant.parse(snapshot.path("at").asText()),new BigDecimal(snapshot.path("equity").asText()),
                    capital,"AVAILABLE","{}"));
        }
        var calculated=calculator.summarizeFromFunding(account,"USDT",fundedAt,capital,readAt,samples);
        var last=history.get(history.size()-1);
        Instant valuationAt=Instant.parse(last.path("at").asText());
        Instant ledgerAt=facts.path("latestLedgerAt").isTextual()
                ? Instant.parse(facts.path("latestLedgerAt").asText()) : null;
        Instant ledgerRecordedAt=facts.path("latestLedgerRecordedAt").isTextual()
                ? Instant.parse(facts.path("latestLedgerRecordedAt").asText()) : null;
        Instant snapshotRecordedAt=last.path("recordedAt").isTextual()
                ? Instant.parse(last.path("recordedAt").asText()) : null;
        boolean stale=Duration.between(valuationAt,readAt).compareTo(Duration.ofHours(2))>0
                || (ledgerAt!=null && ledgerAt.isAfter(valuationAt))
                || (ledgerRecordedAt!=null && (snapshotRecordedAt==null || ledgerRecordedAt.isAfter(snapshotRecordedAt)));
        result.put("status",stale?"STALE":"AVAILABLE").put("reason",stale?"LATEST_VERIFIED_SNAPSHOT_IS_HISTORICAL":null);
        result.put("asOf",valuationAt.toString()).put("coverageStart",fundedAt.toString()).put("coverageEnd",valuationAt.toString());
        result.set("history",history);
        result.set("equity",last.path("equity"));
        result.set("realizedPnl",last.path("realizedPnl")); result.set("unrealizedPnl",last.path("unrealizedPnl"));
        result.put("netPnl",calculated.pnl()==null?null:calculated.pnl().toPlainString());
        result.put("returnRate",calculated.returnRatio()==null?null:calculated.returnRatio().toPlainString());
        result.set("drawdown",last.path("drawdown"));
        // 现金与权益必须指向同一个估值时点，较新的现金投影不能混入历史权益。
        result.set("currentCash",last.path("cash"));
        return result;
    }

    private JsonNode parse(String raw) {
        if (raw==null) return mapper.nullNode();
        try { return mapper.readTree(raw); }
        catch (JsonProcessingException failure) { throw new IllegalStateException("PLATFORM_PROJECTION_INVALID",failure); }
    }
    private static JsonNode required(JsonNode node) {
        if (node==null || node.isNull()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"PLATFORM_TENANT_NOT_FOUND");
        return node;
    }
    private static ResponseStatusException invalid() { return new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_PLATFORM_PAGE"); }
}
