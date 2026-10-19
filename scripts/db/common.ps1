Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$DbGroups = [ordered]@{
    bank_account = @('users', 'accounts', 'loans')
    bank_transfer = @('transactions')
    bank_history = @('transaction_history')
}
$DbUsers = @{ bank_account = 'account_app'; bank_transfer = 'transfer_app'; bank_history = 'history_app' }
$PasswordVariables = @{ bank_account = 'ACCOUNT_DB_PASSWORD'; bank_transfer = 'TRANSFER_DB_PASSWORD'; bank_history = 'HISTORY_DB_PASSWORD' }

function Assert-Fixture([string]$HostName, [int]$Port, [bool]$Confirmed) {
    if (!$Confirmed -or $HostName -notin @('localhost', '127.0.0.1') -or $Port -le 3306 -or $Port -gt 65535) {
        throw 'FixtureOnly, a loopback host and a non-production port above 3306 are required.'
    }
}

function Assert-Schema([string]$Schema) {
    if ($Schema -notin @('bank', 'bank_account', 'bank_transfer', 'bank_history', 'bank_restore')) {
        throw 'Schema is outside the fixed fixture allowlist.'
    }
}

function Get-RequiredEnvironment([string]$Name) {
    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) { throw "Set $Name in the current process; do not pass passwords on the command line." }
    return $value
}

function Invoke-MySql {
    param([string]$Sql, [string]$Schema, [string]$User = 'root', [string]$Password,
          [string]$InputFile, [string]$DumpFile, [string[]]$Tables, [switch]$ExpectAccessDenied)
    if ($Schema) { Assert-Schema $Schema }
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $(if ($DumpFile) { 'mysqldump' } else { 'mysql' })
    $info.UseShellExecute = $false
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.StandardInputEncoding = [Text.UTF8Encoding]::new($false)
    $info.StandardOutputEncoding = [Text.UTF8Encoding]::new($false)
    $info.Environment['MYSQL_PWD'] = $Password
    foreach ($arg in @('--no-defaults', '--protocol=TCP', "--host=$script:FixtureHost", "--port=$script:FixturePort", "--user=$User", '--default-character-set=utf8mb4')) {
        $info.ArgumentList.Add($arg)
    }
    if ($DumpFile) {
        if (Test-Path -LiteralPath $DumpFile) { throw 'Refusing to overwrite an existing dump.' }
        foreach ($arg in @('--single-transaction', '--routines', '--triggers', '--set-gtid-purged=OFF', '--no-tablespaces', '--skip-add-drop-table', "--result-file=$DumpFile", $Schema)) {
            $info.ArgumentList.Add($arg)
        }
        foreach ($table in $Tables) {
            if ($table -notin @('users', 'accounts', 'loans', 'transactions', 'transaction_history')) { throw 'Table is outside the fixture allowlist.' }
            $info.ArgumentList.Add($table)
        }
    } else {
        $info.ArgumentList.Add('--batch'); $info.ArgumentList.Add('--skip-column-names'); $info.ArgumentList.Add('--raw')
        if ($Schema) { $info.ArgumentList.Add("--database=$Schema") }
    }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $info
    try {
        [void]$process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if ($InputFile) {
            $stream = [IO.File]::OpenRead($InputFile)
            try { $stream.CopyTo($process.StandardInput.BaseStream) } finally { $stream.Dispose() }
        } elseif ($Sql) { $process.StandardInput.WriteLine($Sql) }
        $process.StandardInput.Close()
        $process.WaitForExit()
        $output = $stdout.GetAwaiter().GetResult()
        $errorText = $stderr.GetAwaiter().GetResult()
        if ($ExpectAccessDenied) {
            if ($process.ExitCode -eq 0 -or $errorText -notmatch 'ERROR (1044|1142) ') { throw 'Expected a specific MySQL access-denied error; got success or another failure.' }
            return
        }
        # MySQL errors may include SQL literals, so never echo stderr containing passwords/data.
        if ($process.ExitCode -ne 0) { throw "MySQL command failed (exit $($process.ExitCode)); inspect the isolated server without logging secrets." }
        return $output.TrimEnd("`r", "`n")
    } finally { $process.Dispose() }
}

