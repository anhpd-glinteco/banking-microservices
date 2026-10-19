param(
    [Parameter(Mandatory)][string]$OutputDirectory,
    [string]$MavenRepository
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
[void](New-Item -ItemType Directory -Force -Path $OutputDirectory)
$OutputDirectory = (Resolve-Path $OutputDirectory).Path
$results = [Collections.Generic.List[object]]::new()
function Read-TestSummary([string]$Module, [string]$Kind) {
    $reports = @(Get-ChildItem "$root/$Module/target/$Kind-reports/TEST-*.xml" -ErrorAction SilentlyContinue)
    $totals = [ordered]@{ tests = 0; failures = 0; errors = 0; skipped = 0 }
    foreach ($report in $reports) {
        $suite = ([xml](Get-Content -Raw $report.FullName)).testsuite
        foreach ($key in @($totals.Keys)) { $totals[$key] += [int]$suite.GetAttribute($key) }
    }
    return [pscustomobject]@{ reports = $reports.Count; counts = $totals; buildOnly = ($reports.Count -eq 0) }
}
function Invoke-Check([string]$Name, [string]$Executable, [string[]]$Arguments, [string]$Module, [string]$Kind = 'surefire') {
    $log = Join-Path $OutputDirectory "$Name.log"
    & $Executable @Arguments *> $log
    $code = $LASTEXITCODE
    $tests = if ($Module) { Read-TestSummary $Module $Kind } else { $null }
    $results.Add([pscustomobject]@{ name = $Name; command = "$Executable $($Arguments -join ' ')"; exitCode = $code; tests = $tests; log = "$Name.log" })
    $results | ConvertTo-Json -Depth 7 | Set-Content -Encoding utf8 (Join-Path $OutputDirectory 'build-summary.json')
    if ($code -ne 0) { throw "$Name failed with exit $code; see $log" }
    if ($Kind -eq 'failsafe' -and ($tests.counts.tests -le 0 -or $tests.counts.failures -ne 0 -or $tests.counts.errors -ne 0 -or $tests.counts.skipped -ne 0)) {
        throw 'MySQL integration requires executed tests and zero failures/errors/skips.'
    }
    Write-Output "$Name PASS (exit $code)"
}
Push-Location $root
try {
    $maven = @('-B', '-ntp')
    if ($MavenRepository) { $maven += "-Dmaven.repo.local=$MavenRepository" }
    Invoke-Check 'common-install' 'mvn' ($maven + @('-f', 'bank-common/pom.xml', 'install')) 'bank-common'
    foreach ($module in @('account-service', 'transfer-service', 'transaction-history-service', 'notification-service', 'api-gateway', 'discovery-service')) {
        Invoke-Check "$module-verify" 'mvn' ($maven + @('-f', "$module/pom.xml", 'verify')) $module
    }
    Invoke-Check 'account-mysql-it' 'mvn' ($maven + @('-f', 'account-service/pom.xml', '-Pmysql-it', 'verify')) 'account-service' 'failsafe'
    Invoke-Check 'frontend-ci' 'npm' @('ci', '--prefix', 'frontend') ''
    Invoke-Check 'frontend-lint' 'npm' @('run', 'lint', '--prefix', 'frontend') ''
    Invoke-Check 'frontend-build' 'npm' @('run', 'build', '--prefix', 'frontend') ''
} finally { Pop-Location }
