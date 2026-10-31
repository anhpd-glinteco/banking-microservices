Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:Th4Root = Split-Path $PSScriptRoot -Parent
. "$PSScriptRoot/db/common.ps1"
function Initialize-Th4([string]$EnvFile) {
    $script:Th4EnvFile = (Resolve-Path -LiteralPath $EnvFile).Path
    $allowed = @('TH4_FIXTURE_ID', 'MYSQL_ROOT_PASSWORD', 'TH4_MYSQL_ADMIN_PASSWORD', 'ACCOUNT_DB_PASSWORD',
        'TRANSFER_DB_PASSWORD', 'HISTORY_DB_PASSWORD', 'MYSQL_HOST_PORT', 'KAFKA_HOST_PORT', 'REDIS_HOST_PORT',
        'ACCOUNT_HOST_PORT', 'TRANSFER_HOST_PORT', 'HISTORY_HOST_PORT')
    if ($script:Th4EnvFile.StartsWith($script:Th4Root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Runtime env must remain outside the repository.'
    }
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $script:Th4EnvFile) {
        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) { continue }
        if ($line -notmatch '^([A-Z][A-Z0-9_]*)=(.*)$' -or $Matches[1] -notin $allowed) { throw 'Unexpected fixture env entry.' }
        if ($values.ContainsKey($Matches[1])) { throw 'Duplicate fixture env entry.' }
        $values[$Matches[1]] = $Matches[2]
    }
    foreach ($key in $allowed) {
        if (!$values.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($values[$key])) { throw "Missing fixture env key: $key" }
    }
    $ports = @()
    foreach ($key in $allowed | Where-Object { $_ -like '*_PORT' }) {
        $value = $values[$key]
        if ($value -notmatch '^\d+$' -or [long]$value -le 3306 -or [long]$value -gt 65535) { throw 'Fixture port outside allowed range.' }
        $ports += [int]$value
    }
    if (@($ports | Select-Object -Unique).Count -ne $ports.Count) { throw 'Fixture host ports must be distinct.' }
    foreach ($key in $allowed | Where-Object { $_ -like '*PASSWORD' }) {
        if ($values[$key] -notmatch '^[a-f0-9]{48}$') { throw 'Use generated fake fixture credentials.' }
    }
    if ($values.MYSQL_ROOT_PASSWORD -cne $values.TH4_MYSQL_ADMIN_PASSWORD) { throw 'Fixture admin credentials do not match.' }
    if ($values.TH4_FIXTURE_ID -notmatch '^th4-[a-f0-9]{32}$') { throw 'Use a generated TH4 fixture identity.' }
    foreach ($key in $allowed) { [Environment]::SetEnvironmentVariable($key, $values[$key]) }
    Initialize-Fixture '127.0.0.1' ([int](Get-RequiredEnvironment 'MYSQL_HOST_PORT')) $true
    $script:ComposeChecks = [Collections.Generic.List[object]]::new()
    $script:Th4ActionLogDirectory = $null
    $script:ComposePrefix = @('compose', '--env-file', $script:Th4EnvFile, '-p', 'bank-th4-test', '-f', "$script:Th4Root/compose.th4-test.yml")
}
function Invoke-Th4Compose([string[]]$Arguments) {
    $output = & docker @script:ComposePrefix @Arguments 2>&1
    $code = $LASTEXITCODE
    $entry = [ordered]@{ arguments = $Arguments; exitCode = $code }
    if ($script:Th4ActionLogDirectory) {
        $name = "compose-$($script:ComposeChecks.Count)-$($Arguments[0]).log"
        ($output | Out-String) | Set-Content -Encoding utf8 (Join-Path $script:Th4ActionLogDirectory $name)
        $entry['log'] = $name
    }
    $script:ComposeChecks.Add([pscustomobject]$entry)
    if ($code -ne 0) { throw "TH4 Compose $($Arguments[0]) failed with exit $code; inspect project bank-th4-test." }
    return $output
}
function Assert-Th4([bool]$Condition, [string]$Message) {
    if (!$Condition) { throw $Message }
}
function Wait-Th4Health([string]$BaseUrl) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    while ($watch.Elapsed.TotalSeconds -lt 60) {
        try {
            $response = Invoke-WebRequest "$BaseUrl/actuator/health" -Headers @{ Accept = 'application/json' } -TimeoutSec 3 -SkipHttpErrorCheck
            if ($response.StatusCode -eq 200 -and ($response.Content | ConvertFrom-Json).status -eq 'UP') { return }
        } catch { }
        Start-Sleep -Seconds 1
    }
    throw "Service health did not reach UP within 60 seconds: $BaseUrl"
}
