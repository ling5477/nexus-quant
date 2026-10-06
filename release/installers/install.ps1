[CmdletBinding()]
param([string]$InstallRoot = (Join-Path $env:USERPROFILE '.nexusquant'), [string]$PackageRoot = (Split-Path $PSScriptRoot -Parent), [int]$FrontendPort = 18080, [int]$BackendPort = 18888, [string]$ProjectName = 'nexusquant')
& (Join-Path $PSScriptRoot 'nexusquant.ps1') -Action install @PSBoundParameters
