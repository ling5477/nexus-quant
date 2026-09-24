# Post-GateAUDIT documentation link remediation

## Baseline and disposition

The unchanged `scripts/docs/check-doc-links.ps1` was run on the exact `0ee643c813f890a8215876eabe608dcebcea6081` Git archive in a repository-external disposable directory and on `81f3c229245b132971f53f6085ad884a0e55267e`. Both runs reported `checked=1157 warnings=123 errors=85`. The complete `ERROR source:line -> rawLink` tuple sets were equal: 85 pre-existing, zero introduced, zero removed. The disposable archive was deleted after comparison.

[The failure matrix](POST_GATEAUDIT_DOC_LINK_FAILURE_MATRIX.csv) records all 85 tuples, their resolved missing paths, and their unique current canonical Java paths. Every row is `MOVED_TARGET_HAS_CANONICAL_LOCATION` and was repaired by updating only the Markdown link target. No historical conclusion, Java implementation, checker rule, or evidence manifest was changed. There were zero archive target repairs, historical plain-reference conversions, and hash-bound checker exceptions.

The matrix's `sourceSha256At81f` and `formalHashMatchesAt81f` fields describe the source bytes before this link maintenance. The L6 calibration closure's `sourceArtifactHashes` is a historical evidence snapshot; its `frozenAt` value is `null`, and it was left unchanged. Eleven links occurred in four source files whose pre-maintenance bytes matched that snapshot. The recorded historical hashes still describe those historical bytes; they are not claimed as hashes of the updated documents.

After remediation, the same checker reported `checked=1157 warnings=123 errors=0` and `PASS / DOC_LINKS_VALID`. The remaining warnings retain their existing historical-source and evidence-ledger treatment.
