package com.guidinglight.nexusquant.infra.scheduler;

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
    void shouldExcludePositionBackedSnapshotsFromLedgerMissingBranch() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.<LedgerReconcileDiff>of());

        JdbcLedgerReconcileRepository repository = new JdbcLedgerReconcileRepository(jdbcTemplate);

        repository.findDiffs();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class));
        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("split_part(p.symbol, '-', 1)"));
        assertTrue(sql.contains("position_qty = s.balance"));
    }
}
