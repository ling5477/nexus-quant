package com.guidinglight.nexusquant.scheduler.infra.jdbc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.scheduler.model.LedgerReconcileDiff;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcLedgerReconcileRepositoryTest {

    @Test
    void shouldSelectReconciliationSourceFromSnapshotBasisAndEnvironment() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.<LedgerReconcileDiff>of());

        JdbcLedgerReconcileRepository repository = new JdbcLedgerReconcileRepository(jdbcTemplate);

        repository.findDiffs();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class));
        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("s.balance_basis='POSITION_PROJECTION'"));
        assertTrue(sql.contains("t.trade_env=s.trade_env"));
        assertTrue(sql.contains("SIM_FUNDING_CASH"));
        assertTrue(sql.contains("SNAPSHOT_PROVENANCE_UNKNOWN"));
    }
}

