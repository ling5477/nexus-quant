[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar)
$fixture = Join-Path $tempRoot ('java-engineering-validation-' + [guid]::NewGuid().ToString('N'))
$hostPath = (Get-Process -Id $PID).Path
$registered = $false
$negative = 0

function Invoke-Validation([string] $Name, [string] $Expected) {
    $stdout = Join-Path $fixture 'validation.stdout'
    $stderr = Join-Path $fixture 'validation.stderr'
    $verifier = Join-Path $fixture 'scripts/java-standard/verify-java-engineering-standard.ps1'
    $arguments = '-NoProfile -ExecutionPolicy Bypass -File "' + $verifier + '"'
    $process = Start-Process -FilePath $hostPath -ArgumentList $arguments -Wait -PassThru -NoNewWindow -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    $output = [IO.File]::ReadAllText($stdout) + [IO.File]::ReadAllText($stderr)
    if ($Expected -eq 'PASS') {
        if ($process.ExitCode -ne 0 -or $output -notmatch 'GOVERNANCE_CHECKER_RESULT=PASS') { throw "Positive failed: $Name $output" }
    } else {
        if ($process.ExitCode -ne 2 -or $output -notmatch ([regex]::Escape($Expected) + ':')) { throw "Expected $Expected rejection: $Name $output" }
        $reasons = @{
            'platform-ci-version' = 'compiler release differs from CI Java'
            'platform-preview' = 'task must not enable preview or virtual threads'
            'architecture-mode' = 'architecture scope invalid'
            'missing-architecture-source' = 'missing architecture source'
            'unknown-engineering-rule' = 'unknown project rule'
            'invalid-rule-disposition' = 'invalid disposition'
            'baseline-schema' = 'baseline schema/ruleset invalid'
            'baseline-config-hash' = 'BASELINE_CONFIGURATION_HASH_MISMATCH'
            'standard-content-hash' = 'BASELINE_CONFIGURATION_HASH_MISMATCH'
            'baseline-proof-hash' = 'baseline deterministic hash mismatch'
        }
        if (-not $output.Contains($reasons[$Name])) { throw "Wrong rejection reason: $Name $output" }
        $script:negative++
    }
    Write-Output "JAVA_ENGINEERING_FIXTURE $Name=$Expected"
}

function Test-Mutation([string] $Name, [string] $RelativePath, [scriptblock] $Change, [string] $Expected) {
    $path = Join-Path $fixture $RelativePath
    $original = [IO.File]::ReadAllBytes($path)
    try {
        $text = [Text.Encoding]::UTF8.GetString($original)
        $updated = & $Change $text
        if ($updated -ceq $text) { throw "Mutation did not change input: $Name" }
        [IO.File]::WriteAllText($path, $updated, [Text.UTF8Encoding]::new($false))
        Invoke-Validation $Name $Expected
    } finally { [IO.File]::WriteAllBytes($path, $original) }
}

