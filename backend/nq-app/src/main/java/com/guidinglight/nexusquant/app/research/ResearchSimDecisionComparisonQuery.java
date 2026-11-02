package com.guidinglight.nexusquant.app.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.strategy.domain.SpotBarIdentity;
import com.guidinglight.nexusquant.strategy.domain.SpotTargetStrategy;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 在原SIM持久化观察上重算同一策略；不配对不同市场时期的历史回测信号。 */
@Component
public class ResearchSimDecisionComparisonQuery {
    private static final int LIMIT=50;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ResearchSimDecisionComparisonQuery(JdbcTemplate jdbc,ObjectMapper mapper) {
        this.jdbc=new JdbcTemplate(jdbc.getDataSource()); this.jdbc.setQueryTimeout(8); this.mapper=mapper;
    }

    /** 调用方先完成精确实例所有者校验；一个有界连接读取来源、相邻状态和canonical执行事实。 */
    public Decisions read(String run,long account,SpotTargetStrategy strategy,long total) {
        var rows=jdbc.query("""
            WITH windows AS (
                SELECT * FROM strategy_sim_decisions WHERE paper_run_id=? AND canonical_account_id=?
                    AND nq_entity_tenant('paper',paper_run_id)=nq_current_tenant_id()
                ORDER BY signal_open_time DESC LIMIT 50
            )
            SELECT d.*,d.input_snapshot_json::text snapshot,
                prev.signal_open_time previous_open,prev.target_exposure previous_target,
                prev.strategy_version_id previous_version,prev.strategy_checksum previous_checksum,
                c.seed_last_open_time,c.strategy_version_id cursor_version,c.strategy_checksum cursor_checksum,
                proof.checked,proof.invalid,o.status order_status,o.qty order_qty,
                fills.fill_count,fills.measured_fees,fills.measured_currencies,fills.currency_count,fills.currency,fills.fee,
                fills.fill_qty,fills.fill_price
            FROM windows d JOIN continuous_sim_runs c USING(paper_run_id)
            LEFT JOIN LATERAL (SELECT p.* FROM strategy_sim_decisions p
                WHERE p.paper_run_id=d.paper_run_id AND p.canonical_account_id=d.canonical_account_id
                    AND p.signal_open_time<d.signal_open_time ORDER BY p.signal_open_time DESC LIMIT 1) prev ON true
            LEFT JOIN LATERAL (
                SELECT count(*) checked,count(*) FILTER(WHERE saved.open_time IS NULL OR saved.bar_json->0<>b.value
                    OR saved.observed_at>greatest(d.created_at,coalesce(
                        (d.input_snapshot_json->'executionQuote'->>'observedAt')::timestamptz,d.created_at))) invalid
                FROM jsonb_array_elements(CASE WHEN jsonb_typeof(d.input_snapshot_json->'signalBars')='array'
                    THEN d.input_snapshot_json->'signalBars' ELSE '[]'::jsonb END) b(value)
                LEFT JOIN continuous_sim_bars saved ON saved.paper_run_id=d.paper_run_id
                    AND saved.open_time=(b.value->>'openTime')::timestamptz
            ) proof ON true
            LEFT JOIN orders o ON o.order_id=d.order_id AND o.account_id=d.canonical_account_id
                AND o.trade_env='SIM' AND o.exchange_code='PAPER'
            LEFT JOIN LATERAL (
                SELECT count(*) fill_count,count(t.fee) measured_fees,count(t.fee_currency) measured_currencies,
                    count(DISTINCT t.fee_currency) currency_count,
                    min(t.fee_currency) currency,sum(t.fee) fee,sum(t.qty) fill_qty,
                    sum(t.qty*t.price)/nullif(sum(t.qty),0) fill_price
                FROM trades t WHERE t.order_id=o.order_id AND t.account_id=o.account_id
                    AND t.trade_env='SIM' AND t.exchange_code='PAPER'
            ) fills ON true
            ORDER BY d.signal_open_time DESC
            """,(r,n)->row(r,strategy),run,account);
        String status=rows.isEmpty() || rows.stream().allMatch(r->"UNAVAILABLE".equals(r.status()))?"UNAVAILABLE":
                rows.stream().allMatch(r->"NOT_COMPARABLE".equals(r.status()))?"NOT_COMPARABLE":"AVAILABLE";
        return new Decisions(status,rows.isEmpty()?"NO_SAVED_DECISIONS":null,rows,total,LIMIT,total>LIMIT);
    }

