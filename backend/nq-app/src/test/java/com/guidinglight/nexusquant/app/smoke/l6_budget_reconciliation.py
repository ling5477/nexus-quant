"""以保留的正式前缀和真实JDBC单位成本分解事务；不回写任何输入或赋予正式资格。"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def totals(point):
    result = Counter(point["controller"])
    for actor in point["actors"]:
        result.update(actor["transactionsByOriginAndOwner"])
    return result


def analyze(raw, probe):
    records = [json.loads(line) for line in (raw / "resources.ndjson").read_text(encoding="utf-8").splitlines()]
    measured = [r for r in records if r["sources"]["postgres"]["status"] == "MEASURED"]
    first, last = measured[0], measured[-1]
    pg = last["sources"]["postgres"]["values"]
    start = first["sources"]["postgres"]["values"]["transactions"]
    delta = pg["transactions"] - start
    actors = [last["sources"][key]["values"] for key in ("nq0", "nq1")]
    initial = [first["sources"][key]["values"] for key in ("nq0", "nq1")]
    observation = lambda a, key: int(a["observations"][key]["totals"].get("ATTEMPT", 0))
    replay = sum(observation(a, "durable_trade_replay") for a in actors)
    scans = sum(observation(a, "okx_reconcile") - observation(b, "okx_reconcile") for a, b in zip(actors, initial))
    ticks = sum(a["tickCompleted"] - b["tickCompleted"] for a, b in zip(actors, initial))
    validations = sum(observation(a, "validation_refresh") - observation(b, "validation_refresh") for a, b in zip(actors, initial))
    observer_rows = [json.loads(line) for line in (raw / "scheduler.ndjson").read_text(encoding="utf-8").splitlines()]
    observer_calls = sum(r["elapsedNanos"] <= last["elapsedMillis"] * 1_000_000 for r in observer_rows)
    progress = [json.loads(line) for line in (raw / "progress.ndjson").read_text(encoding="utf-8").splitlines()]
    progress = [r for r in progress if r["completedObservedElapsedNanos"] <= last["elapsedMillis"] * 1_000_000]
    checkpoint_attempts = max(r["check"] for r in progress)
    orders = pg["orders"]
    groups = {
        "business_emit": orders * 13,
        "qualification_reconciliation_new_fill": orders * 11,
        "qualification_reconciliation_terminal_replay": replay * 7,
        "qualification_reconciliation_cursor": scans * 2,
        "production_scheduler": ticks + orders * 2,
        "validation": validations,
        "sampler_postgres": (len(measured) - 1) * 17,
        "sampler_actor": (len(measured) - 1) * 2,
        "scheduler_observation": observer_calls * 2,
        "checkpoint_oracle": checkpoint_attempts + len(progress),
        "monitor_report_after_t0": 0,
    }
    classified = sum(groups.values())
    residual = delta - classified
    assert 0 <= residual < delta * .03, (groups, delta, residual)
    instrumented = read(probe)
    for unit in instrumented["units"]:
        actual = sum(v for k, v in (totals(unit["after"]) - totals(unit["before"])).items()
                     if k.startswith("QUALIFICATION_RECONCILIATION/"))
        assert actual == 6 * (7 * unit["orders"] + 2)
    actor_counted = sum(sum(a["transactionsByOriginAndOwner"].values()) for a in instrumented["after"]["actors"])
    controller_counted = sum(instrumented["controllerAfter"].values()) - sum(instrumented["controllerBeforeActors"].values())
    measured_total = instrumented["serverAfterChildExit"] - instrumented["serverBeforeActors"]
    instrumented_residual = measured_total - actor_counted - controller_counted
    assert abs(instrumented_residual) <= measured_total * .04
    last_checkpoint = max(raw.glob("checkpoint-*.json"))
    checkpoint = read(last_checkpoint)
    events = checkpoint["venue"]["events"]
    event_sizes = [len(json.dumps(e, ensure_ascii=False, separators=(",", ":")).encode("utf-8")) + 1 for e in events]
    assert max(event_sizes) <= 128
    ownership = {
        "terminalReplayPerFill": {"trade": 3, "ledger": 1, "audit": 2, "jdbcSession": 1},
        "cursorPerInvocation": {"order": 1, "jdbcSession": 1},
        "newFillPerOrder": 11, "emitPerOrder": 13, "schedulerPerTick": 1,
        "schedulerCompletionPerOrder": 2, "schedulerObservationPerCall": 2,
        "validationPerRefresh": 1, "monitorReportSetup": 21,
    }
    windows = []
    for phase in ("WARMUP", "A1", "A2", "A3", "A4", "DRAIN"):
        rows = [r for r in measured if ("WARMUP" if r["elapsedMillis"] < 600000 else "DRAIN" if r["elapsedMillis"] >= 3000000 else "A" + str(r["elapsedMillis"] // 600000)) == phase]
        if rows:
            windows.append({"phase": phase, "samples": len(rows),
                            "firstTransactions": rows[0]["sources"]["postgres"]["values"]["transactions"],
                            "lastTransactions": rows[-1]["sources"]["postgres"]["values"]["transactions"]})
    observer = sum(value for key, value in groups.items() if key.startswith(("qualification_", "sampler_", "checkpoint_", "scheduler_observation")))
    return {
        "status": "ACCOUNTING_RECONSTRUCTED_WITH_EXPLICIT_RESIDUAL", "formalRunStarted": False,
        "L6AAccepted": False, "L6Accepted": False, "sourceRun": raw.name,
        "sourceHash": hashlib.sha256((raw / "resources.ndjson").read_bytes()).hexdigest(),
        "probeHash": hashlib.sha256(probe.read_bytes()).hexdigest(), "unitCosts": ownership,
        "oldPrefix": {"throughElapsedMillis": last["elapsedMillis"], "startAbsoluteTransactions": start,
                      "lastAbsoluteTransactions": pg["transactions"], "observedDelta": delta,
                      "groups": groups, "classified": classified, "unclassifiedAndSampleSkew": residual,
                      "classifiedFraction": classified / delta, "qualifiedObserverAttributed": observer,
                      "observerFraction": observer / delta, "orders": orders,
                      "terminalReplays": replay, "reconciliationInvocations": scans,
                      "checkpointAttemptsThroughLastSuccess": checkpoint_attempts,
                      "successfulCheckpoints": len(progress), "windows": windows},
        "independentProcessAccounting": {"serverDelta": measured_total, "actorCounted": actor_counted,
                                         "controllerCounted": controller_counted, "unclassified": instrumented_residual,
                                         "unclassifiedFraction": instrumented_residual / measured_total},
        "rawBytes": {"observedFinal": sum(p.stat().st_size for p in raw.rglob("*") if p.is_file()),
                     "maximumVenueEventBytes": max(event_sizes), "venueEventCounts": dict(Counter(e["type"] for e in events)),
                     "checkpointCount": len(list(raw.glob("checkpoint-*.json")))},
        "limits": ["Retrospective groups reconstruct call-site unit costs; old raw did not instrument individual JDBC transactions.",
                   "Residual contains controller transactions, pool health/setup and cross-source collection skew; it is not zero.",
                   "The failed T=3530 sample does not serialize the exact over-cap PG count; only >1000000 is known.",
                   "Initial Paper monitor/report work is before T=0; 21 unit-cost transactions are not charged again to the run delta.",
                   "Runtime duration qualification is still incomplete; accounting cannot upgrade the historical failure."],
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--raw", type=Path, required=True)
    parser.add_argument("--probe", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.write_text(json.dumps(analyze(args.raw, args.probe), indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
