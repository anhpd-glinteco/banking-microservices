param([string]$HostName = '127.0.0.1', [int]$Port = 13306, [switch]$FixtureOnly,
      [Parameter(Mandatory)][string]$BackupDirectory)
. "$PSScriptRoot/common.ps1"
Initialize-Fixture $HostName $Port $FixtureOnly.IsPresent
Assert-EmptySchema 'bank_restore'
$dump = Join-Path $BackupDirectory 'bank.sql'
$manifest = Join-Path $BackupDirectory 'before.json'
if (!(Test-Path -LiteralPath $dump -PathType Leaf) -or !(Test-Path -LiteralPath $manifest -PathType Leaf)) { throw 'Missing original fixture dump or fingerprint manifest.' }
[void](Invoke-MySql -Password $script:AdminPassword -Sql 'CREATE DATABASE IF NOT EXISTS bank_restore;')
[void](Invoke-MySql -Schema bank_restore -Password $script:AdminPassword -InputFile $dump)
$before = @(Get-Content -LiteralPath $manifest -Raw | ConvertFrom-Json)
Assert-SameSnapshot $before @(Get-AllSnapshots 'bank_restore')
Write-Output 'Restored into bank_restore; every fixture row matches the pre-copy manifest. Original bank untouched.'
