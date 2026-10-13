@echo off
rem Runs the policy-overhead benchmarks. Use a DirtyChai JDK.
rem
rem 1. Generate the policy once (DirtyChai's polpAudit records the permissions
rem    the SecurityManager runs need and writes conf\generated.policy).
rem    cmd:        set JAVA_HOME=C:\path\to\dirtychai\jdk
rem                set BENCH_MODE=audit
rem                run.cmd
rem    PowerShell: $env:JAVA_HOME = "C:\path\to\dirtychai\jdk"
rem                $env:BENCH_MODE = "audit"
rem                .\run.cmd
rem    Review conf\generated.policy before using it.
rem
rem 2. Measure, with BENCH_MODE cleared (cmd: set BENCH_MODE=
rem    PowerShell: Remove-Item Env:BENCH_MODE):
rem      run.cmd -rf json -rff results.json
rem    Arguments are passed to JMH (try -h).
rem
rem set BENCH_POLICY=stock to run the SecurityManager forks with the JDK's
rem standard PolicyFile instead of ConcurrentPolicyFile.
rem
rem This script deliberately uses no labels (no goto/call :label): cmd.exe
rem cannot find labels in a file saved with Unix line endings, and line
rem endings are easily changed by editors and file transfers.
setlocal
set "HERE=%~dp0"
if "%HERE:~-1%"=="\" set "HERE=%HERE:~0,-1%"
set "HARNESS=%HERE%\harness\target\benchmarks.jar"
set "WORKLOAD=%HERE%\harness\target\lib\jgdms-bench-workload.jar"
rem Single-line ifs throughout: a path expanded inside a ( ... ) block would
rem end the block early if it contained a parenthesis, e.g. "Program Files (x86)".
set "BUILT=1"
if not exist "%HARNESS%" set "BUILT="
if not exist "%WORKLOAD%" set "BUILT="
if not defined BUILT echo Benchmarks not built: run mvn -f "%HERE%\pom.xml" package first
if not defined BUILT exit /b 1

if "%BENCH_POLICY%"=="" set "BENCH_POLICY=dirtychai"
set "SECURITY="
if /I "%BENCH_POLICY%"=="dirtychai" (
    set "SECURITY=dirtychai.security"
    set "POLICY_CLASS=au.zeus.jdk.authorization.policy.ConcurrentPolicyFile"
)
if /I "%BENCH_POLICY%"=="stock" (
    set "SECURITY=stock.security"
    set "POLICY_CLASS=sun.security.provider.PolicyFile"
)
if "%SECURITY%"=="" (
    echo BENCH_POLICY must be dirtychai or stock
    exit /b 1
)

rem Policy codeBase URLs use forward slashes: file:/C:/...
set "HARNESS_URL=file:/%HARNESS:\=/%"
set "WORKLOAD_URL=file:/%WORKLOAD:\=/%"

set "GENERATED=%HERE%\conf\generated.policy"
set "POLICY_FILE=%HERE%\conf\bench.policy"
if exist "%GENERATED%" set "POLICY_FILE=%GENERATED%"

set "JAVA=java"
if defined JAVA_HOME set "JAVA=%JAVA_HOME%\bin\java"

set "AUDIT_MODE="
if /I "%BENCH_MODE%"=="audit" set "AUDIT_MODE=1"
rem In audit mode, seed with the hand-written grants so polpAudit only appends
rem what is missing.
if defined AUDIT_MODE if not exist "%GENERATED%" copy /Y "%HERE%\conf\bench.policy" "%GENERATED%" >nul
if defined AUDIT_MODE set "POLICY_FILE=%GENERATED%"

rem The host JVM's -D options are inherited by every JMH fork; each benchmark
rem class then appends its own SecurityManager / executor options.
set JMH="%JAVA%" "-Djgdms.bench.harness.url=%HARNESS_URL%" "-Djgdms.bench.workload.url=%WORKLOAD_URL%" "-Djava.security.policy==%POLICY_FILE%" "-Djava.security.properties=%HERE%\conf\%SECURITY%" "-Djgdms.bench.expectedPolicy=%POLICY_CLASS%" -Djmh.link.address=127.0.0.1 -Djgdms.bench.p0=value0 -Djgdms.bench.p1=value1 -Djgdms.bench.p2=value2 -Djgdms.bench.p3=value3 -Djgdms.bench.p4=value4 -Djgdms.bench.p5=value5 -Djgdms.bench.p6=value6 -Djgdms.bench.p7=value7 -cp "%HARNESS%;%WORKLOAD%" org.openjdk.jmh.Main

if not defined AUDIT_MODE echo # jgdms-bench: policy file %POLICY_FILE%
if not defined AUDIT_MODE %JMH% %*
if not defined AUDIT_MODE exit /b

rem Audit mode. polpAudit writes these properties in place of the matching
rem paths, so the generated grants stay portable:
rem codeBase "${jgdms.bench.workload.url}".
set "PATHS=%HERE%\conf\audit-paths.properties"
> "%PATHS%" echo jgdms.bench.harness.url=%HARNESS_URL%
>> "%PATHS%" echo jgdms.bench.workload.url=%WORKLOAD_URL%
set "AUDIT=-Djava.security.manager=polpAudit -DpolpAudit.path.properties=%PATHS% -Djgdms.bench.audit=true"
%JMH% "PolicyOverheadSmOn\." -f 1 -wi 1 -w 200ms -i 1 -r 200ms -jvmArgsAppend "%AUDIT%"
if errorlevel 1 exit /b
%JMH% "PolicyOverheadVirtualSmOn\." -f 1 -wi 1 -w 200ms -i 1 -r 200ms -jvmArgsAppend "%AUDIT% -Djmh.executor=VIRTUAL"
if errorlevel 1 exit /b
echo.
echo Wrote %GENERATED%. Review it: every grant in it is one the benchmarks used.
echo Then clear BENCH_MODE and run again to measure.
exit /b 0