try {
    # 隔离 worktree 保留真实 Git blob 合同；所有变异只修改 fixture，不改调用仓库。
    & git -C $repoRoot worktree add --quiet --detach --no-checkout $fixture HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create validation fixture worktree' }
    $registered = $true
    foreach ($relative in @('backend/pom.xml', '.github/workflows/ci.yml', 'docs/current/ARCHITECTURE.md', 'docs/current/MODULES.md')) {
        $target = Join-Path $fixture $relative
        [IO.Directory]::CreateDirectory((Split-Path $target -Parent)) | Out-Null
        [IO.File]::Copy((Join-Path $repoRoot $relative), $target, $true)
    }
    foreach ($relative in @('scripts/java-standard', 'docs/standards/java')) {
        $source = Join-Path $repoRoot $relative
        foreach ($file in Get-ChildItem -LiteralPath $source -Recurse -File -Force) {
            $tail = $file.FullName.Substring($source.Length).TrimStart('\', '/')
            $target = Join-Path (Join-Path $fixture $relative) $tail
            [IO.Directory]::CreateDirectory((Split-Path $target -Parent)) | Out-Null
            [IO.File]::Copy($file.FullName, $target, $true)
        }
    }
    if (Test-Path (Join-Path $fixture '.agents/skills/nq-java-engineering-standard')) { throw 'Retired Skill unexpectedly present in fixture baseline' }
    Invoke-Validation 'canonical-without-retired-skill' 'PASS'
    Test-Mutation 'platform-ci-version' 'docs/standards/java/platform-profile.json' {
        param($s) $p = $s | ConvertFrom-Json; $p.java.ci_version = 99; $p | ConvertTo-Json -Depth 20
    } 'PLATFORM_PROFILE_INVALID'
    Test-Mutation 'platform-preview' 'docs/standards/java/platform-profile.json' {
        param($s) $p = $s | ConvertFrom-Json; $p.java.preview_enabled = $true; $p | ConvertTo-Json -Depth 20
    } 'PLATFORM_PROFILE_INVALID'
    Test-Mutation 'architecture-mode' 'docs/standards/java/java-shadow-scope.json' {
        param($s) $p = $s | ConvertFrom-Json; $p.scanner_mode = 'DISABLED'; $p | ConvertTo-Json -Depth 20
    } 'CONFIG_INVALID'
    Test-Mutation 'missing-architecture-source' 'docs/standards/java/java-shadow-scope.json' {
        param($s) $p = $s | ConvertFrom-Json; $p.architecture_sources = @('docs/current/nonexistent-architecture.md'); $p | ConvertTo-Json -Depth 20
    } 'CONFIG_INVALID'
    Test-Mutation 'unknown-engineering-rule' 'docs/standards/java/alibaba-huangshan-rule-mapping.yaml' {
        param($s) $s.Replace('project_rule_ids: ["JAVA-COMMON-NAMING-001"]', 'project_rule_ids: ["UNKNOWN-RULE"]')
    } 'MAPPING_INVALID'
    Test-Mutation 'invalid-rule-disposition' 'docs/standards/java/alibaba-huangshan-rule-mapping.yaml' {
        param($s) $s.Replace('disposition: "ADOPTED"', 'disposition: "IGNORED"')
    } 'MAPPING_INVALID'
    Test-Mutation 'baseline-schema' 'docs/standards/java/shadow-baseline.json' {
        param($s) $s.Replace('"schema_version": "2.0.0"', '"schema_version": "9.0.0"')
    } 'BASELINE_SCHEMA_INVALID'
    Test-Mutation 'baseline-config-hash' 'docs/standards/java/shadow-baseline.json' {
        param($s) [regex]::Replace($s, '("configuration_sha256":\s*")[a-f0-9]{64}', ('${1}' + ('0' * 64)))
    } 'BASELINE_CONFIGURATION_HASH_MISMATCH'
    Test-Mutation 'standard-content-hash' 'docs/standards/java/common-java-engineering-standard.md' {
        param($s) $s + "`nFixture content change.`n"
    } 'BASELINE_CONFIGURATION_HASH_MISMATCH'
    Test-Mutation 'baseline-proof-hash' 'docs/standards/java/shadow-baseline.json' {
        param($s) [regex]::Replace($s, '("deterministic_content_sha256":\s*")[a-f0-9]{64}', ('${1}' + ('0' * 64)))
    } 'BASELINE_SCHEMA_INVALID'
    Invoke-Validation 'restored-canonical' 'PASS'
    Write-Output "JAVA_ENGINEERING_VALIDATION_TEST positive=2 negative=$negative canonical-skill-dependency=NONE"
} finally {
    if ($registered) {
        $resolved = (Resolve-Path -LiteralPath $fixture).Path
        if (-not $resolved.StartsWith($tempRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe validation fixture cleanup path' }
        & git -C $repoRoot worktree remove --force -- $resolved
        if ($LASTEXITCODE -ne 0) { throw 'Validation fixture worktree cleanup failed' }
    }
}
