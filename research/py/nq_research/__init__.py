from __future__ import annotations

from pathlib import Path

_SRC_PACKAGE = Path(__file__).resolve().parent.parent / "src" / "nq_research"
__path__ = [str(_SRC_PACKAGE)]
