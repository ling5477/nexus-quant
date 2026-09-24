"""离线身份的既有字节合同兼容向量。"""

from __future__ import annotations

import pytest

from nq_research.serialization.canonical import stable_digest


def test_stable_digest_keeps_existing_unicode_and_nested_vector() -> None:
    payload = {"z": "é", "a": [1, None, {"b": True}]}

    assert stable_digest(payload) == "c08c8ee961ee3a0ab1661a815c9a78f3622008fcef94a7343d73c684243c0dcc"
    assert stable_digest({"a": [1, None, {"b": True}], "z": "é"}) == stable_digest(payload)


def test_stable_digest_keeps_json_serialization_error() -> None:
    with pytest.raises(TypeError, match="not JSON serializable"):
        stable_digest({"x": object()})
