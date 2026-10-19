param(
    [Parameter(Mandatory)][string]$EnvFile,
    [Parameter(Mandatory)][string]$OutputDirectory
)
. "$PSScriptRoot/th4-common.ps1"
Initialize-Th4 $EnvFile
[void](New-Item -ItemType Directory -Force -Path $OutputDirectory)
$OutputDirectory = (Resolve-Path $OutputDirectory).Path
if (Test-Path (Join-Path $OutputDirectory 'e2e-summary.json')) { throw 'Choose a new evidence directory; prior evidence is retained.' }
$script:Th4ActionLogDirectory = $OutputDirectory
$checks = [Collections.Generic.List[object]]::new()
$account = 'http://localhost:' + (Get-RequiredEnvironment 'ACCOUNT_HOST_PORT')
$transfer = 'http://localhost:' + (Get-RequiredEnvironment 'TRANSFER_HOST_PORT')
$history = 'http://localhost:' + (Get-RequiredEnvironment 'HISTORY_HOST_PORT')
function Record([string]$Name, $Evidence) { $checks.Add([pscustomobject]@{ name = $Name; evidence = $Evidence }) }
function Request([string]$Method, [string]$Url, $Body, [int]$Expected) {
    $options = @{ Method = $Method; Uri = $Url; TimeoutSec = 10; SkipHttpErrorCheck = $true; ContentType = 'application/json'; Headers = @{ Accept = 'application/json' } }
    if ($null -ne $Body) { $options.Body = $Body | ConvertTo-Json -Depth 5 -Compress }
    $response = Invoke-WebRequest @options
    Assert-Th4 ($response.StatusCode -eq $Expected) "$Method $Url expected $Expected, got $($response.StatusCode)."
    $parsed = if ($response.Content) { $response.Content | ConvertFrom-Json } else { $null }
    $evidence = @{ status = [int]$response.StatusCode }
    if ($Expected -ge 400) {
        Assert-Th4 ($parsed.status -eq $Expected -and $parsed.path -and $parsed.error -and $parsed.message) 'ErrorResponse shape mismatch.'
        $evidence.error = @{ status = $parsed.status; error = $parsed.error; message = $parsed.message; path = $parsed.path }
    }
    Record "$Method $Url" $evidence
    return $parsed
}
function Sql([string]$Schema, [string]$Query) {
    Invoke-MySql -Schema $Schema -User $DbUsers[$Schema] -Password (Get-RequiredEnvironment $PasswordVariables[$Schema]) -Sql $Query
}
function TransferCount { [long](Sql 'bank_transfer' 'SELECT COUNT(*) FROM transactions;') }
function ErrorRequest([string]$Name, $Body, [int]$Status) {
    $before = TransferCount
    [void](Request 'POST' "$transfer/api/transfers" $Body $Status)
    $after = TransferCount
    Assert-Th4 ($before -eq $after) "$Name inserted a transaction."
    Record $Name @{ httpStatus = $Status; transactionCountBefore = $before; transactionCountAfter = $after }
}
$exitCode = 1
try {
    [void](Invoke-Th4Compose @('config', '--quiet'))
    [void](Invoke-Th4Compose @('up', '-d', '--wait', '--wait-timeout', '180', 'mysql', 'kafka', 'redis'))
    & "$PSScriptRoot/db/provision.ps1" -HostName '127.0.0.1' -Port $script:FixturePort -FixtureOnly | Out-Null
    [void](Invoke-Th4Compose @('up', '-d', '--build', '--wait', '--wait-timeout', '180'))
    foreach ($url in @($account, $transfer, $history)) { Wait-Th4Health $url }
    foreach ($schema in $DbGroups.Keys) {
        foreach ($table in $DbGroups[$schema]) {
            Assert-Th4 ((Sql $schema "SELECT COUNT(*) FROM $table;") -eq '0') 'Fixture is not empty; use th4-init with a new env/volume identity, never reset data.'
        }
    }
    Record 'isolated-project' @{ project = 'bank-th4-test'; volumeIdentity = $env:TH4_FIXTURE_ID; ports = @{ mysql = $script:FixturePort; account = $env:ACCOUNT_HOST_PORT; transfer = $env:TRANSFER_HOST_PORT; history = $env:HISTORY_HOST_PORT } }
    $profiles = @{}
    foreach ($name in @('alice', 'bob', 'empty')) {
        $body = @{ username = "th4_$name"; email = "th4_$name@example.invalid"; password = [guid]::NewGuid().ToString('N'); fullName = "TH4 $name" }
        $profile = Request 'POST' "$account/api/accounts/users" $body 200
        Assert-Th4 ($null -eq $profile.PSObject.Properties['password']) 'Profile response exposed password.'
        $profiles[$name] = $profile
    }
    $alice = Request 'POST' "$account/api/accounts" @{ userId = $profiles.alice.id; accountNumber = 'TH4-A'; accountName = 'TH4 Alice'; balance = 50000000 } 200
    $bob = Request 'POST' "$account/api/accounts" @{ userId = $profiles.bob.id; accountNumber = 'TH4-B'; accountName = 'TH4 Bob'; balance = 1000000 } 200
    $emptyId = $profiles.empty.id
    [void](Request 'GET' "$account/api/accounts/users/$emptyId" $null 200)
    $updated = Request 'PATCH' "$account/api/accounts/users/$emptyId" @{ fullName = 'TH4 updated'; phone = '000-fixture' } 200
    Assert-Th4 ($updated.fullName -eq 'TH4 updated') 'Profile PATCH was not persisted.'
    $page = Request 'GET' "$account/api/accounts/users?page=0&size=1&username=th4_empty&email=th4_empty@example.invalid&sort=createdAt,desc" $null 200
    Assert-Th4 ($page.totalElements -eq 1 -and $page.content.Count -eq 1 -and $page.content[0].id -eq $emptyId) 'Exact AND filter/page mismatch.'
    $last = Request 'GET' "$account/api/accounts/users?page=1&size=1&username=th4_empty&sort=id,asc" $null 200
    Assert-Th4 ($last.totalElements -eq 1 -and $last.content.Count -eq 0) 'Empty page total mismatch.'
    [void](Request 'GET' "$account/api/accounts/users?size=101" $null 400)
    [void](Request 'DELETE' "$account/api/accounts/users/$($profiles.alice.id)" $null 409)
    [void](Request 'DELETE' "$account/api/accounts/users/$emptyId" $null 204)
    [void](Request 'GET' "$account/api/accounts/users/$emptyId" $null 404)
    Record 'profile-crud-pagination' @{ deletedUserId = $emptyId; filteredTotal = $page.totalElements; emptyPageTotal = $last.totalElements; aliceUserRetained = (Sql 'bank_account' "SELECT COUNT(*) FROM users WHERE id=$($profiles.alice.id);") }
    $loan = Request 'POST' "$account/api/loans/apply" @{ accountId = $alice.id; amount = 10000000; termMonths = 12; purpose = 'TH4 fixture' } 200
    $approved = Request 'POST' "$account/api/loans/$($loan.id)/approve" $null 200
    Assert-Th4 ($approved.status -eq 'ACTIVE') 'Loan approval did not reach ACTIVE.'
    $beforeAlice = [decimal](Sql 'bank_account' "SELECT balance FROM accounts WHERE id=$($alice.id);")
    $beforeBob = [decimal](Sql 'bank_account' "SELECT balance FROM accounts WHERE id=$($bob.id);")
    Assert-Th4 ($beforeAlice -eq 60000000 -and $beforeBob -eq 1000000) 'Loan/account SQL baseline mismatch.'
    $accepted = Request 'POST' "$transfer/api/transfers" @{ fromAccountNumber = 'TH4-A'; toAccountNumber = 'TH4-B'; amount = 1000; description = 'TH4 Kafka fixture' } 200
    Assert-Th4 ($accepted.status -eq 'PROCESSING') 'REST did not return PROCESSING.'
    Assert-Th4 ($accepted.id -match '^[a-fA-F0-9-]{36}$') 'Unexpected transaction identifier.'
    $transactionId = $accepted.id
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $completed = $false
    while ($watch.Elapsed.TotalSeconds -lt 60) {
        $state = Request 'GET' "$transfer/api/transfers/$transactionId/status" $null 200
        $afterAlice = [decimal](Sql 'bank_account' "SELECT balance FROM accounts WHERE id=$($alice.id);")
        $afterBob = [decimal](Sql 'bank_account' "SELECT balance FROM accounts WHERE id=$($bob.id);")
        $aliceHistory = @(Request 'GET' "$history/api/history/number/TH4-A" $null 200)
        $bobHistory = @(Request 'GET' "$history/api/history/number/TH4-B" $null 200)
        $aliceCompleted = @($aliceHistory | Where-Object { $_.eventType -eq 'TRANSFER_COMPLETED' }).Count -gt 0
        $bobCompleted = @($bobHistory | Where-Object { $_.eventType -eq 'TRANSFER_COMPLETED' }).Count -gt 0
        if ($aliceCompleted -and $bobCompleted -and $state.status -eq 'SUCCESS' -and $afterAlice -eq ($beforeAlice - 1000) -and $afterBob -eq ($beforeBob + 1000) -and $aliceHistory.Count -gt 0 -and $bobHistory.Count -gt 0) {
            $completed = $true; break
        }
        Start-Sleep -Seconds 1
    }
    Assert-Th4 $completed 'Kafka completion/balances/history did not converge within 60 seconds.'
    foreach ($rows in @($aliceHistory, $bobHistory)) {
        foreach ($row in $rows) { Assert-Th4 ($row.transactionId -eq $transactionId) 'History leaked an unrelated transaction.' }
    }
    $unrelated = @(Request 'GET' "$history/api/history/number/TH4-UNRELATED" $null 200)
    Assert-Th4 ($unrelated.Count -eq 0) 'Unrelated account received history.'
    $legacy = @(Request 'GET' "$history/api/history/$($alice.id)" $null 200)
    Assert-Th4 ($legacy.Count -eq $aliceHistory.Count) 'Legacy ID history mapping mismatch.'
    $stored = Sql 'bank_transfer' "SELECT id,amount,status FROM transactions WHERE id='$transactionId';"
    Record 'rest-accepted-kafka-completed' @{ transactionId = $transactionId; restStatus = $accepted.status; eventualStatus = $state.status; aliceBefore = $beforeAlice; aliceAfter = $afterAlice; bobBefore = $beforeBob; bobAfter = $afterBob; aliceHistoryRows = $aliceHistory.Count; bobHistoryRows = $bobHistory.Count; transferSql = $stored }
    ErrorRequest 'missing-account-404' @{ fromAccountNumber = 'TH4-A'; toAccountNumber = 'TH4-MISSING'; amount = 1002 } 404
    ErrorRequest 'invalid-amount-400' @{ fromAccountNumber = 'TH4-A'; toAccountNumber = 'TH4-B'; amount = 0 } 400
    ErrorRequest 'null-request-400' $null 400
    ErrorRequest 'null-amount-400' @{ fromAccountNumber = 'TH4-A'; toAccountNumber = 'TH4-B'; amount = $null } 400
    ErrorRequest 'blank-account-400' @{ fromAccountNumber = ' '; toAccountNumber = 'TH4-B'; amount = 1003 } 400
    try {
        [void](Invoke-Th4Compose @('stop', 'account-service'))
        ErrorRequest 'account-unavailable-503-unique-request' @{ fromAccountNumber = 'TH4-A'; toAccountNumber = 'TH4-B'; amount = 1001 } 503
    } finally {
        [void](Invoke-Th4Compose @('start', 'account-service'))
        Wait-Th4Health $account
    }
    $denied = 0
    foreach ($schema in $DbGroups.Keys) {
        foreach ($table in $DbGroups[$schema]) { [void](Sql $schema "SELECT id FROM $table LIMIT 0;") }
        foreach ($other in $DbGroups.Keys | Where-Object { $_ -ne $schema }) {
            $table = $DbGroups[$other][0]
            foreach ($query in @("SELECT id FROM $other.$table LIMIT 0;", "UPDATE $other.$table SET id=id WHERE 1=0;")) {
                Invoke-MySql -Schema $schema -User $DbUsers[$schema] -Password (Get-RequiredEnvironment $PasswordVariables[$schema]) -Sql $query -ExpectAccessDenied
                $denied++
            }
        }
    }
    $crossFk = Invoke-MySql -Password $script:AdminPassword -Sql "SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema IN ('bank_account','bank_transfer','bank_history') AND unique_constraint_schema<>constraint_schema;"
    Assert-Th4 ($crossFk -eq '0') 'Cross-schema foreign key found.'
    Record 'database-ownership' @{ ownTablesReadable = 5; crossSchemaSelectUpdateDenied = $denied; crossSchemaForeignKeys = [int]$crossFk }
    $sqlEvidence = @(
        Sql 'bank_account' 'SELECT id,account_number,balance,user_id FROM accounts ORDER BY id;'
        Sql 'bank_account' 'SELECT id,account_id,loan_amount,remaining_amount,status FROM loans ORDER BY id;'
        Sql 'bank_transfer' 'SELECT id,from_account_number,to_account_number,amount,status FROM transactions ORDER BY id;'
        Sql 'bank_history' 'SELECT id,transaction_id,event_type,from_account_number,to_account_number FROM transaction_history ORDER BY id;'
    )
    $sqlEvidence | Set-Content -Encoding utf8 (Join-Path $OutputDirectory 'safe-sql.txt')
    $metadata = Invoke-MySql -Password $script:AdminPassword -Sql "SELECT table_schema,table_name,column_name,column_type,is_nullable,column_key FROM information_schema.columns WHERE table_schema IN ('bank_account','bank_transfer','bank_history') ORDER BY table_schema,table_name,ordinal_position; SELECT constraint_schema,table_name,column_name,referenced_table_schema,referenced_table_name,referenced_column_name FROM information_schema.key_column_usage WHERE constraint_schema IN ('bank_account','bank_transfer','bank_history') ORDER BY constraint_schema,table_name,column_name;"
    $metadata | Set-Content -Encoding utf8 (Join-Path $OutputDirectory 'schema-metadata.txt')
    Record 'timeout-test-reference' @{ module = 'transfer-service'; test = 'AccountPreflightTest.readTimeoutIsSafe503'; evidence = '../build/transfer-service-verify.log'; behavior = 'bounded HTTP stub; dependency timeout returns safe503 without transaction insert' }
    $exitCode = 0
} finally {
    $script:ComposeChecks | ConvertTo-Json -Depth 5 | Set-Content -Encoding utf8 (Join-Path $OutputDirectory 'compose-commands.json')
    [pscustomobject]@{ exitCode = $exitCode; project = 'bank-th4-test'; checks = $checks } | ConvertTo-Json -Depth 9 | Set-Content -Encoding utf8 (Join-Path $OutputDirectory 'e2e-summary.json')
}
Write-Output 'TH4 E2E PASS: REST, SQL, Kafka balances/history, CRUD, errors and schema ownership.'
