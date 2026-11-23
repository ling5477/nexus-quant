package com.guidinglight.nexusquant.observability.operational;

/** 仅接收有限集合中的运行事实；标识符、载荷和异常文本不得穿过此端口。 */
@FunctionalInterface
public interface OperationalObservation {
    OperationalObservation NOOP = (operation, signal, value) -> { };

    void record(Operation operation, Signal signal, long value);

    enum Operation {
        VALIDATION_REFRESH("scheduler", "validation_refresh"),
        LEDGER_RECONCILE("reconciliation", "ledger_reconcile"),
        OKX_RECONCILE("reconciliation", "okx_reconcile"),
        LEDGER_RECOVERY("ledger_recovery", "durable_trade_replay"),
        CRITICAL_ALERT("alert", "critical_alert");

        private final String component;
        private final String operation;

        Operation(String component, String operation) {
            this.component = component;
            this.operation = operation;
        }

        public String component() { return component; }
        public String operation() { return operation; }
    }

    enum Signal { ATTEMPT, SUCCESS, FAILURE, SKIPPED, DEGRADED, UNRESOLVED, UNRESOLVED_SNAPSHOT, EMITTED }
}
