[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$artifactRoot = Join-Path $repo 'artifacts'
New-Item -ItemType Directory -Path $artifactRoot -Force | Out-Null
$runRoot = Join-Path $artifactRoot ('phase5a-gitleaks-execution-test-' + [Guid]::NewGuid().ToString('N'))
$invoker = Join-Path $repo 'scripts\ci\Invoke-VerifiedGitleaks.ps1'

function Assert-Condition([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw $Message }
}

function Invoke-ScannerCase([string] $BinaryPath, [string] $Mode, [string] $Source, [string] $Config, [string] $Report) {
    $stdout = Join-Path $runRoot "$Mode.stdout.txt"
    $stderr = Join-Path $runRoot "$Mode.stderr.txt"
    $previousMode = $env:NQ_GITLEAKS_FIXTURE_MODE
    try {
        $env:NQ_GITLEAKS_FIXTURE_MODE = $Mode
        $process = Start-Process -FilePath (Get-Process -Id $PID).Path -ArgumentList @(
            '-NoProfile', '-File', $invoker,
            '-BinaryPath', $BinaryPath,
            '-SourcePath', $Source,
            '-ConfigPath', $Config,
            '-ReportPath', $Report
        ) -Wait -PassThru -NoNewWindow -RedirectStandardOutput $stdout -RedirectStandardError $stderr
        return $process.ExitCode
    } finally {
        $env:NQ_GITLEAKS_FIXTURE_MODE = $previousMode
    }
}

try {
    New-Item -ItemType Directory -Path $runRoot -Force | Out-Null
    $verifiedDir = Join-Path $runRoot 'verified'
    $sentinelDir = Join-Path $runRoot 'system-path'
    $source = Join-Path $runRoot 'source'
    New-Item -ItemType Directory -Path $verifiedDir,$sentinelDir,$source -Force | Out-Null
    $config = Join-Path $runRoot 'gitleaks.toml'
    $report = Join-Path $runRoot 'report.json'
    [IO.File]::WriteAllText($config, "[extend]`nuseDefault = true`n")

    if ($IsWindows) {
        $verified = Join-Path $verifiedDir 'verified-gitleaks.cmd'
        $sentinel = Join-Path $sentinelDir 'gitleaks.cmd'
        $verifiedContent = @(
            '@echo off',
            'if "%NQ_GITLEAKS_FIXTURE_MODE%"=="finding" exit /b 2',
            'if "%NQ_GITLEAKS_FIXTURE_MODE%"=="error" exit /b 3',
            'exit /b 0'
        ) -join "`r`n"
        $sentinelContent = @(
            '@echo off',
            "echo executed>`"$runRoot\sentinel-executed.txt`"",
            'exit /b 0'
        ) -join "`r`n"
        [IO.File]::WriteAllText($verified, $verifiedContent + "`r`n")
        [IO.File]::WriteAllText($sentinel, $sentinelContent + "`r`n")
    } else {
        $verified = Join-Path $verifiedDir 'verified-gitleaks'
        $sentinel = Join-Path $sentinelDir 'gitleaks'
        $verifiedContent = @'
#!/usr/bin/env sh
[ "$NQ_GITLEAKS_FIXTURE_MODE" = finding ] && exit 2
[ "$NQ_GITLEAKS_FIXTURE_MODE" = error ] && exit 3
exit 0
'@
        $sentinelTarget = (Join-Path $runRoot 'sentinel-executed.txt').Replace('\', '/')
        $sentinelContent = @"
#!/usr/bin/env sh
printf executed > '$sentinelTarget'
exit 0
"@
        [IO.File]::WriteAllText($verified, $verifiedContent.Replace("`r`n", "`n"))
        [IO.File]::WriteAllText($sentinel, $sentinelContent.Replace("`r`n", "`n"))
        & chmod 0755 $verified $sentinel
        if ($LASTEXITCODE -ne 0) { throw 'Unable to mark gitleaks fixtures executable' }
    }

    $sentinelHashBefore = (Get-FileHash -Algorithm SHA256 -LiteralPath $sentinel).Hash
    $previousPath = $env:PATH
    try {
        $env:PATH = $sentinelDir + [IO.Path]::PathSeparator + $previousPath
        Assert-Condition ((Invoke-ScannerCase $verified 'success' $source $config $report) -eq 0) 'Verified isolated scanner did not pass'
        Assert-Condition ((Invoke-ScannerCase $verified 'finding' $source $config $report) -eq 2) 'Scanner finding did not fail closed'
        Assert-Condition ((Invoke-ScannerCase $verified 'error' $source $config $report) -eq 3) 'Scanner runtime error did not fail closed'

        $missing = Join-Path $verifiedDir 'missing-gitleaks'
        Assert-Condition ((Invoke-ScannerCase $missing 'missing' $source $config $report) -ne 0) 'Missing verified binary unexpectedly passed'
    } finally {
        $env:PATH = $previousPath
    }

    Assert-Condition (-not (Test-Path -LiteralPath (Join-Path $runRoot 'sentinel-executed.txt'))) 'System PATH sentinel was executed'
    Assert-Condition ((Get-FileHash -Algorithm SHA256 -LiteralPath $sentinel).Hash -ceq $sentinelHashBefore) 'System PATH sentinel was modified'
    Write-Output 'GITLEAKS_EXECUTION_TEST isolated=PASS sentinel=NOT_EXECUTED missing=REJECTED finding=REJECTED scanner-error=REJECTED'
} finally {
    if (Test-Path -LiteralPath $runRoot) {
        $resolvedArtifacts = (Resolve-Path -LiteralPath $artifactRoot).Path
        $resolvedRun = (Resolve-Path -LiteralPath $runRoot).Path
        if (-not $resolvedRun.StartsWith($resolvedArtifacts + [IO.Path]::DirectorySeparatorChar)) {
            throw 'Refusing to remove gitleaks test artifacts outside repository artifacts root'
        }
        Remove-Item -LiteralPath $resolvedRun -Recurse -Force
    }
}
