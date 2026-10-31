param(
    [Parameter(Mandatory)][string]$EnvFile,
    [int]$MySqlPort = 13306,
    [int]$KafkaPort = 39092,
    [int]$RedisPort = 16379
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$destination = [IO.Path]::GetFullPath($EnvFile)
$root = [IO.Path]::GetFullPath((Split-Path $PSScriptRoot -Parent))
if ($destination.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Place fake runtime credentials outside the repository.'
}
if (Test-Path -LiteralPath $destination) { throw 'Refusing to overwrite an existing env file.' }
foreach ($port in @($MySqlPort, $KafkaPort, $RedisPort)) {
    if ($port -le 3306 -or $port -gt 65535) { throw 'Use fixture ports above 3306.' }
}
function New-FixturePassword { [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24)).ToLowerInvariant() }
$admin = New-FixturePassword
$values = [ordered]@{
    TH4_FIXTURE_ID = 'th4-' + [guid]::NewGuid().ToString('N')
    MYSQL_ROOT_PASSWORD = $admin
    TH4_MYSQL_ADMIN_PASSWORD = $admin
    ACCOUNT_DB_PASSWORD = New-FixturePassword
    TRANSFER_DB_PASSWORD = New-FixturePassword
    HISTORY_DB_PASSWORD = New-FixturePassword
    MYSQL_HOST_PORT = $MySqlPort
    KAFKA_HOST_PORT = $KafkaPort
    REDIS_HOST_PORT = $RedisPort
    ACCOUNT_HOST_PORT = 18081
    TRANSFER_HOST_PORT = 18082
    HISTORY_HOST_PORT = 18084
}
[void](New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent))
($values.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) | Set-Content -Encoding utf8 $destination
Write-Output 'Created fake runtime env outside repository with a new retained-volume identity; passwords not displayed.'
