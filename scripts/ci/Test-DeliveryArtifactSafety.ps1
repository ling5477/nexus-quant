[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $EvidenceRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $EvidenceRoot).Path
$violations = New-Object System.Collections.Generic.List[string]
$textExtensions = @('.json', '.html', '.js', '.css', '.txt', '.xml', '.yml', '.yaml', '.toml', '.md')
$forbiddenPath = '(?i)(^|[\\/])(secrets?|credentials?)([\\/]|$)|(^|[\\/])\.env($|\.)|\.(pem|key|p12|jks|keystore|dump|backup|log)$'
$contentRules = [ordered]@{
    aws_access_key = 'AKIA[0-9A-Z]{16}'
    github_token = '(github_pat_[A-Za-z0-9_]{20,}|gh[pousr]_[A-Za-z0-9]{30,})'
    provider_key = '(?<![A-Za-z0-9])sk-(proj-|ant-)?[A-Za-z0-9_-]{20,}'
    private_key = '-----BEGIN (RSA |EC |OPENSSH |DSA |PGP )?PRIVATE KEY-----'
    credentials_in_url = '[a-zA-Z][a-zA-Z0-9+.-]*://[^/@\s]+:[^/@\s]+@'
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach ($file in @(Get-ChildItem -LiteralPath $root -File -Recurse -Force)) {
    $relative = $file.FullName.Substring($root.Length).TrimStart('\', '/').Replace('\', '/')
    if (($file.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        $violations.Add("link file=$relative")
        continue
    }
    if ($relative -match $forbiddenPath) {
        $violations.Add("forbidden_path file=$relative")
        continue
    }
    if ($file.Extension -in @('.jar', '.zip')) {
        $archive = [IO.Compression.ZipFile]::OpenRead($file.FullName)
        try {
            foreach ($entry in $archive.Entries) {
                if ($entry.FullName -match $forbiddenPath) {
                    $violations.Add("forbidden_archive_path file=$relative entry=$($entry.FullName)")
                }
            }
        } finally {
            $archive.Dispose()
        }
        continue
    }
    if ($textExtensions -contains $file.Extension.ToLowerInvariant()) {
        $content = [IO.File]::ReadAllText($file.FullName, [Text.Encoding]::UTF8)
        foreach ($rule in $contentRules.GetEnumerator()) {
            if ([regex]::IsMatch($content, [string]$rule.Value)) {
                $violations.Add("$($rule.Key) file=$relative")
            }
        }
    }
}

if ($violations.Count -gt 0) {
    Write-Output 'Delivery artifact safety gate failed (rule and path only; matched content is suppressed):'
    $violations | Sort-Object -Unique | ForEach-Object { Write-Output "  $_" }
    throw "Delivery artifact safety violations=$($violations.Count)"
}
Write-Output "DELIVERY_ARTIFACT_SAFETY=PASS root=$EvidenceRoot"
