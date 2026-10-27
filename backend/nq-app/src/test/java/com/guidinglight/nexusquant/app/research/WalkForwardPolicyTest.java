package com.guidinglight.nexusquant.app.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.research.domain.BacktestWindow;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WalkForwardPolicyTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private WalkForwardPolicy.Score score(int ordinal,String profit,String drawdown) {
        return new WalkForwardPolicy.Score(ordinal,"trial-"+ordinal,"version-"+ordinal,"run-"+ordinal,"evaluation-"+ordinal,new BigDecimal(profit),new BigDecimal(drawdown));
    }
    @Test void trainingSelectionIgnoresOppositeTestWinnerAndHasDeterministicTies() {
        var training=List.of(score(2,"0.20","0.10"),score(1,"0.10","0.01"));
        var tests=List.of(score(2,"-0.50","0.5"),score(1,"0.99","0"));
        assertEquals(2,WalkForwardPolicy.rank(training).getFirst().ordinal());
        assertEquals(1,WalkForwardPolicy.rank(tests).getFirst().ordinal());
        var frozen=WalkForwardPolicy.rank(training).getFirst();
        tests=List.of(score(1,"1000","0"),score(2,"-1","1"));
        assertEquals(frozen,WalkForwardPolicy.rank(training).getFirst());
        assertEquals(1,WalkForwardPolicy.rank(tests).getFirst().ordinal());
        assertEquals(2,WalkForwardPolicy.rank(List.of(score(3,"0.2","0.02"),score(1,"0.2","0.1"),score(2,"0.2","0.02"))).getFirst().ordinal());
        assertTrue(WalkForwardPolicy.rank(List.of()).isEmpty());
    }
    @Test void temporalPolicyIsHalfOpenBoundedAndHoldoutOnlyFollowsFinalTraining() {
        for(int fold=0;fold<4;fold++) {
            var train=WalkForwardPolicy.window(fold*4);var test=WalkForwardPolicy.window(fold*4+1);
            assertEquals(0,train.startBar());assertEquals(train.endBar(),test.startBar());
            assertEquals(120,test.endBar()-test.startBar());assertTrue(train.endBar()<=600);
            if(fold<3)assertEquals(test.endBar(),WalkForwardPolicy.window((fold+1)*4+1).startBar());
        }
        assertEquals("FINAL_HOLDOUT",WalkForwardPolicy.window(13).role());
        assertEquals(600,WalkForwardPolicy.window(12).endBar());
        assertEquals(720,WalkForwardPolicy.window(13).endBar());
        assertThrows(IllegalArgumentException.class,()->WalkForwardPolicy.window(16));
    }
    @Test void stressFreezesExactHypothesisAndRejectsUntrustedSourceOrWindow() throws Exception {
        var base=mapper.readTree("{\"feeRate\":\"0.001\",\"slippageBps\":\"10\"}");
        var fee=WalkForwardPolicy.execution(base,WalkForwardPolicy.window(2),mapper);
        assertEquals("0.002",fee.path("feeRate").asText());assertEquals("10",fee.path("slippageBps").asText());
        assertEquals("FRACTION",fee.path("costStress").path("unit").asText());
        var slip=WalkForwardPolicy.execution(base,WalkForwardPolicy.window(3),mapper);
        assertEquals("20",slip.path("slippageBps").asText());assertEquals("0.001",slip.path("feeRate").asText());
        assertEquals(new BacktestWindow(240,360),BacktestWindow.read(slip,720));
        ((com.fasterxml.jackson.databind.node.ObjectNode)slip.path("windowExecution")).put("endBar",721);
        assertThrows(IllegalArgumentException.class,()->BacktestWindow.read(slip,720));
        var privateCost=mapper.readTree("{\"feeRate\":\"0.001\",\"slippageBps\":\"10\",\"feeSourceSnapshotJson\":\"untrusted\"}");
        assertNull(WalkForwardPolicy.execution(privateCost,WalkForwardPolicy.window(2),mapper));
        assertEquals("0.001",base.path("feeRate").asText());
    }
}
