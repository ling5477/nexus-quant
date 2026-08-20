[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ReleaseRoot,
    [Parameter(Mandatory = $true)][string]$EvidenceRoot,
    [ValidateSet('PRE_DEPLOYMENT', 'POST_ACTIVATION')]
    [string]$Phase = 'PRE_DEPLOYMENT'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'gatey-readonly-release-contract.psm1') -Force -DisableNameChecking

try
{
    if (-not (Test-Path -LiteralPath $EvidenceRoot -PathType Container))
    {
        throw 'BLOCKED / DEPLOYMENT_RECEIPT_MISSING'
    }
    $requirePosix = $Phase -ceq 'POST_ACTIVATION'
    $release = Test-GateYReadonlyRelease $ReleaseRoot -RequirePosix:$requirePosix
    $manifest = Get-Content -LiteralPath (Join-Path $ReleaseRoot 'release-manifest.json') -Raw | ConvertFrom-Json

    if ($Phase -ceq 'PRE_DEPLOYMENT')
    {
        $rollback = Assert-GateYRollbackContract $manifest $release.manifestSha256 $ReleaseRoot
        [pscustomobject][ordered]@{
            decision = 'PASS / GATEY_READONLY_PRE_DEPLOYMENT_READY'
            contractState = 'PRE_DEPLOYMENT_READY'
            releaseId = $release.releaseId
            manifestSha256 = $release.manifestSha256
            migration = $rollback.migration
            rollback = $rollback
            activationPath = "/opt/nexus-quant/releases/$($release.releaseId)"
            currentPointer = '/opt/nexus-quant/current'
            serverMutation = $false
            deploymentPerformed = $false
            migrationPerformed = $false
        } | ConvertTo-Json -Depth 10
    }
    else
    {
        $healthDecision = Assert-GateYPostActivationHealth `
            $manifest $release.manifestSha256 $ReleaseRoot '/opt/nexus-quant/current'
        [pscustomobject][ordered]@{
            decision = 'PASS / GATEY_READONLY_POST_ACTIVATION_ACCEPTED'
            contractState = 'POST_ACTIVATION_ACCEPTED'
            releaseId = $release.releaseId
            manifestSha256 = $release.manifestSha256
            healthContract = $healthDecision
            posixVerified = $release.posixVerified
            serverMutation = $false
            deploymentPerformed = $false
            migrationPerformed = $false
        } | ConvertTo-Json -Depth 8
    }
}
catch
{
    $decision = if ($_.Exception.Message -match '^(BLOCKED|FAIL) / [A-Z0-9_]+$') {
        $_.Exception.Message
    } else { 'FAIL / GATEY_READONLY_DEPLOYMENT_CONTRACT_INTERNAL_ERROR' }
    [pscustomobject]@{ decision = $decision; contractState = 'BLOCKED'; serverMutation = $false } |
        ConvertTo-Json
    exit 2
}
