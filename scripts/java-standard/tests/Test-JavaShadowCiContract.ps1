[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$artifactRoot = Join-Path $repoRoot 'artifacts'
New-Item -ItemType Directory -Path $artifactRoot -Force | Out-Null
$runRoot = Join-Path $artifactRoot ('java-shadow-ci-contract-test-' + [Guid]::NewGuid().ToString('N'))
$verifier = Join-Path $repoRoot 'scripts\java-standard\verify-java-engineering-standard.ps1'
$workflow = Join-Path $repoRoot '.github\workflows\ci.yml'
$pwsh = (Get-Process -Id $PID).Path

function Assert-Condition([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Invoke-Contract([string]$Name, [string]$Content) {
    $fixture = Join-Path $runRoot "$Name.yml"
    $stdout = Join-Path $runRoot "$Name.stdout.txt"
    $stderr = Join-Path $runRoot "$Name.stderr.txt"
    [IO.File]::WriteAllText($fixture, $Content, [Text.UTF8Encoding]::new($false))
    $process = Start-Process -FilePath $pwsh -ArgumentList @(
        '-NoProfile', '-File', $verifier,
        '-ValidateCiShadowContractOnly',
        '-WorkflowPath', $fixture
    ) -Wait -PassThru -NoNewWindow -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    return $process.ExitCode
}

try {
    New-Item -ItemType Directory -Path $runRoot -Force | Out-Null
    $canonical = Get-Content -LiteralPath $workflow -Raw -Encoding UTF8
    Assert-Condition ((Invoke-Contract 'canonical' $canonical) -eq 0) 'Canonical Java Shadow CI contract failed'

    $displayName = $canonical.Replace(
        '    name: Java architecture guard',
        '    name: Arbitrary Java Guard Label'
    )
    Assert-Condition ((Invoke-Contract 'display-name-independent' $displayName) -eq 0) 'Display-name-only mutation changed capability identity'

    $missingJob = [regex]::Replace(
        $canonical,
        '(?ms)^  java-engineering-shadow:\s*\r?\n.*?(?=^  [A-Za-z0-9_-]+:\s*$|\z)',
        ''
    )
    Assert-Condition ((Invoke-Contract 'missing-job' $missingJob) -ne 0) 'Missing Java Shadow job unexpectedly passed'

    $missingScanner = $canonical.Replace(
        './scripts/java-standard/invoke-java-shadow-scan.ps1',
        './scripts/java-standard/missing-shadow-scan.ps1'
    )
    Assert-Condition ((Invoke-Contract 'missing-scanner' $missingScanner) -ne 0) 'Missing Java Shadow scanner unexpectedly passed'

    $embeddedScanner = $canonical.Replace(
        '        run: ./scripts/java-standard/invoke-java-shadow-scan.ps1 -OutputPath artifacts/java-shadow/shadow-report.json',
        "        run: |`n          <#`n          run: ./scripts/java-standard/invoke-java-shadow-scan.ps1 -OutputPath artifacts/java-shadow/shadow-report.json`n          #>"
    )
    Assert-Condition ((Invoke-Contract 'embedded-scanner' $embeddedScanner) -ne 0) 'Scanner text embedded in a block scalar unexpectedly passed'

    $maskedScanner = $canonical.Replace(
        '        run: ./scripts/java-standard/invoke-java-shadow-scan.ps1 -OutputPath artifacts/java-shadow/shadow-report.json',
        '        run: ./scripts/java-standard/invoke-java-shadow-scan.ps1 -OutputPath artifacts/java-shadow/shadow-report.json; exit 0'
    )
    Assert-Condition ((Invoke-Contract 'masked-scanner' $maskedScanner) -ne 0) 'Scanner invocation with a masking shell tail unexpectedly passed'

    $softFail = $canonical.Replace(
        '      - name: Run Java engineering Shadow checker',
        "      - name: Run Java engineering Shadow checker`n        continue-on-error: true"
    )
    Assert-Condition ((Invoke-Contract 'scanner-soft-fail' $softFail) -ne 0) 'Soft-failing Java Shadow scanner unexpectedly passed'

    $conditional = $canonical.Replace(
        '      - name: Run Java engineering Shadow checker',
        "      - name: Run Java engineering Shadow checker`n        if: " + '${{ false }}'
    )
    Assert-Condition ((Invoke-Contract 'scanner-conditional' $conditional) -ne 0) 'Conditional Java Shadow scanner unexpectedly passed'

    Write-Output 'JAVA_SHADOW_CI_CONTRACT_TEST canonical=PASS display-name=PASS missing-job=REJECTED missing-scanner=REJECTED embedded-scanner=REJECTED masked-scanner=REJECTED soft-fail=REJECTED conditional=REJECTED'
} finally {
    if (Test-Path -LiteralPath $runRoot) {
        $resolvedArtifacts = (Resolve-Path -LiteralPath $artifactRoot).Path
        $resolvedRun = (Resolve-Path -LiteralPath $runRoot).Path
        if (-not $resolvedRun.StartsWith($resolvedArtifacts + [IO.Path]::DirectorySeparatorChar)) {
            throw 'Refusing to remove Java Shadow CI contract test artifacts outside repository artifacts root'
        }
        Remove-Item -LiteralPath $resolvedRun -Recurse -Force
    }
}
