"""Offline mutation tests for active/history separation and fail-closed exception handling."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import subprocess
import os
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("stage_guard", Path(__file__).resolve().parents[1] / "check-stage-assets.py")
guard = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(guard)

LIFECYCLE_SPEC = importlib.util.spec_from_file_location("asset_lifecycle", Path(__file__).resolve().parents[1] / "stage-asset-lifecycle.py")
lifecycle = importlib.util.module_from_spec(LIFECYCLE_SPEC)
LIFECYCLE_SPEC.loader.exec_module(lifecycle)


class StageAssetGuardTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="nq-stage-guard-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        for namespace in guard.ROOTS:
            (self.root / namespace).mkdir(parents=True)
        for path in guard.ROOT_FILES:
            self.write(path, "# Stable repository instructions\n")
        self.policy([])

    def write(self, path, content):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")

    def policy(self, exceptions, retired_paths=(), contracts=(), safe_inputs=()):
        self.write(guard.POLICY_PATH, json.dumps({"schemaVersion": 2, "exceptions": exceptions,
                   "retiredPaths": list(retired_paths), "compatibilityContracts": list(contracts),
                   "safeControlPlaneInputs": list(safe_inputs)}))

    def assert_rejected(self):
        self.assertTrue(guard.check(self.root)[0])

    def test_empty_stable_tree_and_repeat_are_valid(self):
        self.assertEqual([], guard.check(self.root)[0])
        self.assertEqual(guard.check(self.root), guard.check(self.root))

    def evolution_fixture(self):
        path = "backend/app/src/test/java/CompatibilityTest.java"
        self.write(path, 'class CompatibilityTest { String fixture = "GATEY_WIRE_V1"; }')
        self.policy([dict(path=path, sha256=guard.inspect(self.root, path)["sha256"],
                          kind="FIXTURE_IDENTITY", reason="negative fixture", owner="test owner",
                          removalTrigger="fixture retirement")])
        return path

    def reviewed_plan(self):
        plan = lifecycle.propose(self.root)
        output = Path(self.temporary.name).parent / (self.root.name + "-proposal.json")
        self.addCleanup(lambda: output.unlink(missing_ok=True))
        raw = lifecycle.encode(plan)
        output.write_bytes(raw)
        return output, lifecycle.digest(raw)

    def test_lifecycle_reviewed_evolution_and_unregistered_drift(self):
        path = self.evolution_fixture()
        self.write(path, 'class CompatibilityTest { String fixture = "GATEY_WIRE_V2"; }')
        self.assert_rejected()
        before = (self.root / guard.POLICY_PATH).read_bytes()
        plan, identity = self.reviewed_plan()
        self.assertEqual(before, (self.root / guard.POLICY_PATH).read_bytes())
        lifecycle.apply_reviewed(self.root, plan, identity)
        self.assertEqual([], guard.check(self.root)[0])
        self.write(path, 'class CompatibilityTest { String fixture = "GATEY_WIRE_V3"; }')
        self.assert_rejected()
        with self.assertRaises(ValueError):
            lifecycle.apply_reviewed(self.root, plan, identity)

    def test_lifecycle_new_semantic_asset_cannot_be_auto_registered(self):
        self.evolution_fixture()
        self.write("scripts/new-entry.py", 'mode = "gatey"')
        self.assert_rejected()
        with self.assertRaises(ValueError):
            lifecycle.propose(self.root)

    def test_lifecycle_superseded_registration_is_removed_deterministically(self):
        path = self.evolution_fixture()
        for removed in (False, True):
            with self.subTest(removed=removed):
                path = self.evolution_fixture()
                if removed:
                    (self.root / path).unlink()
                else:
                    self.write(path, "class CompatibilityTest {}")
                first = lifecycle.propose(self.root)
                self.assertEqual(first, lifecycle.propose(self.root))
                self.assertEqual([], first["policy"]["exceptions"])
                plan, identity = self.reviewed_plan()
                lifecycle.apply_reviewed(self.root, plan, identity)
                self.assertEqual([], guard.check(self.root)[0])

    def test_lifecycle_review_digest_and_proposed_output_are_bound(self):
        self.evolution_fixture()
        plan, identity = self.reviewed_plan()
        before = (self.root / guard.POLICY_PATH).read_bytes()
        with self.assertRaises(ValueError):
            lifecycle.apply_reviewed(self.root, plan, "0" * 64)
        data = json.loads(plan.read_bytes())
        data["policy"]["exceptions"][0]["owner"] = "unreviewed owner"
        raw = lifecycle.encode(data)
        plan.write_bytes(raw)
        with self.assertRaises(ValueError):
            lifecycle.apply_reviewed(self.root, plan, lifecycle.digest(raw))
        self.assertEqual(before, (self.root / guard.POLICY_PATH).read_bytes())

    def test_lifecycle_dynamic_inventory_changes_invalidate_review(self):
        self.evolution_fixture()
        plan, identity = self.reviewed_plan()
        # 任意新中性文件也属于候选变更，不能依赖固定文件数量。
        self.write("scripts/additional.py", "value = 1")
        with self.assertRaises(ValueError):
            lifecycle.apply_reviewed(self.root, plan, identity)
        fresh = lifecycle.propose(self.root)
        self.assertIn("scripts/additional.py", {x["path"] for x in fresh["candidate"]})

    def test_lifecycle_duplicate_registration_is_rejected(self):
        self.evolution_fixture()
        policy = json.loads((self.root / guard.POLICY_PATH).read_bytes())
        policy["exceptions"] *= 2
        self.write(guard.POLICY_PATH, json.dumps(policy))
        with self.assertRaises(ValueError):
            lifecycle.propose(self.root)

    def test_lifecycle_new_input_during_apply_is_rejected_without_policy_write(self):
        self.evolution_fixture()
        plan, identity = self.reviewed_plan()
        before = (self.root / guard.POLICY_PATH).read_bytes()
        real_mkstemp = tempfile.mkstemp

        def concurrent_creation(*args, **kwargs):
            self.write("scripts/new-entry.py", 'mode = "gatey"')
            return real_mkstemp(*args, **kwargs)

        with patch.object(lifecycle.tempfile, "mkstemp", side_effect=concurrent_creation):
            with self.assertRaises(ValueError):
                lifecycle.apply_reviewed(self.root, plan, identity)
        self.assertEqual(before, (self.root / guard.POLICY_PATH).read_bytes())
        self.assertFalse(list(self.root.glob(".asset-registration-*")))

    def test_lifecycle_external_input_change_during_proposal_is_rejected(self):
        self.write(".github/workflows/run.yml", "run: python tools/neutral.py")
        self.write("tools/neutral.py", "value = 1")
        real_check = lifecycle.guard.check

        def concurrent_change(*args, **kwargs):
            result = real_check(*args, **kwargs)
            self.write("tools/neutral.py", "value = 2")
            return result

        with patch.object(lifecycle.guard, "check", side_effect=concurrent_change):
            with self.assertRaises(ValueError):
                lifecycle.propose(self.root)

    def test_all_stage_names_rejected_in_active_paths(self):
        for stage in ("gate-a", "gateM", "gatew", "gatey", "gatez", "phase2", "freeze"):
            with self.subTest(stage=stage):
                path = "scripts/" + stage + "-install.ps1"
                self.write(path, "Write-Output 'fixture'\n")
                self.assert_rejected()
                (self.root / path).unlink()

    def test_profile_service_workflow_and_test_entrypoints_rejected(self):
        for path in ("backend/app/src/main/resources/application-gatec.yml", "deploy/nq-gatej.service",
                     ".github/workflows/gatet-qualification.yml", "backend/app/src/test/java/GateNPilotTest.java"):
            with self.subTest(path=path):
                self.write(path, "fixture")
                self.assert_rejected()
                (self.root / path).unlink()

    def test_retired_neutral_helpers_and_composed_callers_are_rejected(self):
        self.policy([], ["scripts/backup-db.sh"])
        self.write("scripts/backup-db.sh", "echo fixture")
        self.assert_rejected()
        (self.root / "scripts/backup-db.sh").unlink()
        self.write("scripts/deployment/backup.ps1", "& (Join-Path $scriptRoot 'backup-db.sh')")
        self.assert_rejected()

    def test_archive_namespace_is_not_a_stage_identifier(self):
        self.write("scripts/archive-index.ps1", "$archive = 'docs/gates'")
        self.assertEqual([], guard.check(self.root)[0])

    def test_lowercase_s_stage_is_only_exempt_in_archive_namespace(self):
        for path, text in (
            ("backend/app/src/main/resources/application-gates.yml", "fixture"),
            ("backend/app/src/main/java/Runtime.java", '@Profile("gates") class Runtime {}'),
            ("scripts/deployment/install.ps1", "if ($mode -eq 'gates') { throw 'fixture' }"),
        ):
            with self.subTest(path=path):
                self.write(path, text)
                self.assert_rejected()
                (self.root / path).unlink()

    def test_neutral_filenames_cannot_hide_stage_semantics(self):
        for path, text in (
            ("scripts/deployment/install.ps1", "if ($mode -eq 'GateY') { throw 'fixture' }"),
            ("deploy/runtime.service", "ExecStart=java --spring.profiles.active=gatew-okx-readonly"),
            ("backend/app/src/main/java/Runtime.java", '@Profile("gatea")\nclass Runtime {}'),
            ("docs/current/RUNBOOK.md", "```sh\npwsh scripts/gatex/deploy.ps1\n```"),
            (".github/workflows/ci.yml", "run: pwsh ./scripts/freeze-install.ps1"),
        ):
            with self.subTest(path=path):
                self.write(path, text)
                self.assert_rejected()
                (self.root / path).unlink()

    def test_historical_evidence_and_current_authority_are_allowed(self):
        for path in ("docs/gates/gate-y/README.md", "docs/audit/evidence/old.md", "docs/archive/old.md",
                     "docs/current/WORKLOG.md", "docs/current/evidence/gate-w/old.md"):
            self.write(path, "pwsh scripts/gatey/deploy.ps1\n")
        self.write("docs/current/STATUS.md", "active_gate=GateAUDIT\nwork_batch=GateAUDIT-PHASE5-F009\nlast_frozen_gate=GateY\n")
        self.assertEqual([], guard.check(self.root)[0])

    def test_comment_is_not_an_executable_caller(self):
        self.write("backend/app/src/main/java/Runtime.java", '// Historical GateW provenance\nclass Runtime {}')
        self.assertEqual([], guard.check(self.root)[0])

    def test_fixture_exception_cannot_gain_runtime_code(self):
        path = "backend/app/src/test/java/CompatibilityTest.java"
        self.write(path, 'class CompatibilityTest { String fixture = "GATEY_WIRE_V1"; }')
        risk = guard.inspect(self.root, path)
        self.policy([dict(path=path, sha256=risk["sha256"], kind="FIXTURE_IDENTITY", reason="Fixed signed wire fixture",
                          owner="Contract owner", removalTrigger="Versioned wire migration")])
        self.assertEqual([], guard.check(self.root)[0])
        self.write(path, 'class CompatibilityTest { String fixture = "GATEY_WIRE_V1"; void launch() {} }')
        self.assert_rejected()

    def test_stale_exception_fails(self):
        self.policy([dict(path="scripts/missing.ps1", sha256="a" * 64, kind="FIXTURE_IDENTITY", reason="Fixture",
                          owner="Tooling", removalTrigger="Fixture retired")])
        self.assert_rejected()

    def test_schema_missing_owner_duplicate_and_path_escape_fail(self):
        entry = dict(path="scripts/sample.ps1", sha256="a" * 64, kind="FIXTURE_IDENTITY", reason="Fixture",
                     owner="Tooling", removalTrigger="Fixture retired")
        for entries in ([{k: v for k, v in entry.items() if k != "owner"}], [entry, entry],
                        [dict(entry, path="../outside")], [dict(entry, kind="ALLOW_ANYTHING")]):
            with self.subTest(entries=entries):
                self.policy(entries)
                with self.assertRaises(ValueError):
                    guard.check(self.root)

    def test_unreadable_or_missing_scope_is_not_a_pass(self):
        (self.root / "deploy").rmdir()
        with self.assertRaises(ValueError):
            guard.check(self.root)

    def test_invalid_encoding_is_not_silently_skipped(self):
        (self.root / "scripts/bad.ps1").write_bytes(b"\xff\xfe\x00")
        with self.assertRaises(UnicodeError):
            guard.check(self.root)

    def test_symlinks_are_not_followed(self):
        target = self.root / "scripts/linked.ps1"
        try:
            target.symlink_to(self.root / "README.md")
        except OSError:
            self.skipTest("Host does not permit creating symlinks")
        with self.assertRaises(ValueError):
            guard.check(self.root)

    def test_active_spring_selector_cannot_be_pinned_as_metadata(self):
        path = "backend/app/src/main/java/Runtime.java"
        self.write(path, '@Profile("!gatew") class Runtime {}')
        self.policy([dict(path=path, sha256="a" * 64, kind="HISTORICAL_METADATA", reason="False classification",
                          owner="Owner", removalTrigger="Migration")])
        self.assertTrue(any(e.startswith("ACTIVE_SPRING_STAGE_SELECTOR:") for e in guard.check(self.root)[0]))
        self.write(path, r'@org.springframework.context.annotation.Profile("!gate\u0077") class Runtime {}')
        self.assertTrue(any(e.startswith("ACTIVE_SPRING_STAGE_SELECTOR:") for e in guard.check(self.root)[0]))

    def test_workflow_neutral_executable_semantics_all_supported_types(self):
        for suffix, runner in ((".ps1", "pwsh"), (".sh", "bash"), (".py", "python"),
                               (".js", "node"), (".mjs", "node"), (".cjs", "node")):
            with self.subTest(suffix=suffix):
                path = "scripts/runtime" + suffix
                self.write(".github/workflows/neutral.yml", "jobs:\n  check:\n    steps:\n      - run: " + runner + " " + path)
                self.write(path, 'if (GateYMode) { launch("--spring.profiles.active=gatey-readonly-qualification"); }')
                self.assertIn("STAGE_SEMANTICS: " + path, guard.check(self.root)[0])
                (self.root / path).unlink()

    def test_unknown_active_inputs_rejected_even_without_stage_literal_or_caller(self):
        for suffix in (".rb", ".lua", ".cmd", ".opaque", ""):
            with self.subTest(suffix=suffix):
                path = "scripts/runtime" + suffix
                self.write(".github/workflows/neutral.yml", "run: node " + path)
                self.write(path, "neutral content")
                self.assertIn("UNSUPPORTED_ACTIVE_EXECUTABLE_INPUT: " + path, guard.check(self.root)[0])
                (self.root / path).unlink()

    def test_interpreter_input_outside_control_plane_is_inspected_or_rejected(self):
        for path in ("tools/runtime.js", "tools/runtime.opaque"):
            with self.subTest(path=path):
                self.write(path, "const GateYMode = true;")
                self.write(".github/workflows/neutral.yml", "run: node " + path)
                self.assert_rejected()

    def test_direct_workflow_launcher_and_missing_inputs_fail_closed(self):
        self.write("tools/runtime.opaque", "neutral")
        self.write(".github/workflows/neutral.yml", "run: ./tools/runtime.opaque")
        self.assertIn("UNSUPPORTED_ACTIVE_EXECUTABLE_INPUT: tools/runtime.opaque", guard.check(self.root)[0])
        self.write(".github/workflows/neutral.yml", "run: tools/runtime.opaque")
        self.assertIn("UNSUPPORTED_ACTIVE_EXECUTABLE_INPUT: tools/runtime.opaque", guard.check(self.root)[0])
        self.write(".github/workflows/neutral.yml", "run: node tools/missing.js")
        self.assertIn("UNSUPPORTED_ACTIVE_EXECUTABLE_INPUT: tools/missing.js", guard.check(self.root)[0])

    def test_javascript_module_inputs_are_transitively_inspected(self):
        self.write(".github/workflows/neutral.yml", "run: node scripts/runtime.js")
        for suffix in (".mjs", ".opaque"):
            with self.subTest(suffix=suffix):
                self.write("scripts/runtime.js", "import '../tools/secondary" + suffix + "';")
                self.write("tools/secondary" + suffix, 'const GateYMode = true;')
                self.assert_rejected()

    def test_historical_javascript_and_generated_inputs_remain_excluded(self):
        for path in ("docs/audit/evidence/runtime.js", "docs/gates/runtime.mjs", "docs/archive/runtime.opaque",
                     "backend/app/target/runtime.js", "scripts/node_modules/runtime.opaque"):
            self.write(path, "const GateYMode = true;")
        self.assertEqual([], guard.check(self.root)[0])

    def test_safe_data_cannot_be_executed_or_mutated(self):
        import hashlib
        path = "scripts/ownership"
        self.write(path, "metadata")
        self.policy([], safe_inputs=[dict(path=path, sha256=hashlib.sha256(b"metadata").hexdigest(), reason="Ownership metadata")])
        self.assertEqual([], guard.check(self.root)[0])
        self.write(".github/workflows/neutral.yml", "run: node scripts/ownership")
        # An extensionless active file remains data only if it is never used as an input.
        self.assertIn("DATA_CLASSIFICATION_USED_AS_EXECUTABLE: " + path, guard.check(self.root)[0])
        self.write(path, "changed")
        self.assert_rejected()


class JavaScriptDependencyTest(unittest.TestCase):
    setUp = StageAssetGuardTest.setUp
    write = StageAssetGuardTest.write
    policy = StageAssetGuardTest.policy

    def graph(self, statement, secondary='export const something = 1; const GateYMode = true;'):
        self.write('.github/workflows/neutral.yml', 'run: node scripts/runtime.mjs')
        self.write('scripts/runtime.mjs', statement)
        self.write('tools/secondary.mjs', secondary)

    def node_pass(self):
        # Only execute these test-owned, literal fixtures, never analyzed repository JS.
        # Environment preloads must not turn a pure fixture into arbitrary execution.
        env = {k: v for k, v in os.environ.items() if k.upper() not in {'NODE_OPTIONS', 'NODE_PATH'}}
        result = subprocess.run(['node', str(self.root / 'scripts/runtime.mjs')],
                                cwd=self.root, env=env, capture_output=True, text=True, timeout=15)
        self.assertEqual(0, result.returncode, result.stderr)

    def stage_rejected(self):
        self.assertIn('STAGE_SEMANTICS: tools/secondary.mjs', guard.check(self.root)[0])

    def test_original_commented_import_executes_but_guard_rejects(self):
        self.graph("import /* dependency */ '../tools/secondary.mjs';")
        self.node_pass()
        self.stage_rejected()

    def test_compact_comment_import_executes_but_guard_rejects(self):
        self.graph("import/*x*/'../tools/secondary.mjs';")
        self.node_pass()
        self.stage_rejected()

    def test_multiline_from_import_executes_but_guard_rejects(self):
        self.graph("import {\n something\n} from /* x */ '../tools/secondary.mjs';")
        self.node_pass()
        self.stage_rejected()

    def test_static_import_and_export_grammar(self):
        cases = (
            "import '../tools/secondary.mjs';",
            "import value from '../tools/secondary.mjs';",
            "import value /* varied */ from '../tools/secondary.mjs';",
            "import { something } from '../tools/secondary.mjs';",
            "import { something as renamed, } from '../tools/secondary.mjs';",
            "import * as ns from '../tools/secondary.mjs';",
            "import value, { something } from '../tools/secondary.mjs';",
            "import value, * as ns from '../tools/secondary.mjs';",
            "export { something } from '../tools/secondary.mjs';",
            "export { something as renamed, } from '../tools/secondary.mjs';",
            "export * from '../tools/secondary.mjs';",
            "export * as ns from '../tools/secondary.mjs';",
            "import\n// local module\n{something}\nfrom\n'../tools/secondary.mjs'\n",
        )
        for statement in cases:
            with self.subTest(statement=statement):
                self.graph(statement)
                self.assertEqual(['../tools/secondary.mjs'], guard.javascript_dependencies(statement))
                self.stage_rejected()

    def test_reexports_execute_and_recurse(self):
        for statement in ("export { something } from/*note*/'../tools/secondary.mjs';",
                          "export * from '../tools/secondary.mjs';",
                          "export * as ns from '../tools/secondary.mjs';"):
            with self.subTest(statement=statement):
                self.graph(statement)
                self.node_pass()
                self.stage_rejected()

    def test_string_false_positive_allowed(self):
        self.graph('const x = "import \'./fake.mjs\'";', 'const neutral = true;')
        self.node_pass()
        self.assertEqual([], guard.javascript_dependencies((self.root / 'scripts/runtime.mjs').read_text()))
        self.assertEqual([], guard.check(self.root)[0])

    def test_block_comment_false_positive_allowed(self):
        self.graph("/*\nimport './fake.mjs';\n*/", 'const neutral = true;')
        self.node_pass()
        self.assertEqual([], guard.check(self.root)[0])

    def test_line_comment_false_positive_allowed(self):
        self.graph("// import './fake.mjs';\n", 'const neutral = true;')
        self.node_pass()
        self.assertEqual([], guard.check(self.root)[0])

    def test_comments_and_quotes_do_not_hide_real_dependency(self):
        self.graph('const x = "/* import \'./fake.mjs\' */";\n'
                   "/* decoy ' */ import/* different\ncomment */'../tools/secondary.mjs';")
        self.node_pass()
        self.stage_rejected()

    def test_multihop_and_cycle_are_deterministic(self):
        self.graph("import '../tools/secondary.mjs';", "import './third.mjs';")
        self.write('tools/third.mjs', "import '../scripts/runtime.mjs';")
        self.node_pass()
        expected = guard.check(self.root)
        self.assertEqual([], expected[0])
        self.assertEqual(expected, guard.check(self.root))
        self.write('tools/third.mjs', "import '../scripts/runtime.mjs'; const GateYMode = true;")
        self.assertIn('STAGE_SEMANTICS: tools/third.mjs', guard.check(self.root)[0])

    def test_missing_dependency_and_unknown_suffix_rejected(self):
        for dep in ('../tools/missing.mjs', '../tools/unknown.opaque', '../tools/extensionless'):
            with self.subTest(dep=dep):
                self.graph('import ' + repr(dep) + ';')
                self.assertTrue(guard.check(self.root)[0])

    def test_nonrelative_path_escape_and_url_forms_rejected(self):
        for dep in ('../../outside.mjs', '/tmp/outside.mjs', 'node:fs', 'package-name',
                    '../tools/secondary.mjs?x', '../tools/secondary.mjs#x',
                    '../tools/%73econdary.mjs', '.hidden.mjs'):
            with self.subTest(dep=dep):
                self.graph('import ' + repr(dep) + ';')
                self.assertTrue(guard.check(self.root)[0])

    def test_unsupported_active_syntax_and_lexical_ambiguity_rejected(self):
        cases = (
            "require('../tools/secondary.cjs');", "import('../tools/secondary.mjs');",
            "import(name);", "const load = import;", "import.meta.resolve('./x.mjs');",
            "const x = `import './fake.mjs'`;", "const x = `${import('./x.mjs')}`;",
            "const x = /import './fake.mjs'/;", 'const x = 8 / 2;',
            "import './x.mjs' with { type: 'json' };",
            r"import './\u0078.mjs';", r"const \u0061 = 1;",
            "function run() { import('./x.mjs'); }", "export { local };",
            "const x = '\\xZZ';", "import './x.mjs' + other;",
        )
        for statement in cases:
            with self.subTest(statement=statement):
                self.graph(statement)
                with self.assertRaisesRegex(ValueError, 'JS_'):
                    guard.javascript_dependencies(statement)
                self.assertIn('UNSUPPORTED_ACTIVE_JS_SYNTAX: scripts/runtime.mjs', guard.check(self.root)[0])

    def test_parse_failures_reject(self):
        for source in ("import /* unfinished", "import './unfinished", 'import { value from "./x.mjs";',
                       "import value { x } from './x.mjs';", "import value, from './x.mjs';",
                       "import { default } from './x.mjs';", "import './x.mjs' const x = 1;",
                       'const x = 01;', 'const\x85x = 1;', 'const x = 1; const x = 2;',
                       "import { x, x } from './x.mjs';", "export { a as x, b as x } from './x.mjs';"):
            with self.subTest(source=source):
                self.graph(source)
                with self.assertRaisesRegex(ValueError, 'JS_'):
                    guard.javascript_dependencies(source)
                self.assertIn('UNSUPPORTED_ACTIVE_JS_SYNTAX: scripts/runtime.mjs', guard.check(self.root)[0])

    def test_malformed_input_cli_fails_closed_without_source_output(self):
        self.graph("import /* unfinished SENTINEL_PRIVATE_CONTENT")
        result = subprocess.run(['python3' if os.name != 'nt' else 'python', '-B', str(SPEC.origin),
                                 '--root', str(self.root)], capture_output=True, text=True, timeout=15)
        self.assertEqual(1, result.returncode)
        self.assertIn('UNSUPPORTED_ACTIVE_JS_SYNTAX: scripts/runtime.mjs', result.stdout)
        self.assertNotIn('SENTINEL_PRIVATE_CONTENT', result.stderr + result.stdout)

    def test_historical_identical_text_outside_active_scope(self):
        for base in ('docs/audit/evidence', 'docs/archive', 'docs/gates', 'docs/current/evidence'):
            self.write(base + '/runtime.mjs', "import /* dependency */ './missing.mjs'; const GateYMode = true;")
        self.assertEqual([], guard.check(self.root)[0])
        self.graph("import '../docs/audit/evidence/runtime.mjs';")
        self.assertTrue(guard.check(self.root)[0])

    def test_active_js_and_cjs_are_checked(self):
        for suffix in ('.js', '.cjs'):
            with self.subTest(suffix=suffix):
                path = 'scripts/runtime' + suffix
                self.write(path, 'const stable = true;')
                self.assertEqual([], guard.check(self.root)[0])
                self.write(path, "require('../tools/secondary.cjs');")
                self.assertIn('UNSUPPORTED_ACTIVE_JS_SYNTAX: ' + path, guard.check(self.root)[0])
                (self.root / path).unlink()
        for statement in ("import './other.mjs';", 'export const x = 1;'):
            self.write('scripts/runtime.cjs', statement)
            self.assertIn('UNSUPPORTED_ACTIVE_JS_SYNTAX: scripts/runtime.cjs', guard.check(self.root)[0])

    def test_dependency_parent_symlink_rejected(self):
        self.graph("import '../tools/alias/secondary.mjs';", 'const neutral = true;')
        target = self.root / 'tools/alias'
        try:
            target.symlink_to(self.root / 'tools', target_is_directory=True)
        except OSError:
            self.skipTest('Host does not permit creating symlinks')
        self.assertTrue(guard.check(self.root)[0])


class CompatibilityRelationshipTest(unittest.TestCase):
    setUp = StageAssetGuardTest.setUp
    write = StageAssetGuardTest.write
    policy = StageAssetGuardTest.policy
    # Keep these in the canonical suite so existing CI invocation executes every regression.
    def contract_fixture(self):
        path = "backend/app/src/main/java/sample/Contract.java"
        self.write(path, 'package sample; public class Contract { public static final String VALUE = "gate-x5-v1"; }')
        contract = dict(identity="sample.Contract", path=path, canonicalOwner="Domain contract owner",
                        migrationTrigger="Versioned migration", removalCondition="All old readers migrated",
                        members=["Contract", "VALUE"], approvedCallers=[])
        exception = dict(path=path, sha256=guard.inspect(self.root, path)["sha256"], kind="WIRE_COMPATIBILITY",
                         reason="Persisted wire identity", owner="Domain contract owner", removalTrigger="Versioned migration")
        return contract, exception

    def test_exact_member_edges_authorize_existing_but_not_new_methods_or_files(self):
        contract, exception = self.contract_fixture()
        path = "backend/app/src/main/java/sample/Consumer.java"
        self.write(path, 'package sample; class Consumer { String existing() { return Contract.VALUE; } }')
        edges = guard.compatibility_edges(self.root, guard.sources(self.root), [contract])
        contract["approvedCallers"] = [dict(path=e[1], callerMember=e[2], contractMember=e[3]) for e in sorted(edges)]
        self.policy([exception], contracts=[contract])
        self.assertEqual([], guard.check(self.root)[0])
        for caller, content in ((path, 'package sample; class Consumer { String existing() { return Contract.VALUE; } String added() { return Contract.VALUE; } }'),
                                ("backend/app/src/main/java/sample/Neutral.java", 'package sample; class Neutral { String read() { return Contract.VALUE; } }')):
            with self.subTest(caller=caller):
                self.write(caller, content)
                self.assertTrue(any(e.startswith("UNAUTHORIZED_COMPATIBILITY_CALLER:") and caller in e for e in guard.check(self.root)[0]))
                self.write(path, 'package sample; class Consumer { String existing() { return Contract.VALUE; } }')

    def test_lifecycle_caller_evolution_preserves_precise_negative_guard(self):
        contract, exception = self.contract_fixture()
        path = "backend/app/src/main/java/sample/Consumer.java"
        self.write(path, 'package sample; class Consumer { String existing() { return Contract.VALUE; } }')
        edges = guard.compatibility_edges(self.root, guard.sources(self.root), [contract])
        contract["approvedCallers"] = [dict(path=e[1], callerMember=e[2], contractMember=e[3]) for e in sorted(edges)]
        self.policy([exception], contracts=[contract])
        self.write(path, 'package sample; class Consumer { String evolved() { return Contract.VALUE; } }')
        errors = guard.check(self.root)[0]
        self.assertTrue(any(e.startswith("STALE_COMPATIBILITY_CALLER:") for e in errors))
        self.assertTrue(any(e.startswith("UNAUTHORIZED_COMPATIBILITY_CALLER:") for e in errors))
        plan = self.root / "review-proposal.json"
        raw = lifecycle.encode(lifecycle.propose(self.root))
        plan.write_bytes(raw)
        identity = lifecycle.digest(raw)
        lifecycle.apply_reviewed(self.root, plan, identity)
        self.assertEqual([], guard.check(self.root)[0])
        self.write(path, 'package sample; class Consumer { String evolved() { return Contract.VALUE; } String surprise() { return Contract.VALUE; } }')
        self.assertTrue(any("#surprise()" in e for e in guard.check(self.root)[0]))

    def test_static_import_fully_qualified_inheritance_and_typed_uses_are_edges(self):
        contract, exception = self.contract_fixture()
        self.policy([exception], contracts=[contract])
        cases = (
            'package neutral; import static sample.Contract.VALUE; class Consumer { String read() { return VALUE; } }',
            'package neutral; import static sample.Contract.*; class Consumer { String read() { return VALUE; } }',
            'package neutral; class Consumer { String read() { return sample.Contract.VALUE; } }',
            'package neutral; class Consumer extends sample.Contract { String read() { return VALUE; } }',
            'package neutral; import sample.Contract; class Consumer { Contract value; String read() { return value.toString(); } }',
        )
        for content in cases:
            with self.subTest(content=content):
                self.write("backend/app/src/main/java/neutral/Consumer.java", content)
                errors = guard.check(self.root)[0]
                self.assertTrue(any("UNAUTHORIZED_COMPATIBILITY_CALLER:" in e and "#read()" in e for e in errors))

    def test_comments_and_string_literals_do_not_create_callers(self):
        contract, exception = self.contract_fixture()
        self.policy([exception], contracts=[contract])
        self.write("backend/app/src/main/java/neutral/Consumer.java", 'package neutral; /** sample.Contract.VALUE */ class Consumer { String text = "Contract.VALUE"; }')
        self.assertEqual(set(), guard.compatibility_edges(self.root, guard.sources(self.root), [contract]))

    def test_missing_contract_wildcard_unknown_member_and_missing_owner_fail_closed(self):
        contract, exception = self.contract_fixture()
        mutations = [[], [dict(contract, canonicalOwner="")], [dict(contract, members=["*"])], [dict(contract, members=["Contract"])],
                     [dict(contract, approvedCallers=[dict(path="backend/**", callerMember="*", contractMember="VALUE")])]]
        for contracts in mutations:
            self.policy([exception], contracts=contracts)
            with self.assertRaises(ValueError):
                guard.check(self.root)

    def test_existing_nested_type_import_cannot_authorize_a_new_method(self):
        contract, exception = self.contract_fixture()
        self.write(contract["path"], 'package sample; public class Contract { public static final String VALUE = "gate-x5-v1"; public static class Nested {} }')
        exception["sha256"] = guard.inspect(self.root, contract["path"])["sha256"]
        contract["members"] = sorted(guard.declared_compatibility_members((self.root / contract["path"]).read_text()))
        caller = "backend/app/src/main/java/neutral/Consumer.java"
        self.write(caller, 'package neutral; import sample.Contract.Nested; class Consumer {}')
        edges = guard.compatibility_edges(self.root, guard.sources(self.root), [contract])
        contract["approvedCallers"] = [dict(path=e[1], callerMember=e[2], contractMember=e[3]) for e in sorted(edges)]
        self.policy([exception], contracts=[contract])
        self.assertEqual([], guard.check(self.root)[0])
        self.write(caller, 'package neutral; import sample.Contract.Nested; class Consumer { Nested added() { return new Nested(); } }')
        self.assertTrue(any("UNAUTHORIZED_COMPATIBILITY_CALLER:" in e and "#added()" in e for e in guard.check(self.root)[0]))

    def test_type_grant_does_not_authorize_new_fully_qualified_member_use(self):
        contract, exception = self.contract_fixture()
        caller = "backend/app/src/main/java/neutral/Consumer.java"
        self.write(caller, 'package neutral; class Consumer { Object read() { return sample.Contract.class; } }')
        edges = guard.compatibility_edges(self.root, guard.sources(self.root), [contract])
        contract["approvedCallers"] = [dict(path=e[1], callerMember=e[2], contractMember=e[3]) for e in sorted(edges)]
        self.policy([exception], contracts=[contract])
        self.assertEqual([], guard.check(self.root)[0])
        self.write(caller, 'package neutral; class Consumer { Object read() { return sample.Contract.VALUE; } }')
        self.assertTrue(any("UNAUTHORIZED_COMPATIBILITY_CALLER:" in e and "#read()" in e and "VALUE" in e for e in guard.check(self.root)[0]))

    def test_java_parser_failure_and_scanner_failure_cannot_pass(self):
        contract, exception = self.contract_fixture()
        self.policy([exception], contracts=[contract])
        self.write("backend/app/src/main/java/neutral/Invalid.java", 'class Invalid { syntax error')
        with self.assertRaisesRegex(ValueError, "JAVA_REFERENCE_SCANNER_FAILED"):
            guard.check(self.root)
        with patch.object(guard.subprocess, "run", side_effect=FileNotFoundError("JDK absent")):
            with self.assertRaises(OSError):
                guard.check(self.root)

    def test_review_members_compile_run_but_neutral_caller_is_not_authorized(self):
        repository = Path(__file__).resolve().parents[3]
        manifest = json.loads((repository / guard.POLICY_PATH).read_text(encoding="utf-8"))
        contracts = [c for c in manifest["compatibilityContracts"] if c["identity"].endswith((".ExactPilotBinding", ".AdmissionGuard"))]
        self.assertEqual(2, len(contracts))
        caller = "backend/app/src/main/java/neutral/Consumer.java"
        self.write(caller, '''package neutral;
import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.AdmissionGuard;
public class Consumer {
 public static void main(String[] args) {
  if (ExactPilotBinding.DeploymentIdentity.RUNTIME_PROFILE.isEmpty()) throw new AssertionError();
  if (AdmissionGuard.SIDE_EFFECT_POLICY_VERSION.isEmpty()) throw new AssertionError();
 }
}''')
        classes = self.root / "compiled"
        classes.mkdir()
        result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(classes), "-sourcepath",
                                 str(repository / "backend/nq-core/src/main/java"), str(self.root / caller)], capture_output=True, text=True, timeout=60)
        self.assertEqual(0, result.returncode, result.stderr)
        result = subprocess.run(["java", "-cp", str(classes), "neutral.Consumer"], capture_output=True, text=True, timeout=30)
        self.assertEqual(0, result.returncode, result.stderr)
        actual = guard.compatibility_edges(self.root, guard.sources(self.root), contracts)
        approved = {(c["identity"], e["path"], e["callerMember"], e["contractMember"]) for c in contracts for e in c["approvedCallers"]}
        rejected = actual - approved
        for member in ("RUNTIME_PROFILE", "SIDE_EFFECT_POLICY_VERSION"):
            self.assertTrue(any(e[1] == caller and e[3] == member and "#main(" in e[2] for e in rejected))
        for contract in contracts:
            self.write(contract["path"], (repository / contract["path"]).read_text(encoding="utf-8"))
            contract["approvedCallers"] = [e for e in contract["approvedCallers"] if e["path"] in {c["path"] for c in contracts}]
        exceptions = [e for e in manifest["exceptions"] if e["path"] in {c["path"] for c in contracts}]
        self.policy(exceptions, contracts=contracts)
        errors = guard.check(self.root)[0]
        for member in ("RUNTIME_PROFILE", "SIDE_EFFECT_POLICY_VERSION"):
            self.assertTrue(any(e.startswith("UNAUTHORIZED_COMPATIBILITY_CALLER:") and caller in e and member in e for e in errors))


if __name__ == "__main__":
    unittest.main()
