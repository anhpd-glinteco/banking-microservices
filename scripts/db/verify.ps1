param([string]$HostName = '127.0.0.1', [int]$Port = 13306, [switch]$FixtureOnly)
. "$PSScriptRoot/common.ps1"
Initialize-Fixture $HostName $Port $FixtureOnly.IsPresent
foreach ($schema in $DbGroups.Keys) {
    $userName = $DbUsers[$schema]
    $password = Get-RequiredEnvironment $PasswordVariables[$schema]
    foreach ($table in $DbGroups[$schema]) {
        Assert-SameSnapshot (Get-TableSnapshot bank $table) (Get-TableSnapshot $schema $table)
        [void](Invoke-MySql -Schema $schema -User $userName -Password $password -Sql "SELECT id FROM $table LIMIT 0;")
    }
    foreach ($other in $DbGroups.Keys | Where-Object { $_ -ne $schema }) {
        $table = $DbGroups[$other][0]
        foreach ($query in @("SELECT id FROM $other.$table LIMIT 0;", "UPDATE $other.$table SET id=id WHERE 1=0;")) {
            Invoke-MySql -Schema $schema -User $userName -Password $password -Sql $query -ExpectAccessDenied
        }
    }
    Write-Output "${schema}: fingerprints and service SELECT passed; other-service SELECT/UPDATE denied."
}
