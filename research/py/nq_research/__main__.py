"""Uninstalled launcher for the canonical src-layout package.

Why:
`research/py` uses a src-layout package (`src/nq_research`). This launcher keeps
`python -m nq_research` working from the subproject directory before editable
installation, while avoiding a duplicate top-level `nq_research` package that
breaks repository-root mypy discovery.
"""

from __future__ import annotations

import sys
from pathlib import Path

_SRC_ROOT = Path(__file__).resolve().parent.parent / "src"
sys.path.insert(0, str(_SRC_ROOT))
sys.modules.pop("nq_research", None)

from nq_research.cli import main  # noqa: E402


if __name__ == "__main__":
    raise SystemExit(main())
