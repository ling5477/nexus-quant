#requires -Version 7.4
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Repository,
    [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{40}$')][string]$Commit,
    [Parameter(Mandatory)][string]$SourceDirectory,
    [Parameter(Mandatory)][string]$Wheelhouse,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [Parameter(Mandatory)][string]$Python
)
$ErrorActionPreference = 'Stop'
# 独立入口只构建离线研究资产，不调用安装器、Docker 或任何交易运行时。
$helper = Join-Path $SourceDirectory 'scripts/release/python_companion.py'
& $Python -B $helper build --repo $Repository --commit $Commit --source $SourceDirectory --wheelhouse $Wheelhouse --output $OutputDirectory
if ($LASTEXITCODE -ne 0) { throw 'Python Companion build failed' }
