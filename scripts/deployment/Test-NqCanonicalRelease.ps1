[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ReleaseRoot,
    [string]$ExpectedSourceCommit,
    [string]$ExpectedSchemaTarget,
    [switch]$RequirePosix
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'nq-canonical-release.psm1') -Force -DisableNameChecking

Test-NqCanonicalRelease -ReleaseRoot $ReleaseRoot `
    -ExpectedSourceCommit $ExpectedSourceCommit `
    -ExpectedSchemaTarget $ExpectedSchemaTarget `
    -RequirePosix:$RequirePosix
