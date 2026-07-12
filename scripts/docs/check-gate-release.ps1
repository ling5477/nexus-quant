<#
.SYNOPSIS
Validates release commit, exact-HEAD CI, annotated tag, and local/remote peeled target.
.NOTES
This checker does not read archive roles or work batch state and never mutates Git state.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^gate-[a-z0-9-]+$')]
    [string] $Gate,
    [string] $ExpectedTag,
    [string] $ExpectedCommit
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
. (Join-Path $PSScriptRoot 'governance-workflow-lib.ps1')
$errors = New-Object System.Collections.Generic.List[string]

function Add-ReleaseError {
    param([string] $Code, [string] $Message)
    $script:errors.Add("$Code $Message")
    Write-Output "ERROR $Code $Message"
}

function Invoke-GitRead {
    param([string[]] $Arguments, [switch] $AllowFailure)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $output = & git -C $repoRoot @Arguments 2>&1
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = $previous
    $text = (($output | ForEach-Object { $_.ToString() }) -join "`n").Trim()
    if ($exitCode -ne 0 -and -not $AllowFailure) { throw "git $($Arguments -join ' ') failed: $text" }
    return [pscustomobject]@{ ExitCode = $exitCode; Text = $text }
}

function Get-GateLabel {
    param([string] $GateName)
    $suffix = (($GateName.Substring(5) -split '-') | ForEach-Object {
        if ($_.Length -eq 1) { $_.ToUpperInvariant() } else { $_.Substring(0, 1).ToUpperInvariant() + $_.Substring(1) }
    }) -join ''
    return "Gate$suffix"
}

function Resolve-RepoPath {
    param([string] $Path)
    if ([System.IO.Path]::IsPathRooted($Path)) { return $Path }
    return (Join-Path $repoRoot $Path)
}

try {
    $contract = Get-GovernanceWorkflowContract (Join-Path $PSScriptRoot 'governance-workflow-contract.json')
} catch {
    Add-ReleaseError 'RELEASE_CONTRACT_INVALID' $_.Exception.Message
    $contract = $null
}

