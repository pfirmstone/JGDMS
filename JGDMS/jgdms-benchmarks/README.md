# Policy-overhead benchmarks

JMH micro-benchmarks that measure what a SecurityManager with DirtyChai's
`ConcurrentPolicyFile` costs ordinary code: the price of one permission check,
and that price as a fraction of a small request.

They exist so that a claim like "less than 1% policy overhead" has a
reproducible measurement behind it, with its conditions stated, rather than
being asserted.

## What is measured

The same code runs in four JVM configurations, and results are compared
pairwise (off vs on):

| Class                        | SecurityManager                       | Benchmark threads                    |
|------------------------------|---------------------------------------|--------------------------------------|
| `PolicyOverheadSmOff`        | none                                  | 1 platform thread                    |
| `PolicyOverheadSmOn`         | `-Djava.security.manager=default`     | 1 platform thread                    |
| `PolicyOverheadVirtualSmOff` | none                                  | 1 virtual thread per core            |
| `PolicyOverheadVirtualSmOn`  | `-Djava.security.manager=default`     | 1 virtual thread per core            |

On DirtyChai, `-Djava.security.manager=default` installs `CombinerSecurityManager`.
The run scripts set `policy.provider` to `ConcurrentPolicyFile` explicitly,
because DirtyChai's shipped `java.security` still names
`sun.security.provider.PolicyFile`.

Two benchmarks run in each configuration:

- **`guardedRead`**: one `System.getProperty` call from the workload. With a
  SecurityManager that is one `PropertyPermission` check; without one it is a
  plain map lookup. *SM on minus SM off is the cost of one permission check.*
- **`request`**: a small request handler (parse 16 query parameters, CRC32 a
  512-byte body, format a response) making `guardedReadsPerRequest` checked
  calls, for 0, 1 and 4. *(SM on − SM off) / SM off is the policy overhead for
  that request.* The 0 case makes no checks and is the control: its overhead
  should be within the error.

### Why two jars

The request handler lives in its own jar (`jgdms-bench-workload.jar`), which
the policy grants only what a request handler needs: `PropertyPermission
"jgdms.bench.*", "read"`, plus what DirtyChai requires to load its classes.
JMH and the harness (`benchmarks.jar`) get their own least-privilege grants
from the audit step. No domain is granted `AllPermission`, and `default.policy`
(whose broad `AllPermission` grants come with the JDK) is excluded by passing
the policy with `-Djava.security.policy==`. Every check therefore evaluates
real, restricted `ProtectionDomain`s on the stack, as it would for application
code deployed with least privilege.

### Self-checks

Before measuring, each fork verifies its own configuration and fails rather
than producing numbers if any of these are wrong:

- a SecurityManager is installed exactly when the class name says so
  (`...SmOn`), and the benchmark thread is virtual exactly when it says so
  (`...Virtual...`);
- the installed `Policy` is the expected class (`ConcurrentPolicyFile`, or
  `PolicyFile` with `BENCH_POLICY=stock`);
- neither the harness nor the workload holds `AllPermission`;
- the workload can read a granted property, **and is refused** `user.home`,
  which proves the codeBase grants in the policy match the jars and the
  checks being timed are real.

On DirtyChai, a SecurityManager fork that fails with "was refused a
permission" usually means the policy hasn't been generated yet (step 1 below).

## Building

A JDK 21 or later and Maven. The project is standalone (it does not use the
JGDMS root pom, which targets release 8 and runs JGDMS's own annotation
processor):

    mvn -f jgdms-benchmarks/pom.xml package

This produces `harness/target/benchmarks.jar` and
`harness/target/lib/jgdms-bench-workload.jar`.

## Running

Run with a DirtyChai JDK. There are two steps: generate the policy once, then
measure.

### 1. Generate the policy (once)

`conf/bench.policy` holds the only grant written by hand: the workload may
read its own properties. Everything else is left to the audit, with no
`AllPermission` anywhere. That covers JMH's needs (its control connection to
the host JVM, reflection, threads), the harness's own checks, and DirtyChai's
class loading. Before `SecureClassLoader` defines a class, the new domain
itself must hold read access to its codebase and permission to load classes.
Rather than guess those, the
audit step runs the SecurityManager configurations briefly under DirtyChai's
`polpAudit` (`SecurityPolicyWriter`). It records every permission used and
appends the missing grants to `conf/generated.policy`, seeded from
`bench.policy`. The jar locations are written as `${jgdms.bench.harness.url}`
and `${jgdms.bench.workload.url}`, so the file stays portable.

Windows:

    set JAVA_HOME=C:\path\to\dirtychai\jdk
    set BENCH_MODE=audit
    jgdms-benchmarks\run.cmd
    set BENCH_MODE=

