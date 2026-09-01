[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $BinaryPath,
    [Parameter(Mandatory = $true)][string] $SourcePath,
    [Parameter(Mandatory = $true)][string] $ConfigPath,
    [Parameter(Mandatory = $true)][string] $ReportPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not [IO.Path]::IsPathRooted($BinaryPath)) {
    throw 'Verified gitleaks binary path must be absolute'
}
$resolvedBinary = (Resolve-Path -LiteralPath $BinaryPath -ErrorAction Stop).Path
$binary = Get-Item -LiteralPath $resolvedBinary -Force
if ($binary.PSIsContainer -or ($binary.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
    throw 'Verified gitleaks binary must be a regular file'
}
$resolvedSource = (Resolve-Path -LiteralPath $SourcePath -ErrorAction Stop).Path
$resolvedConfig = (Resolve-Path -LiteralPath $ConfigPath -ErrorAction Stop).Path
$reportParent = Split-Path -Parent ([IO.Path]::GetFullPath($ReportPath))
if (-not [string]::IsNullOrWhiteSpace($reportParent)) {
    New-Item -ItemType Directory -Path $reportParent -Force | Out-Null
}

& $resolvedBinary detect `
    --source $resolvedSource `
    --no-git `
    --config $resolvedConfig `
    --redact `
    --report-format json `
    --report-path $ReportPath `
    --exit-code 2 `
    --no-banner
exit $LASTEXITCODE
