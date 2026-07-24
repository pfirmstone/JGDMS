<#
  Builds the showcase and runs the library-only demonstrations (1, 3 and 4), then runs
  the automated checks. For the "match without the class" demonstration, which needs two
  separate processes with different classpaths, see demo2-match-without-the-class\run.ps1.
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
    Write-Host "# Demonstration 4: Feed it garbage, it stops politely"
    Write-Host "########################################################################"
    # A deliberately small memory ceiling (128 MB) makes the point concrete: the
    # expansion bomb would want gigabytes, yet the whole demonstration runs inside it
    # without ever running out of memory.
    & java -Xmx128m -cp $cp au.net.zeus.jgdms.showcase.demo.HostileInputDemo
    if ($LASTEXITCODE -ne 0) { throw "hostile-input demonstration reported a failure" }

    Write-Host ""
    Write-Host "########################################################################"
    Write-Host "# Automated checks (a build server can run these)"
    Write-Host "########################################################################"
    & mvn -q test "-DredirectTestOutputToFile=true"
    if ($LASTEXITCODE -ne 0) { throw "tests failed" }
    Write-Host "All automated checks passed."
} finally { Pop-Location }
