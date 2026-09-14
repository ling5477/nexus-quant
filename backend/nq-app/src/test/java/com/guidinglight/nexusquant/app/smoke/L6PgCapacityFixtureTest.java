package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** 只验证真实fixture容量、读写和资源清理，不启动业务订单或正式计时器。 */
@EnabledIfSystemProperty(named="nq.l6.capacity.fixture", matches="true")
class L6PgCapacityFixtureTest {
    @Test void realPgScopePreflightObservationProjectionAndCleanup() throws Exception {
        var c = L6PgCapacityContractTest.contract(); var json = new ObjectMapper();
        var dir = B0Processes.root().resolve("backend/nq-app/target/l6-capacity-fixture/"+UUID.randomUUID()); Files.createDirectories(dir);
        var proof = json.createObjectNode().put("formalRunStarted", false).put("orders",0);
        System.out.println("L6_CAPACITY_FIXTURE_ROOT "+dir);
        proof.set("contract",c.identity());
        proof.set("hostMemoryPreflight",L6HostMemoryPreflight.verify(c,L6HostMemoryPreflight.observe()));
        var containers = proof.putArray("containers");
        try {
            for (boolean formal : new boolean[]{false,true}) {
                String id;
                try (var pg = formal ? B0Processes.Pg.startL6(c) : B0Processes.Pg.startBounded();
                     var connection = DriverManager.getConnection(pg.ownedUrl(),"postgres",""); var s = connection.createStatement()) {
                    id = pg.ownedContainerId();
                    s.execute("CREATE TABLE capacity_fixture(id bigint PRIMARY KEY, value text NOT NULL)");
                    assertEquals(1,s.executeUpdate("INSERT INTO capacity_fixture VALUES(1,'capacity-proof')"));
                    try(var rs=s.executeQuery("SELECT value FROM capacity_fixture WHERE id=1")) { assertTrue(rs.next()); assertEquals("capacity-proof",rs.getString(1)); assertFalse(rs.next()); }
                    var observation = L6PgStorageObservation.collect(connection,id,B0Processes::command);
                    assertEquals(formal ? c.capacity() : B0Processes.Pg.defaultTmpfsBytes(),observation.path("pgTmpfsCapacityBytes").asLong());
                    long memory = Long.parseLong(B0Processes.command("docker","inspect","--format","{{.HostConfig.Memory}}",id));
                    assertEquals(formal ? c.pgMemory() : 768L*1_048_576,memory);
                    var row = containers.addObject().put("scope",formal?"FORMAL_L6_A":"L5_DEFAULT").put("id",id).put("memoryLimitBytes",memory).put("readWrite","PASS");
                    row.set("observation",observation);
                    if(formal) {
                        var guard = new L6PgProjectionGuard(c);
                        var sample = L6PgProjectionGuardTest.sample(0,observation.path("pgTmpfsFreeBytes").asLong(),0,0);
                        guard.observe(sample,0); assertTrue(guard.producerAllowed()); row.set("projectionGuard",guard.evidence());
                        assertTrue(memory >= c.capacity());
                    }
                }
                assertTrue(B0Processes.command("docker","ps","-a","--filter","id="+id,"--format","{{.ID}}").isBlank());
            }
            proof.put("cleanup","PASS").put("ownedSurvivors",0).put("result","SHORT_PG_FIXTURE_PASS");
        } catch(Exception | AssertionError e) { proof.put("result","FAILED").put("error",e.toString()); throw e; }
        finally { c.verifyUnchanged(); Files.writeString(dir.resolve("proof.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(proof)); }
    }
}
