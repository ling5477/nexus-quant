Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-NqBackupSha256 {
    param([Parameter(Mandatory = $true)][string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function ConvertFrom-NqBackupJson([string]$Text) {
    $command = Get-Command ConvertFrom-Json -ErrorAction Stop
    if ($command.Parameters.ContainsKey('DateKind')) {
        return $Text | ConvertFrom-Json -DateKind String
    }
    return $Text | ConvertFrom-Json
}

function Write-NqCanonicalBackupMetadata {
    param(
        [Parameter(Mandatory = $true)][string]$DumpPath,
        [Parameter(Mandatory = $true)][string]$MetadataPath,
        [Parameter(Mandatory = $true)][string]$SourceSchemaVersion,
        [Parameter(Mandatory = $true)][string]$DatabaseIdentity,
        [Parameter(Mandatory = $true)][string]$ToolIdentity,
        [Parameter(Mandatory = $true)][int]$PostgresqlServerMajor,
        [Parameter(Mandatory = $true)][int]$BackupToolMajor,
        [Parameter(Mandatory = $true)][int]$RestoreToolMajor,
        [Parameter(Mandatory = $true)][string]$SourceCommit
    )
    if (-not (Test-Path -LiteralPath $DumpPath -PathType Leaf)) {
        throw 'BLOCKED / BACKUP_DUMP_MISSING'
    }
    $item = Get-Item -LiteralPath $DumpPath -Force
    if ($item.Length -lt 1 -or $SourceSchemaVersion -cnotmatch '^V[1-9][0-9]*$' -or
            $SourceCommit -cnotmatch '^[0-9a-f]{40}$' -or
            [string]::IsNullOrWhiteSpace($DatabaseIdentity) -or
            [string]::IsNullOrWhiteSpace($ToolIdentity) -or
            $PostgresqlServerMajor -ne 16 -or $BackupToolMajor -ne 16 -or $RestoreToolMajor -ne 16) {
        throw 'BLOCKED / BACKUP_METADATA_INPUT_INVALID'
    }
    $metadata = [pscustomobject][ordered]@{
        schemaVersion = 'nq-canonical-backup.v1'
        backupSha256 = Get-NqBackupSha256 $DumpPath
        backupSize = [long]$item.Length
        sourceSchemaVersion = $SourceSchemaVersion
        databaseIdentity = $DatabaseIdentity
        format = 'POSTGRESQL_CUSTOM'
        toolIdentity = $ToolIdentity
        postgresqlServerMajor = $PostgresqlServerMajor
        backupToolMajor = $BackupToolMajor
        restoreToolMajor = $RestoreToolMajor
        sourceCommit = $SourceCommit.ToLowerInvariant()
        createdAt = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
    }
    [IO.File]::WriteAllText(
        $MetadataPath,
        ($metadata | ConvertTo-Json -Depth 8 -Compress),
        [Text.UTF8Encoding]::new($false)
    )
    return $metadata
}

function Test-NqCanonicalBackup {
    param(
        [Parameter(Mandatory = $true)][string]$DumpPath,
        [Parameter(Mandatory = $true)][string]$MetadataPath,
        [Parameter(Mandatory = $true)][string]$ExpectedSchemaVersion,
        [Parameter(Mandatory = $true)][string]$ExpectedSourceCommit,
        [int]$ExpectedPostgresqlMajor = 16
    )
    if (-not (Test-Path -LiteralPath $DumpPath -PathType Leaf) -or
            -not (Test-Path -LiteralPath $MetadataPath -PathType Leaf)) {
        throw 'BLOCKED / BACKUP_ARTIFACT_MISSING'
    }
    try { $metadata = ConvertFrom-NqBackupJson (Get-Content -LiteralPath $MetadataPath -Raw) } catch {
        throw 'BLOCKED / BACKUP_METADATA_INVALID'
    }
    $fields = @($metadata.PSObject.Properties.Name | Sort-Object)
    $expectedFields = @('backupSha256', 'backupSize', 'createdAt', 'databaseIdentity', 'format',
        'schemaVersion', 'sourceCommit', 'sourceSchemaVersion', 'toolIdentity',
        'postgresqlServerMajor', 'backupToolMajor', 'restoreToolMajor') | Sort-Object
    $item = Get-Item -LiteralPath $DumpPath -Force
    if (($fields -join '|') -cne ($expectedFields -join '|') -or
            [string]$metadata.schemaVersion -cne 'nq-canonical-backup.v1' -or
            [string]$metadata.backupSha256 -cnotmatch '^[0-9a-f]{64}$' -or
            [long]$metadata.backupSize -ne [long]$item.Length -or
            [string]$metadata.sourceSchemaVersion -cne $ExpectedSchemaVersion -or
            [string]$metadata.sourceCommit -cne $ExpectedSourceCommit.ToLowerInvariant() -or
            [string]$metadata.format -cne 'POSTGRESQL_CUSTOM' -or
            [string]::IsNullOrWhiteSpace([string]$metadata.databaseIdentity) -or
            [string]::IsNullOrWhiteSpace([string]$metadata.toolIdentity) -or
            [int]$metadata.postgresqlServerMajor -ne $ExpectedPostgresqlMajor -or
            [int]$metadata.backupToolMajor -ne $ExpectedPostgresqlMajor -or
            [int]$metadata.restoreToolMajor -ne $ExpectedPostgresqlMajor -or
            [string]$metadata.createdAt -cnotmatch '^20[0-9]{2}-[01][0-9]-[0-3][0-9]T[0-2][0-9]:[0-5][0-9]:[0-5][0-9]Z$' -or
            (Get-NqBackupSha256 $DumpPath) -cne [string]$metadata.backupSha256) {
        throw 'BLOCKED / BACKUP_INTEGRITY_VERIFICATION_FAILED'
    }
    return [pscustomobject][ordered]@{
        decision = 'PASS / NQ_CANONICAL_BACKUP_VERIFIED'
        backupSha256 = [string]$metadata.backupSha256
        backupSize = [long]$metadata.backupSize
        sourceSchemaVersion = [string]$metadata.sourceSchemaVersion
        databaseIdentity = [string]$metadata.databaseIdentity
        toolIdentity = [string]$metadata.toolIdentity
        postgresqlServerMajor = [int]$metadata.postgresqlServerMajor
        backupToolMajor = [int]$metadata.backupToolMajor
        restoreToolMajor = [int]$metadata.restoreToolMajor
    }
}

Export-ModuleMember -Function @(
    'Get-NqBackupSha256', 'Write-NqCanonicalBackupMetadata', 'Test-NqCanonicalBackup'
)
