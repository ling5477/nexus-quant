"""L4 隔离测试的 evidence 导出；原始运行文件留在 target，不覆盖运行身份。"""
import argparse
import copy
import json
import re
from collections import defaultdict
from pathlib import Path


class IdentityMapper:
    """调用方提供批次和批次内唯一 run 序号；不同 run 不共享实例。"""

    def __init__(self, batch, run):
        if not re.fullmatch(r"B[0-9]+(?:-[A-Z0-9]+)*", batch) or len(batch) > 9 or not 1 <= run <= 99999999:
            raise ValueError("invalid synthetic batch/run")
        # 分隔每个短字段，避免固定前缀与序号拼接后再次成为长 token。
        self.prefix = f"SYNTH-L4:{batch}:R{run:02d}"
        self.identities = {}
        self.counts = defaultdict(int)

    def register(self, kind, raw):
        if not re.fullmatch(r"[A-Z]{1,9}", kind) or not isinstance(raw, str) or not raw:
            raise ValueError("invalid synthetic identity")
        key = (kind, raw)
        if key not in self.identities:
            self.counts[kind] += 1
            self.identities[key] = f"{self.prefix}:{kind}:{self.counts[kind]:03d}"
        return self.identities[key]

    def reference(self, raw, kinds):
        matches = [self.identities[kind, raw] for kind in kinds if (kind, raw) in self.identities]
        if len(matches) > 1:
            raise ValueError("ambiguous synthetic reference namespace")
        return matches[0] if matches else raw


# 仅列出已知 harness 的身份字段；凭证子树整体保留，不能通过嵌套身份字段隐藏秘密。
PROTECTED = {"credential", "credentials", "secret", "token", "authorization", "password",
             "apikey", "apisecret", "passphrase", "accesstoken", "authtoken", "clientsecret"}
FIELDS = {
    "database": "DATABASE", "runId": "RUN", "run_id": "RUN",
    "order_id": "ORDER", "orderId": "ORDER",
    "client_order_id": "CLIENT", "clientOrderId": "CLIENT", "clOrdId": "CLIENT",
    "trade_id": "TRADE", "entry_id": "ENTRY", "risk_event_id": "RISK", "event_id": "EVENT",
    "request_id": "REQUEST", "requestId": "REQUEST", "trace_id": "TRACE", "traceId": "TRACE",
    "exchange_order_id": "VENUE", "external_order_id": "VENUE", "venueOrderId": "VENUE", "ordId": "VENUE",
    "exchange_trade_id": "FILL", "exchangeTradeId": "FILL", "fillId": "FILL", "tradeId": "FILL",
}
REFERENCES = {"ref_id": ("TRADE",), "scope_id": ("ORDER",), "actor_id": ("ORDER",),
              "key_value": ("CLIENT", "ORDER", "TRADE"), "key": ("CLIENT", "ORDER", "TRADE"),
              "client": ("CLIENT",)}


def protected(field):
    return re.sub(r"[_-]", "", field).lower() in PROTECTED


def export(proof, mapper):
    """只复制和转换证据树；不修改输入，先登记主身份，再解析引用和复合幂等键。"""
    def visit(value, register=False):
        if isinstance(value, list):
            return [visit(item, register) for item in value]
        if not isinstance(value, dict):
            return value
        result = {}
        for field, item in value.items():
            if protected(field):
                result[field] = copy.deepcopy(item)
            elif isinstance(item, str) and item and field in FIELDS:
                result[field] = mapper.register(FIELDS[field], item)
            elif isinstance(item, str) and not register and field in REFERENCES:
                result[field] = mapper.reference(item, REFERENCES[field])
            elif isinstance(item, str) and not register and field == "dedup_key":
                account, separator, client = item.partition(":")
                result[field] = account + separator + mapper.reference(client, ("CLIENT",))
            elif isinstance(item, str) and not register and field == "idempotency_key":
                trade, separator, suffix = item.partition(":")
                result[field] = mapper.reference(trade, ("TRADE",)) + separator + suffix
            else:
                result[field] = visit(item, register)
        return result

    visit(proof, True)
    return visit(proof)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    parser.add_argument("batch")
    parser.add_argument("run", type=int)
    args = parser.parse_args()
    if args.source.resolve() == args.destination.resolve():
        raise ValueError("raw evidence must remain separate")
    proof = json.loads(args.source.read_text(encoding="utf-8-sig"))
    canonical = export(proof, IdentityMapper(args.batch, args.run))
    args.destination.write_text(json.dumps(canonical, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