Unix:

    JAVA_HOME=/path/to/dirtychai/jdk BENCH_MODE=audit jgdms-benchmarks/run.sh

**Review `conf/generated.policy` before measuring.** Every grant in it is one
the benchmarks used, and the workload's grants should stay narrow. During the
audit, the harness deliberately skips its `user.home` refusal probe, so the
audit can't record that read and grant it. In measurement runs the probe is
back on and confirms the workload is still restricted. Re-run the audit after
moving the jars or changing the code. It only appends grants, so delete
`generated.policy` to start clean.

### 2. Measure

Once `conf/generated.policy` exists, the scripts use it instead of
`bench.policy` and print which file they're using. Arguments are passed to JMH.

Windows:

    jgdms-benchmarks\run.cmd -rf json -rff results.json

Unix:

    JAVA_HOME=/path/to/dirtychai/jdk jgdms-benchmarks/run.sh -rf json -rff results.json

Then:

    python3 jgdms-benchmarks/summarise.py results.json

The defaults are 3 forks × 5 one-second warm-up and 5 one-second measurement
iterations per benchmark and parameter: 16 benchmark runs, roughly 10 minutes.
For a quick check that everything is wired correctly:

    run.cmd -f 1 -wi 1 -w 200ms -i 2 -r 300ms

To compare with the JDK's standard, synchronised `PolicyFile` under the same
SecurityManager, set `BENCH_POLICY=stock`.

To pass extra JVM options to the forks (for example
`-Djava.security.debug=access:failure`), use `-jvmArgsPrepend`, never
`-jvmArgsAppend`. A command-line `-jvmArgsAppend` replaces each class's own
`@Fork(jvmArgsAppend)`, which is where `-Djava.security.manager=default` and
`-Djmh.executor=VIRTUAL` are set, so every fork would silently run without a
SecurityManager on platform threads. The self-checks refuse to measure if that
happens.

## Reading the results

`summarise.py` prints, for each benchmark and parameter, SM-off and SM-on
times with JMH's 99.9% error, the difference, and the relative overhead.

- `guardedRead` **difference**: the cost of one permission check in ns.
- `request` **overhead %**: the policy cost of that request.
  `guardedReadsPerRequest=0` should be "within error"; if it isn't, something
  other than permission checks differs between the forks, and the other rows
  can't be trusted.

Any overhead percentage depends on the workload: a check costs the same
whether the surrounding request takes 1 µs or 1 ms. So report the per-check
cost along with the percentage, and say what the request was. "Less than 1%"
is a statement about a particular check-to-work ratio, not a constant.

## Diagnostics

Two further benchmark sets explain where a check's cost goes. They need a
SecurityManager, run only under DirtyChai with the generated policy, and are
ignored by `summarise.py`.

- `CheckBreakdown` splits one check into capturing the context
  (`getContext`), the SecurityManager's cached check against a fixed context,
  and full policy evaluation of that context.
- `ContextCaptureSplitPlatform` and `ContextCaptureSplitVirtual` split
  `getContext()` itself: the native stack walk, `optimize()`, the
  inherited-context native, and the Subject lookup, on a platform and on a
  virtual thread. `stackWalkAtDepth` repeats the walk with 0, 32 and 128 extra
  frames, so the slope gives the walk's cost per frame and the intercept its
  fixed cost. These reach private JDK methods through
  `MethodHandles.privateLookupIn`, so their forks carry `--add-opens` in
  `@Fork(jvmArgsPrepend)` (a command-line `-jvmArgsPrepend` replaces it), and
  the audit records the harness's `suppressAccessChecks` grant.

Run them by name, for example:

    run.cmd ContextCaptureSplit

## What these numbers do not cover

- **Steady state only.** Every check is repeated many times, so DirtyChai's
  context cache and `CombinerSecurityManager`'s checked-permission cache are
  warm. The first check of a new context, policy refresh and cache expiry are
  not isolated here.
- **Short stacks.** Two non-system domains are on the stack (harness and
  workload). Deep stacks with many codebases cost more per check.
- **One permission type.** `PropertyPermission` is cheap to evaluate.
  Permissions with expensive `implies` (for example `SocketPermission` or
  `FilePermission`) are not measured.
- **No Subject.** No `Subject.callAs` scope is active, so no principals are
  folded into the context.
- **Not JERI vs RMI.** Remote-invocation performance needs a separate,
  two-JVM benchmark; see `docs/investigation-jeri-der-vs-jsonrpc-benchmark-scope.md`
  for the fairness issues.

Run on a quiet machine with frequency scaling off where possible, and publish
the JDK build, CPU, OS and full JMH output alongside any figure quoted.

## Provenance

The benchmark code, scripts and this README were written with AI assistance
(Claude, Anthropic) and reviewed by the project lead. It is not part of the
DirtyChai repository and is not subject to its AI policy.
