[CmdletBinding()]
param([string]$InstallRoot = (Join-Path $env:USERPROFILE '.nexusquant'), [string]$PackageRoot = '', [int]$FrontendPort = 18080, [int]$BackendPort = 18888, [string]$ProjectName = 'nexusquant')
if (-not $PackageRoot) { $PackageRoot = Split-Path $PSScriptRoot -Parent; $PSBoundParameters['PackageRoot'] = $PackageRoot }
& (Join-Path $PSScriptRoot 'nexusquant.ps1') -Action install @PSBoundParameters
