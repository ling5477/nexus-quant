"""离线研究身份使用的紧凑 JSON 编码与摘要。"""

from __future__ import annotations

import json
from collections.abc import Mapping
from hashlib import sha256


def stable_digest(payload: Mapping[str, object]) -> str:
    """保持 UTF-8、原始 Unicode、排序键和紧凑分隔符的身份字节合同。"""

    encoded = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return sha256(encoded).hexdigest()
