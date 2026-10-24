"""为固定实例签署明确授权；不声称Actions独立构建了本地发行包。"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time

REPOSITORY = 'ling5477/nexus-quant'
REPOSITORY_ID = '1156012145'
OWNER = 'ling5477'
OWNER_ID = '128130348'
ENVIRONMENT = 'nq-release-transition-signing'
WORKFLOW = REPOSITORY + '/.github/workflows/nq-release-transition-attest.yml'
PREDICATE = 'https://github.com/' + REPOSITORY + '/attestations/legacy-transition-authorization/v1'
SOURCE_COMMIT = '1dc5ea7ea5971a7621c3bbfc66a0e9fe8cd14d71'
SOURCE_TREE = 'c323207865b8ef6a978746dd2ae63f15fdcc3ca4'
TRANSITION_SHA256 = 'c084eda5916007d90d1e3799a906ab4f7b95da0e87b8b0f3a7cb08f88d95fd56'
TARGET_MANIFEST_SHA256 = 'c3437d4bab960546cfba57d065cda375610ccd700f593ea95827a77020e1a67d'
REPO = Path(__file__).resolve().parents[2]
PUBLIC = REPO / '.github/authorizations'
FIELDS = {'format', 'bridgeSourceCommit', 'bridgeSha256', 'baselineToolSha256',
          'sourceManifestSha256', 'targetManifestSha256', 'targetArchiveSha256',
          'targetSourceCommit', 'architecture', 'installationConfigSha256', 'projectName'}


def require(value, message):
    if not value:
        raise RuntimeError(message)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def strict_object(items):
    result = {}
    for key, value in items:
        require(key not in result, 'DUPLICATE_JSON_FIELD')
        result[key] = value
    return result


def read_json(path):
    data = path.read_bytes()
    require(len(data) <= 1024 * 1024, 'JSON_SIZE_LIMIT')
    return json.loads(data, object_pairs_hook=strict_object)


def write_json(path, value):
    path.write_text(json.dumps(value, sort_keys=True, indent=2) + '\n', encoding='utf-8', newline='\n')


def command(args, timeout=30):
    result = subprocess.run(args, cwd=REPO, capture_output=True, timeout=timeout)
    require(result.returncode == 0 and len(result.stdout) <= 4 * 1024 * 1024, 'EXTERNAL_VERIFICATION_FAILED')
    return result.stdout


def api(path):
    return json.loads(command(['gh', 'api', path]), object_pairs_hook=strict_object)


def context():
    values = os.environ
    require(values.get('GITHUB_ACTIONS') == 'true' and values.get('GITHUB_SERVER_URL') == 'https://github.com', 'ACTIONS_CONTEXT_REQUIRED')
    require(values.get('GITHUB_REPOSITORY') == REPOSITORY and values.get('GITHUB_REPOSITORY_ID') == REPOSITORY_ID
            and values.get('GITHUB_REPOSITORY_OWNER_ID') == OWNER_ID, 'WRONG_REPOSITORY_IDENTITY')
    require(values.get('GITHUB_EVENT_NAME') == 'workflow_dispatch' and values.get('GITHUB_REF') == 'refs/heads/dev', 'MANUAL_DEV_DISPATCH_REQUIRED')
    require(values.get('GITHUB_ACTOR') == values.get('GITHUB_TRIGGERING_ACTOR') == OWNER, 'EXPLICIT_OWNER_REQUIRED')
    require(values.get('GITHUB_ACTOR_ID') == OWNER_ID, 'WRONG_OWNER_IDENTITY')
    require(re.fullmatch('[a-f0-9]{40}', values.get('GITHUB_SHA', '')), 'INVALID_SIGNER_COMMIT')
    require(values.get('GITHUB_WORKFLOW_REF') == WORKFLOW + '@refs/heads/dev', 'WRONG_SIGNER_WORKFLOW')
    event = read_json(Path(values['GITHUB_EVENT_PATH']))
    require(event.get('inputs') == {'authorization': 'AUTHORIZE ' + TRANSITION_SHA256,
                                   'local_identity_verified': 'true'}, 'EXPLICIT_FIXED_CONTENT_CONFIRMATION_REQUIRED')
    return values


def approval_policy():
    context()
    environment = api('repos/' + REPOSITORY + '/environments/' + ENVIRONMENT)
    rules = [r for r in environment.get('protection_rules', []) if r.get('type') == 'required_reviewers']
    require(len(rules) == 1 and environment.get('can_admins_bypass') is False, 'PROTECTED_OWNER_REVIEW_REQUIRED')
    reviewers = rules[0].get('reviewers', [])
    require(len(reviewers) == 1 and reviewers[0].get('type') == 'User'
            and str(reviewers[0].get('reviewer', {}).get('id')) == OWNER_ID, 'OWNER_REVIEWER_REQUIRED')
    require(environment.get('name') == ENVIRONMENT and isinstance(environment.get('id'), int), 'WRONG_APPROVAL_ENVIRONMENT')
    return environment


def fixed_inputs():
    raw = (PUBLIC / 'transition-v1.json').read_bytes()
    require(digest(raw) == TRANSITION_SHA256, 'FIXED_TRANSITION_BYTES_CHANGED')
    transition = json.loads(raw, object_pairs_hook=strict_object)
    require(set(transition) == FIELDS and transition['format'] == 'NQ_LEGACY_BRIDGE_V1', 'TRANSITION_CONTRACT_CHANGED')
    require(transition['bridgeSourceCommit'] == transition['targetSourceCommit'] == SOURCE_COMMIT, 'WRONG_RELEASE_COMMIT')
    require(transition['projectName'] == 'nq-v101-formal-bridge-20261024' and transition['architecture'] == 'amd64', 'WRONG_FIXED_INSTALLATION')
    require(command(['git', 'rev-parse', SOURCE_COMMIT + '^{tree}']).decode().strip() == SOURCE_TREE, 'WRONG_RELEASE_TREE')
    for path, key in (('release/installers/legacy_upgrade_bridge.py', 'bridgeSha256'),
                      ('scripts/verify/upgrade_business_baseline.py', 'baselineToolSha256')):
        require(digest(command(['git', 'show', SOURCE_COMMIT + ':' + path])) == transition[key], 'FROZEN_SOURCE_TOOL_MISMATCH')
    manifest_raw = (PUBLIC / 'target-package-amd64.env').read_bytes()
    require(digest(manifest_raw) == TARGET_MANIFEST_SHA256 == transition['targetManifestSha256'], 'TARGET_MANIFEST_BYTES_CHANGED')
    manifest = dict(line.split('=', 1) for line in manifest_raw.decode().splitlines())
    require(manifest['SOURCE_SHA'] == SOURCE_COMMIT and manifest['VERSION'] == '1.0.1'
            and manifest['SCHEMA_VERSION'] == '47' and manifest['ARCH'] == transition['architecture']
            and manifest['ARCHIVE_SHA256'] == transition['targetArchiveSha256'], 'TARGET_IDENTITY_MISMATCH')
    return raw, transition


def prepare(output):
    values = context()
    environment = approval_policy()
    raw, transition = fixed_inputs()
    reviews = api('repos/' + REPOSITORY + '/actions/runs/' + values['GITHUB_RUN_ID'] + '/approvals')
    approved = [r for r in reviews if r.get('state') == 'approved' and str(r.get('user', {}).get('id')) == OWNER_ID
                and any(e.get('id') == environment['id'] for e in r.get('environments', []))]
    require(approved, 'ACTUAL_OWNER_ENVIRONMENT_APPROVAL_REQUIRED')
    release = api('repos/' + REPOSITORY + '/releases/tags/v1.0.0')
    assets = [a for a in release.get('assets', []) if a.get('id') == 659954162]
    require(release.get('id') == 418603194 and not release.get('draft') and len(assets) == 1
            and assets[0].get('digest') == 'sha256:37d79f1b752cd1a4967cc4a5acec4a51f3074c636f3b3be1be5ecc335e6b4931', 'OFFICIAL_SOURCE_RELEASE_CHANGED')
    require(not output.exists(), 'FRESH_OUTPUT_REQUIRED')
    output.mkdir(parents=True)
    (output / 'transition-v1.json').write_bytes(raw)
    predicate = {'format': 'NQ_LEGACY_TRANSITION_AUTHORIZATION_V1', 'authorization': 'EXPLICIT_OWNER_APPROVAL',
        'scope': 'ISOLATED_V100_TO_V101_UPGRADE', 'transitionSha256': TRANSITION_SHA256,
        'transition': transition, 'releaseSourceCommit': SOURCE_COMMIT, 'releaseSourceTree': SOURCE_TREE,
        'validationMode': 'OWNER_AUTHORIZED_DIGEST_ATTESTATION',
        'runnerVerification': 'EXACT_TRANSITION_AND_TARGET_MANIFEST_BYTES_AND_FROZEN_SOURCE_TOOLS',
        'targetArchiveVerification': 'OWNER_CONFIRMED_LOCAL_BYTES_NOT_RUNNER_BUILT_OR_HASHED',
        'installationConfigVerification': 'OWNER_CONFIRMED_LOCAL_BYTES_NOT_UPLOADED',
        'officialSourceRelease': {'releaseId': 418603194, 'assetId': 659954162, 'verification': 'GITHUB_RELEASE_DIGEST_METADATA'},
        'ownerApproval': {'login': OWNER, 'userId': OWNER_ID, 'environment': ENVIRONMENT,
                          'environmentId': environment['id'], 'state': 'approved'},
        'signerWorkflow': WORKFLOW, 'signerWorkflowCommit': values['GITHUB_SHA'],
        'runId': values['GITHUB_RUN_ID'], 'runAttempt': values['GITHUB_RUN_ATTEMPT'],
        'authorizationPreparedAt': datetime.now(timezone.utc).isoformat(),
        'productReleaseAuthorized': False, 'liveAuthorized': False}
    write_json(output / 'authorization.json', predicate)


def validate_statement(result, expected):
    require(isinstance(result, list) and result, 'VERIFIED_ATTESTATION_REQUIRED')
    valid = []
    for item in result:
        verified = item.get('verificationResult', {})
        statement = verified.get('statement', {})
        signature = verified.get('signature', {})
        require(signature.get('certificate') and verified.get('verifiedTimestamps'), 'VERIFIED_CERTIFICATE_AND_TIMESTAMP_REQUIRED')
        # CLI的工作流过滤允许SAN前缀；完整身份还必须排除其他文件名或分支后缀。
        require(signature['certificate'].get('subjectAlternativeName') == 'https://github.com/' + WORKFLOW + '@refs/heads/dev',
                'EXACT_SIGNER_CERTIFICATE_IDENTITY_REQUIRED')
        require(statement.get('predicateType') == PREDICATE, 'WRONG_PREDICATE_TYPE')
        require(statement.get('subject') == [{'name': 'transition-v1.json', 'digest': {'sha256': TRANSITION_SHA256}}], 'WRONG_SIGNED_SUBJECT')
        require(statement.get('predicate') == expected, 'SIGNED_AUTHORIZATION_MISMATCH')
        valid.append(item)
    return valid


def verify(output, signer_commit):
    require(re.fullmatch('[a-f0-9]{40}', signer_commit), 'EXACT_SIGNER_COMMIT_REQUIRED')
    expected = read_json(output / 'authorization.json')
    require(expected.get('signerWorkflowCommit') == signer_commit and expected.get('releaseSourceCommit') == SOURCE_COMMIT, 'SIGNER_AND_RELEASE_COMMIT_CONFUSION')
    _, transition = fixed_inputs()
    require(expected.get('format') == 'NQ_LEGACY_TRANSITION_AUTHORIZATION_V1'
            and expected.get('authorization') == 'EXPLICIT_OWNER_APPROVAL'
            and expected.get('scope') == 'ISOLATED_V100_TO_V101_UPGRADE'
            and expected.get('validationMode') == 'OWNER_AUTHORIZED_DIGEST_ATTESTATION'
            and expected.get('transitionSha256') == TRANSITION_SHA256
            and expected.get('transition') == transition and expected.get('releaseSourceTree') == SOURCE_TREE
            and expected.get('signerWorkflow') == WORKFLOW
            and expected.get('productReleaseAuthorized') is False and expected.get('liveAuthorized') is False,
            'EXPLICIT_FIXED_SCOPE_AUTHORIZATION_REQUIRED')
    owner = expected.get('ownerApproval', {})
    require(owner.get('login') == OWNER and owner.get('userId') == OWNER_ID and owner.get('state') == 'approved'
            and owner.get('environment') == ENVIRONMENT and isinstance(owner.get('environmentId'), int), 'SIGNED_OWNER_APPROVAL_REQUIRED')
    require(expected.get('runId') == os.environ.get('GITHUB_RUN_ID')
            and expected.get('runAttempt') == os.environ.get('GITHUB_RUN_ATTEMPT'), 'CURRENT_SIGNING_RUN_REQUIRED')
    require(digest((output / 'transition-v1.json').read_bytes()) == TRANSITION_SHA256, 'LOCAL_SUBJECT_CHANGED')
    args = ['gh', 'attestation', 'verify', str(output / 'transition-v1.json'), '--repo', REPOSITORY,
            '--predicate-type', PREDICATE, '--signer-workflow', WORKFLOW, '--signer-digest', signer_commit,
            '--source-digest', signer_commit, '--source-ref', 'refs/heads/dev',
            '--cert-oidc-issuer', 'https://token.actions.githubusercontent.com', '--format', 'json']
    bundle = os.environ.get('NQ_BUNDLE_PATH')
    require(bundle and Path(bundle).is_file(), 'ACTUAL_ATTESTATION_BUNDLE_REQUIRED')
    shutil.copyfile(bundle, output / 'attestation-bundle.json')
    args += ['--bundle', str(output / 'attestation-bundle.json')]
    # 直接验证本次bundle，避免混入同一subject的其他授权；只读网络失败可有界重试。
    for attempt in range(3):
        result = subprocess.run(args, capture_output=True, timeout=60)
        (output / f'verification-{attempt + 1}.stderr').write_bytes(result.stderr)
        if result.returncode == 0:
            parsed = json.loads(result.stdout)
            validate_statement(parsed, expected)
            (output / 'verified-attestation.json').write_bytes(result.stdout)
            write_json(output / 'signing-record.json', {'attestationId': os.environ['NQ_ATTESTATION_ID'],
                'attestationUrl': os.environ['NQ_ATTESTATION_URL'], 'signerWorkflow': WORKFLOW,
                'signerCommit': signer_commit, 'releaseSourceCommit': SOURCE_COMMIT,
                'runUrl': 'https://github.com/' + REPOSITORY + '/actions/runs/' + expected['runId'],
                'bundleSha256': digest((output / 'attestation-bundle.json').read_bytes()),
                'recordedAt': datetime.now(timezone.utc).isoformat(), 'verification': 'CRYPTOGRAPHIC_AND_COMPLETE_CLAIM_VERIFIED'})
            return
        if attempt < 2:
            time.sleep(3)
    raise RuntimeError('CRYPTOGRAPHIC_VERIFICATION_FAILED')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('policy', 'prepare', 'verify'))
    parser.add_argument('--output', type=Path)
    parser.add_argument('--signer-commit')
    args = parser.parse_args()
    if args.action == 'policy':
        approval_policy()
    elif args.action == 'prepare':
        require(args.output is not None, 'OUTPUT_REQUIRED')
        prepare(args.output)
    else:
        require(args.output is not None and args.signer_commit is not None, 'OUTPUT_AND_SIGNER_COMMIT_REQUIRED')
        verify(args.output, args.signer_commit)


if __name__ == '__main__':
    main()
