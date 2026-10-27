package com.guidinglight.nexusquant.app.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExperimentParametersTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private java.util.List<com.fasterxml.jackson.databind.JsonNode> expand(String type,String candidates,String grid) throws Exception {
        return ExperimentParameters.expand(type,mapper.readTree("{\"executable\":\""+type+"\"}"),
                candidates==null?null:mapper.readTree(candidates),grid==null?null:mapper.readTree(grid),mapper);
    }
    @Test void boundedProductsUseBothRealStrategyValidatorsAndCanonicalDuplicateIdentity() throws Exception {
        assertEquals(4,expand("SPOT_SMA_TARGET_V1",null,"{\"window\":[2,4],\"investedExposure\":[\"0.5\",\"1\"]}").size());
        assertEquals(4,expand("SPOT_CHANNEL_BREAKOUT_V1",null,"{\"entryLookbackBars\":[4,8],\"exitLookbackBars\":[2],\"investedExposure\":[\"0.5\",\"1\"]}").size());
        assertThrows(IllegalArgumentException.class,()->expand("SPOT_SMA_TARGET_V1","[{\"window\":4,\"investedExposure\":\"0.50\"},{\"window\":4,\"investedExposure\":0.5}]",null));
        for(String candidate:new String[]{"{\"window\":1,\"investedExposure\":\"1\"}","{\"window\":2.5,\"investedExposure\":\"1\"}","{\"window\":2,\"investedExposure\":\"1.1\"}","{\"window\":201,\"investedExposure\":\"0.5\"}","{\"window\":4,\"investedExposure\":\"1\",\"extra\":1}"})
            assertThrows(IllegalArgumentException.class,()->expand("SPOT_SMA_TARGET_V1","["+candidate+"]",null));
        assertThrows(IllegalArgumentException.class,()->expand("SPOT_CHANNEL_BREAKOUT_V1","[{\"entryLookbackBars\":4,\"exitLookbackBars\":4,\"investedExposure\":\"1\"}]",null));
        assertThrows(IllegalArgumentException.class,()->expand("SPOT_CHANNEL_BREAKOUT_V1","[{\"entryLookbackBars\":4,\"exitLookbackBars\":2,\"investedExposure\":\"0.123456789\"}]",null));
        assertThrows(IllegalArgumentException.class,()->expand("SPOT_SMA_TARGET_V1",null,"{\"window\":[2,3,4,5,6],\"investedExposure\":[\"0.1\",\"0.2\",\"0.3\",\"0.4\"]}"));
    }
}
