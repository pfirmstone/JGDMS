<#
  Builds the showcase and runs the two library-only demonstrations, then runs the
  automated checks. For the third demonstration (matching without the class), see
  demo2-match-without-the-class\run.ps1.
#>
$ErrorActionPreference = "Stop"
$module = $PSScriptRoot
Push-Location $module
try {
    Write-Host "Building the showcase (resolves two libraries from the local repository)..."
    & mvn -q package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "build failed" }

    $cp = "target\classes;target\lib\*"

    Write-Host ""
    Write-Host "########################################################################"
    Write-Host "# Demonstration 1: Same object, same bytes -- everywhere"
    Write-Host "########################################################################"
    & java -cp $cp au.net.zeus.jgdms.showcase.demo.SameObjectSameBytesDemo

    Write-Host ""
    Write-Host "(Running it a second time in a fresh process -- the checksum is identical,"
    Write-Host " because the bytes depend only on the value, not on the run.)"
    $run1 = & java -cp $cp au.net.zeus.jgdms.showcase.demo.SameObjectSameBytesDemo | Select-String "checksum \(SHA-256\) of pass 1"
    Write-Host "  fresh process: $run1"

    Write-Host ""
    Write-Host "########################################################################"
    Write-Host "# Demonstration 3: The shape description travels once"
    Write-Host "########################################################################"
    & java -cp $cp au.net.zeus.jgdms.showcase.demo.SchemaSentOnceDemo

    Write-Host ""
    Write-Host "########################################################################"
    Write-Host "# Automated checks (a build server can run these)"
    Write-Host "########################################################################"
    & mvn -q test "-DredirectTestOutputToFile=true"
    if ($LASTEXITCODE -ne 0) { throw "tests failed" }
    Write-Host "All automated checks passed."
} finally { Pop-Location }
