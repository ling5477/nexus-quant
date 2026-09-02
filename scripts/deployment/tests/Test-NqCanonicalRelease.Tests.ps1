[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$deploymentRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$repo = [IO.Path]::GetFullPath((Join-Path $deploymentRoot '..\..'))
$builder = Join-Path $deploymentRoot 'New-NqCanonicalRelease.ps1'
$verifier = Join-Path $deploymentRoot 'Test-NqCanonicalRelease.ps1'
$installer = Join-Path $deploymentRoot 'Install-NqCanonicalRelease.ps1'
$concurrencyWorker = Join-Path $PSScriptRoot 'Invoke-NqActivationConcurrencyWorker.ps1'
$admissionProducer = Join-Path $deploymentRoot 'New-NqCanonicalReleaseAdmission.ps1'
$admissionVerifier = Join-Path $deploymentRoot 'Test-NqCanonicalReleaseAdmission.ps1'
$head = (& git -C $repo rev-parse HEAD).Trim().ToLowerInvariant()
$tempRoot = Join-Path ([IO.Path]::GetTempPath()) ('nq-canonical-release-tests-' + [Guid]::NewGuid().ToString('N'))
$utf8 = [Text.UTF8Encoding]::new($false)
$cases = [Collections.Generic.List[string]]::new()
Import-Module (Join-Path $deploymentRoot 'nq-canonical-release.psm1') -Force -DisableNameChecking

function Complete-Case([string]$Name) {
    $cases.Add($Name)
    Write-Output "PASS / $Name"
}

function Expect-Rejected([scriptblock]$Action, [string]$Name) {
    try { & $Action; throw "NEGATIVE_CASE_ACCEPTED / $Name" } catch {
        if ($_.Exception.Message -like 'NEGATIVE_CASE_ACCEPTED*') { throw }
    }
    Complete-Case $Name
}

function New-FixtureJar([string]$Path, [string]$Payload) {
    Add-Type -AssemblyName System.IO.Compression
    [IO.Directory]::CreateDirectory((Split-Path -Parent $Path)) | Out-Null
    $stream = [IO.File]::Open($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::ReadWrite)
    try {
        $archive = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Create, $false)
        try {
            foreach ($entrySpec in @(
                @('META-INF/MANIFEST.MF', "Manifest-Version: 1.0`nMain-Class: org.springframework.boot.loader.launch.JarLauncher`nStart-Class: fixture.Main`n"),
                @('BOOT-INF/classes/fixture.txt', $Payload),
                @('BOOT-INF/lib/fixture.jar', 'nested-fixture')
            )) {
                $entry = $archive.CreateEntry($entrySpec[0])
                $writer = [IO.StreamWriter]::new($entry.Open(), $utf8)
                try { $writer.Write($entrySpec[1]) } finally { $writer.Dispose() }
            }
        } finally { $archive.Dispose() }
    } finally { $stream.Dispose() }
}

function New-Fixture([string]$Root, [string]$Payload) {
    [IO.Directory]::CreateDirectory($Root) | Out-Null
    $jar = Join-Path $Root 'nq-app.jar'
    $frontend = Join-Path $Root 'frontend'
    [IO.Directory]::CreateDirectory((Join-Path $frontend 'assets')) | Out-Null
    New-FixtureJar $jar $Payload
    [IO.File]::WriteAllText((Join-Path $frontend 'index.html'), '<!doctype html><title>NQ</title>', $utf8)
    [IO.File]::WriteAllText((Join-Path $frontend 'assets/app.js'), "console.log('$Payload');", $utf8)
    return [pscustomobject]@{ Jar = $jar; Frontend = $frontend }
}

function Write-DeliveryManifest([string]$Path,[string]$ArtifactSetName,[object[]]$Files) {
    $manifest=[pscustomobject][ordered]@{schemaVersion='nq-delivery-artifact-manifest-v1';artifactSetName=$ArtifactSetName;fileCount=$Files.Count;aggregateSha256=('0'*64);files=$Files}
    [IO.Directory]::CreateDirectory((Split-Path -Parent $Path))|Out-Null
    [IO.File]::WriteAllText($Path,($manifest|ConvertTo-Json -Depth 10),$utf8)
}

