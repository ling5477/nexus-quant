"""固定内容、真实审批及签名声明的负例；不把合成JSON当成密码学证明。"""
import copy
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

REPO = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location('authorization', REPO / 'scripts/release/authorize_legacy_transition.py')
auth = importlib.util.module_from_spec(spec)
spec.loader.exec_module(auth)


class TransitionAuthorizationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.event = self.root / 'event.json'
        self.event.write_text(json.dumps({'inputs': {'authorization': 'AUTHORIZE ' + auth.TRANSITION_SHA256,
            'local_identity_verified': 'true'}}), encoding='utf-8')
        self.env = {'GITHUB_ACTIONS': 'true', 'GITHUB_SERVER_URL': 'https://github.com',
            'GITHUB_REPOSITORY': auth.REPOSITORY, 'GITHUB_REPOSITORY_ID': auth.REPOSITORY_ID,
            'GITHUB_REPOSITORY_OWNER_ID': auth.OWNER_ID, 'GITHUB_ACTOR_ID': auth.OWNER_ID,
            'GITHUB_EVENT_NAME': 'workflow_dispatch', 'GITHUB_REF': 'refs/heads/dev',
            'GITHUB_ACTOR': auth.OWNER, 'GITHUB_TRIGGERING_ACTOR': auth.OWNER,
            'GITHUB_SHA': 'a' * 40, 'GITHUB_WORKFLOW_REF': auth.WORKFLOW + '@refs/heads/dev',
            'GITHUB_EVENT_PATH': str(self.event), 'GITHUB_RUN_ID': '12345', 'GITHUB_RUN_ATTEMPT': '1'}
        self.env.update({key: os.environ[key] for key in ('PATH', 'SystemRoot', 'TEMP', 'TMP') if key in os.environ})
        self.environment = {'name': auth.ENVIRONMENT, 'id': 77, 'can_admins_bypass': False,
            'protection_rules': [{'type': 'required_reviewers',
                'reviewers': [{'type': 'User', 'reviewer': {'id': int(auth.OWNER_ID)}}]}]}

    def test_real_fixed_bytes_and_frozen_source(self):
        raw, transition = auth.fixed_inputs()
        self.assertEqual(auth.digest(raw), auth.TRANSITION_SHA256)
        self.assertEqual(transition['targetSourceCommit'], auth.SOURCE_COMMIT)

    def test_pr_fork_other_actor_branch_and_rerun_actor_refused(self):
        for key, value in [('GITHUB_EVENT_NAME', 'pull_request'), ('GITHUB_REPOSITORY_ID', '42'),
                           ('GITHUB_ACTOR', 'other'), ('GITHUB_TRIGGERING_ACTOR', 'other'),
                           ('GITHUB_REF', 'refs/heads/feature'), ('GITHUB_ACTOR_ID', '42'),
                           ('GITHUB_WORKFLOW_REF', auth.WORKFLOW + '@refs/heads/feature')]:
            with self.subTest(key=key), patch.dict(os.environ, {**self.env, key: value}, clear=True):
                with self.assertRaises(RuntimeError):
                    auth.context()

    def test_owner_must_confirm_full_digest_and_local_identity(self):
        for inputs in ({'authorization': 'AUTHORIZE ' + '0' * 64, 'local_identity_verified': 'true'},
                       {'authorization': 'AUTHORIZE ' + auth.TRANSITION_SHA256, 'local_identity_verified': 'false'},
                       {'authorization': 'AUTHORIZE ' + auth.TRANSITION_SHA256, 'local_identity_verified': 'true', 'sha256': '0' * 64}):
            self.event.write_text(json.dumps({'inputs': inputs}), encoding='utf-8')
            with patch.dict(os.environ, self.env, clear=True), self.assertRaises(RuntimeError):
                auth.context()

    def test_environment_required_review_and_admin_bypass_enforced(self):
        cases = [dict(self.environment, can_admins_bypass=True), dict(self.environment, protection_rules=[]),
                 dict(self.environment, protection_rules=[{'type': 'required_reviewers', 'reviewers': []}])]
        for environment in cases:
            with patch.dict(os.environ, self.env, clear=True), patch.object(auth, 'api', return_value=environment):
                with self.assertRaises(RuntimeError):
                    auth.approval_policy()

    def test_no_actual_approval_refuses_before_output(self):
        with patch.dict(os.environ, self.env, clear=True), patch.object(auth, 'api', side_effect=[self.environment, []]):
            with self.assertRaisesRegex(RuntimeError, 'ACTUAL_OWNER_ENVIRONMENT_APPROVAL_REQUIRED'):
                auth.prepare(self.root / 'out')
        self.assertFalse((self.root / 'out').exists())

    def test_wrong_environment_and_rejected_review_cannot_authorize(self):
        for review in ({'state': 'rejected', 'user': {'id': int(auth.OWNER_ID)}, 'environments': [{'id': 77}]},
                       {'state': 'approved', 'user': {'id': int(auth.OWNER_ID)}, 'environments': [{'id': 88}]},
                       {'state': 'approved', 'user': {'id': 42}, 'environments': [{'id': 77}]}):
            with patch.dict(os.environ, self.env, clear=True), patch.object(auth, 'api', side_effect=[self.environment, [review]]):
                with self.assertRaisesRegex(RuntimeError, 'ACTUAL_OWNER_ENVIRONMENT_APPROVAL_REQUIRED'):
                    auth.prepare(self.root / 'out')

    def prepared(self):
        review = {'state': 'approved', 'user': {'id': int(auth.OWNER_ID)}, 'environments': [{'id': 77}],
                  'comment': 'A comment is intentionally never copied to public proof'}
        release = {'id': 418603194, 'draft': False, 'assets': [{'id': 659954162,
            'digest': 'sha256:37d79f1b752cd1a4967cc4a5acec4a51f3074c636f3b3be1be5ecc335e6b4931'}]}
        output = self.root / 'out'
        with patch.dict(os.environ, self.env, clear=True), patch.object(auth, 'api', side_effect=[self.environment, [review], release]):
            auth.prepare(output)
        return output, auth.read_json(output / 'authorization.json')

    def test_statement_distinguishes_signer_from_release_and_local_checks(self):
        output, claim = self.prepared()
        self.assertEqual(claim['signerWorkflowCommit'], 'a' * 40)
        self.assertNotEqual(claim['signerWorkflowCommit'], claim['releaseSourceCommit'])
        self.assertEqual(claim['authorization'], 'EXPLICIT_OWNER_APPROVAL')
        self.assertIn('NOT_RUNNER_BUILT_OR_HASHED', claim['targetArchiveVerification'])
        self.assertNotIn('comment', json.dumps(claim))
        self.assertEqual(sorted(p.name for p in output.iterdir()), ['authorization.json', 'transition-v1.json'])

    def test_verifier_checks_complete_predicate_subject_and_certificate_result(self):
        _, claim = self.prepared()
        # 这里只验证CLI结果后的语义层，不模拟或声称生成了真实签名。
        item = {'verificationResult': {'statement': {'subject': [{'name': 'transition-v1.json',
            'digest': {'sha256': auth.TRANSITION_SHA256}}], 'predicateType': auth.PREDICATE, 'predicate': claim},
            'signature': {'certificate': {'subjectAlternativeName': 'https://github.com/' + auth.WORKFLOW + '@refs/heads/dev'}},
            'verifiedTimestamps': [{'testOnly': True}]}}
        self.assertEqual(len(auth.validate_statement([item], claim)), 1)
        for path, value in [(('statement', 'predicateType'), 'https://example.invalid/other'),
                            (('statement', 'subject'), []), (('signature', 'certificate'), {}),
                            (('verifiedTimestamps',), [])]:
            changed = copy.deepcopy(item)
            target = changed['verificationResult']
            for key in path[:-1]:
                target = target[key]
            target[path[-1]] = value
            with self.subTest(path=path), self.assertRaises(RuntimeError):
                auth.validate_statement([changed], claim)
        changed = copy.deepcopy(item)
        changed['verificationResult']['statement']['predicate']['transition']['installationConfigSha256'] = '0' * 64
        with self.assertRaisesRegex(RuntimeError, 'SIGNED_AUTHORIZATION_MISMATCH'):
            auth.validate_statement([changed], claim)
        for identity in ('https://github.com/' + auth.WORKFLOW + '.other@refs/heads/dev',
                         'https://github.com/' + auth.WORKFLOW + '@refs/heads/feature'):
            changed = copy.deepcopy(item)
            changed['verificationResult']['signature']['certificate']['subjectAlternativeName'] = identity
            with self.assertRaisesRegex(RuntimeError, 'EXACT_SIGNER_CERTIFICATE_IDENTITY_REQUIRED'):
                auth.validate_statement([changed], claim)

    def test_subject_bytes_whitespace_tampering_refused(self):
        public = self.root / 'public'
        public.mkdir()
        (public / 'transition-v1.json').write_bytes((auth.PUBLIC / 'transition-v1.json').read_bytes() + b'\n')
        with patch.object(auth, 'PUBLIC', public), self.assertRaisesRegex(RuntimeError, 'FIXED_TRANSITION_BYTES_CHANGED'):
            auth.fixed_inputs()

    def test_duplicate_json_keys_refused(self):
        with self.assertRaisesRegex(RuntimeError, 'DUPLICATE_JSON_FIELD'):
            json.loads('{"authorization":"approved","authorization":"denied"}', object_pairs_hook=auth.strict_object)

    def test_modified_scope_owner_and_run_refused_before_crypto_command(self):
        output, claim = self.prepared()
        raw, transition = auth.fixed_inputs()
        cases = [dict(claim, scope='PRODUCT_RELEASE'), dict(claim, productReleaseAuthorized=True),
                 dict(claim, ownerApproval={**claim['ownerApproval'], 'state': 'rejected'}),
                 dict(claim, runId='99999')]
        for changed in cases:
            auth.write_json(output / 'authorization.json', changed)
            with patch.dict(os.environ, self.env, clear=True), patch.object(auth, 'fixed_inputs', return_value=(raw, transition)), \
                    patch.object(auth.subprocess, 'run') as external:
                with self.assertRaises(RuntimeError):
                    auth.verify(output, 'a' * 40)
                external.assert_not_called()

    def test_crypto_failure_is_bounded_and_uses_exact_bundle_and_workflow_commit(self):
        output, _ = self.prepared()
        raw, transition = auth.fixed_inputs()
        bundle = self.root / 'unsigned-test-fixture.json'
        bundle.write_text('{}', encoding='utf-8')
        env = {**self.env, 'NQ_BUNDLE_PATH': str(bundle)}
        failed = subprocess.CompletedProcess([], 1, stdout=b'', stderr=b'SIGNATURE_REJECTED_TEST_FIXTURE')
        with patch.dict(os.environ, env, clear=True), patch.object(auth, 'fixed_inputs', return_value=(raw, transition)), \
                patch.object(auth.subprocess, 'run', return_value=failed) as external, patch.object(auth.time, 'sleep'):
            with self.assertRaisesRegex(RuntimeError, 'CRYPTOGRAPHIC_VERIFICATION_FAILED'):
                auth.verify(output, 'a' * 40)
        self.assertEqual(external.call_count, 3)
        args = external.call_args.args[0]
        self.assertIn('--bundle', args)
        self.assertEqual(args[args.index('--signer-digest') + 1], 'a' * 40)
        self.assertEqual(args[args.index('--source-digest') + 1], 'a' * 40)
        self.assertEqual(args[args.index('--source-ref') + 1], 'refs/heads/dev')
        self.assertNotIn('--cert-identity', args)
        self.assertFalse((output / 'signing-record.json').exists())


if __name__ == '__main__':
    unittest.main()
