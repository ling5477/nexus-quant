[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$helperPath = Join-Path $repoRoot 'scripts\java-standard\invoke-maven-profile-validation.ps1'
$backendPom = Join-Path $repoRoot 'backend\pom.xml'
$currentPwsh = [Diagnostics.Process]::GetCurrentProcess().MainModule.FileName

function Assert-Equal([object]$Expected, [object]$Actual, [string]$Name) {
    if ($Expected -cne $Actual) { throw "ASSERT_EQUAL_FAILED: $Name expected=$Expected actual=$Actual" }
}

function Invoke-Helper([string[]]$Arguments) {
    $output = @(& $currentPwsh -NoProfile -File $helperPath @Arguments 2>&1 | ForEach-Object { $_.ToString() })
    return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = $output }
}

$statusResult = Invoke-Helper @('-PomPath', $backendPom, '-Profile', 'quality', '-StatusOnly')
Assert-Equal 0 $statusResult.ExitCode 'current quality status exit'
if (-not ($statusResult.Output -contains 'QUALITY_PROFILE=NOT_AVAILABLE')) { throw 'CURRENT_QUALITY_STATUS_FAILED: expected NOT_AVAILABLE' }

$tempRoot = Join-Path ([IO.Path]::GetTempPath()) ("maven-profile-validation-{0}" -f [guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($tempRoot) | Out-Null
try {
    $missingPom = Join-Path $tempRoot 'missing-profile-pom.xml'
    $existingPom = Join-Path $tempRoot 'existing-profile-pom.xml'
    $pomPrefix = '<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><groupId>test</groupId><artifactId>test</artifactId><version>1</version>'
    [IO.File]::WriteAllText($missingPom, "$pomPrefix</project>", [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText($existingPom, "$pomPrefix<profiles><profile><id>existing</id></profile></profiles></project>", [Text.UTF8Encoding]::new($false))

    $markerPath = Join-Path $tempRoot 'maven-invoked.txt'
    $successName = if ($IsWindows) { 'fake-maven-success.cmd' } else { 'fake-maven-success.sh' }
    $warningName = if ($IsWindows) { 'fake-maven-warning.cmd' } else { 'fake-maven-warning.sh' }
    $successMaven = Join-Path $tempRoot $successName
    $warningMaven = Join-Path $tempRoot $warningName
    if ($IsWindows) {
        [IO.File]::WriteAllText($successMaven, "@echo invoked>`"%NQ_MAVEN_MARKER%`"`r`n@echo [INFO] BUILD SUCCESS`r`n@exit /b 0`r`n", [Text.UTF8Encoding]::new($false))
        [IO.File]::WriteAllText($warningMaven, "@echo [WARNING] The requested profile `"existing`" could not be activated because it does not exist.`r`n@echo [INFO] BUILD SUCCESS`r`n@exit /b 0`r`n", [Text.UTF8Encoding]::new($false))
    }
    else {
        [IO.File]::WriteAllText($successMaven, "#!/bin/sh`nprintf invoked > `"`$NQ_MAVEN_MARKER`"`nprintf '%s\n' '[INFO] BUILD SUCCESS'`nexit 0`n", [Text.UTF8Encoding]::new($false))
        [IO.File]::WriteAllText($warningMaven, "#!/bin/sh`nprintf '%s\n' '[WARNING] The requested profile `"existing`" could not be activated because it does not exist.'`nprintf '%s\n' '[INFO] BUILD SUCCESS'`nexit 0`n", [Text.UTF8Encoding]::new($false))
        & chmod +x $successMaven $warningMaven
        if ($LASTEXITCODE -ne 0) { throw 'FAKE_MAVEN_CHMOD_FAILED' }
    }
    $env:NQ_MAVEN_MARKER = $markerPath

    $missingResult = Invoke-Helper @(
        '-PomPath', $missingPom, '-Profile', 'quality',
        '-MavenExecutable', $successMaven,
        '-Goals', 'validate'
    )
    Assert-Equal 2 $missingResult.ExitCode 'missing profile hard failure'
    if (-not (($missingResult.Output -join "`n").Contains('QUALITY_PROFILE_NOT_FOUND:'))) { throw 'MISSING_PROFILE_ERROR_CODE_FAILED' }
    if (Test-Path -LiteralPath $markerPath) { throw 'MISSING_PROFILE_EXECUTED_MAVEN' }

    $existingResult = Invoke-Helper @(
        '-PomPath', $existingPom, '-Profile', 'existing',
        '-MavenExecutable', $successMaven,
        '-Goals', 'validate'
    )
    Assert-Equal 0 $existingResult.ExitCode 'existing profile accepted'
    if (-not ($existingResult.Output -contains 'MAVEN_PROFILE_VALIDATION=PASS')) { throw 'EXISTING_PROFILE_PASS_OUTPUT_FAILED' }
    if (-not (Test-Path -LiteralPath $markerPath)) { throw 'EXISTING_PROFILE_MAVEN_NOT_EXECUTED' }

    $warningResult = Invoke-Helper @(
        '-PomPath', $existingPom, '-Profile', 'existing',
        '-MavenExecutable', $warningMaven,
        '-Goals', 'validate'
    )
    Assert-Equal 2 $warningResult.ExitCode 'Maven exit-0 missing-profile warning hard failure'
    if (-not (($warningResult.Output -join "`n").Contains('MAVEN_PROFILE_NOT_FOUND:'))) { throw 'MAVEN_WARNING_ERROR_CODE_FAILED' }
}
finally {
    if (Test-Path -LiteralPath $tempRoot) { Remove-Item -LiteralPath $tempRoot -Recurse -Force }
}

Write-Output 'MAVEN_PROFILE_VALIDATION_TEST=PASS'
Write-Output 'QUALITY_PROFILE=NOT_AVAILABLE'
