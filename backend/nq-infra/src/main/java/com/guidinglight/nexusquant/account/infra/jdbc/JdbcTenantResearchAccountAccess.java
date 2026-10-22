package com.guidinglight.nexusquant.account.infra.jdbc;

import com.guidinglight.nexusquant.account.domain.port.TenantResearchAccountAccess;
import com.guidinglight.nexusquant.common.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 不创建账户、不写资金；交易仍必须通过原 canonical 环境与运行绑定校验。 */
@Repository
public class JdbcTenantResearchAccountAccess implements TenantResearchAccountAccess {
    private final JdbcTemplate jdbc;
    public JdbcTenantResearchAccountAccess(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void requirePaperResearchAccount(long accountId) {
        var tenant=TenantContext.requireTenantId();
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM accounts WHERE account_id=? AND tenant_id=? AND venue='PAPER' AND status='ACTIVE')",
                Boolean.class,accountId,tenant.value()))) throw new IllegalArgumentException("TENANT_ACCESS_DENIED");
    }
}
