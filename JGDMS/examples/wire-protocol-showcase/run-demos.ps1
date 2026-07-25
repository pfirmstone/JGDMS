<#
  Single-command parent: builds the JGDMS modules the showcase depends on from the
  CURRENT source tree, then runs all six wire-protocol-showcase demonstrations in
  sequence, then the automated checks, and prints a pass/fail summary.

  Demonstrations:
    1. Same object, same bytes -- everywhere         (in-process, main module)
    2. Match a template without ever loading the class (two processes, demo2 subdir)
    3. The shape description travels once            (in-process, main module)
    4. Feed it garbage, it stops politely             (in-process, main module)
    5. Filter records by a written rule, without the class (two processes, demo5 subdir)
    6. Two different collection classes, one value, one encoding (in-process, main module)

  Demonstration 4 (the cumulative-bomb refusal) only proves anything if the
  jgdms-der it runs against is built from the CURRENT source tree: an installed
  jgdms-der from before the bomb fix would silently fail to demonstrate the
  refusal. Step 0 below builds and installs jgdms-der (and every other module the
  showcase and its sub-demos depend on -- jgdms-platform, jgdms-lib-dl, jgdms-cel,
  ...) from source before any demo runs, so every demo -- not just 4 -- runs
  against current code, never a stale ~/.m2 artifact.

  demo2 and demo5 each have their own run.ps1 (needed because they launch two
  separate JVMs with different classpaths -- see those scripts for why). This
  parent script invokes them as a fresh child `powershell.exe` process rather
  than dot-sourcing or call-operator-invoking them in-process: both scripts call
  `exit <code>` on failure, and in Windows PowerShell 5.1 an `exit` executed by a
  script that was `&`-invoked in the SAME session terminates the whole session --
  including this parent script and every demo still queued behind it. Running
  each as its own process contains that `exit` to the child and lets us read its
  real exit code back via $LASTEXITCODE.
#>
$ErrorActionPreference = "Stop"
$showcase = $PSScriptRoot
$jgdms = Split-Path (Split-Path $showcase -Parent) -Parent

$results = @()
function Add-Result([string]$name, [bool]$pass) {
    $script:results += [PSCustomObject]@{ Name = $name; Pass = $pass }
}

function Write-Header([string]$text) {
    Write-Host ""
    Write-Host "########################################################################"
    Write-Host "# $text"
    Write-Host "########################################################################"
}

# ---------------------------------------------------------------------------
# Step 0: build and install, from the current source tree, every module the
# showcase (jgdms-der, jgdms-platform) and its sub-demos (jgdms-lib-dl for
# demo2, jgdms-cel for demo5) depend on. One combined reactor build covers
# both -am closures; -Dmaven.test.skip=true keeps it to main-code compile+install.
# ---------------------------------------------------------------------------

