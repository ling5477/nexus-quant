[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$artifactRoot = Join-Path $repo 'artifacts'
New-Item -ItemType Directory -Path $artifactRoot -Force | Out-Null
$runRoot = Join-Path $artifactRoot ('phase5a-supply-chain-test-' + [Guid]::NewGuid().ToString('N'))
$workflowValidator = Join-Path $repo 'scripts\ci\Test-CanonicalDeliveryWorkflow.ps1'
$archiveValidator = Join-Path $repo 'scripts\ci\Test-DeliveryToolArchive.ps1'
$realWorkflow = Join-Path $repo '.github\workflows\ci.yml'
$realLock = Join-Path $repo 'scripts\ci\delivery-supply-chain-lock.json'
$realRestoreDrill = Join-Path $repo 'scripts\deployment\Invoke-NqCanonicalRestoreDrill.ps1'
$script:negativeCount = 0

function Assert-Condition([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw $Message }
}

function Write-Json([string] $Path, [object] $Value) {
    $json = ($Value | ConvertTo-Json -Depth 40).Replace("`r`n", "`n").TrimEnd() + "`n"
    [IO.File]::WriteAllText($Path, $json, (New-Object Text.UTF8Encoding($false)))
}

function New-Case([string] $Name) {
    $root = Join-Path $runRoot $Name
    New-Item -ItemType Directory -Path $root -Force | Out-Null
    $workflow = Join-Path $root 'ci.yml'
    $lock = Join-Path $root 'delivery-supply-chain-lock.json'
    Copy-Item -LiteralPath $realWorkflow -Destination $workflow
    Copy-Item -LiteralPath $realLock -Destination $lock
    return [pscustomobject]@{ Root = $root; Workflow = $workflow; Lock = $lock }
}

function Assert-WorkflowRejected([string] $Name, [scriptblock] $Mutate) {
    $case = New-Case $Name
    & $Mutate $case
    $rejected = $false
    try { & $workflowValidator -WorkflowPath $case.Workflow -SupplyChainLockPath $case.Lock | Out-Null }
    catch { $rejected = $true }
    Assert-Condition $rejected "Negative supply-chain case unexpectedly passed: $Name"
    $script:negativeCount += 1
    Write-Output "NEGATIVE_REJECTED=$Name"
}

function Remove-CriticalE2eStepBlocks([string] $Content) {
    $names = @(
        'Run loopback critical E2E allowlist',
        'Run real-backend critical E2E allowlist'
    )
    $matches = @()
    foreach ($name in $names) {
        $pattern = '(?ms)^      - name:\s*' + [regex]::Escape($name) + '\s*\r?\n.*?(?=^      - name:|^  [A-Za-z0-9_-]+:\s*$|\z)'
        $match = [regex]::Match($Content, $pattern)
        Assert-Condition $match.Success "Unable to locate critical E2E step fixture: $name"
        $matches += $match
    }
    $remaining = $Content
    foreach ($match in @($matches | Sort-Object Index -Descending)) {
        $remaining = $remaining.Remove($match.Index, $match.Length)
    }
    $specs = @(
        [regex]::Matches(($matches.Value -join "`n"), '[A-Za-z0-9-]+\.spec\.ts') |
            ForEach-Object Value |
            Sort-Object -Unique
    )
    return [pscustomobject]@{
        Content = $remaining
        Blocks = @($matches | Sort-Object Index | ForEach-Object Value)
        Specs = $specs
    }
}

function Move-CriticalE2eStepsToOptionalJob([object] $Case, [bool] $Conditional) {
    $removed = Remove-CriticalE2eStepBlocks (Get-Content -Raw -LiteralPath $Case.Workflow)
    $conditionLine = if ($Conditional) { '    if: ${{ false }}' + "`n" } else { '' }
    $job = "  optional-critical-e2e:`n    name: Optional critical E2E fixture`n    runs-on: ubuntu-latest`n${conditionLine}    steps:`n" +
        ($removed.Blocks -join "`n") + "`n"
    $content = $removed.Content.Replace('  delivery-provenance:', $job + '  delivery-provenance:')
    [IO.File]::WriteAllText($Case.Workflow, $content)
}

try {
    New-Item -ItemType Directory -Path $runRoot -Force | Out-Null
    $baselineOutput = @(& $workflowValidator -WorkflowPath $realWorkflow -SupplyChainLockPath $realLock)
    $requiredJobLine = @($baselineOutput | Where-Object { $_ -like 'REQUIRED_JOB_IDS=*' })
    Assert-Condition ($requiredJobLine.Count -eq 1) 'Canonical validator did not expose one required-job identity set'
    $requiredJobIds = @(($requiredJobLine[0] -replace '^REQUIRED_JOB_IDS=', '').Split(',') | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    Assert-Condition ($requiredJobIds.Count -eq 9) "Expected nine canonical required jobs; found=$($requiredJobIds.Count)"

    Assert-WorkflowRejected 'action-sha-mismatch' {
        param($case)
        $lock = Get-Content -Raw -LiteralPath $case.Lock | ConvertFrom-Json
        $content = Get-Content -Raw -LiteralPath $case.Workflow
        $content = $content.Replace([string]$lock.actions[0].commit, ('0' * 40))
        [IO.File]::WriteAllText($case.Workflow, $content)
    }
    Assert-WorkflowRejected 'action-missing-from-lock' {
        param($case)
        $lock = Get-Content -Raw -LiteralPath $case.Lock | ConvertFrom-Json
        $lock.actions = @($lock.actions | Select-Object -Skip 1)
        Write-Json $case.Lock $lock
    }
    Assert-WorkflowRejected 'extra-active-action' {
        param($case)
        $lock = Get-Content -Raw -LiteralPath $case.Lock | ConvertFrom-Json
        $lock.actions += [pscustomobject][ordered]@{
            repository = 'actions/cache'
            tag = 'v4.0.2'
            commit = ('1' * 40)
            usage = 'ACTIVE_CI'
            expectedOccurrences = 1
            source = 'https://api.github.com/repos/actions/cache/git/refs/tags/v4.0.2'
        }
        Write-Json $case.Lock $lock
    }
    Assert-WorkflowRejected 'gitleaks-version-mismatch' {
        param($case)
        $lock = Get-Content -Raw -LiteralPath $case.Lock | ConvertFrom-Json
        ($lock.tools | Where-Object name -eq 'gitleaks').version = '8.18.5'
        Write-Json $case.Lock $lock
    }
    Assert-WorkflowRejected 'postgres-tag-mismatch' {
        param($case)
        $lock = Get-Content -Raw -LiteralPath $case.Lock | ConvertFrom-Json
        $image = $lock.images | Where-Object usage -eq 'ACTIVE_CI_PINNED'
        $old = "$($image.name):$($image.tag)@$($image.digest)"
        $replacement = "$($image.name):mismatch@$($image.digest)"
        $content = Get-Content -Raw -LiteralPath $case.Workflow
        $content = [regex]::Replace($content, [regex]::Escape($old), $replacement, 1)
        [IO.File]::WriteAllText($case.Workflow, $content)
    }
    Assert-WorkflowRejected 'postgres-digest-mismatch' {
        param($case)
        $lock = Get-Content -Raw -LiteralPath $case.Lock | ConvertFrom-Json
        $image = $lock.images | Where-Object usage -eq 'ACTIVE_CI_PINNED'
        $old = "$($image.name):$($image.tag)@$($image.digest)"
        $replacement = "$($image.name):$($image.tag)@sha256:$('0' * 64)"
        $content = Get-Content -Raw -LiteralPath $case.Workflow
        $content = [regex]::Replace($content, [regex]::Escape($old), $replacement, 1)
        [IO.File]::WriteAllText($case.Workflow, $content)
    }
    Assert-WorkflowRejected 'missing-required-lock-entry' {
        param($case)
        $lock = Get-Content -Raw -LiteralPath $case.Lock | ConvertFrom-Json
        $lock.tools = @($lock.tools | Where-Object name -ne 'gitleaks')
        Write-Json $case.Lock $lock
    }
    Assert-WorkflowRejected 'duplicate-identity' {
        param($case)
        $lock = Get-Content -Raw -LiteralPath $case.Lock | ConvertFrom-Json
        $lock.actions += $lock.actions[0]
        Write-Json $case.Lock $lock
    }
    Assert-WorkflowRejected 'unsupported-schema' {
        param($case)
        $lock = Get-Content -Raw -LiteralPath $case.Lock | ConvertFrom-Json
        $lock.schemaVersion = 'nq-delivery-supply-chain-lock-v999'
        Write-Json $case.Lock $lock
    }
    Assert-WorkflowRejected 'lock-validator-if-false' {
        param($case)
        $content = Get-Content -Raw -LiteralPath $case.Workflow
        $content = $content.Replace(
            '      - name: Validate canonical delivery workflow contract',
            "      - name: Validate canonical delivery workflow contract`n        if: " + '${{ false }}'
        )
        [IO.File]::WriteAllText($case.Workflow, $content)
    }
    Assert-WorkflowRejected 'lock-validator-continue-on-error' {
        param($case)
        $content = Get-Content -Raw -LiteralPath $case.Workflow
        $content = $content.Replace(
            '      - name: Validate canonical delivery workflow contract',
            "      - name: Validate canonical delivery workflow contract`n        continue-on-error: true"
        )
        [IO.File]::WriteAllText($case.Workflow, $content)
    }
    foreach ($requiredJobId in $requiredJobIds) {
        $jobId = $requiredJobId
        $ifMutation = {
            param($case)
            $content = Get-Content -Raw -LiteralPath $case.Workflow
            $content = $content.Replace(
                "  ${jobId}:",
                "  ${jobId}:`n    if: " + '${{ false }}'
            )
            [IO.File]::WriteAllText($case.Workflow, $content)
        }.GetNewClosure()
        Assert-WorkflowRejected "required-job-if-false-$jobId" $ifMutation

        $softFailMutation = {
            param($case)
            $content = Get-Content -Raw -LiteralPath $case.Workflow
            $content = $content.Replace(
                "  ${jobId}:",
                "  ${jobId}:`n    continue-on-error: true"
            )
            [IO.File]::WriteAllText($case.Workflow, $content)
        }.GetNewClosure()
        Assert-WorkflowRejected "required-job-soft-fail-$jobId" $softFailMutation
    }
    Assert-WorkflowRejected 'critical-e2e-conditional-relocation' {
        param($case)
        Move-CriticalE2eStepsToOptionalJob $case $true
    }
    Assert-WorkflowRejected 'critical-e2e-non-required-relocation' {
        param($case)
        Move-CriticalE2eStepsToOptionalJob $case $false
    }
    Assert-WorkflowRejected 'critical-e2e-execution-removed-text-retained' {
        param($case)
        $removed = Remove-CriticalE2eStepBlocks (Get-Content -Raw -LiteralPath $case.Workflow)
        $comments = ($removed.Specs | ForEach-Object { "# retained-non-executable-spec=$_" }) -join "`n"
        [IO.File]::WriteAllText($case.Workflow, $comments + "`n" + $removed.Content)
    }
    Assert-WorkflowRejected 'npm-lock-bypass' {
        param($case)
        $content = (Get-Content -Raw -LiteralPath $case.Workflow).Replace('run: npm ci', 'run: npm install')
        [IO.File]::WriteAllText($case.Workflow, $content)
    }
    Assert-WorkflowRejected 'playwright-latest' {
        param($case)
        $content = (Get-Content -Raw -LiteralPath $case.Workflow).Replace(
            'npx --no-install playwright install --with-deps chromium',
            'npx playwright@latest install --with-deps chromium'
        )
        [IO.File]::WriteAllText($case.Workflow, $content)
    }
    Assert-WorkflowRejected 'maven-plugin-unpinned' {
        param($case)
        $content = (Get-Content -Raw -LiteralPath $case.Workflow).Replace(
            'org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom',
            'org.cyclonedx:cyclonedx-maven-plugin:LATEST:makeAggregateBom'
        )
        [IO.File]::WriteAllText($case.Workflow, $content)
    }
    Assert-WorkflowRejected 'gitleaks-scan-removed' {
        param($case)
        $content = (Get-Content -Raw -LiteralPath $case.Workflow).Replace(
            './scripts/ci/Invoke-VerifiedGitleaks.ps1',
            './scripts/ci/Missing-GitleaksConsumer.ps1'
        )
        [IO.File]::WriteAllText($case.Workflow, $content)
    }
    Assert-WorkflowRejected 'provenance-continue-on-error' {
        param($case)
        $content = Get-Content -Raw -LiteralPath $case.Workflow
        $content = $content.Replace(
            '      - name: Validate internal provenance admission',
            "      - name: Validate internal provenance admission`n        continue-on-error: true"
        )
        [IO.File]::WriteAllText($case.Workflow, $content)
    }
    Assert-WorkflowRejected 'provenance-if-false' {
        param($case)
        $content = Get-Content -Raw -LiteralPath $case.Workflow
        $content = $content.Replace(
            '      - name: Validate internal provenance admission',
            "      - name: Validate internal provenance admission`n        if: " + '${{ false }}'
        )
        [IO.File]::WriteAllText($case.Workflow, $content)
    }
    Assert-WorkflowRejected 'provenance-validator-removed' {
        param($case)
        $content = (Get-Content -Raw -LiteralPath $case.Workflow).Replace(
            './scripts/ci/Test-InternalProvenance.ps1',
            './scripts/ci/Missing-ProvenanceValidator.ps1'
        )
        [IO.File]::WriteAllText($case.Workflow, $content)
    }
    Assert-WorkflowRejected 'provenance-validator-after-upload' {
        param($case)
        $content = Get-Content -Raw -LiteralPath $case.Workflow
        $content = $content.Replace('      - name: Validate internal provenance admission', '      - name: __NQ_ADMISSION_PLACEHOLDER__')
        $content = $content.Replace('      - name: Upload backend delivery evidence', '      - name: Validate internal provenance admission')
        $content = $content.Replace('      - name: __NQ_ADMISSION_PLACEHOLDER__', '      - name: Upload backend delivery evidence')
        [IO.File]::WriteAllText($case.Workflow, $content)
    }
    $criticalStepNames=@(
        'Build canonical release and external admission',
        'Verify canonical release and external admission',
        'Install and activate admitted canonical release',
        'Run current-schema backup and restore drill',
        'Verify canonical backup creation and integrity',
        'Verify canonical post-restore validation'
    )
    foreach($criticalName in $criticalStepNames){
        $caseName=($criticalName.ToLowerInvariant()-replace'[^a-z0-9]+','-').Trim('-')
        $name=$criticalName
        Assert-WorkflowRejected "$caseName-removed" {
            param($case);$content=Get-Content $case.Workflow -Raw;$pattern='(?ms)^      - name:\s*'+[regex]::Escape($name)+'\s*\r?\n.*?(?=^      - name:|^  [A-Za-z0-9_-]+:\s*$|\z)';[IO.File]::WriteAllText($case.Workflow,[regex]::Replace($content,$pattern,''))
        }.GetNewClosure()
        Assert-WorkflowRejected "$caseName-conditional" {
            param($case);$content=(Get-Content $case.Workflow -Raw).Replace("      - name: $name","      - name: $name`n        if: "+'${{ false }}');[IO.File]::WriteAllText($case.Workflow,$content)
        }.GetNewClosure()
        Assert-WorkflowRejected "$caseName-soft-fail" {
            param($case);$content=(Get-Content $case.Workflow -Raw).Replace("      - name: $name","      - name: $name`n        continue-on-error: true");[IO.File]::WriteAllText($case.Workflow,$content)
        }.GetNewClosure()
        Assert-WorkflowRejected "$caseName-failure-ignored" {
            param($case);$content=Get-Content $case.Workflow -Raw;$pattern='(?m)^(\s*run:\s*[^\r\n]+)$';$stepPattern='(?ms)(^      - name:\s*'+[regex]::Escape($name)+'\s*\r?\n.*?)(?=^      - name:|^  [A-Za-z0-9_-]+:\s*$|\z)';$match=[regex]::Match($content,$stepPattern);if(-not$match.Success){throw "Missing critical fixture: $name"};$mutated=[regex]::Replace($match.Value,$pattern,'$1 || true',1);[IO.File]::WriteAllText($case.Workflow,$content.Remove($match.Index,$match.Length).Insert($match.Index,$mutated))
        }.GetNewClosure()
    }

    $archiveCase = New-Case 'gitleaks-archive-checksum'
    $lock = Get-Content -Raw -LiteralPath $archiveCase.Lock | ConvertFrom-Json
    $tool = $lock.tools | Where-Object name -eq 'gitleaks'
    $archive = Join-Path $archiveCase.Root ([string]$tool.artifact)
    [IO.File]::WriteAllText($archive, 'verified archive fixture', [Text.Encoding]::UTF8)
    $tool.sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $archive).Hash.ToLowerInvariant()
    Write-Json $archiveCase.Lock $lock
    & $archiveValidator -SupplyChainLockPath $archiveCase.Lock -ToolName gitleaks -Platform linux -Architecture x64 -ArchivePath $archive | Out-Null

    $checksumMismatchLock = Join-Path $archiveCase.Root 'checksum-mismatch-lock.json'
    $lock.tools[0].sha256 = ('0' * 64)
    Write-Json $checksumMismatchLock $lock
    $checksumRejected = $false
    try {
        & $archiveValidator -SupplyChainLockPath $checksumMismatchLock -ToolName gitleaks -Platform linux -Architecture x64 -ArchivePath $archive | Out-Null
    } catch { $checksumRejected = $true }
    Assert-Condition $checksumRejected 'Gitleaks checksum mismatch unexpectedly passed'

    Add-Content -LiteralPath $archive -Value 'tamper'
    $tamperRejected = $false
    try {
        & $archiveValidator -SupplyChainLockPath $archiveCase.Lock -ToolName gitleaks -Platform linux -Architecture x64 -ArchivePath $archive | Out-Null
    } catch { $tamperRejected = $true }
    Assert-Condition $tamperRejected 'Tampered gitleaks archive unexpectedly passed'

    Write-Output "MUTATIONS_REJECTED=$script:negativeCount"
    Write-Output 'SUPPLY_CHAIN_TEST real=PASS actions-negative=PASS tools-negative=PASS images-negative=PASS consumer-negative=PASS provenance-admission-negative=PASS schema-negative=PASS tamper-negative=PASS'
} finally {
    if (Test-Path -LiteralPath $runRoot) {
        $resolvedArtifacts = (Resolve-Path -LiteralPath $artifactRoot).Path
        $resolvedRun = (Resolve-Path -LiteralPath $runRoot).Path
        if (-not $resolvedRun.StartsWith($resolvedArtifacts + [IO.Path]::DirectorySeparatorChar)) {
            throw 'Refusing to remove supply-chain test artifacts outside repository artifacts root'
        }
        Remove-Item -LiteralPath $resolvedRun -Recurse -Force
    }
}
