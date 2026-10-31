. "$PSScriptRoot/common.ps1"
$checks = 0
function Assert-Throws([scriptblock]$Action, [string]$Expected) {
    $caught = $false
    try { & $Action } catch { if ($_.Exception.Message -notlike "*$Expected*") { throw }; $caught = $true }
    if (!$caught) { throw "Expected rejection: $Expected" }
    $script:checks++
}
Assert-Throws { Assert-Fixture localhost 13306 $false } 'FixtureOnly'
Assert-Throws { Assert-Fixture localhost 3306 $true } 'FixtureOnly'
Assert-Throws { Assert-Fixture remote.example 13306 $true } 'FixtureOnly'
Assert-Fixture 127.0.0.1 13306 $true
$checks++
Assert-Throws { New-ProvisionSql 'bank; DROP DATABASE bank' 'fake' } 'allowlist'
Assert-Throws { New-ProvisionSql bank 'fake' } 'restricted'
Assert-Throws { New-ProvisionSql bank_account '' } 'empty'
$sql = New-ProvisionSql bank_account "fixture'quote\slash"
if ($sql -notmatch "IDENTIFIED BY 'fixture''quote\\slash'" -or !$sql.Contains('ON `bank\_account`.*') -or $sql -match 'REVOKE|DROP|ALTER USER') { throw 'Provision SQL escaping/scope changed.' }
$checks++
$ownGrant = 'GRANT SELECT, INSERT ON `bank\_account`.* TO `account_app`@`%`'
Assert-AllowedGrants bank_account $ownGrant 0
$checks++
Assert-Throws { Assert-AllowedGrants bank_account 'GRANT SELECT ON *.* TO `account_app`@`%`' 0 } 'unexpected privileges'
Assert-Throws { Assert-AllowedGrants bank_account 'GRANT SELECT ON `bank\_history`.* TO `account_app`@`%`' 0 } 'unexpected privileges'
Assert-Throws { Assert-AllowedGrants bank_account ($ownGrant + ' WITH GRANT OPTION') 0 } 'unexpected privileges'
Assert-Throws { Assert-AllowedGrants bank_account $ownGrant 1 } 'host variants'
$script:AdminPassword = 'fixture-not-used'
# Exercise the actual empty-destination guard with a fake CLI result, before any write could run.
function Invoke-MySql { param($Sql, $Password) return $script:TableCount }
$script:TableCount = '1'
Assert-Throws { Assert-EmptySchema bank_account } 'Refusing to overwrite'
Assert-Throws { Assert-EmptySchema 'bank_account;DROP' } 'allowlist'
$script:TableCount = '0'
Assert-EmptySchema bank_account
$checks++
$row = [pscustomobject]@{ Table='accounts'; Count=1; Columns='id,balance'; Digest='original' }
Assert-SameSnapshot $row $row
$checks++
Assert-Throws { Assert-SameSnapshot $row ([pscustomobject]@{ Table='accounts'; Count=1; Columns='id,balance'; Digest='changed' }) } 'fingerprint mismatch'
foreach ($file in Get-ChildItem $PSScriptRoot -Filter '*.ps1') {
    $tokens = $null; $errors = $null
    [void][Management.Automation.Language.Parser]::ParseFile($file.FullName, [ref]$tokens, [ref]$errors)
    if ($errors.Count) { throw ($errors | Out-String) }
    $checks++
}
Write-Output "PASS: $checks fixture/name/SQL/non-overwrite/fingerprint/parser checks. No MySQL connection was made."