    private Decision row(ResultSet r,SpotTargetStrategy strategy) throws SQLException {
        String id=r.getString("decision_id"),version=r.getString("strategy_version_id");
        Instant open=time(r,"signal_open_time"),available=time(r,"signal_available_at"),execution=time(r,"execution_open_time");
        String decisionStatus=r.getString("status"),reason=r.getString("reason"),orderStatus=r.getString("order_status");
        var attribution=new ArrayList<String>();
        long fills=r.getLong("fill_count");
        String fillStatus=r.getString("order_id")==null?"NO_ORDER":orderStatus==null?"UNAVAILABLE":fills==0?"NO_FILL":
                r.getBigDecimal("fill_qty").compareTo(r.getBigDecimal("order_qty"))<0?"PARTIAL":"FILLED";
        String fee=fills>0 && fills==r.getLong("measured_fees") && fills==r.getLong("measured_currencies") && r.getLong("currency_count")==1
                && r.getString("currency")!=null?decimal(r.getBigDecimal("fee")):null;
        String currency=fee==null?null:r.getString("currency");
        String slippage=null,expectedAction=null,actualAction=null,comparison="NOT_COMPARABLE";
        try {
            JsonNode snapshot=mapper.readTree(r.getString("snapshot"));
            actualAction=snapshot.path("strategyDecision").path("action").asText(null);
            require(strategy.strategyVersionId().equals(version) && strategy.checksum().equals(r.getString("strategy_checksum"))
                    && version.equals(r.getString("cursor_version")) && strategy.checksum().equals(r.getString("cursor_checksum")),"STRATEGY_IDENTITY_UNAVAILABLE");
            JsonNode saved=snapshot.path("signalBars");
            require(saved.isArray() && !saved.isEmpty() && saved.size()<=720 && r.getLong("checked")==saved.size()
                    && r.getLong("invalid")==0,"DATA_SOURCE_OR_CONTENT_DIFFERENCE");
            List<HistoricalBar> bars=bars(saved);
            require(bars.stream().allMatch(b->"OKX".equals(b.exchangeCode()) && "SPOT".equals(b.marketType())
                    && "BTC-USDT".equals(b.symbol()) && b.interval()==BarInterval.ONE_HOUR),"DATA_SOURCE_OR_CONTENT_DIFFERENCE");
            require(SpotBarIdentity.capture(bars,mapper).sha256().equals(r.getString("input_sha256")),"DATA_SOURCE_OR_CONTENT_DIFFERENCE");
            SpotTargetStrategy.PositionState previous=SpotTargetStrategy.PositionState.FLAT;
            if (strategy.stateful()) {
                Instant prior=time(r,"previous_open");
                if (prior==null) require(time(r,"seed_last_open_time").plusSeconds(3600).equals(open),"PREVIOUS_STRATEGY_STATE_UNAVAILABLE");
                else {
                    require(prior.plusSeconds(3600).equals(open) && version.equals(r.getString("previous_version"))
                            && strategy.checksum().equals(r.getString("previous_checksum")),"PREVIOUS_STRATEGY_STATE_UNAVAILABLE");
                    previous=strategy.stateForTarget(r.getBigDecimal("previous_target"));
                }
            }
            var expected=strategy.evaluate(bars,previous);
            expectedAction=expected.action().name();
            comparison="MATCH";
            if (!bars.getLast().openTime().equals(open) || !expected.availableAt().equals(available)) {
                attribution.add("SIGNAL_TIME_DIFFERENCE"); comparison="DIFFERENT";
            }
            if (actualAction==null) {
                comparison="UNAVAILABLE"; attribution.add("INSUFFICIENT_FACTS");
            } else if (!expectedAction.equals(actualAction) || expected.targetExposure().compareTo(r.getBigDecimal("target_exposure"))!=0
                    || !previous.name().equals(snapshot.path("strategyDecision").path("previousState").asText())
                    || !expected.desiredState().name().equals(snapshot.path("strategyDecision").path("desiredState").asText())) {
                attribution.add("DECISION_ACTION_DIFFERENCE"); comparison="DIFFERENT";
            }
            if ("RISK_REJECTED".equals(decisionStatus) || List.of("RISK_REJECTED","REJECTED","FAILED","CANCELLED").contains(orderStatus==null?"":orderStatus))
                attribution.add("RISK_REJECTION_OR_EXECUTION_FAILURE");
            if ("NOT_TRADABLE".equals(decisionStatus) || "NO_FILL".equals(fillStatus) || "PARTIAL".equals(fillStatus))
                attribution.add("MARKET_EXECUTION_EVENT_DIFFERENCE");
            JsonNode quote=snapshot.path("executionQuote");
            if (execution!=null) {
                if (!quote.hasNonNull("observedAt") || !quote.hasNonNull("price")) attribution.add("INSUFFICIENT_FACTS");
                else {
                    Instant quoteAt=Instant.parse(quote.path("observedAt").asText());
                    if (!execution.equals(quoteAt) || !execution.isAfter(expected.availableAt())) attribution.add("MARKET_EXECUTION_EVENT_DIFFERENCE");
                    BigDecimal price=new BigDecimal(quote.path("price").asText());
                    if (price.signum()>0 && fills>0) {
                        BigDecimal actual=r.getBigDecimal("fill_price");
                        BigDecimal bps=actual.subtract(price).divide(price,MathContext.DECIMAL128).multiply(new BigDecimal("10000"));
                        if ("SELL".equals(r.getString("side"))) bps=bps.negate();
                        slippage=decimal(bps);
                        if (actual.compareTo(r.getBigDecimal("execution_price"))!=0) attribution.add("FEE_OR_SLIPPAGE_ASSUMPTION_DIFFERENCE");
                    }
                }
            }
            if (fills>0 && fee==null) attribution.add("INSUFFICIENT_FACTS");
            // 多笔成交需要逐笔费用舍入口径；原币或优惠费用还需要原charge合同，聚合值不能反推费率。
            if (fills>1 || fills>0 && r.getBigDecimal("fee_rate")==null) attribution.add("INSUFFICIENT_FACTS");
            if (fills==1 && fee!=null && "USDT".equals(currency) && r.getBigDecimal("fee_rate")!=null) {
                BigDecimal assumed=r.getBigDecimal("fill_qty").multiply(r.getBigDecimal("fill_price"))
                        .multiply(r.getBigDecimal("fee_rate")).setScale(8,RoundingMode.HALF_UP);
                if (assumed.compareTo(new BigDecimal(fee))!=0) attribution.add("FEE_OR_SLIPPAGE_ASSUMPTION_DIFFERENCE");
            }
            if (attribution.isEmpty()) attribution.add("SAME_SAVED_OBSERVATION_AND_SIGNAL");
        } catch (Incomparable e) {
            attribution.add(e.getMessage()); attribution.add("INSUFFICIENT_FACTS"); reason=e.getMessage();
        } catch (java.io.IOException | IllegalArgumentException | IllegalStateException e) {
            comparison="NOT_COMPARABLE"; expectedAction=null;
            attribution.add("INSUFFICIENT_FACTS"); reason="SAVED_OBSERVATION_INVALID";
        }
        return new Decision(id,version,open,available,expectedAction,actualAction,decisionStatus,execution,orderStatus,fillStatus,
                fee,currency,slippage,comparison,List.copyOf(new LinkedHashSet<>(attribution)),reason);
    }

