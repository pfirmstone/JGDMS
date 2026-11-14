# Run brief: TLS accept-rate measurements on the 36-core Windows workstation (2026-11-14)

**For a local agent that runs these tests on the maintainer's workstation and relays the results to the cloud session that wrote this brief.**
The cloud session reads your output as RELAYED evidence, so what matters is that the raw outputs reach it unchanged, with the command that produced each.
It bears on the register rows CL-DERPERF-0015 (the default cap), CL-DERPERF-0006 (the `BindException` failures) and CL-DERPERF-0016 (several destination addresses), in the register `claim_register/hypotheses/DERPERF/INDEX.md` of pfirmstone/Claude.

## 0. What you may and may not do

- **You may:** read this file and the repository, run the commands below, write files under `$work` (a results folder you create), and stop a `java.exe` that you started.
- **You may not, without asking the maintainer first and saying exactly what and why:** change Windows settings (`netsh`, the registry, the firewall, services), install software, edit or commit anything in the repository, open ports, or install or start an ssh server.
- **If anything is unexpected, stop and report it. Do not fix it, retry with other options, or substitute commands.** A failed or odd run is a result.
- Do not summarise outputs in place of the raw text. Save each run's complete output to a file and send the files' contents verbatim.

## 1. What is being measured, and what counts

`TlsAcceptRate` opens TLS connections to the benchmark server as fast as it can for a fixed time and prints how many handshakes completed per second, with latency and failures.
**A run counts only if it prints `failures 0` and no `RUN INVALID` line.** A run with any failures, or with `RUN INVALID`, is kept and reported but not used.
Before the runs, `TIME_WAIT` must have drained (section 4); a run started while the previous run's sockets are still held can fail with `BindException: Address already in use`, which is the client running out of ports, not a server fault.

## 2. Inputs

- The jar: `serialization-benchmarks.jar`, built from JGDMS commit `d7dfb0140` on branch `claude/focused-bohr-t6bwal`, SHA-256 `de2a3776d1983a800f2adc1565473524db873954f23d8a491a0ace5ac0e52596` (8,516,523 bytes). Check the hash first. If your copy differs, say so and stop.
  Its source is `jgdms-benchmarks/serialization/src/main/java/au/net/zeus/jgdms/bench/serial/` in the same commit (unchanged since); rebuild commands are in the handover `JGDMS/docs/HANDOVER-focused-bohr-2026-11-14.md`, section 8.
- A JDK 25 or later: the maintainer's DirtyChai build. Record which one (section 3).
- The workstation's own addresses. At the time of writing the Wi-Fi adapters had `10.1.1.28` and `10.1.1.126` (DHCP, which can change): run `ipconfig` and use the addresses it shows now.

## 3. Capture the environment first (one file, before any run)

Create `$work\env.txt` with the output of each, in this order, each preceded by its command:
```powershell
$jar  = 'C:\bench\serialization-benchmarks.jar'      # the maintainer says where
$work = 'C:\bench\work'                              # no spaces in either path
$java = "$env:JAVA_HOME\bin\java.exe"
New-Item -ItemType Directory -Force "$work\results" | Out-Null
Get-FileHash $jar -Algorithm SHA256
& $java -version
Get-ComputerInfo -Property OsName,OsVersion,OsBuildNumber,CsProcessors,CsNumberOfLogicalProcessors,CsNumberOfProcessors
echo 'System.out.println(Runtime.getRuntime().availableProcessors()); /exit' | jshell -    # if jshell exists
netsh int ipv4 show dynamicport tcp
Get-ItemProperty 'HKLM:\SYSTEM\CurrentControlSet\Services\Tcpip\Parameters' | Select-Object TcpTimedWaitDelay, MaxUserPort
ipconfig
(netstat -an | Select-String TIME_WAIT | Measure-Object).Count
```
The cap default is `min(512, maxFds/8, max(16, 4 x availableProcessors))` (`HandshakeLimits.java`; RESULTS section 39). On Windows the JVM reports no descriptor limit and the code assumes 16384, so the second term is 2048; on 72 logical processors the default would therefore be 288 **if** the JVM reports 72. Record what `availableProcessors` prints: a machine with more than 64 logical processors uses processor groups and the JVM may count fewer.

## 4. The drain wait, used before every run

