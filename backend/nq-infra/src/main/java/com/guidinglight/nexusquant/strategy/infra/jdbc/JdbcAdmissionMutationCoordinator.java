package com.guidinglight.nexusquant.strategy.infra.jdbc;

import com.guidinglight.nexusquant.strategy.strategyrelease.application.AdmissionMutationCoordinationException;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.AdmissionMutationCoordinator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL state-first coordinator；source trigger 是 revision bump 的唯一权威写方。 */
@Component
public class JdbcAdmissionMutationCoordinator implements AdmissionMutationCoordinator {

    public static final int DEFAULT_MAX_FAN_OUT = AdmissionMutationCoordinator.HARD_MAX_FAN_OUT;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final int maxFanOut;

    public JdbcAdmissionMutationCoordinator(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            @Value("${nexusquant.strategy-release.admission.max-fan-out:256}") int maxFanOut
    ) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.transactionTemplate = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager must not be null")
        );
        if (maxFanOut < 1 || maxFanOut > AdmissionMutationCoordinator.HARD_MAX_FAN_OUT) {
            throw new IllegalArgumentException("admission max fan-out must be between 1 and 256");
        }
        this.maxFanOut = maxFanOut;
    }

    @Override
    public <T> T withLockedAdmissionStates(Collection<String> publishRecordIds, Supplier<T> mutation) {
        Objects.requireNonNull(mutation, "mutation must not be null");
        List<String> orderedIds = normalize(publishRecordIds);
        if (orderedIds.size() > maxFanOut) {
            throw new AdmissionMutationCoordinationException("admission mutation fan-out limit exceeded");
        }
        return transactionTemplate.execute(status -> {
            jdbcTemplate.queryForObject(
                    "SELECT set_config('nexusquant.admission.max_fan_out', ?, true)",
                    String.class,
                    Integer.toString(maxFanOut)
            );
            lockExactly(orderedIds);
            return mutation.get();
        });
    }

    private List<String> normalize(Collection<String> publishRecordIds) {
        TreeSet<String> sorted = new TreeSet<>();
        if (publishRecordIds != null) {
            for (String publishRecordId : publishRecordIds) {
                if (publishRecordId != null && !publishRecordId.isBlank()) {
                    sorted.add(publishRecordId.trim());
                }
            }
        }
        return List.copyOf(sorted);
    }

    private void lockExactly(List<String> orderedIds) {
        if (orderedIds.isEmpty()) {
            return;
        }
        LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("publishRecordIds", orderedIds);
        List<String> locked = namedJdbcTemplate.queryForList(
                """
                        SELECT publish_record_id
                        FROM strategy_release_admission_state
                        WHERE publish_record_id IN (:publishRecordIds)
                        ORDER BY publish_record_id
                        FOR UPDATE
                        """,
                parameters,
                String.class
        );
        if (!new ArrayList<>(orderedIds).equals(locked)) {
            throw new AdmissionMutationCoordinationException("strategy release admission state is missing");
        }
    }
}
