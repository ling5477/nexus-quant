package com.guidinglight.nexusquant.tenant.infra.jdbc;

import com.guidinglight.nexusquant.common.tenant.*;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 仅调用有界身份投影，不暴露全局业务查询，也不接受客户端 tenant 参数。 */
@Repository
public class JdbcTenantWorkAccess implements TenantWorkAccess {
    private final JdbcTemplate jdbc;
    public JdbcTenantWorkAccess(JdbcTemplate jdbc) { this.jdbc = Objects.requireNonNull(jdbc); }
    @Override public List<String> next(Kind kind, int limit) {
        if (limit<1 || limit>64 || TenantContext.currentTenantId().isPresent()) {
            throw new IllegalStateException("TENANT_ACCESS_DENIED: invalid background scan");
        }
        return jdbc.queryForList("SELECT work_id FROM nq_poll_tenant_work(?,?)",String.class,kind.name(),limit);
    }
    @Override public TenantExecutionIdentity restore(Kind kind,String workId) {
        if (workId==null || workId.isBlank() || workId.length()>128) throw new IllegalArgumentException("RUN_NOT_FOUND");
        return jdbc.queryForObject("SELECT tenant_id,execution_epoch FROM nq_restore_tenant_work(?,?)",
                (result,row)->new TenantExecutionIdentity(new TenantId(result.getObject(1,java.util.UUID.class)),result.getLong(2)),kind.name(),workId);
    }
}
