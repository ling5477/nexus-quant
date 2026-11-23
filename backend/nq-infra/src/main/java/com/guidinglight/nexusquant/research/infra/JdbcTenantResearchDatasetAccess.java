package com.guidinglight.nexusquant.research.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.common.tenant.TenantContext;
import com.guidinglight.nexusquant.marketdata.application.service.MarketdataDatasetService;
import com.guidinglight.nexusquant.research.domain.port.TenantResearchDatasetAccess;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** 描述符和许可经过原 MarketData 仓储的 RLS；不访问用户指定文件，也不复制原始行情。 */
@Repository
public class JdbcTenantResearchDatasetAccess implements TenantResearchDatasetAccess {
    private final MarketdataDatasetService datasets;
    private final ObjectMapper mapper;
    public JdbcTenantResearchDatasetAccess(MarketdataDatasetService datasets,ObjectMapper mapper) {this.datasets=datasets;this.mapper=mapper;}
    @Override public String canonicalSnapshot(String requestedSnapshot) {
        TenantContext.requireTenantId();
        try {
            var requested=mapper.readTree(requestedSnapshot);
            String canonical=datasets.buildDatasetSnapshot(UUID.fromString(requested.path("datasetId").asText()));
            var actual=mapper.readTree(canonical);
            if(!"db".equals(actual.path("provider").asText()))throw new IllegalArgumentException("TENANT_ACCESS_DENIED");
            for(String field:List.of("provider","resourcePath","exchangeCode","marketType","symbol","interval")) {
                if(requested.has(field) && !actual.path(field).equals(requested.path(field)))throw new IllegalArgumentException("TENANT_ACCESS_DENIED");
            }
            return canonical;
        } catch(java.io.IOException | IllegalArgumentException failure) {throw new IllegalArgumentException("TENANT_ACCESS_DENIED",failure);}
    }
}
