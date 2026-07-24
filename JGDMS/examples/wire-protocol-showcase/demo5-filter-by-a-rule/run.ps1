<#
  Runs the "filter records by a written rule, without the class" demonstration.

  It compiles two small record classes into one folder, then runs two separate Java
  processes:
    * a producer that HAS the record classes, and writes several weather readings and
      one survey observation to the wire, and
    * a reader that does NOT have the record classes on its classpath, yet filters the
      readings by a written rule, refuses rules that could misbehave at registration,
      and shows the same rule gives the same answer every time.

  The reader program checks every claim and exits non-zero if any fails, so this
  script's success is a real, automated confirmation.
#>
$ErrorActionPreference = "Stop"
$demo   = $PSScriptRoot
$module = Split-Path $demo -Parent
$jgdms  = Split-Path (Split-Path $module -Parent) -Parent
$cel    = Join-Path $jgdms "jgdms-cel"
$cpFile = Join-Path $module "target\cel-cp.txt"
$marker = Join-Path $cel "target\classes\au\net\zeus\jgdms\cel\verifier\CelVerifier.class"

# Build the rule-language library and its wire-format dependencies, if not built yet.
if (-not (Test-Path $marker) -or -not (Test-Path $cpFile)) {
    Write-Host "Building the rule-language library and its wire-format dependencies (offline)..."
    Push-Location $jgdms
    try {
        # Each -D argument is quoted: Windows PowerShell 5.1 otherwise splits an unquoted
        # token like -Dtidy.skip=true into "-Dtidy" and ".skip=true", which Maven rejects.
        # -am builds jgdms-cel plus the wire-format modules it depends on; -Dmaven.test.skip=true
        # skips test compile+run so the offline build is not blocked by a test-scoped dependency.
        & mvn -o -q -pl jgdms-cel -am "-Dmaven.test.skip=true" "-Dtidy.skip=true" "-Drat.skip=true" `
            install dependency:build-classpath "-Dmdep.outputFile=$cpFile"
        if ($LASTEXITCODE -ne 0) { throw "library build failed (exit $LASTEXITCODE)" }
    } finally { Pop-Location }
}

$cpLib = (Join-Path $cel "target\classes") + ";" + (Get-Content $cpFile -Raw).Trim()
$outEntry = Join-Path $demo "out\entry"
$outApp   = Join-Path $demo "out\app"
$wireFile = Join-Path $demo "out\records.wire"
Remove-Item -Recurse -Force (Join-Path $demo "out") -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $outEntry, $outApp | Out-Null

$src = Join-Path $demo "src\au\net\zeus\jgdms\showcase\rule"

Write-Host "Compiling the record classes and the two programs..."
& javac -d $outEntry -cp $cpLib `
    (Join-Path $src "records\WeatherReading.java") `
    (Join-Path $src "records\SurveyObservation.java")
if ($LASTEXITCODE -ne 0) { throw "record classes did not compile" }
& javac -d $outApp -cp "$cpLib;$outEntry" `
    (Join-Path $src "RuleWire.java") `
    (Join-Path $src "Blocks.java") `
    (Join-Path $src "Producer.java") `
    (Join-Path $src "Reader.java")
if ($LASTEXITCODE -ne 0) { throw "programs did not compile" }

$rmi = "-Djava.rmi.server.RMIClassLoaderSpi=default"

Write-Host ""
Write-Host "==================== PRODUCER (record classes ARE present) ===================="
& java $rmi -cp "$cpLib;$outApp;$outEntry" au.net.zeus.jgdms.showcase.rule.Producer $wireFile
if ($LASTEXITCODE -ne 0) { throw "producer failed" }

Write-Host ""
Write-Host "============ READER (record classes are NOT on the classpath) ================="
Write-Host "reader classpath deliberately EXCLUDES the folder with the record classes."
& java $rmi -cp "$cpLib;$outApp" au.net.zeus.jgdms.showcase.rule.Reader $wireFile
$readerExit = $LASTEXITCODE

Write-Host ""
if ($readerExit -eq 0) {
    Write-Host "RESULT: demonstration succeeded (reader exit code 0)."
} else {
    Write-Host "RESULT: demonstration FAILED (reader exit code $readerExit)."
    exit $readerExit
}