function Initialize-Fixture([string]$HostName, [int]$Port, [bool]$Confirmed) {
    Assert-Fixture $HostName $Port $Confirmed
    $script:FixtureHost = $HostName
    $script:FixturePort = $Port
    $script:AdminPassword = Get-RequiredEnvironment 'TH4_MYSQL_ADMIN_PASSWORD'
}

function Assert-EmptySchema([string]$Schema) {
    Assert-Schema $Schema
    $count = Invoke-MySql -Password $script:AdminPassword -Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$Schema';"
    if ($count -ne '0') { throw "Refusing to overwrite non-empty schema $Schema; use a fresh fixture, never DROP it." }
}

function Get-TableSnapshot([string]$Schema, [string]$Table) {
    Assert-Schema $Schema
    if ($Table -notin @('users', 'accounts', 'loans', 'transactions', 'transaction_history')) { throw 'Unknown fixture table.' }
    $columns = Invoke-MySql -Password $script:AdminPassword -Sql "SELECT column_name FROM information_schema.columns WHERE table_schema='$Schema' AND table_name='$Table' ORDER BY ordinal_position;"
    if (!$columns) { throw "Missing fixture table $Schema.$Table." }
    $names = @($columns -split '\r?\n')
    foreach ($name in $names) { if ($name -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') { throw 'Unsupported column identifier.' } }
    if ('id' -notin $names) { throw 'Fixture table must have its original id primary key.' }
    $projection = ($names | ForEach-Object { '`' + $_ + '`' }) -join ','
    # Every field, including PK, exact DECIMAL strings and raw history payload, is compared without disclosing it.
    $rows = Invoke-MySql -Schema $Schema -Password $script:AdminPassword -Sql "SELECT SHA2(CAST(JSON_ARRAY($projection) AS CHAR),256) FROM $Table ORDER BY id;"
    $count = Invoke-MySql -Schema $Schema -Password $script:AdminPassword -Sql "SELECT COUNT(*) FROM $Table;"
    $digest = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($rows)))
    [pscustomobject]@{ Table = $Table; Count = [long]$count; Columns = $names -join ','; Digest = $digest }
}

function Assert-SameSnapshot($Expected, $Actual) {
    if (($Expected | ConvertTo-Json -Compress -Depth 5) -cne ($Actual | ConvertTo-Json -Compress -Depth 5)) {
        throw 'Fixture count/PK/field fingerprint mismatch; stop writers and investigate. No source data was modified.'
    }
}

function Get-AllSnapshots([string]$Schema) {
    foreach ($table in @('users', 'accounts', 'loans', 'transactions', 'transaction_history')) { Get-TableSnapshot $Schema $table }
}

function New-ProvisionSql([string]$Schema, [string]$Password) {
    Assert-Schema $Schema
    if (!$DbGroups.Contains($Schema)) { throw 'Provisioning is restricted to service schemas.' }
    if ([string]::IsNullOrWhiteSpace($Password)) { throw 'Service password cannot be empty.' }
    $literal = $Password.Replace("'", "''")
    $userName = $DbUsers[$Schema]
    $grantSchema = $Schema.Replace('_', '\_')
    return "SET SESSION sql_mode='NO_BACKSLASH_ESCAPES'; CREATE DATABASE IF NOT EXISTS $Schema; CREATE USER IF NOT EXISTS '$userName'@'%' IDENTIFIED BY '$literal'; GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,INDEX,REFERENCES ON ``$grantSchema``.* TO '$userName'@'%';"
}

function Assert-AllowedGrants([string]$Schema, [string]$Grants, [int]$OtherHosts) {
    Assert-Schema $Schema
    if ($OtherHosts -ne 0) { throw 'Unexpected service user host variants; existing identities were not changed.' }
    $target = [regex]::Escape(('`' + $Schema.Replace('_', '\_') + '`.*'))
    foreach ($grant in ($Grants -split '\r?\n')) {
        if ($grant -match 'WITH GRANT OPTION' -or ($grant -notmatch '^GRANT USAGE ON \*\.\* TO ' -and $grant -notmatch ('^GRANT [A-Z, ]+ ON ' + $target + ' TO '))) {
            throw 'Existing service user has unexpected privileges; no grants/passwords were revoked or overwritten.'
        }
    }
}