# `clean install`, not plain `install`: a *failed* compile leaves whatever
# .class files an earlier successful build already produced sitting in
# target/classes untouched (maven-compiler-plugin does not wipe stale
# output on failure). Without `clean`, a module that fails to build here
# can silently leave a stale, pre-fix jar behind in the shared local repo
# (~/.m2) instead of failing loudly -- which is exactly how a demo that
# depends on a just-changed jgdms-der API can go on to fail downstream
# with a confusing ClassNotFoundException instead of a build error.
Write-Header "Step 0: building JGDMS modules from current source (jgdms-der, jgdms-platform, jgdms-lib-dl, jgdms-cel, ...)"
Push-Location $jgdms
try {
    & mvn -pl services/outrigger/outrigger-dl,jgdms-cel -am `
        "-Dmaven.test.skip=true" "-Dtidy.skip=true" "-Drat.skip=true" clean install
    if (-not $?) {
        Write-Host ""
        Write-Host "FATAL: module build failed (exit $LASTEXITCODE) -- aborting before any demo runs."
        Write-Host "The showcase would otherwise run against stale, pre-existing jars/classes"
        Write-Host "and fail confusingly downstream instead of here, where the real error is."
        exit 1
    }
} finally { Pop-Location }
Write-Host "Modules built and installed from current source."

# ---------------------------------------------------------------------------
# Build the main showcase module (demos 1, 3, 4, 6 live here) against the jars
# just installed above. `clean package`, for the same stale-target reason as
# Step 0 above: a compile failure here must not be masked by leftover
# .class files from an earlier, unrelated successful build.
# ---------------------------------------------------------------------------
Write-Header "Building the showcase module (demos 1, 3, 4, 6)"
Push-Location $showcase
try {
    & mvn -q clean package -DskipTests
    if (-not $?) {
        Write-Host ""
        Write-Host "FATAL: showcase build failed (exit $LASTEXITCODE) -- aborting before any demo runs."
        Write-Host "The demos would otherwise run against a stale target/classes and fail"
        Write-Host "confusingly downstream (e.g. ClassNotFoundException) instead of here."
        exit 1
    }
} finally { Pop-Location }

$cp = "$showcase\target\classes;$showcase\target\lib\*"

# ---------------------------------------------------------------------------
# Demonstration 1: Same object, same bytes -- everywhere
# ---------------------------------------------------------------------------
Write-Header "Demonstration 1: Same object, same bytes -- everywhere"
try {
    & java -cp $cp au.net.zeus.jgdms.showcase.demo.SameObjectSameBytesDemo
    if ($LASTEXITCODE -ne 0) { throw "exit code $LASTEXITCODE" }

    Write-Host ""
    Write-Host "(Running it a second time in a fresh process -- the checksum is identical,"
    Write-Host " because the bytes depend only on the value, not on the run.)"
    $run1 = & java -cp $cp au.net.zeus.jgdms.showcase.demo.SameObjectSameBytesDemo | Select-String "checksum \(SHA-256\) of pass 1"
    if ($LASTEXITCODE -ne 0) { throw "second run exit code $LASTEXITCODE" }
    Write-Host "  fresh process: $run1"
    Add-Result "Demo 1: Same object, same bytes" $true
} catch {
    Write-Host "DEMO 1 FAILED: $_"
    Add-Result "Demo 1: Same object, same bytes" $false
}

# ---------------------------------------------------------------------------
# Demonstration 2: Match a template without ever loading the class
# ---------------------------------------------------------------------------
Write-Header "Demonstration 2: Match a template without ever loading the class"
try {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $showcase "demo2-match-without-the-class\run.ps1")
    if ($LASTEXITCODE -ne 0) { throw "exit code $LASTEXITCODE" }
    Add-Result "Demo 2: Match without the class" $true
} catch {
    Write-Host "DEMO 2 FAILED: $_"
    Add-Result "Demo 2: Match without the class" $false
}

# ---------------------------------------------------------------------------
# Demonstration 3: The shape description travels once
# ---------------------------------------------------------------------------
Write-Header "Demonstration 3: The shape description travels once"
try {
    & java -cp $cp au.net.zeus.jgdms.showcase.demo.SchemaSentOnceDemo
    if ($LASTEXITCODE -ne 0) { throw "exit code $LASTEXITCODE" }
    Add-Result "Demo 3: Schema sent once" $true
} catch {
    Write-Host "DEMO 3 FAILED: $_"
    Add-Result "Demo 3: Schema sent once" $false
}

# ---------------------------------------------------------------------------
# Demonstration 4: Feed it garbage, it stops politely
# ---------------------------------------------------------------------------
Write-Header "Demonstration 4: Feed it garbage, it stops politely"
try {
    # A deliberately small memory ceiling (128 MB) makes the point concrete: the
    # expansion bomb would want gigabytes, yet the whole demonstration runs inside
    # it without ever running out of memory -- but only because jgdms-der (built
    # fresh in Step 0) actually contains the bomb-refusal fix.
    & java -Xmx128m -cp $cp au.net.zeus.jgdms.showcase.demo.HostileInputDemo
    if ($LASTEXITCODE -ne 0) { throw "exit code $LASTEXITCODE" }
    Add-Result "Demo 4: Feed it garbage" $true
} catch {
    Write-Host "DEMO 4 FAILED: $_"
    Add-Result "Demo 4: Feed it garbage" $false
}

# ---------------------------------------------------------------------------
# Demonstration 5: Filter records by a written rule, without the class
# ---------------------------------------------------------------------------
Write-Header "Demonstration 5: Filter records by a written rule, without the class"
try {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $showcase "demo5-filter-by-a-rule\run.ps1")
    if ($LASTEXITCODE -ne 0) { throw "exit code $LASTEXITCODE" }
    Add-Result "Demo 5: Filter by a rule" $true
} catch {
    Write-Host "DEMO 5 FAILED: $_"
    Add-Result "Demo 5: Filter by a rule" $false
}

# ---------------------------------------------------------------------------
# Demonstration 6: Two different collection classes, one value, one encoding
# ---------------------------------------------------------------------------
Write-Header "Demonstration 6: Two different collection classes, one value, one encoding"
try {
    & java -cp $cp au.net.zeus.jgdms.showcase.demo.CollectionEqualityDemo
    if ($LASTEXITCODE -ne 0) { throw "exit code $LASTEXITCODE" }
    Add-Result "Demo 6: Collection equality" $true
} catch {
    Write-Host "DEMO 6 FAILED: $_"
    Add-Result "Demo 6: Collection equality" $false
}

# ---------------------------------------------------------------------------
# Automated checks (a build server can run these) -- not one of the 6 demos,
# but the existing regression coverage for all of them; kept as a bonus step.
# ---------------------------------------------------------------------------
Write-Header "Automated checks (mvn test)"
Push-Location $showcase
try {
    & mvn -q test "-DredirectTestOutputToFile=true"
    if ($LASTEXITCODE -ne 0) { throw "exit code $LASTEXITCODE" }
    Add-Result "Automated checks (mvn test)" $true
} catch {
    Write-Host "AUTOMATED CHECKS FAILED: $_"
    Add-Result "Automated checks (mvn test)" $false
} finally { Pop-Location }

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
Write-Header "Summary"
$anyFailed = $false
foreach ($r in $results) {
    if ($r.Pass) {
        $status = "PASS"
    } else {
        $status = "FAIL"
        $anyFailed = $true
    }
    Write-Host ("  [{0}] {1}" -f $status, $r.Name)
}
Write-Host ""
if ($anyFailed) {
    Write-Host "RESULT: one or more demonstrations FAILED."
    exit 1
} else {
    Write-Host "RESULT: all demonstrations and automated checks passed."
    exit 0
}
