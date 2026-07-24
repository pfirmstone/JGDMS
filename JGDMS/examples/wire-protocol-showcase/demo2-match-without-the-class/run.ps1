<#
  Runs the "match a template without ever loading the class" demonstration.

  It compiles a small record class into one folder, then runs two separate Java
  processes:
    * a producer that HAS the record class, and writes three records to the wire, and
    * a matching server that does NOT have the record class on its classpath, yet
      matches a template against the stored record by comparing bytes.

  The server program checks every claim and exits non-zero if any fails, so this
  script's success is a real, automated confirmation.
#>
$ErrorActionPreference = "Stop"
$demo   = $PSScriptRoot
$module = Split-Path $demo -Parent
$jgdms  = Split-Path (Split-Path $module -Parent) -Parent
$outriggerDl = Join-Path $jgdms "services\outrigger\outrigger-dl"
$cpFile = Join-Path $module "target\outrigger-dl-cp.txt"
$entryClass = Join-Path $outriggerDl "target\classes\org\apache\river\outrigger\proxy\EntryRep.class"

# Build the shared-space library classes we depend on, if they are not built yet.
if (-not (Test-Path $entryClass) -or -not (Test-Path $cpFile)) {
    Write-Host "Building the shared-space library (one module, offline)..."
    Push-Location $jgdms
    try {
        # Each -D argument is quoted: Windows PowerShell 5.1 otherwise splits an
        # unquoted token like -Dtidy.skip=true into "-Dtidy" and ".skip=true",
        # which Maven rejects as an unknown lifecycle phase. -Dmaven.test.skip=true
        # skips test COMPILE and run (unlike -DskipTests, which still compiles test
        # sources); the demo needs only main classes, keeping the offline build from
        # being blocked by any test-scoped dependency.
        & mvn -o -q -pl services/outrigger/outrigger-dl "-Dmaven.test.skip=true" "-Dtidy.skip=true" "-Drat.skip=true" `
            package dependency:build-classpath "-Dmdep.outputFile=$cpFile"
        if ($LASTEXITCODE -ne 0) { throw "library build failed (exit $LASTEXITCODE)" }
    } finally { Pop-Location }
}

$cpLib = (Join-Path $outriggerDl "target\classes") + ";" + (Get-Content $cpFile -Raw).Trim()
$outEntry = Join-Path $demo "out\entry"
$outApp   = Join-Path $demo "out\app"
$wireFile = Join-Path $demo "out\records.wire"
Remove-Item -Recurse -Force (Join-Path $demo "out") -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $outEntry, $outApp | Out-Null

Write-Host "Compiling the record class and the two programs..."
& javac -d $outEntry -cp $cpLib (Join-Path $demo "src\au\net\zeus\jgdms\showcase\entry\SensorReadingEntry.java")
if ($LASTEXITCODE -ne 0) { throw "record class did not compile" }
& javac -d $outApp -cp "$cpLib;$outEntry" `
    (Join-Path $demo "src\au\net\zeus\jgdms\showcase\match\Producer.java") `
    (Join-Path $demo "src\au\net\zeus\jgdms\showcase\match\Server.java")
if ($LASTEXITCODE -ne 0) { throw "programs did not compile" }

$rmi = "-Djava.rmi.server.RMIClassLoaderSpi=default"

Write-Host ""
Write-Host "==================== PRODUCER (record class IS present) ===================="
& java $rmi -cp "$cpLib;$outApp;$outEntry" au.net.zeus.jgdms.showcase.match.Producer $wireFile
if ($LASTEXITCODE -ne 0) { throw "producer failed" }

Write-Host ""
Write-Host "============ MATCHING SERVER (record class is NOT on the classpath) ========"
Write-Host "server classpath deliberately EXCLUDES the folder with the record class."
Write-Host ""
& java $rmi -cp "$cpLib;$outApp" au.net.zeus.jgdms.showcase.match.Server $wireFile
$serverExit = $LASTEXITCODE

Write-Host ""
if ($serverExit -eq 0) {
    Write-Host "RESULT: demonstration succeeded (server exit code 0)."
} else {
    Write-Host "RESULT: demonstration FAILED (server exit code $serverExit)."
    exit $serverExit
}
