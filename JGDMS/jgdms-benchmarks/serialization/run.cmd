@echo off
rem Runs the serialization-format benchmarks (JOSS vs DER). Use a DirtyChai JDK
rem (or any JDK <= 23 with the SecurityManager permitted) for the JERI tier.
rem
rem   cmd:        set JAVA_HOME=C:\path\to\dirtychai\jdk
rem               run.cmd -rf json -rff results.json
rem   PowerShell: $env:JAVA_HOME = "C:\path\to\dirtychai\jdk"
rem               .\run.cmd -rf json -rff results.json
rem   then:       python summarise.py results.json
rem
rem Arguments are passed to JMH (try -h). Useful ones:
rem   "CodecBenchmark.*"     only the codec tier
rem   "JeriBenchmark.*"      only the JERI tier
rem   -t 4                   four concurrent callers (throughput rather than latency)
rem   -prof gc               allocation per operation
rem
rem No labels (goto/call :label): cmd.exe cannot find labels in a file saved
rem with Unix line endings.
setlocal
set "HERE=%~dp0"
if "%HERE:~-1%"=="\" set "HERE=%HERE:~0,-1%"
set "JAR=%HERE%\target\serialization-benchmarks.jar"
if not exist "%JAR%" echo Benchmarks not built: run mvn -f "%HERE%\..\pom.xml" package first
if not exist "%JAR%" exit /b 1
set "JAVA=java"
if defined JAVA_HOME set "JAVA=%JAVA_HOME%\bin\java"

echo == Wire sizes (bytes per object in a fresh stream)
"%JAVA%" -cp "%JAR%" au.net.zeus.jgdms.bench.serial.WireSize
echo.
echo == JMH
"%JAVA%" -Djmh.link.address=127.0.0.1 -jar "%JAR%" -foe true %*
