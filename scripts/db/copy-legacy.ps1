param([string]$HostName = '127.0.0.1', [int]$Port = 13306, [switch]$FixtureOnly,
      [Parameter(Mandatory)][string]$OutputDirectory)
. "$PSScriptRoot/common.ps1"
Initialize-Fixture $HostName $Port $FixtureOnly.IsPresent
foreach ($schema in $DbGroups.Keys) { Assert-EmptySchema $schema }
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Choose a new output directory; backups are never overwritten.' }
[void][IO.Directory]::CreateDirectory($OutputDirectory)
$before = @(Get-AllSnapshots 'bank')
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'before.json'), ($before | ConvertTo-Json -Depth 5))
[void](Invoke-MySql -Password $script:AdminPassword -Schema bank -DumpFile (Join-Path $OutputDirectory 'bank.sql'))
foreach ($schema in $DbGroups.Keys) {
    $dump = Join-Path $OutputDirectory "$schema.sql"
    [void](Invoke-MySql -Password $script:AdminPassword -Schema bank -Tables $DbGroups[$schema] -DumpFile $dump)
    Assert-EmptySchema $schema
    [void](Invoke-MySql -Password $script:AdminPassword -Schema $schema -InputFile $dump)
    foreach ($table in $DbGroups[$schema]) {
        Assert-SameSnapshot ($before | Where-Object Table -eq $table) (Get-TableSnapshot $schema $table)
    }
}
Assert-SameSnapshot $before @(Get-AllSnapshots 'bank')
Write-Output 'Fixture copy fingerprints match; bank source unchanged. Retain backup and run verify/restore-fixture.'
