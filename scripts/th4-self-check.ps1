param([Parameter(Mandatory)][string]$EnvFile)
. "$PSScriptRoot/th4-common.ps1"
$parsed = 0
foreach ($file in Get-ChildItem "$PSScriptRoot/th4-*.ps1") {
    $tokens = $null; $parseErrors = $null
    [void][Management.Automation.Language.Parser]::ParseFile($file.FullName, [ref]$tokens, [ref]$parseErrors)
    Assert-Th4 ($parseErrors.Count -eq 0) "PowerShell parser rejected $($file.Name)."
    $parsed++
}
Initialize-Th4 $EnvFile
$originalIdentity = $env:TH4_FIXTURE_ID
$lines = @(Get-Content -LiteralPath $EnvFile)
$temp = Join-Path (Split-Path (Resolve-Path $EnvFile).Path -Parent) ('th4-check-' + [guid]::NewGuid().ToString('N') + '.env')
$rejected = 0
function Expect-Rejected([string[]]$Content) {
    $Content | Set-Content -Encoding utf8 $temp
    $didReject = $false
    try { Initialize-Th4 $temp } catch { $didReject = $true }
    Assert-Th4 $didReject 'Invalid fixture env was accepted.'
    Assert-Th4 ($env:TH4_FIXTURE_ID -eq $originalIdentity) 'Invalid env mutated process settings.'
    $script:rejected++
}
try {
    Expect-Rejected ($lines + 'TH4_FIXTURE_ID=duplicate')
    Expect-Rejected @($lines | Where-Object { $_ -notlike 'MYSQL_ROOT_PASSWORD=*' })
    Expect-Rejected @($lines | ForEach-Object { if ($_ -like 'TH4_FIXTURE_ID=*') { 'TH4_FIXTURE_ID=legacy' } else { $_ } })
    Expect-Rejected @($lines | ForEach-Object { if ($_ -like 'MYSQL_HOST_PORT=*') { 'MYSQL_HOST_PORT=3306' } else { $_ } })
    Expect-Rejected @($lines | ForEach-Object { if ($_ -like 'MYSQL_HOST_PORT=*') { 'MYSQL_HOST_PORT=18081' } else { $_ } })
    Expect-Rejected ($lines + 'PATH=unexpected')
    Initialize-Th4 $EnvFile
    [void](Invoke-Th4Compose @('config', '--quiet'))
} finally {
    if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp }
}
Write-Output "PASS: $parsed PowerShell parsers, $rejected rejected unsafe env cases, isolated Compose config."