```powershell
function Wait-Drain { while ((netstat -an | Select-String TIME_WAIT | Measure-Object).Count -gt 500) { Start-Sleep 5 } }
```

## 5. The sequence (do it in this order; one server per setting)

Settings, called A to D. Each is a different server start; the client command is the same for all.

| Setting | Extra `-D` options on the **server** only |
|---|---|
| A | none (the default cap) |
| B | `-Dorg.apache.river.jeri.ssl.maxServerHandshakes=24` |
| C | `-Dorg.apache.river.jeri.ssl.maxServerHandshakes=12` |
| D | `-Dorg.apache.river.jeri.ssl.serverHandshakeClientFlightTimeout=0` (`0` removes the post-flight bound; default 3000 ms; `BOARD-REVIEW-S0-tls-accept-path-2026-10-20.md`, commit `6d9f4489f`) |

For each setting X in A, B, C, D:

1. Start the server in its own window, pinned to NUMA node 0, with output to a file (a fresh `srv` folder per setting, the same `tls` folder):
   ```powershell
   $s = "$work\srv${X}"
   cmd /c start "TLS server ${X}" /node 0 cmd /c "`"$java`" -Dbench.server.keepalive=true -Dorg.apache.river.jeri.ssl.handshakeSummaryInterval=2000 <EXTRA -D OPTIONS FOR X> -Dbench.tls.dir=$work\tls -cp $jar au.net.zeus.jgdms.bench.serial.ServerMain $s > $work\results\server-${X}.log 2>&1"
   while (-not (Select-String -Path "$work\results\server-${X}.log" -Pattern '^ready' -Quiet -ErrorAction SilentlyContinue)) { Start-Sleep 1 }
   ```
   If `ready` never appears, send `server-${X}.log` and stop.
2. Warm-up run, **discarded** (it is there to warm the JIT and the caches):
   ```powershell
   Wait-Drain
   & $java -Dbench.tls.dir=$work\tls '-Dbench.connect.hosts=127.0.0.1,<ADDR1>,<ADDR2>' -cp $jar au.net.zeus.jgdms.bench.serial.TlsAcceptRate $s 64 20 full 2>&1 | Tee-Object -FilePath "$work\results\${X}-warmup.txt"
   "exit=$LASTEXITCODE" | Add-Content "$work\results\${X}-warmup.txt"
   ```
   (the single quotes around the `-Dbench.connect.hosts=...` argument are required: PowerShell treats an unquoted comma as an array separator; `<ADDR1>` and `<ADDR2>` are the two addresses from `ipconfig`.)
3. Two measured runs, the same command, saved as `${X}-run1.txt` and `${X}-run2.txt`, each preceded by `Wait-Drain`, each followed by its `exit=` line.
4. Stop that server before the next setting:
   ```powershell
   Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object CommandLine -like '*ServerMain*' | ForEach-Object { Stop-Process -Id $_.ProcessId }
   ```
5. Before the next setting, `Wait-Drain`.

**Additionally, for setting A only,** after its three-address runs: one warm-up and two measured runs with the single address (`'-Dbench.connect.hosts=127.0.0.1'` or no option), saved as `A1-warmup.txt`, `A1-run1.txt`, `A1-run2.txt`, to show whether the extra addresses make a difference.

Optional, only if the maintainer says so: the client pinned to NUMA node 1 (`cmd /c start /wait /node 1 ...`) in place of unpinned. Do not do this unasked.

## 6. If a run is invalid

If the three-address runs still print `RUN INVALID`, **stop at that setting and report**: do not widen the port range or change the registry yourself (section 0); the maintainer decides. Send the output you have.

## 7. What to send back (verbatim files, then a plain list)

1. The contents of `env.txt`.
2. Every file in `$work\results` (the `server-*.log` files and every run file), unmodified, each headed with the file's name.
3. A plain list, one line per run: file, setting, valid (yes/no by the rule in section 1), the exit code, and for a valid run the `connections ... per second` line copied exactly.
4. What you did not run and why, and anything that differed from this brief (a path, an address, a changed command). Do not interpret the numbers: say nothing about which setting is better. The cloud session draws the conclusions from the raw files.

If a server log contains lines after `ready` (handshake summary lines appear only when handshakes fail), include them as they are.