if ($null -ne $contract) {
    # Release identity is contract-owned. Callers may assert an expected tag/commit, but cannot redirect
    # validation to another branch, remote, Gate tag, or STATUS document.
    $ExpectedBranch = [string]$contract.release.expectedBranch
    $RemoteName = [string]$contract.release.remoteName
    $canonicalTag = 'nq-{0}-freeze' -f $Gate.Replace('-', '')
    if ([string]::IsNullOrWhiteSpace($ExpectedTag)) { $ExpectedTag = $canonicalTag }
    $inputValid = $true
    if ($ExpectedTag -notmatch [string]$contract.release.tagPattern -or $ExpectedTag -ne $canonicalTag) {
        Add-ReleaseError 'RELEASE_TAG_INVALID' "TAG_NAME_INVALID tag=$ExpectedTag"
        $inputValid = $false
    }

    $resolvedStatus = Join-Path $repoRoot 'docs/current/STATUS.md'
    $declaredCommit = $null
    if (Test-Path -LiteralPath $resolvedStatus -PathType Leaf) {
        $statusContent = [System.IO.File]::ReadAllText($resolvedStatus, (New-Object System.Text.UTF8Encoding($false)))
        $authority = Read-GovernanceAuthorityBlock $statusContent
        $gateLabel = Get-GateLabel $Gate
        if ($null -ne $authority -and $authority.last_frozen_gate -eq $gateLabel -and $authority.last_frozen_gate_tag -eq $ExpectedTag) {
            $declaredCommit = $authority.last_frozen_gate_commit
        } elseif ($statusContent -match '(?m)^current_gate_tag=(.+)$' -and $Matches[1].Trim() -eq $ExpectedTag -and
            $statusContent -match '(?m)^updated_commit=([0-9a-f]{40})$') {
            $declaredCommit = $Matches[1]
        }
    }
    if ([string]::IsNullOrWhiteSpace($ExpectedCommit)) { $ExpectedCommit = $declaredCommit }
    elseif (-not [string]::IsNullOrWhiteSpace($declaredCommit) -and $ExpectedCommit -ne $declaredCommit) {
        Add-ReleaseError 'RELEASE_COMMIT_INVALID' "EXPECTED_COMMIT_CONFLICT declared=$declaredCommit supplied=$ExpectedCommit"
        $inputValid = $false
    }
    if ($ExpectedCommit -notmatch '^[0-9a-f]{40}$') {
        Add-ReleaseError 'RELEASE_COMMIT_INVALID' "EXPECTED_COMMIT_MISSING_OR_INVALID value=$ExpectedCommit"
        $inputValid = $false
    }
    if ($inputValid) {
        $commitType = Invoke-GitRead @('cat-file', '-t', $ExpectedCommit) -AllowFailure
        if ($commitType.ExitCode -ne 0 -or $commitType.Text -ne 'commit') {
            Add-ReleaseError 'RELEASE_COMMIT_INVALID' "RELEASE_COMMIT_MISSING commit=$ExpectedCommit"
        }
        $branchRef = "refs/remotes/$RemoteName/$ExpectedBranch"
        $branchExists = Invoke-GitRead @('show-ref', '--verify', '--quiet', $branchRef) -AllowFailure
        if ($branchExists.ExitCode -ne 0) {
            Add-ReleaseError 'RELEASE_COMMIT_INVALID' "EXPECTED_BRANCH_REF_MISSING ref=$branchRef"
        } else {
            # ls-remote is the current remote fact; a stale local tracking ref must never authorize release.
            $remoteBranch = Invoke-GitRead @('ls-remote', $RemoteName, "refs/heads/$ExpectedBranch") -AllowFailure
            $remoteBranchMatch = [regex]::Match($remoteBranch.Text, "(?m)^([0-9a-f]{40})\s+refs/heads/$([regex]::Escape($ExpectedBranch))$")
            if ($remoteBranch.ExitCode -ne 0 -or -not $remoteBranchMatch.Success) {
                Add-ReleaseError 'RELEASE_COMMIT_INVALID' "REMOTE_BRANCH_QUERY_FAILED branch=$RemoteName/$ExpectedBranch"
            } else {
                $remoteBranchCommit = $remoteBranchMatch.Groups[1].Value
                $localTrackingCommit = (Invoke-GitRead @('rev-parse', $branchRef)).Text
                if ($localTrackingCommit -ne $remoteBranchCommit) {
                    Add-ReleaseError 'RELEASE_COMMIT_INVALID' "REMOTE_BRANCH_NOT_ALIGNED local=$localTrackingCommit remote=$remoteBranchCommit branch=$RemoteName/$ExpectedBranch"
                } else {
                    $ancestry = Invoke-GitRead @('merge-base', '--is-ancestor', $ExpectedCommit, $remoteBranchCommit) -AllowFailure
                    if ($ancestry.ExitCode -ne 0) {
                        Add-ReleaseError 'RELEASE_COMMIT_INVALID' "RELEASE_COMMIT_NOT_ON_BRANCH commit=$ExpectedCommit branch=$RemoteName/$ExpectedBranch"
                    }
                }
            }
        }

        $tagType = Invoke-GitRead @('cat-file', '-t', $ExpectedTag) -AllowFailure
        if ($tagType.ExitCode -ne 0) {
            Add-ReleaseError 'RELEASE_TAG_INVALID' "LOCAL_TAG_MISSING tag=$ExpectedTag"
        } elseif ($tagType.Text -ne 'tag') {
            Add-ReleaseError 'RELEASE_TAG_INVALID' "TAG_NOT_ANNOTATED tag=$ExpectedTag type=$($tagType.Text)"
        } else {
            $tagObject = (Invoke-GitRead @('rev-parse', "$ExpectedTag^{tag}")).Text
            $peeled = (Invoke-GitRead @('rev-parse', "$ExpectedTag^{}")).Text
            if ($peeled -ne $ExpectedCommit) {
                Add-ReleaseError 'RELEASE_TAG_INVALID' "TAG_TARGET_MISMATCH expected=$ExpectedCommit actual=$peeled"
            }

            $remoteRefs = Invoke-GitRead @('ls-remote', '--tags', $RemoteName, "refs/tags/$ExpectedTag", "refs/tags/$ExpectedTag^{}") -AllowFailure
            if ($remoteRefs.ExitCode -ne 0) {
                Add-ReleaseError 'REMOTE_TAG_INVALID' "REMOTE_TAG_QUERY_FAILED remote=$RemoteName tag=$ExpectedTag"
            } else {
                $escaped = [regex]::Escape($ExpectedTag)
                $objectMatch = [regex]::Match($remoteRefs.Text, "(?m)^([0-9a-f]{40})\s+refs/tags/$escaped$")
                $peeledMatch = [regex]::Match($remoteRefs.Text, "(?m)^([0-9a-f]{40})\s+refs/tags/$escaped\^\{\}$")
                if (-not $objectMatch.Success -or -not $peeledMatch.Success) {
                    Add-ReleaseError 'REMOTE_TAG_INVALID' "REMOTE_TAG_MISSING tag=$ExpectedTag"
                } elseif ($objectMatch.Groups[1].Value -ne $tagObject -or $peeledMatch.Groups[1].Value -ne $ExpectedCommit) {
                    Add-ReleaseError 'REMOTE_TAG_INVALID' ("REMOTE_TAG_MOVED tag={0} localObject={1} remoteObject={2} expectedTarget={3} remoteTarget={4}" -f
                        $ExpectedTag, $tagObject, $objectMatch.Groups[1].Value, $ExpectedCommit, $peeledMatch.Groups[1].Value)
                }
            }
        }

        $gh = Get-Command gh -ErrorAction SilentlyContinue
        if (-not $gh) {
            Add-ReleaseError 'RELEASE_CI_NOT_GREEN' 'GH_CLI_NOT_FOUND'
        } else {
            $previous = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            $json = & gh run list --commit $ExpectedCommit --limit 20 --json databaseId,workflowName,status,conclusion,headSha 2>&1
            $exitCode = $LASTEXITCODE
            $ErrorActionPreference = $previous
            if ($exitCode -ne 0) {
                # Do not echo provider/auth output; an exit code is sufficient and avoids accidental credential disclosure.
                Add-ReleaseError 'RELEASE_CI_NOT_GREEN' "GH_RUN_LIST_FAILED exit=$exitCode"
            } else {
                try { $runs = @($json | ConvertFrom-Json) } catch { $runs = @() }
                $green = @($runs | Where-Object {
                    $_.workflowName -eq [string]$contract.release.workflowName -and
                    $_.headSha -eq $ExpectedCommit -and $_.status -eq 'completed' -and $_.conclusion -eq 'success'
                } | Sort-Object databaseId -Descending | Select-Object -First 1)
                if ($green.Count -eq 0) {
                    Add-ReleaseError 'RELEASE_CI_NOT_GREEN' "EXACT_HEAD_CI_NOT_GREEN headSha=$ExpectedCommit"
                } else {
                    Write-Output "RELEASE_CI run=$($green[0].databaseId) headSha=$ExpectedCommit status=completed conclusion=success"
                }
            }
        }
    }
}

if ($errors.Count -gt 0) {
    Write-Output "RELEASE_CHECK gate=$Gate errors=$($errors.Count)"
    Write-Output 'BLOCKED / GATE_RELEASE_INVALID'
    exit 1
}
Write-Output "RELEASE_CHECK gate=$Gate tag=$ExpectedTag commit=$ExpectedCommit branch=$RemoteName/$ExpectedBranch errors=0"
Write-Output 'PASS / GATE_RELEASE_VALID'
exit 0
