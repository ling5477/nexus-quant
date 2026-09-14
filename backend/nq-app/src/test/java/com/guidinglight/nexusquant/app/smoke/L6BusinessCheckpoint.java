package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.guidinglight.nexusquant.app.smoke.L5BoundedWorkloadTest.number;
import static com.guidinglight.nexusquant.app.smoke.L5BoundedWorkloadTest.sample;
import static com.guidinglight.nexusquant.app.smoke.L5BoundedWorkloadTest.facts;
import static com.guidinglight.nexusquant.app.smoke.L5BoundedWorkloadTest.cursor;
import static com.guidinglight.nexusquant.app.smoke.L5BoundedWorkloadTest.http;

/** 校准和正式运行复用同一持久化与Venue关系检查，未完成状态不算全链成功。 */
final class L6BusinessCheckpoint {
    private static final ObjectMapper JSON = new ObjectMapper();
    static final class StoragePending extends RuntimeException { }
    static ObjectNode verify(Connection reader, String endpoint, List<B0Processes.Child> children,
                             Path dir, String phase, int checkIndex, boolean calibration) throws Exception {
        return verify(reader, endpoint, children, dir, phase, checkIndex, calibration, 0, List.of());
    }
    static ObjectNode verify(Connection reader, String endpoint, List<B0Processes.Child> children,
                             Path dir, String phase, int checkIndex, boolean calibration, int formalBudget,
                             List<L6DeterministicPacer.Slot> slots) throws Exception {
        return verify(reader, endpoint, children, dir, phase, checkIndex, calibration, formalBudget, slots, "FORMAL_L6_A");
    }
    static ObjectNode verify(Connection reader, String endpoint, List<B0Processes.Child> children,
                             Path dir, String phase, int checkIndex, boolean calibration, int formalBudget,
                             List<L6DeterministicPacer.Slot> slots, String qualificationMode) throws Exception {
        // 真实完成后才用完整源账务重建；短暂在途状态不能被误报成投影损坏。
        long deadline=System.nanoTime()+Duration.ofSeconds(120).toNanos();
        long waitingFrom = -1;
        while(true) {
            long busy=number(reader,"SELECT count(*) FROM strategy_runs WHERE status<>'SUCCEEDED'");
            ObjectNode sample=sample(reader);reader.commit();
            if(busy==0 && sample.path("backlog").asInt()==0)break;
            // storage采样不能进入旧120秒等待；未完成交由既有5秒reconcile周期继续推进。
            if (L6StorageCalibrationContract.MODE.equals(qualificationMode)) throw new StoragePending();
            if (waitingFrom < 0) waitingFrom = System.nanoTime();
            if(System.nanoTime()>deadline)throw new AssertionError("L6 work failed bounded convergence");
            http(endpoint,"FILL");
            for(var c:children)c.send("L6_RECONCILE");Thread.sleep(500);
        }
        ObjectNode point=sample(reader);
        if (waitingFrom >= 0) point.put("convergenceWaitStartedNanos", waitingFrom).put("convergenceWaitEndedNanos", System.nanoTime());
        point.put("phase",phase).put("check",checkIndex);
        point.put("nonActionable",number(reader,"SELECT count(*) FROM orders WHERE status IN ('FILLED','CANCELLED','RISK_REJECTED')"));
        point.set("cursor",cursor(reader));
        point.put("auditRows",number(reader,"SELECT count(*) FROM audit_logs"));
        point.put("eventRows",number(reader,"SELECT count(*) FROM event_store"));
        long transitionAudits=number(reader,"SELECT count(*) FROM audit_logs WHERE action='ORDER_STATUS_TRANSITION'");
        long duplicateTransitions=number(reader,"SELECT count(*) FROM (SELECT actor_id,detail_json->>'expected_version',"
                + "detail_json->>'version' FROM audit_logs WHERE action='ORDER_STATUS_TRANSITION' GROUP BY 1,2,3 HAVING count(*)>1) d");
        assertEquals(0,duplicateTransitions);
        assertEquals(0,number(reader,"SELECT count(*) FROM audit_logs WHERE action='ORDER_STATUS_TRANSITION' "
                + "AND detail_json->>'from'=detail_json->>'to'"));
        assertEquals(0,number(reader,"SELECT count(*) FROM orders WHERE version<>4 OR status<>'FILLED'"));
        assertEquals(4*point.path("orders").asLong(),transitionAudits);
        point.put("transitionAuditRows",transitionAudits).put("duplicateTransitionAudit",duplicateTransitions);
        point.put("databaseBytes",number(reader,"SELECT pg_database_size(current_database())"));
        ObjectNode check=JSON.createObjectNode();check.set("facts",facts(reader));
        check.put("expectedStrategyRuns",number(reader,"SELECT count(*) FROM strategy_runs"));reader.commit();
        check.set("venue",http(endpoint,null));
        if (calibration) check.put("mode", "CALIBRATION");
        if (formalBudget > 0) {
            check.put("mode", qualificationMode).put("runOrderBudget", formalBudget);
            check.set("pacingSlots", JSON.valueToTree(slots));
        }
        Path file=dir.resolve(String.format("checkpoint-%03d.json",checkIndex));
        Files.writeString(file,JSON.writeValueAsString(check));
        String oracle=B0Processes.command("python","-X","utf8",B0Processes.root().resolve(
                "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/l6_oracle.py").toString(),file.toString());
        point.set("oracle",JSON.readTree(oracle));
        var ids=point.putArray("fullChainOrderIds");
        check.path("facts").path("orders").forEach(o -> ids.add(o.path("order_id").asText()));
        // 重型业务快照不再承担资源采样；独立采样线程使用自己的只读连接和HTTP端点。
        Files.writeString(dir.resolve("checkpoints.ndjson"), JSON.writeValueAsString(point)+"\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        System.out.println("L6_PROGRESS phase="+phase+" checks="+checkIndex+" orders="+point.path("orders")+" backlog="+point.path("actionable"));
        System.out.flush();return point;
    }
}
