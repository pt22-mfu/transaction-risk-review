$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
  throw "Install JDK 17 or newer and make java available on PATH, then run this script again."
}
$info = New-Object System.Diagnostics.ProcessStartInfo
$info.FileName = "java"
$info.Arguments = "-version"
$info.UseShellExecute = $false
$info.RedirectStandardError = $true
$info.RedirectStandardOutput = $true
$process = [System.Diagnostics.Process]::Start($info)
$versionText = $process.StandardError.ReadToEnd() + $process.StandardOutput.ReadToEnd()
$process.WaitForExit()
$versionLine = ($versionText -split "`n" | Select-Object -First 1).Trim()
if ($versionLine -notmatch '"(\d+)\.') { throw "Could not determine Java version. Run java -version and check for JDK 17+." }
if ([int]$Matches[1] -lt 17) { throw "This app needs Java 17+. Keep Hadoop's Java configuration separate." }
if (-not (Test-Path "run/transaction-risk-review.jar")) { throw "The bundled JAR is missing. Follow the source build steps in README.md." }
if (-not $env:REVIEWER_TOKEN) { $env:REVIEWER_TOKEN = "local-demo-reviewer-2026" }
Write-Host "Starting RiskDesk. Open http://localhost:8080 after startup."
Write-Host "For local review access, use the token you configured; the default is local-demo-reviewer-2026."
& java -jar run/transaction-risk-review.jar
