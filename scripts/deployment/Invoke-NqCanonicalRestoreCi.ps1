[CmdletBinding()]param([Parameter(Mandatory=$true)][string]$SourceCommit)
Set-StrictMode -Version Latest;$ErrorActionPreference='Stop'
& (Join-Path $PSScriptRoot 'Invoke-NqCanonicalRestoreDrill.ps1') -ConfirmDisposable -ExpectedCommit $SourceCommit -EvidenceRoot 'artifacts/phase5b-current-schema-restore'