    private List<HistoricalBar> bars(JsonNode nodes) {
        var bars=new ArrayList<HistoricalBar>(nodes.size());
        for (JsonNode b:nodes) bars.add(new HistoricalBar(b.path("exchangeCode").asText(),b.path("marketType").asText(),b.path("symbol").asText(),
                BarInterval.fromWireValue(b.path("interval").asText()),Instant.parse(b.path("openTime").asText()),Instant.parse(b.path("closeTime").asText()),
                number(b,"openPrice"),number(b,"highPrice"),number(b,"lowPrice"),number(b,"closePrice"),number(b,"volume"),
                b.path("quoteVolume").isNull()?null:number(b,"quoteVolume"),b.path("tradeCount").isNull()?null:b.path("tradeCount").longValue(),
                b.path("qualityStatus").asText(),b.path("rawPayloadJson").asText(),Instant.parse(b.path("availableAt").asText())));
        return bars;
    }
    private static BigDecimal number(JsonNode n,String field) {return new BigDecimal(n.path(field).asText());}
    private static String decimal(BigDecimal n) {return n==null?null:n.toPlainString();}
    private static Instant time(ResultSet r,String field) throws SQLException {var t=r.getTimestamp(field);return t==null?null:t.toInstant();}
    private static void require(boolean valid,String reason) {if(!valid) throw new Incomparable(reason);}
    private static class Incomparable extends RuntimeException {Incomparable(String reason) {super(reason);}}
    public static Decisions unavailable(String reason) {return new Decisions("UNAVAILABLE",reason,List.of(),0,LIMIT,false);}
    public record Decisions(String status,String reason,List<Decision> items,long total,int limit,boolean windowLimited) {}
    public record Decision(String decisionId,String strategyVersionId,Instant signalOpenTime,Instant signalAvailableAt,
            String expectedAction,String actualDecision,String decisionStatus,Instant executionOpenTime,String orderStatus,String fillStatus,
            String fee,String feeCurrency,String slippage,String status,List<String> attribution,String reason) {}
}
