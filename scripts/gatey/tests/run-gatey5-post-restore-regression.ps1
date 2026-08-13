[CmdletBinding()] param()
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$files=@('run-gatey5-post-restore-drill.ps1','gatey5-pre-fixture.sql','gatey5-post-fixture.sql') | ForEach-Object { Join-Path $root $_ }
$text=($files | ForEach-Object { Get-Content -Raw $_ }) -join "`n"
foreach($marker in @('ConfirmDisposable','127.0.0.1::5432','execution_receipts=6000000','pg_dump','pg_restore','allSessionsTerminal','RESTART IDENTITY','FIXTURE_TERMINAL','receipts_per_intent')) {
    if(-not $text.Contains($marker)){throw "missing marker: $marker"}
}
foreach($forbidden in @('Invoke-WebRequest','Invoke-RestMethod','systemctl','flyway repair','DISABLE TRIGGER','apiKey','passphrase')) {
    if($text.IndexOf($forbidden,[StringComparison]::OrdinalIgnoreCase)-ge 0){throw "forbidden marker: $forbidden"}
}
'PASS / GATEY5_POST_RESTORE_TOOLING_REGRESSION'
