---
name: nq-java-engineering-standard
description: Apply NexusQuant Java engineering standards when adding, changing, reviewing, or refactoring Java, Spring Boot, JDBC, transactions, concurrency, logging, exceptions, Maven Java dependencies/tests, Checkstyle, PMD, SpotBugs, or ArchUnit. Do not auto-trigger for pure frontend, Python, docs-only, Authority-only, read-only acceptance, or Git-only work with no Java scope.
---

# NQ Java Engineering Standard

Use this skill for Java engineering work in NexusQuant. The project platform and architecture win over external style guidance; read version facts from the repository instead of model memory.

## Required context

1. Read the machine authority block at the top of `docs/current/STATUS.md` and confirm the repository root; never hard-code the current Gate or `next_action` here.
2. Detect the repository and read `AGENTS.md`.
3. Read `docs/standards/java/platform-profile.json` before making Java or Spring decisions.
4. Read `docs/standards/java/common-java-engineering-standard.md`.
5. Read `docs/standards/java/java-platform-profile.md`.
6. Read `docs/standards/java/spring-platform-profile.md`.
7. Read `docs/standards/java/architecture-overlay.md`.
8. Read `docs/standards/java/nq-java-domain-overlay.md`.
9. Read the relevant entries in `docs/standards/java/alibaba-huangshan-rule-mapping.yaml`.
10. Read `docs/standards/java/java-rule-exceptions.yaml`.

## Workflow

1. Identify target Java/Maven files, excluded files, expected output and existing dirty paths.
2. Check whether frozen contracts, Schema, Golden Case, state machines, transaction boundaries, trading modes or Authority could be affected; stop if the task requires an unauthorized change.
3. Inspect the affected module, package, port/adapter direction, Spring bean boundary and existing ArchUnit patterns before applying the smallest valid change.
4. Apply rule IDs compatible with the detected platform; report any rule intentionally not applied because of platform, architecture or domain priority.
5. Do not remediate unrelated historical Shadow findings or batch-format untouched files.
6. Run the repository's relevant Maven tests and existing quality checks, then run:

```powershell
pwsh -NoProfile -File scripts/java-standard/verify-java-engineering-standard.ps1
pwsh -NoProfile -File scripts/java-standard/invoke-java-shadow-scan.ps1 -OutputPath artifacts/java-shadow/shadow-report.json
```

7. Treat `VIOLATION_FOUND` as Shadow-only. Treat invalid mapping/platform/baseline, rule collisions, checker failure, missing report or nondeterministic output as blocking.

## Prohibited actions

- Do not use style rules to override NQ domain contracts, risk checks, account context, mode isolation, state machines or current Authority.
- Do not enable LIVE, Shadow trading, real provider, real client, exchange mutation, AI or DH runtime.
- Do not create broad exceptions, allow new usages under migration exceptions, or upgrade Shadow to a required incremental gate.
- Do not force obsolete Java or Spring idioms merely because they appear in Huangshan guidance.
- Do not infer Spring APIs from model knowledge; use `platform-profile.json` and effective repository dependencies.
- Do not use preview features, enable virtual threads, change Spring profiles or introduce unmanaged executors unless separately authorized and already supported by project configuration.
- Do not create `ServiceImpl`/repository interface ceremony or downgrade architecture invariants into style rules.

## Final report

Report: `Repository`, `Task classification`, `Authority inspected`, `Platform profile`, `Rules applied`, `Rules intentionally not applied`, `Platform compatibility`, `Exceptions used`, `Java files changed`, `Contracts affected`, `Validation performed`, `Remaining risks`, and `Final decision`.
