[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$PomPath,
    [Parameter(Mandatory = $true)]
    [string]$Profile,
    [string[]]$Goals = @('validate'),
    [string]$MavenExecutable = 'mvn',
    [string[]]$MavenPrefixArguments = @(),
    [switch]$StatusOnly
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Get-ProfileNotFoundCode([string]$ProfileId) {
    if ($ProfileId -ceq 'quality') { return 'QUALITY_PROFILE_NOT_FOUND' }
    return 'MAVEN_PROFILE_NOT_FOUND'
}

function Get-DeclaredMavenProfiles([string]$Path) {
    $raw = [IO.File]::ReadAllText($Path, [Text.UTF8Encoding]::new($false, $true))
    try { [xml]$document = $raw }
    catch { throw 'MAVEN_PROFILE_CONFIG_INVALID: POM is not valid XML' }
    $profiles = [Collections.Generic.List[string]]::new()
    foreach ($profileNode in @($document.SelectNodes("//*[local-name()='profile']"))) {
        $idNode = $profileNode.SelectSingleNode("./*[local-name()='id']")
        if ($null -ne $idNode -and -not [string]::IsNullOrWhiteSpace($idNode.InnerText)) {
            $profiles.Add($idNode.InnerText.Trim())
        }
    }
    return @($profiles)
}

try {
    if ($Profile -notmatch '^[A-Za-z0-9_.-]+$') { throw 'MAVEN_PROFILE_CONFIG_INVALID: profile id is invalid' }
    $resolvedPom = (Resolve-Path -LiteralPath $PomPath).Path
    if (-not (Test-Path -LiteralPath $resolvedPom -PathType Leaf)) { throw 'MAVEN_PROFILE_CONFIG_INVALID: POM is missing' }
    $declaredProfiles = @(Get-DeclaredMavenProfiles $resolvedPom)
    $profilePresent = @($declaredProfiles | Where-Object { $_ -ceq $Profile }).Count -eq 1
    $status = if ($profilePresent) { 'AVAILABLE' } else { 'NOT_AVAILABLE' }

    if ($StatusOnly) {
        if ($Profile -ceq 'quality') { Write-Output "QUALITY_PROFILE=$status" }
        Write-Output "MAVEN_PROFILE_STATUS=$status"
        Write-Output "MAVEN_PROFILE_ID=$Profile"
        exit 0
    }

    if (-not $profilePresent) {
        $code = Get-ProfileNotFoundCode $Profile
        throw "${code}: Maven profile '$Profile' is not declared in $resolvedPom"
    }

    $arguments = [Collections.Generic.List[string]]::new()
    foreach ($argument in $MavenPrefixArguments) { $arguments.Add($argument) }
    foreach ($argument in @('-f', $resolvedPom, "-P$Profile")) { $arguments.Add($argument) }
    foreach ($goal in $Goals) { $arguments.Add($goal) }
    $mavenOutput = @(& $MavenExecutable @($arguments.ToArray()) 2>&1 | ForEach-Object { $_.ToString() })
    $mavenExit = $LASTEXITCODE
    $joinedOutput = $mavenOutput -join "`n"
    $profilePattern = [regex]::Escape($Profile)
    $missingProfileWarning = '(?i)requested profile\s+[''"]?' + $profilePattern + '[''"]?\s+could not be activated'
    $missingProfileError = '(?i)profile\s+[''"]?' + $profilePattern + '[''"]?.*does not exist'
    if ($joinedOutput -match $missingProfileWarning -or $joinedOutput -match $missingProfileError) {
        $code = Get-ProfileNotFoundCode $Profile
        throw "${code}: Maven reported that profile '$Profile' is missing despite exit=$mavenExit"
    }
    $mavenOutput | Write-Output
    if ($mavenExit -ne 0) { throw "MAVEN_PROFILE_EXECUTION_FAILED: Maven exit=$mavenExit" }

    Write-Output 'MAVEN_PROFILE_VALIDATION=PASS'
    Write-Output "MAVEN_PROFILE_ID=$Profile"
    exit 0
}
catch {
    $message = $_.Exception.Message
    Write-Output 'MAVEN_PROFILE_VALIDATION=FAIL'
    if ($message -match '^(QUALITY_PROFILE_NOT_FOUND|MAVEN_PROFILE_NOT_FOUND|MAVEN_PROFILE_CONFIG_INVALID):') {
        [Console]::Error.WriteLine($message)
        exit 2
    }
    [Console]::Error.WriteLine($message)
    exit 3
}