function New-SourcePolicyRepository([string]$Name) {
    $root=Join-Path $tempRoot $Name
    foreach($dir in @('scripts/deployment','deploy/canonical','backend/nq-infra/src/main/resources/db/migration','build/frontend/assets')){[IO.Directory]::CreateDirectory((Join-Path $root $dir))|Out-Null}
    foreach($file in @('New-NqCanonicalRelease.ps1','nq-canonical-release.psm1','Test-NqCanonicalRelease.ps1','Install-NqCanonicalRelease.ps1','New-NqCanonicalReleaseAdmission.ps1','Test-NqCanonicalReleaseAdmission.ps1')){Copy-Item -LiteralPath (Join-Path $deploymentRoot $file) -Destination (Join-Path $root "scripts/deployment/$file")}
    Copy-Item -LiteralPath (Join-Path $repo 'deploy/canonical/deployment-contract.json') -Destination (Join-Path $root 'deploy/canonical/deployment-contract.json')
    Copy-Item -LiteralPath (Join-Path $repo 'deploy/canonical/nq-canonical.service') -Destination (Join-Path $root 'deploy/canonical/nq-canonical.service')
    foreach($migrationFile in Get-ChildItem -LiteralPath (Join-Path $repo 'backend/nq-infra/src/main/resources/db/migration') -File -Filter '*.sql'){
        Copy-Item -LiteralPath $migrationFile.FullName -Destination (Join-Path $root 'backend/nq-infra/src/main/resources/db/migration')
    }
    [IO.File]::WriteAllText((Join-Path $root '.gitignore'),"/build/`n",$utf8)
    & git -C $root init -q 2>$null|Out-Null; & git -C $root config user.email 'fixture@nq.invalid' 2>$null|Out-Null; & git -C $root config user.name 'NQ Fixture' 2>$null|Out-Null; & git -C $root add . 2>$null|Out-Null; & git -C $root commit -q -m fixture 2>$null|Out-Null
    if($LASTEXITCODE-ne0){throw 'SOURCE_POLICY_FIXTURE_GIT_FAILED'}
    $jar=Join-Path $root 'build/nq-app.jar';New-FixtureJar $jar $Name
    $frontend=Join-Path $root 'build/frontend';[IO.File]::WriteAllText((Join-Path $frontend 'index.html'),'<title>NQ</title>',$utf8);[IO.File]::WriteAllText((Join-Path $frontend 'assets/app.js'),'fixture',$utf8)
    $backendManifest=Join-Path $root 'build/backend-manifest.json'
    Write-DeliveryManifest $backendManifest 'backend-application' @([pscustomobject]@{relativePath='artifacts/nq-app.jar';size=(Get-Item $jar).Length;sha256=(Get-FileHash $jar -Algorithm SHA256).Hash.ToLowerInvariant()})
    $frontendFiles=@(Get-ChildItem $frontend -File -Recurse|ForEach-Object{[pscustomobject]@{relativePath=('artifacts/dist/'+$_.FullName.Substring($frontend.Length+1).Replace('\','/'));size=$_.Length;sha256=(Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}})
    $frontendManifest=Join-Path $root 'build/frontend-manifest.json';Write-DeliveryManifest $frontendManifest 'frontend-production-dist' $frontendFiles
    [pscustomobject]@{Root=$root;Builder=Join-Path $root 'scripts/deployment/New-NqCanonicalRelease.ps1';Head=(& git -C $root rev-parse HEAD).Trim();Tree=(& git -C $root rev-parse 'HEAD^{tree}').Trim();Jar=$jar;Frontend=$frontend;BackendManifest=$backendManifest;FrontendManifest=$frontendManifest}
}

function Invoke-PolicyBuild($Policy,[string]$OutputName,[string]$Commit) {
    & $Policy.Builder -ExpectedCommit $Commit -BackendArtifactPath $Policy.Jar -BackendArtifactManifestPath $Policy.BackendManifest `
        -FrontendArtifactRoot $Policy.Frontend -FrontendArtifactManifestPath $Policy.FrontendManifest -OutputRoot (Join-Path $Policy.Root "build/$OutputName")
}

function Build-Release([string]$Output, $Fixture) {
    return & $builder -ExpectedCommit $head -BackendArtifactPath $Fixture.Jar `
        -FrontendArtifactRoot $Fixture.Frontend -OutputRoot $Output -BuildMode TEST_ONLY
}

function Copy-Release([string]$Source, [string]$Name) {
    $destination = Join-Path $tempRoot $Name
    Copy-Item -LiteralPath $Source -Destination $destination -Recurse
    return $destination
}

function Get-TestCurrentReleaseId([string]$Root){
    if($IsLinux){$target=(@(& /usr/bin/readlink -f -- (Join-Path $Root 'current'))-join'').Trim();return Split-Path -Leaf $target}
    (Get-Content -LiteralPath (Join-Path $Root 'current.release') -Raw).Trim()
}
function Set-TestCurrentReleaseId([string]$Root,[string]$Id){
    if($IsLinux){$next=Join-Path $Root ('.test-current-'+[Guid]::NewGuid().ToString('N'));& /usr/bin/ln -s -- (Join-Path (Join-Path $Root 'releases') $Id) $next;& /usr/bin/mv -Tf -- $next (Join-Path $Root 'current');return}
    [IO.File]::WriteAllText((Join-Path $Root 'current.release'),$Id,$utf8)
}

function Start-ConcurrencyWorker([string]$Operation,[string]$Root,[string]$Target,[string]$DbState,[int]$Hold=100,[int]$Timeout=15){
    $psi=[Diagnostics.ProcessStartInfo]::new();$psi.FileName=(Get-Process -Id $PID).Path;$psi.UseShellExecute=$false;$psi.RedirectStandardOutput=$true;$psi.RedirectStandardError=$true;$psi.CreateNoWindow=$true
    foreach($argument in @('-NoProfile','-File',$concurrencyWorker,'-InstallerPath',$installer,'-Operation',$Operation,'-InstallationRoot',$Root,'-ExpectedSourceCommit',$head,'-LockTimeoutSeconds',[string]$Timeout,'-HoldMilliseconds',[string]$Hold)){[void]$psi.ArgumentList.Add($argument)}
    if(-not[string]::IsNullOrWhiteSpace($Target)){[void]$psi.ArgumentList.Add('-ReleaseId');[void]$psi.ArgumentList.Add($Target)}
    if(-not[string]::IsNullOrWhiteSpace($DbState)){[void]$psi.ArgumentList.Add('-DatabaseStatePath');[void]$psi.ArgumentList.Add($DbState)}
    [Diagnostics.Process]::Start($psi)
}
function Wait-ConcurrencyWorker($Process,[int]$TimeoutSeconds=60){
    if(-not$Process.WaitForExit($TimeoutSeconds*1000)){try{$Process.Kill($true)}catch{};throw 'CONCURRENCY_WORKER_TIMEOUT'}
    $stdout=$Process.StandardOutput.ReadToEnd().Trim();$stderr=$Process.StandardError.ReadToEnd().Trim();$line=@($stdout-split"`n"|Where-Object{-not[string]::IsNullOrWhiteSpace($_)})[-1]
    try{$result=$line|ConvertFrom-Json}catch{throw "CONCURRENCY_WORKER_OUTPUT_INVALID exit=$($Process.ExitCode) stderr=$stderr stdout=$stdout"}
    [pscustomobject]@{ExitCode=$Process.ExitCode;Payload=$result;StdErr=$stderr}
}
function Wait-LockHeld([string]$Path,[int]$TimeoutSeconds=5){
    $deadline=[DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while([DateTime]::UtcNow-lt$deadline){if(Test-Path $Path){try{$probe=[IO.File]::Open($Path,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::ReadWrite);$probe.Dispose()}catch [IO.IOException]{return}};Start-Sleep -Milliseconds 25}
    throw 'LOCK_HOLDER_DID_NOT_ACQUIRE'
}
function Get-AuthorityStateFingerprint([string]$Root){
    $records=@(@('activation-head.json','activation-journal.json')|ForEach-Object{$path=Join-Path $Root $_;"$_|$((Get-FileHash $path -Algorithm SHA256).Hash.ToLowerInvariant())"})
    ($records-join'|')+'|current='+(Get-TestCurrentReleaseId $Root)
}

function Rebind-JarArtifact([string]$ReleaseRoot) {
    $manifestPath=Join-Path $ReleaseRoot 'release-manifest.json'
    $manifest=Get-Content -LiteralPath $manifestPath -Raw|ConvertFrom-Json
    $jarPath=Join-Path $ReleaseRoot 'app/nq-app.jar'
    $jar=@($manifest.artifacts|Where-Object role -eq 'application-jar')[0]
    $jar.size=(Get-Item -LiteralPath $jarPath).Length
    $jar.sha256=(Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $manifest.releaseId=Get-NqExpectedReleaseId $manifest
    Write-NqCanonicalManifest $manifestPath $manifest
}

function Rebind-Artifact([string]$ReleaseRoot,[string]$RelativePath) {
    $manifestPath=Join-Path $ReleaseRoot 'release-manifest.json';$manifest=Get-Content $manifestPath -Raw|ConvertFrom-Json
    $artifact=@($manifest.artifacts|Where-Object relativePath -eq $RelativePath)[0];$path=Join-Path $ReleaseRoot $RelativePath
    $artifact.size=(Get-Item $path).Length;$artifact.sha256=(Get-FileHash $path -Algorithm SHA256).Hash.ToLowerInvariant();$manifest.releaseId=Get-NqExpectedReleaseId $manifest;Write-NqCanonicalManifest $manifestPath $manifest
}

try {
    [IO.Directory]::CreateDirectory($tempRoot) | Out-Null
    $cleanPolicy=@(New-SourcePolicyRepository 'policy-clean')[-1]
    $cleanRelease=Invoke-PolicyBuild $cleanPolicy 'release-clean' $cleanPolicy.Head
    if(-not[bool]$cleanRelease.deployable -or [string]$cleanRelease.sourceTreeIdentity -cne ('git-tree:'+$cleanPolicy.Tree)){throw 'CLEAN_DEPLOYABLE_SOURCE_IDENTITY_FAILED'}
    Complete-Case 'clean-committed-exact-source-deployable-pass'
    $savedCi=$env:CI;$savedActions=$env:GITHUB_ACTIONS;$savedSha=$env:GITHUB_SHA;$savedRun=$env:GITHUB_RUN_ID;$savedAttempt=$env:GITHUB_RUN_ATTEMPT
    try{
        $env:CI='true';$env:GITHUB_ACTIONS='true';$env:GITHUB_SHA=$cleanPolicy.Head;$env:GITHUB_RUN_ID='fixture-run';$env:GITHUB_RUN_ATTEMPT='1'
        $policyAdmissionPath=Join-Path $cleanPolicy.Root 'build/external-admission.json';$policyDigestPath=Join-Path $cleanPolicy.Root 'build/external-admission.sha256'
        $policyAdmission=& (Join-Path $cleanPolicy.Root 'scripts/deployment/New-NqCanonicalReleaseAdmission.ps1') -ReleaseRoot $cleanRelease.releaseRoot -OutputPath $policyAdmissionPath -DigestOutputPath $policyDigestPath -Mode EXACT_HEAD_CI
    }finally{$env:CI=$savedCi;$env:GITHUB_ACTIONS=$savedActions;$env:GITHUB_SHA=$savedSha;$env:GITHUB_RUN_ID=$savedRun;$env:GITHUB_RUN_ATTEMPT=$savedAttempt}
    $policyInstall=Join-Path $tempRoot 'policy-production-install';$trustedDir=Join-Path $policyInstall 'trusted-release-admission';[IO.Directory]::CreateDirectory($trustedDir)|Out-Null;Copy-Item $policyAdmissionPath (Join-Path $trustedDir "$($cleanRelease.releaseId).json");Copy-Item $policyDigestPath (Join-Path $trustedDir "$($cleanRelease.releaseId).sha256")
    $policyPreflight=& (Join-Path $cleanPolicy.Root 'scripts/deployment/Install-NqCanonicalRelease.ps1') -Action preflight -InstallationRoot $policyInstall -SourceRoot $cleanRelease.releaseRoot -ExpectedSourceCommit $cleanPolicy.Head -ConfirmDisposable -TestProductionPolicy
    if([string]$policyPreflight.decision-cne'PASS / NQ_CANONICAL_INSTALL_PREFLIGHT'){throw 'EXTERNAL_ADMISSION_PRODUCTION_POLICY_FAILED'}
    Complete-Case 'external-exact-head-admission-production-policy-pass'
    $untrustedInstall=Join-Path $tempRoot 'policy-untrusted-install'
    Expect-Rejected { & (Join-Path $cleanPolicy.Root 'scripts/deployment/Install-NqCanonicalRelease.ps1') -Action preflight -InstallationRoot $untrustedInstall -SourceRoot $cleanRelease.releaseRoot -ExpectedSourceCommit $cleanPolicy.Head -AdmissionRootPath $policyAdmissionPath -ExpectedAdmissionSha256 $policyAdmission.admissionSha256 -ConfirmDisposable -TestProductionPolicy } 'caller-provided-admission-root-without-trusted-placement-rejected'
    Expect-Rejected { Invoke-PolicyBuild $cleanPolicy 'release-spoof' ('f'*40) } 'spoofed-source-commit-rejected'

    $trackedPolicy=@(New-SourcePolicyRepository 'policy-tracked-dirty')[-1]
    [IO.File]::AppendAllText((Join-Path $trackedPolicy.Root 'deploy/canonical/nq-canonical.service'),'dirty',$utf8)
    Expect-Rejected { Invoke-PolicyBuild $trackedPolicy 'release-tracked-dirty' $trackedPolicy.Head } 'tracked-dirty-deployable-rejected'

    $stagedPolicy=@(New-SourcePolicyRepository 'policy-staged-dirty')[-1]
    [IO.File]::AppendAllText((Join-Path $stagedPolicy.Root 'deploy/canonical/nq-canonical.service'),'staged',$utf8)
    & git -C $stagedPolicy.Root add deploy/canonical/nq-canonical.service 2>$null|Out-Null
    Expect-Rejected { Invoke-PolicyBuild $stagedPolicy 'release-staged-dirty' $stagedPolicy.Head } 'staged-dirty-deployable-rejected'

    $untrackedPolicy=@(New-SourcePolicyRepository 'policy-untracked')[-1]
    [IO.File]::WriteAllText((Join-Path $untrackedPolicy.Root 'deploy/canonical/untracked.conf'),'untracked',$utf8)
    Expect-Rejected { Invoke-PolicyBuild $untrackedPolicy 'release-untracked' $untrackedPolicy.Head } 'untracked-release-input-deployable-rejected'

    $artifactPolicy=@(New-SourcePolicyRepository 'policy-artifact-closed-set')[-1]
    [IO.File]::WriteAllText((Join-Path $artifactPolicy.Frontend 'unexpected.js'),'unexpected',$utf8)
    Expect-Rejected { Invoke-PolicyBuild $artifactPolicy 'release-artifact-extra' $artifactPolicy.Head } 'unmanifested-generated-release-input-rejected'

    $fixtureA = New-Fixture (Join-Path $tempRoot 'fixture-a') 'stable'
    $releaseA = Join-Path $tempRoot 'release-a'
    $releaseB = Join-Path $tempRoot 'release-b'
    $resultA = Build-Release $releaseA $fixtureA
    $resultB = Build-Release $releaseB $fixtureA
    if ([string]$resultA.releaseId -cne [string]$resultB.releaseId -or
            [IO.File]::ReadAllText((Join-Path $releaseA 'release-manifest.json')) -cne
            [IO.File]::ReadAllText((Join-Path $releaseB 'release-manifest.json'))) {
        throw 'DETERMINISTIC_RELEASE_IDENTITY_FAILED'
    }
    Complete-Case 'same-source-same-artifact-deterministic-identity'

    $verified = & $verifier -ReleaseRoot $releaseA -ExpectedSourceCommit $head
    if ([string]$verified.decision -cne 'PASS / NQ_CANONICAL_RELEASE_VERIFIED') {
        throw 'CANONICAL_RELEASE_VERIFICATION_FAILED'
    }
    Complete-Case 'canonical-release-positive'
    if ([bool]$verified.deployable -or [string]$verified.sourceState -cne 'UNCOMMITTED_CANDIDATE' -or
            [string]$verified.releaseId -cnotmatch '^nq-test-') { throw 'TEST_ONLY_IDENTITY_INVALID' }
    Complete-Case 'test-only-release-identity-is-non-deployable'
    $admissionAPath=Join-Path $tempRoot 'admission-a.json'
    $admissionA=& $admissionProducer -ReleaseRoot $releaseA -OutputPath $admissionAPath -Mode TEST_ONLY
    $null=& $admissionVerifier -ReleaseRoot $releaseA -AdmissionRootPath $admissionAPath -ExpectedAdmissionSha256 $admissionA.admissionSha256 -RequiredMode TEST_ONLY
    Complete-Case 'external-test-only-admission-positive'

    foreach($admissionCase in @(
        @('backend','app/nq-app.jar'),
        @('frontend','frontend/index.html'),
        @('deployment','deploy/deployment-contract.json')
    )){
        $changed=Copy-Release $releaseA ("negative-admission-"+$admissionCase[0]);$relative=$admissionCase[1];$changedPath=Join-Path $changed $relative
        if($relative-ceq'app/nq-app.jar'){Remove-Item $changedPath -Force;New-FixtureJar $changedPath 'changed-valid-jar'}else{[IO.File]::AppendAllText($changedPath,'changed',$utf8)}
        Rebind-Artifact $changed $relative
        Expect-Rejected { & $admissionVerifier -ReleaseRoot $changed -AdmissionRootPath $admissionAPath -ExpectedAdmissionSha256 $admissionA.admissionSha256 -RequiredMode TEST_ONLY } ("external-admission-rejects-"+$admissionCase[0]+"-and-manifest-replacement")
    }

    $wholeChanged=Copy-Release $releaseA 'negative-admission-whole-set'
    foreach($relative in @('app/nq-app.jar','frontend/index.html','deploy/deployment-contract.json')){$wholePath=Join-Path $wholeChanged $relative;if($relative-ceq'app/nq-app.jar'){Remove-Item $wholePath -Force;New-FixtureJar $wholePath 'whole-valid-jar'}else{[IO.File]::AppendAllText($wholePath,'whole',$utf8)};Rebind-Artifact $wholeChanged $relative}
    Expect-Rejected { & $admissionVerifier -ReleaseRoot $wholeChanged -AdmissionRootPath $admissionAPath -ExpectedAdmissionSha256 $admissionA.admissionSha256 -RequiredMode TEST_ONLY } 'external-admission-rejects-whole-artifact-set-replacement'

    Expect-Rejected {
        & $builder -ExpectedCommit $head -BackendArtifactPath $fixtureA.Jar `
            -FrontendArtifactRoot $fixtureA.Frontend -OutputRoot (Join-Path $tempRoot 'deployable-dirty')
    } 'deployable-current-dirty-and-untracked-rejected'

    $fixtureC = New-Fixture (Join-Path $tempRoot 'fixture-c') 'changed'
    $releaseC = Join-Path $tempRoot 'release-c'
    $resultC = Build-Release $releaseC $fixtureC
    $admissionCPath=Join-Path $tempRoot 'admission-c.json';$admissionC=& $admissionProducer -ReleaseRoot $releaseC -OutputPath $admissionCPath -Mode TEST_ONLY
    if ([string]$resultC.releaseId -ceq [string]$resultA.releaseId) {
        throw 'DIFFERENT_ARTIFACT_HAS_SAME_RELEASE_ID'
    }
    Complete-Case 'different-artifact-different-identity'

    $missingManifest = Copy-Release $releaseA 'negative-missing-manifest'
    Remove-Item -LiteralPath (Join-Path $missingManifest 'release-manifest.json') -Force
    Expect-Rejected { & $verifier -ReleaseRoot $missingManifest } 'manifest-missing-rejected'

    $missingArtifact = Copy-Release $releaseA 'negative-missing-artifact'
    Remove-Item -LiteralPath (Join-Path $missingArtifact 'frontend/index.html') -Force
    Expect-Rejected { & $verifier -ReleaseRoot $missingArtifact } 'artifact-missing-rejected'

    $tampered = Copy-Release $releaseA 'negative-tampered'
    [IO.File]::AppendAllText((Join-Path $tampered 'frontend/index.html'), 'tamper', $utf8)
    Expect-Rejected { & $verifier -ReleaseRoot $tampered } 'hash-and-size-mismatch-rejected'

    $unexpected = Copy-Release $releaseA 'negative-unexpected'
    [IO.File]::WriteAllText((Join-Path $unexpected 'unexpected.txt'), 'unexpected', $utf8)
    Expect-Rejected { & $verifier -ReleaseRoot $unexpected } 'unexpected-file-rejected'

    $wrongCommit = ('f' * 40)
    if ($wrongCommit -ceq $head) { $wrongCommit = 'e' * 40 }
    Expect-Rejected { & $verifier -ReleaseRoot $releaseA -ExpectedSourceCommit $wrongCommit } 'wrong-source-commit-rejected'
    Expect-Rejected { & $verifier -ReleaseRoot $releaseA -ExpectedSchemaTarget 'V999999' } 'wrong-schema-identity-rejected'

    $escaping = Copy-Release $releaseA 'negative-escaping'
    $escapingManifestPath = Join-Path $escaping 'release-manifest.json'
    $escapingManifest = Get-Content -LiteralPath $escapingManifestPath -Raw | ConvertFrom-Json
    $escapingManifest.artifacts[0].relativePath = '../escape'
    [IO.File]::WriteAllText($escapingManifestPath, ($escapingManifest | ConvertTo-Json -Depth 20 -Compress), $utf8)
    Expect-Rejected { & $verifier -ReleaseRoot $escaping } 'escaping-path-rejected'

    $localNameMismatch=Copy-Release $releaseA 'negative-jar-local-name'
    $localNameJar=Join-Path $localNameMismatch 'app/nq-app.jar'
    $localBytes=[IO.File]::ReadAllBytes($localNameJar)
    if([BitConverter]::ToUInt32($localBytes,0)-ne0x04034b50){throw 'FIXTURE_LOCAL_HEADER_MISSING'}
    $localBytes[30]=$localBytes[30]-bxor 1
    [IO.File]::WriteAllBytes($localNameJar,$localBytes)
    Rebind-JarArtifact $localNameMismatch
    Expect-Rejected { & $verifier -ReleaseRoot $localNameMismatch } 'jar-local-central-name-mismatch-rejected'

    $localMethodMismatch=Copy-Release $releaseA 'negative-jar-local-method'
    $localMethodJar=Join-Path $localMethodMismatch 'app/nq-app.jar'
    $methodBytes=[IO.File]::ReadAllBytes($localMethodJar)
    $method=[BitConverter]::ToUInt16($methodBytes,8)
    $newMethod=if($method-eq0){8}else{0}
    $replacement=[BitConverter]::GetBytes([uint16]$newMethod)
    [Array]::Copy($replacement,0,$methodBytes,8,2)
    [IO.File]::WriteAllBytes($localMethodJar,$methodBytes)
    Rebind-JarArtifact $localMethodMismatch
    Expect-Rejected { & $verifier -ReleaseRoot $localMethodMismatch } 'jar-local-central-method-mismatch-rejected'

    $hardlink = Copy-Release $releaseA 'negative-hardlink'
    $hardlinkArtifact = Join-Path $hardlink 'frontend/index.html'
    $hardlinkOutside = Join-Path $tempRoot 'outside-hardlink.txt'
    [IO.File]::Copy($hardlinkArtifact, $hardlinkOutside)
    Remove-Item -LiteralPath $hardlinkArtifact -Force
    New-Item -ItemType HardLink -Path $hardlinkArtifact -Target $hardlinkOutside | Out-Null
    Expect-Rejected { & $verifier -ReleaseRoot $hardlink } 'hard-link-rejected'

    if ($IsLinux) {
        $wrongMode = Copy-Release $releaseA 'negative-mode'
        & /usr/bin/chmod 0777 '--' (Join-Path $wrongMode 'bin/Test-NqCanonicalRelease.ps1')
        Expect-Rejected { & $verifier -ReleaseRoot $wrongMode -RequirePosix } 'wrong-posix-mode-rejected'

        $symlink = Copy-Release $releaseA 'negative-symlink'
        $symlinkArtifact = Join-Path $symlink 'frontend/index.html'
        $symlinkOutside = Join-Path $tempRoot 'outside-symlink.txt'
        [IO.File]::WriteAllText($symlinkOutside, '<!doctype html><title>NQ</title>', $utf8)
        Remove-Item -LiteralPath $symlinkArtifact -Force
        & /usr/bin/ln '-s' '--' $symlinkOutside $symlinkArtifact
        Expect-Rejected { & $verifier -ReleaseRoot $symlink } 'symlink-rejected'
    } else {
        $junctionParent = Join-Path $tempRoot 'junction-parent'
        [IO.Directory]::CreateDirectory($junctionParent) | Out-Null
        $junction = Join-Path $tempRoot 'release-junction'
        New-Item -ItemType Junction -Path $junction -Target $releaseA | Out-Null
        Expect-Rejected { & $verifier -ReleaseRoot $junction } 'reparse-root-rejected'
    }

    $installRoot = Join-Path $tempRoot 'installation'
    Expect-Rejected {
        & $installer -Action preflight -InstallationRoot $installRoot -SourceRoot $releaseA `
            -ExpectedSourceCommit $head -ConfirmDisposable -TestProductionPolicy
    } 'production-policy-rejects-test-only-release'
    $preflight = & $installer -Action preflight -InstallationRoot $installRoot `
        -SourceRoot $releaseA -ExpectedSourceCommit $head -AdmissionRootPath $admissionAPath -ExpectedAdmissionSha256 $admissionA.admissionSha256 -ConfirmDisposable
    if ([string]$preflight.decision -cne 'PASS / NQ_CANONICAL_INSTALL_PREFLIGHT') {
        throw 'INSTALL_PREFLIGHT_FAILED'
    }
    Complete-Case 'installer-preflight'

    $installedA = & $installer -Action install -InstallationRoot $installRoot `
        -SourceRoot $releaseA -ExpectedSourceCommit $head -AdmissionRootPath $admissionAPath -ExpectedAdmissionSha256 $admissionA.admissionSha256 -ConfirmDisposable
    $databaseState = Join-Path $installRoot 'database-state.json'
    $null = & $installer -Action observe-database -InstallationRoot $installRoot `
        -DatabaseStatePath $databaseState -TestDatabaseSchemaVersion V46 -TestPostgresqlMajor 16 -ConfirmDisposable
    $activationA = & $installer -Action activate -InstallationRoot $installRoot `
        -ReleaseId $installedA.releaseId -DatabaseStatePath $databaseState `
        -ExpectedSourceCommit $head -AdmissionRootPath $admissionAPath -ExpectedAdmissionSha256 $admissionA.admissionSha256 -ConfirmDisposable
    if ([string]$activationA.previousReleaseId -cne 'NONE') { throw 'INITIAL_ACTIVATION_PREVIOUS_INVALID' }
    Complete-Case 'immutable-install-and-atomic-initial-activation'

    Expect-Rejected {
        & $installer -Action install -InstallationRoot $installRoot -SourceRoot $releaseA `
            -ExpectedSourceCommit $head -ConfirmDisposable
    } 'existing-release-mutation-rejected'

    $installedC = & $installer -Action install -InstallationRoot $installRoot `
        -SourceRoot $releaseC -ExpectedSourceCommit $head -AdmissionRootPath $admissionCPath -ExpectedAdmissionSha256 $admissionC.admissionSha256 -ConfirmDisposable
    $activationC = & $installer -Action activate -InstallationRoot $installRoot `
        -ReleaseId $installedC.releaseId -DatabaseStatePath $databaseState `
        -ExpectedSourceCommit $head -AdmissionRootPath $admissionCPath -ExpectedAdmissionSha256 $admissionC.admissionSha256 -ConfirmDisposable
    if ([string]$activationC.previousReleaseId -cne [string]$installedA.releaseId) {
        throw 'PREVIOUS_RELEASE_ID_NOT_RECORDED'
    }
    Complete-Case 'atomic-switch-records-previous-release'

    $rollback = & $installer -Action rollback -InstallationRoot $installRoot `
        -DatabaseStatePath $databaseState -ExpectedSourceCommit $head -ConfirmDisposable
    if ([string]$rollback.previousReleaseId -cne [string]$installedC.releaseId -or
            [string]$rollback.currentReleaseId -cne [string]$installedA.releaseId) {
        throw 'CODE_ROLLBACK_POINTER_INVALID'
    }
    Complete-Case 'verified-code-rollback'

    Expect-Rejected {
        & $installer -Action activate -InstallationRoot $installRoot -ReleaseId $installedC.releaseId `
            -DatabaseStatePath $databaseState -ExpectedSourceCommit $head -ConfirmDisposable `
            -TestFault AUTHORITY_PREWRITE
    } 'activation-authority-prewrite-failure-preserves-current'
    if ((Get-TestCurrentReleaseId $installRoot) -cne [string]$installedA.releaseId) {
        throw 'AUTHORITY_PREWRITE_FAILURE_MOVED_POINTER'
    }

    Expect-Rejected {
        & $installer -Action activate -InstallationRoot $installRoot -ReleaseId $installedC.releaseId `
            -DatabaseStatePath $databaseState -ExpectedSourceCommit $head -ConfirmDisposable `
            -TestFault POINTER_SWAP
    } 'pointer-swap-failure-is-aborted'
    if ((Get-TestCurrentReleaseId $installRoot) -cne [string]$installedA.releaseId) {
        throw 'POINTER_FAILURE_MOVED_POINTER'
    }

    Expect-Rejected {
        & $installer -Action activate -InstallationRoot $installRoot -ReleaseId $installedC.releaseId `
            -DatabaseStatePath $databaseState -ExpectedSourceCommit $head -ConfirmDisposable `
            -TestFault COMPLETION_WRITE
    } 'post-swap-completion-failure-leaves-recoverable-prepared-journal'
    $recovered = & $installer -Action recover -InstallationRoot $installRoot -ExpectedSourceCommit $head -ConfirmDisposable
    if ([string]$recovered.state -cne 'COMPLETED' -or
            (Get-TestCurrentReleaseId $installRoot) -cne [string]$installedC.releaseId) {
        throw 'PREPARED_ACTIVATION_RECOVERY_FAILED'
    }
    Complete-Case 'prepared-journal-reconciles-deterministically'

    Expect-Rejected {
        & $installer -Action rollback -InstallationRoot $installRoot -ReleaseId $installedA.releaseId `
            -DatabaseStatePath $databaseState -ExpectedSourceCommit $head -ConfirmDisposable
    } 'caller-controlled-rollback-target-rejected'

    $journalPath = Join-Path $installRoot 'activation-journal.json'
    $trustedJournal = [IO.File]::ReadAllText($journalPath, [Text.Encoding]::UTF8)
    $forged = $trustedJournal | ConvertFrom-Json
    $forged.previousReleaseId = $installedC.releaseId
    [IO.File]::WriteAllText($journalPath, ($forged | ConvertTo-Json -Depth 16 -Compress), $utf8)
    Expect-Rejected {
        & $installer -Action rollback -InstallationRoot $installRoot -DatabaseStatePath $databaseState `
            -ExpectedSourceCommit $head -ConfirmDisposable
    } 'forged-activation-journal-rejected'
    [IO.File]::WriteAllText($journalPath, $trustedJournal, $utf8)

    Set-TestCurrentReleaseId $installRoot ([string]$installedA.releaseId)
    Expect-Rejected {
        & $installer -Action rollback -InstallationRoot $installRoot -DatabaseStatePath $databaseState `
            -ExpectedSourceCommit $head -ConfirmDisposable
    } 'stale-journal-current-pointer-mismatch-rejected'
    Set-TestCurrentReleaseId $installRoot ([string]$installedC.releaseId)

    $databaseStateV47 = Join-Path $installRoot 'database-state-v47.json'
    $null = & $installer -Action observe-database -InstallationRoot $installRoot `
        -DatabaseStatePath $databaseStateV47 -TestDatabaseSchemaVersion V47 -TestPostgresqlMajor 16 -ConfirmDisposable
    Expect-Rejected {
        & $installer -Action rollback -InstallationRoot $installRoot -DatabaseStatePath $databaseStateV47 `
            -ExpectedSourceCommit $head -ConfirmDisposable
    } 'schema-incompatible-code-rollback-requires-database-recovery'
    Expect-Rejected {
        & $installer -Action rollback -InstallationRoot $installRoot -ExpectedSourceCommit $head -ConfirmDisposable
    } 'missing-database-state-evidence-fails-closed'

    $previousReleaseRoot = Join-Path (Join-Path $installRoot 'releases') ([string]$installedA.releaseId)
    $hiddenPreviousRoot = Join-Path $tempRoot 'hidden-previous-release'
    Move-Item -LiteralPath $previousReleaseRoot -Destination $hiddenPreviousRoot
    try {
        Expect-Rejected {
            & $installer -Action rollback -InstallationRoot $installRoot -DatabaseStatePath $databaseState `
                -ExpectedSourceCommit $head -ConfirmDisposable
        } 'missing-previous-release-rejected'
    } finally { Move-Item -LiteralPath $hiddenPreviousRoot -Destination $previousReleaseRoot }

    $rollbackAfterRecovery = & $installer -Action rollback -InstallationRoot $installRoot `
        -DatabaseStatePath $databaseState -ExpectedSourceCommit $head -ConfirmDisposable
    if ([string]$rollbackAfterRecovery.currentReleaseId -cne [string]$installedA.releaseId) {
        throw 'TRUSTED_LAST_ACTIVATION_ROLLBACK_FAILED'
    }
    Complete-Case 'trusted-last-activation-only-rollback'

    $activationReplay1=& $installer -Action activate -InstallationRoot $installRoot -ReleaseId $installedC.releaseId `
        -DatabaseStatePath $databaseState -ExpectedSourceCommit $head -AdmissionRootPath $admissionCPath -ExpectedAdmissionSha256 $admissionC.admissionSha256 -ConfirmDisposable
    $journalPath=Join-Path $installRoot 'activation-journal.json';$headPath=Join-Path $installRoot 'activation-head.json'
    $journalReplay1=[IO.File]::ReadAllText($journalPath);$headReplay1=[IO.File]::ReadAllText($headPath)
    $null=& $installer -Action rollback -InstallationRoot $installRoot -DatabaseStatePath $databaseState -ExpectedSourceCommit $head `
        -AdmissionRootPath $admissionAPath -ExpectedAdmissionSha256 $admissionA.admissionSha256 -ConfirmDisposable
    $null=& $installer -Action activate -InstallationRoot $installRoot -ReleaseId $installedC.releaseId -DatabaseStatePath $databaseState `
        -ExpectedSourceCommit $head -AdmissionRootPath $admissionCPath -ExpectedAdmissionSha256 $admissionC.admissionSha256 -ConfirmDisposable
    $latestJournal=[IO.File]::ReadAllText($journalPath);$latestHead=[IO.File]::ReadAllText($headPath)
    [IO.File]::WriteAllText($journalPath,$journalReplay1,$utf8)
    Expect-Rejected { & $installer -Action recover -InstallationRoot $installRoot -ExpectedSourceCommit $head -ConfirmDisposable } 'a-b-a-old-completed-journal-replay-rejected'
    [IO.File]::WriteAllText($journalPath,$latestJournal,$utf8)
    [IO.File]::WriteAllText($headPath,$headReplay1,$utf8)
    Expect-Rejected { & $installer -Action recover -InstallationRoot $installRoot -ExpectedSourceCommit $head -ConfirmDisposable } 'older-valid-activation-head-replay-rejected'
    [IO.File]::WriteAllText($headPath,$latestHead,$utf8)

    Expect-Rejected {
        & $installer -Action activate -InstallationRoot $installRoot -ReleaseId $installedA.releaseId -DatabaseStatePath $databaseState `
            -ExpectedSourceCommit $head -AdmissionRootPath $admissionAPath -ExpectedAdmissionSha256 $admissionA.admissionSha256 -ConfirmDisposable -TestFault COMPLETION_WRITE
    } 'prepared-journal-crash-window-created'
    $oldPrepared=[IO.File]::ReadAllText($journalPath)
    $null=& $installer -Action recover -InstallationRoot $installRoot -ExpectedSourceCommit $head -ConfirmDisposable
    $null=& $installer -Action activate -InstallationRoot $installRoot -ReleaseId $installedC.releaseId -DatabaseStatePath $databaseState `
        -ExpectedSourceCommit $head -AdmissionRootPath $admissionCPath -ExpectedAdmissionSha256 $admissionC.admissionSha256 -ConfirmDisposable
    $null=& $installer -Action rollback -InstallationRoot $installRoot -DatabaseStatePath $databaseState -ExpectedSourceCommit $head `
        -AdmissionRootPath $admissionAPath -ExpectedAdmissionSha256 $admissionA.admissionSha256 -ConfirmDisposable
    $freshJournal=[IO.File]::ReadAllText($journalPath);$freshHead=[IO.File]::ReadAllText($headPath)
    [IO.File]::WriteAllText($journalPath,$oldPrepared,$utf8)
    Expect-Rejected { & $installer -Action recover -InstallationRoot $installRoot -ExpectedSourceCommit $head -ConfirmDisposable } 'old-prepared-journal-replay-rejected'
    [IO.File]::WriteAllText($journalPath,$freshJournal,$utf8);[IO.File]::WriteAllText($headPath,$freshHead,$utf8)
    $oldRollbackJournal=$freshJournal
    $null=& $installer -Action activate -InstallationRoot $installRoot -ReleaseId $installedC.releaseId -DatabaseStatePath $databaseState `
        -ExpectedSourceCommit $head -AdmissionRootPath $admissionCPath -ExpectedAdmissionSha256 $admissionC.admissionSha256 -ConfirmDisposable
    $null=& $installer -Action rollback -InstallationRoot $installRoot -DatabaseStatePath $databaseState -ExpectedSourceCommit $head `
        -AdmissionRootPath $admissionAPath -ExpectedAdmissionSha256 $admissionA.admissionSha256 -ConfirmDisposable
    $latestRollbackJournal=[IO.File]::ReadAllText($journalPath);$latestRollbackHead=[IO.File]::ReadAllText($headPath)
    [IO.File]::WriteAllText($journalPath,$oldRollbackJournal,$utf8)
    Expect-Rejected { & $installer -Action recover -InstallationRoot $installRoot -ExpectedSourceCommit $head -ConfirmDisposable } 'old-rollback-journal-replay-rejected'
    [IO.File]::WriteAllText($journalPath,$latestRollbackJournal,$utf8);[IO.File]::WriteAllText($headPath,$latestRollbackHead,$utf8)
    Complete-Case 'activation-generation-and-predecessor-chain-replay-guard'

    $authorityBeforeKeyNegatives=Get-AuthorityStateFingerprint $installRoot
    $keyPath=Join-Path $installRoot '.activation-authority.key';$savedKey=[IO.File]::ReadAllText($keyPath)
    [IO.File]::WriteAllText($keyPath,[Convert]::ToBase64String([byte[]](1..32)),$utf8)
    Expect-Rejected { & $installer -Action recover -InstallationRoot $installRoot -ExpectedSourceCommit $head -ConfirmDisposable } 'wrong-existing-authority-key-rejected'
    [IO.File]::WriteAllText($keyPath,$savedKey,$utf8)
    $keyBackup=Join-Path $tempRoot 'authority-key-backup';Move-Item $keyPath $keyBackup
    try { Expect-Rejected { & $installer -Action recover -InstallationRoot $installRoot -ExpectedSourceCommit $head -ConfirmDisposable } 'missing-existing-authority-key-rejected' }
    finally { if(Test-Path $keyPath){Remove-Item $keyPath -Force};Move-Item $keyBackup $keyPath }
    $keyHardlink=Join-Path $tempRoot 'authority-key-hardlink-source';Move-Item $keyPath $keyHardlink;New-Item -ItemType HardLink -Path $keyPath -Target $keyHardlink|Out-Null
    try { Expect-Rejected { & $installer -Action recover -InstallationRoot $installRoot -ExpectedSourceCommit $head -ConfirmDisposable } 'existing-authority-key-hardlink-rejected' }
    finally { Remove-Item $keyPath -Force;Move-Item $keyHardlink $keyPath }
    if($IsLinux){
        & /usr/bin/chmod 0644 $keyPath;try{Expect-Rejected { & $installer -Action recover -InstallationRoot $installRoot -ExpectedSourceCommit $head -ConfirmDisposable } 'existing-authority-key-0644-rejected'}finally{& /usr/bin/chmod 0600 $keyPath}
        & /usr/bin/chmod 0666 $keyPath;try{Expect-Rejected { & $installer -Action recover -InstallationRoot $installRoot -ExpectedSourceCommit $head -ConfirmDisposable } 'existing-authority-key-0666-rejected'}finally{& /usr/bin/chmod 0600 $keyPath}
        $keyTarget=Join-Path $tempRoot 'authority-key-symlink-target';Move-Item $keyPath $keyTarget;& /usr/bin/ln -s $keyTarget $keyPath
        try{Expect-Rejected { & $installer -Action recover -InstallationRoot $installRoot -ExpectedSourceCommit $head -ConfirmDisposable } 'existing-authority-key-symlink-rejected'}finally{Remove-Item $keyPath -Force;Move-Item $keyTarget $keyPath}
        & /usr/bin/sudo -n true 2>$null
        if($LASTEXITCODE-eq0){
            $owner=(@(& /usr/bin/id -un)-join'').Trim();& /usr/bin/sudo -n /usr/bin/chown nobody $keyPath
            try{Expect-Rejected { & $installer -Action recover -InstallationRoot $installRoot -ExpectedSourceCommit $head -ConfirmDisposable } 'existing-authority-key-wrong-owner-rejected'}finally{& /usr/bin/sudo -n /usr/bin/chown $owner $keyPath;& /usr/bin/chmod 0600 $keyPath}
        }
    }
    if((Get-AuthorityStateFingerprint $installRoot)-cne$authorityBeforeKeyNegatives){throw 'KEY_VALIDATION_FAILURE_MUTATED_AUTHORITY'}
    Complete-Case 'existing-authority-key-identity-revalidated-on-read'

    $concurrencyRoot=Join-Path $tempRoot 'concurrency-installation'
    $concurrentA=& $installer -Action install -InstallationRoot $concurrencyRoot -SourceRoot $releaseA -ExpectedSourceCommit $head -ConfirmDisposable
    $concurrentC=& $installer -Action install -InstallationRoot $concurrencyRoot -SourceRoot $releaseC -ExpectedSourceCommit $head -ConfirmDisposable
    $concurrencyDbState=Join-Path $concurrencyRoot 'database-state.json';$null=& $installer -Action observe-database -InstallationRoot $concurrencyRoot -DatabaseStatePath $concurrencyDbState -TestDatabaseSchemaVersion V46 -TestPostgresqlMajor 16 -ConfirmDisposable
    $initialConcurrent=& $installer -Action activate -InstallationRoot $concurrencyRoot -ReleaseId $concurrentA.releaseId -DatabaseStatePath $concurrencyDbState -ExpectedSourceCommit $head -ConfirmDisposable
    $startGeneration=[long]$initialConcurrent.generation
    $workers=[Collections.Generic.List[object]]::new()
    for($index=0;$index-lt8;$index++){$target=if($index%2-eq0){[string]$concurrentC.releaseId}else{[string]$concurrentA.releaseId};$workers.Add((Start-ConcurrencyWorker 'ACTIVATE' $concurrencyRoot $target $concurrencyDbState 125 20))}
    $results=@($workers|ForEach-Object{Wait-ConcurrencyWorker $_ 60})
    if(@($results|Where-Object{$_.ExitCode-ne0-or-not[bool]$_.Payload.success}).Count-ne0){throw ('CONCURRENT_ACTIVATION_REQUEST_FAILED / '+($results|ConvertTo-Json -Depth 10 -Compress))}
    $generations=@($results|ForEach-Object{[long]$_.Payload.result.generation}|Sort-Object)
    $expectedGenerations=@(($startGeneration+1)..($startGeneration+8))
    if(($generations-join'|')-cne($expectedGenerations-join'|')-or@($generations|Sort-Object -Unique).Count-ne8){throw 'CONCURRENT_ACTIVATION_GENERATION_FORK'}
    $concurrentHead=Get-Content (Join-Path $concurrencyRoot 'activation-head.json') -Raw|ConvertFrom-Json
    if([long]$concurrentHead.generation-ne($startGeneration+8)-or[string]$concurrentHead.currentReleaseId-cne(Get-TestCurrentReleaseId $concurrencyRoot)){throw 'CONCURRENT_ACTIVATION_HEAD_POINTER_MISMATCH'}
    Complete-Case 'cross-process-activation-serialization-eight-processes'

    $beforeMixed=[long]$concurrentHead.generation;$currentId=Get-TestCurrentReleaseId $concurrencyRoot;$activateTarget=if($currentId-ceq[string]$concurrentA.releaseId){[string]$concurrentC.releaseId}else{[string]$concurrentA.releaseId}
    $activateWorker=Start-ConcurrencyWorker 'ACTIVATE' $concurrencyRoot $activateTarget $concurrencyDbState 200 20
    $rollbackWorker=Start-ConcurrencyWorker 'ROLLBACK' $concurrencyRoot '' $concurrencyDbState 200 20
    $mixed=@(Wait-ConcurrencyWorker $activateWorker 60;Wait-ConcurrencyWorker $rollbackWorker 60)
    if(@($mixed|Where-Object{$_.ExitCode-ne0-or-not[bool]$_.Payload.success}).Count-ne0){throw 'ACTIVATION_ROLLBACK_CONCURRENCY_FAILED'}
    $mixedHead=Get-Content (Join-Path $concurrencyRoot 'activation-head.json') -Raw|ConvertFrom-Json
    if([long]$mixedHead.generation-ne($beforeMixed+2)-or[string]$mixedHead.currentReleaseId-cne(Get-TestCurrentReleaseId $concurrencyRoot)){throw 'ACTIVATION_ROLLBACK_CHAIN_INVALID'}
    Complete-Case 'activation-vs-rollback-serialized'

    $beforeRecovery=[long]$mixedHead.generation;$preparedTarget=if((Get-TestCurrentReleaseId $concurrencyRoot)-ceq[string]$concurrentA.releaseId){[string]$concurrentC.releaseId}else{[string]$concurrentA.releaseId}
    Expect-Rejected { & $installer -Action activate -InstallationRoot $concurrencyRoot -ReleaseId $preparedTarget -DatabaseStatePath $concurrencyDbState -ExpectedSourceCommit $head -ConfirmDisposable -TestFault COMPLETION_WRITE } 'concurrency-prepared-window-created'
    $newTarget=if($preparedTarget-ceq[string]$concurrentA.releaseId){[string]$concurrentC.releaseId}else{[string]$concurrentA.releaseId}
    $recoverWorker=Start-ConcurrencyWorker 'RECOVER' $concurrencyRoot '' '' 200 20
    $newActivationWorker=Start-ConcurrencyWorker 'ACTIVATE' $concurrencyRoot $newTarget $concurrencyDbState 200 20
    $recoveryMixed=@(Wait-ConcurrencyWorker $recoverWorker 60;Wait-ConcurrencyWorker $newActivationWorker 60)
    if(@($recoveryMixed|Where-Object{$_.ExitCode-ne0-or-not[bool]$_.Payload.success}).Count-ne0){throw 'RECOVERY_ACTIVATION_CONCURRENCY_FAILED'}
    $recoveryHead=Get-Content (Join-Path $concurrencyRoot 'activation-head.json') -Raw|ConvertFrom-Json
    if([long]$recoveryHead.generation-ne($beforeRecovery+2)-or[string]$recoveryHead.currentReleaseId-cne(Get-TestCurrentReleaseId $concurrencyRoot)){throw 'RECOVERY_ACTIVATION_CHAIN_INVALID'}
    Complete-Case 'recovery-vs-activation-serialized'

    $lockPath=Join-Path $concurrencyRoot '.activation-operation.lock'
    $holder=Start-ConcurrencyWorker 'HOLD' $concurrencyRoot '' '' 3000 20;Wait-LockHeld $lockPath 5;$beforeTimeout=Get-AuthorityStateFingerprint $concurrencyRoot
    Expect-Rejected { & $installer -Action recover -InstallationRoot $concurrencyRoot -ExpectedSourceCommit $head -ConfirmDisposable -OperationLockTimeoutSeconds 1 } 'activation-operation-lock-timeout-rejected'
    $afterTimeout=Get-AuthorityStateFingerprint $concurrencyRoot
    if($beforeTimeout-cne$afterTimeout){throw 'LOCK_TIMEOUT_MUTATED_AUTHORITY'}
    $holderResult=Wait-ConcurrencyWorker $holder 10;if($holderResult.ExitCode-ne0){throw 'LOCK_HOLDER_FAILED'}
    Complete-Case 'lock-timeout-has-zero-authority-side-effects'

    $crashHolder=Start-ConcurrencyWorker 'HOLD' $concurrencyRoot '' '' 30000 20;Wait-LockHeld $lockPath 5;$crashHolder.Kill($true);$crashHolder.WaitForExit();$null=& $installer -Action recover -InstallationRoot $concurrencyRoot -ExpectedSourceCommit $head -ConfirmDisposable -OperationLockTimeoutSeconds 5
    Complete-Case 'process-termination-releases-operation-lock'

    $lockSource=Join-Path $tempRoot 'operation-lock-hardlink-source';Move-Item $lockPath $lockSource;New-Item -ItemType HardLink -Path $lockPath -Target $lockSource|Out-Null
    try{Expect-Rejected { & $installer -Action recover -InstallationRoot $concurrencyRoot -ExpectedSourceCommit $head -ConfirmDisposable } 'operation-lock-hardlink-rejected'}finally{Remove-Item $lockPath -Force;Move-Item $lockSource $lockPath}
    if($IsLinux){
        & /usr/bin/chmod 0644 $lockPath;try{Expect-Rejected { & $installer -Action recover -InstallationRoot $concurrencyRoot -ExpectedSourceCommit $head -ConfirmDisposable } 'operation-lock-0644-rejected'}finally{& /usr/bin/chmod 0600 $lockPath}
        & /usr/bin/chmod 0666 $lockPath;try{Expect-Rejected { & $installer -Action recover -InstallationRoot $concurrencyRoot -ExpectedSourceCommit $head -ConfirmDisposable } 'operation-lock-0666-rejected'}finally{& /usr/bin/chmod 0600 $lockPath}
        $lockTarget=Join-Path $tempRoot 'operation-lock-symlink-target';Move-Item $lockPath $lockTarget;& /usr/bin/ln -s $lockTarget $lockPath
        try{Expect-Rejected { & $installer -Action recover -InstallationRoot $concurrencyRoot -ExpectedSourceCommit $head -ConfirmDisposable } 'operation-lock-symlink-rejected'}finally{Remove-Item $lockPath -Force;Move-Item $lockTarget $lockPath}
        & /usr/bin/sudo -n true 2>$null
        if($LASTEXITCODE-eq0){$owner=(@(& /usr/bin/id -un)-join'').Trim();& /usr/bin/sudo -n /usr/bin/chown nobody $lockPath;try{Expect-Rejected { & $installer -Action recover -InstallationRoot $concurrencyRoot -ExpectedSourceCommit $head -ConfirmDisposable } 'operation-lock-wrong-owner-rejected'}finally{& /usr/bin/sudo -n /usr/bin/chown $owner $lockPath;& /usr/bin/chmod 0600 $lockPath}}
    }
    Complete-Case 'installation-scoped-operation-lock-identity-enforced'

    $partial = Copy-Release $releaseC 'negative-partial-install'
    [IO.File]::AppendAllText((Join-Path $partial 'frontend/index.html'), 'tamper', $utf8)
    Expect-Rejected {
        & $installer -Action install -InstallationRoot $installRoot -SourceRoot $partial `
            -ExpectedSourceCommit $head -ConfirmDisposable
    } 'partial-install-fails-before-copy'

    Write-Output "PASS / NQ_CANONICAL_RELEASE_TESTS cases=$($cases.Count)"
} finally {
    if (Test-Path -LiteralPath $tempRoot -PathType Container) {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
}
