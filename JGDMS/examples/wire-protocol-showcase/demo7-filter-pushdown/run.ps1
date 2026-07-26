<#
  demo7 - "filter records inside a live space, server-side, without the class".

  Runs ONE Java process. The space (transient Outrigger) and transaction manager
  (transient Mahalo) run in the application class loader, whose classpath
  deliberately OMITS the entry class WeatherReading (asserted by a Class.forName
  that must throw). A child URLClassLoader over out\entry supplies WeatherReading
  to the client logic. The space filters a CEL value predicate over each
  candidate's own DER schema, class-free, and the orchestrator verifies it
  server-side via the operator counters and the FilterEvaluation JFR stream.

  See run.sh for the bash version. The orchestrator exits non-zero if any
  server-side or client-side claim fails, so this script's success is a real,
  automated confirmation. (The §8.3 confused-deputy step is a best-effort
  demonstration; see the README and Server.runConfusedDeputyBestEffort.)
#>
$ErrorActionPreference = "Stop"
$demo   = $PSScriptRoot
$module = Split-Path $demo -Parent
$jgdms  = Split-Path (Split-Path $module -Parent) -Parent

# Modules whose freshly-compiled target\classes are prepended (they win over the
# installed jars, so a local edit to FilterEval/FilterAdmission is exercised).
$mods = @(
    "services\outrigger\outrigger-service",
    "services\outrigger\outrigger-dl",
    "services\outrigger\outrigger-cel-authoring",
    "services\mahalo\mahalo-service",
    "services\mahalo\mahalo-dl",
    "jgdms-lib-dl",
    "jgdms-cel",
    "jgdms-der",
    "jgdms-rmi-tls",
    "service-starter",
    "jgdms-discovery-providers"
)
# Roots whose dependency:build-classpath union covers every third-party and
# sibling dependency the JVM needs.
$roots = @(
    "services/outrigger/outrigger-service",
    "services/mahalo/mahalo-service",
    "services/outrigger/outrigger-cel-authoring",
    "jgdms-rmi-tls",
    "service-starter"
)

$marker = Join-Path $jgdms "services\outrigger\outrigger-service\target\classes\org\apache\river\outrigger\FilterAdmission.class"
$cpDir  = Join-Path $demo "target"
New-Item -ItemType Directory -Force -Path $cpDir | Out-Null

if (-not (Test-Path $marker)) {
    Write-Host "Building the reactor modules (offline, tests skipped)..."
    $plist = ($mods | ForEach-Object { $_ -replace '\\','/' }) -join ","
    Push-Location $jgdms
    try {
        & mvn -o -q -pl $plist -am "-Dmaven.test.skip=true" "-Dtidy.skip=true" "-Drat.skip=true" install
        if ($LASTEXITCODE -ne 0) { throw "module build failed (exit $LASTEXITCODE)" }
    } finally { Pop-Location }
}

# Assemble the dependency classpath (union of the roots), once.
$depCp = Join-Path $cpDir "dep-cp.txt"
if (-not (Test-Path $depCp)) {
    $oneCp = Join-Path $cpDir "one-cp.txt"
    $parts = ""
    Push-Location $jgdms
    try {
        foreach ($r in $roots) {
            & mvn -o -q -pl $r dependency:build-classpath "-Dmdep.outputFile=$oneCp"
            if ($LASTEXITCODE -ne 0) { throw "build-classpath failed for $r" }
            $parts += (Get-Content $oneCp -Raw).Trim() + ";"
        }
    } finally { Pop-Location }
    Set-Content -Path $depCp -Value $parts -NoNewline
}

# Prepend the fresh module classes to the dependency classpath.
$libCp = ""
foreach ($m in $mods) { $libCp += (Join-Path $jgdms "$m\target\classes") + ";" }
$cpLib = $libCp + (Get-Content $depCp -Raw).Trim()

$outEntry = Join-Path $demo "out\entry"
$outApp   = Join-Path $demo "out\app"
Remove-Item -Recurse -Force (Join-Path $demo "out") -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $outEntry, $outApp | Out-Null

$src  = Join-Path $demo "src\au\net\zeus\jgdms\showcase\pushdown"
$ocfg = Join-Path $demo "configs\outrigger.config"
$mcfg = Join-Path $demo "configs\mahalo.config"

Write-Host "Compiling the entry+client classes (out\entry) and the orchestrator (out\app)..."
& javac -d $outEntry -cp $cpLib `
    (Join-Path $src "records\WeatherReading.java") `
    (Join-Path $src "records\NorthStationReading.java") `
    (Join-Path $src "ClientLogic.java")
if ($LASTEXITCODE -ne 0) { throw "entry+client classes did not compile" }
& javac -d $outApp -cp $cpLib `
    (Join-Path $src "Rendezvous.java") `
    (Join-Path $src "Server.java")
if ($LASTEXITCODE -ne 0) { throw "orchestrator did not compile" }

$rmi = "-Djava.rmi.server.RMIClassLoaderSpi=default"

Write-Host ""
Write-Host "Running demo7 (one JVM; the space's app loader OMITS WeatherReading, a child"
Write-Host "loader over out\entry supplies it to the client logic)..."
# The orchestrator's classpath EXCLUDES out\entry -> WeatherReading absent from
# the space's loader; out\entry is passed as an argument for the child loader.
& java $rmi -cp "$cpLib;$outApp" au.net.zeus.jgdms.showcase.pushdown.Server $outEntry $ocfg $mcfg
$exitCode = $LASTEXITCODE

Write-Host ""
Write-Host "exit code = $exitCode"
if ($exitCode -eq 0) {
    Write-Host "RESULT: demo7 succeeded (exit code 0)."
} else {
    Write-Host "RESULT: demo7 FAILED (exit code $exitCode)."
    exit $exitCode
}
