"""不启动交易进程的导出回归；复用完整 B2 证据结构并注入新随机身份。"""
import copy
import json
import re
import unittest
import uuid
from pathlib import Path
from synthetic_evidence import IdentityMapper, export


class SyntheticEvidenceTest(unittest.TestCase):
    def test_identity_namespaces_and_runs(self):
        first, second = IdentityMapper("B3", 1), IdentityMapper("B3", 2)
        raw, other = str(uuid.uuid4()), str(uuid.uuid4())
        reference = first.register("ORDER", raw)
        self.assertEqual(reference, first.register("ORDER", raw))
        self.assertNotEqual(reference, first.register("ORDER", other))
        self.assertNotEqual(reference, first.register("FILL", raw))
        self.assertNotEqual(reference, second.register("ORDER", raw))
        self.assertEqual("SYNTH-L4:B3:R01:ORDER:001", reference)

    def test_credentials_and_unknown_fields_are_untouched(self):
        raw = str(uuid.uuid4())
        sentinel = "AbCdEfGhIjKlMnOp" + "QrStUvWxYz0192837465"
        proof = {"order_id": raw, "apiKey": sentinel, "token": sentinel,
                 "Authorization": sentinel, "secret": {"order_id": raw},
                 "access_token": sentinel, "unknown": raw}
        result = export(proof, IdentityMapper("B2", 1))
        for field in proof.keys() - {"order_id"}:
            self.assertEqual(proof[field], result[field])
        self.assertNotEqual(raw, result["order_id"])

    def test_forward_references_and_composite_keys(self):
        raw = str(uuid.uuid4())
        proof = {"early": {"ref_id": raw, "idempotency_key": raw + ":LEDGER:FEE_2"},
                 "later": {"trade_id": raw}, "apiKey": raw}
        result = export(proof, IdentityMapper("B4", 3))
        self.assertEqual(result["later"]["trade_id"], result["early"]["ref_id"])
        self.assertEqual(result["later"]["trade_id"] + ":LEDGER:FEE_2", result["early"]["idempotency_key"])
        self.assertEqual(raw, result["apiKey"])

    def test_full_b2_evidence_leakage_and_graph(self):
        root = next(p for p in Path(__file__).resolve().parents if (p / "scripts/ci/delivery-supply-chain-lock.json").exists())
        paths = sorted((root / "docs/audit/evidence/l4-b2-qualification-resume-attempt01").glob("*-*-*.json"))
        self.assertEqual(48, len(paths))
        references = re.compile(r"SYNTH-L4:B2(?:-[A-Z0-9]+)*:R\d+:[A-Z]+:\d+")
        for path in paths:
            source = path.read_text(encoding="utf-8-sig")
            raw_values = {ref: "random-" + str(uuid.uuid4()) for ref in set(references.findall(source))}
            self.assertTrue(raw_values, path.name)
            # 此处仅构建测试输入，不是导出器；把已确认的 canonical 身份替成随机值。
            raw = references.sub(lambda match: raw_values[match[0]], source)
            original = json.loads(raw)
            before = copy.deepcopy(original)
            mapper = IdentityMapper("B5", 1)
            result = export(original, mapper)
            serialized = json.dumps(result)
            self.assertEqual(before, original)
            self.assertFalse(any(value in serialized for value in raw_values.values()), path.name)
            inverse = {canonical: raw for (_, raw), canonical in mapper.identities.items()}
            pattern = re.compile(r"SYNTH-L4:B5:R01:[A-Z]+:\d+")
            restored = json.loads(pattern.sub(lambda match: inverse[match[0]], serialized))
            # 全树逆映射相等覆盖所有重复边、重启连续性、数量、费用、PID、版本与结论。
            self.assertEqual(original, restored, path.name)
            self.assertEqual(len(mapper.identities), len(inverse))

    def test_command_and_ledger_audit_references(self):
        client, trade, order = (str(uuid.uuid4()) for _ in range(3))
        original = {"client_order_id": client, "trade_id": trade, "order_id": order,
                    "command": {"idempotency_key": "42:" + client},
                    "ledger_audit": {"actor_id": trade}, "order_audit": {"actor_id": order},
                    "ledger": {"idempotency_key": trade + ":LEDGER:FEE_1"},
                    "unknown": {"idempotency_key": "42:unregistered", "actor_id": "unknown"},
                    "credential": {"actor_id": trade, "idempotency_key": "42:" + client}}
        result = export(original, IdentityMapper("B4", 1))
        self.assertEqual("42:" + result["client_order_id"], result["command"]["idempotency_key"])
        self.assertEqual(result["trade_id"], result["ledger_audit"]["actor_id"])
        self.assertEqual(result["order_id"], result["order_audit"]["actor_id"])
        self.assertEqual(result["trade_id"] + ":LEDGER:FEE_1", result["ledger"]["idempotency_key"])
        self.assertEqual(original["unknown"], result["unknown"])
        self.assertEqual(original["credential"], result["credential"])


if __name__ == "__main__":
    unittest.main()
