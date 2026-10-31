param([string]$HostName = '127.0.0.1', [int]$Port = 13306, [switch]$FixtureOnly)
. "$PSScriptRoot/common.ps1"
Initialize-Fixture $HostName $Port $FixtureOnly.IsPresent
$partialRevokes = Invoke-MySql -Password $script:AdminPassword -Sql 'SELECT @@global.partial_revokes;'
if ($partialRevokes -ne '0') { throw 'This fixture requires MySQL partial_revokes=OFF for escaped database-name grants; server configuration was not changed.' }
foreach ($schema in $DbGroups.Keys) {
    $password = Get-RequiredEnvironment $PasswordVariables[$schema]
    $userName = $DbUsers[$schema]
    $existing = Invoke-MySql -Password $script:AdminPassword -Sql "SELECT COUNT(*) FROM mysql.user WHERE user='$userName';"
    if ($existing -ne '0') {
        $otherHosts = Invoke-MySql -Password $script:AdminPassword -Sql "SELECT COUNT(*) FROM mysql.user WHERE user='$userName' AND host<>'%';"
        $grants = Invoke-MySql -Password $script:AdminPassword -Sql "SHOW GRANTS FOR '$userName'@'%';"
        Assert-AllowedGrants $schema $grants ([int]$otherHosts)
    }
    [void](Invoke-MySql -Password $script:AdminPassword -Sql (New-ProvisionSql $schema $password))
    [void](Invoke-MySql -Schema $schema -User $userName -Password $password -Sql 'SELECT 1;')
    Write-Output "Provisioned $schema / $userName; existing user passwords are unchanged."
}
