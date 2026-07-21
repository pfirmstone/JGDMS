# SOW — Activation jtreg Harness Repair (`net/jini/activation/ActivationSystem/accessControl`)

**Status:** Scope-of-Work for review. Not yet implemented (except WI‑1, already applied on this branch).
**Branch:** `fix/jtreg-activationlibrary-jrmp-rot` (base = trunk `290a3243f`, ProxyTrust present).
**Author/driver context:** scoped after empirical investigation on the DirtyChai JDK
(`/home/user/GitHub/DirtyChai/build/linux-x86_64-server-release/images/jdk`, OpenJDK 27, SM‑capable).
**Motivating goal:** make the `accessControl` activation test actually *run* end‑to‑end, so it can validate
whether the phoenix‑dl ProxyTrust removal (`task-proxytrust-stage2-phoenix-dl`, deletes
`getProxyTrustIterator()` from `phoenix-dl/ConstrainableAID`) breaks activation access control.

All paths below are repo‑relative to the worktree root unless noted. Line numbers are as of this branch.

---

## 1. Problem statement

`accessControl` is a **real** security test: `AccessControl.main` stands up a secure phoenix (rmid) over
SSL, logs in three keystore subjects, and asserts a permission matrix — crucially, that
`Security.verifyObjectTrust(activationID, …)` (the phoenix `ConstrainableAID` ProxyTrust path) is **denied**
for client1 and **allowed** for client2. That `verifyObjectTrust(aid)` assertion is exactly what the
phoenix‑dl `getProxyTrustIterator()` deletion touches. So the test is the right instrument — *if it runs.*

It does not run. Two independent, **pre‑existing** defects (neither caused by phoenix‑dl) make it a vacuous pass:

### 1a. The `@run shell` action never launches the test main (root cause of the vacuous pass)

Original tag in `qa/jtreg/net/jini/activation/ActivationSystem/accessControl/AccessControl.java:27‑28`:

```
 * @run shell classpath.sh main/othervm/timeout=240/policy=security.policy
 * -Djava.protocol.handler.pkgs=net.jini.url AccessControl
```

Under stock jtreg 8.1 a `shell` action runs literally `sh classpath.sh <args>`. The script
`qa/jtreg/net/jini/activation/ActivationSystem/accessControl/classpath.sh` only does:

```
!#/bin/sh          <- reversed shebang (should be #!/bin/sh); line 1 is executed as a command
CLASSPATH="../../../../../JTlib-tmp:."; export CLASSPATH;
```

It sets `CLASSPATH` and exits 0. The `main/othervm/…/AccessControl` tokens are inert arguments it ignores.
`AccessControl.main` is never invoked. `classpath.sh` is **byte‑identical** across the whole activation
shell‑test family (md5 `0aa4a1d9484bd34a6f7cc413d0c594f5`: `ensureRestart`, `unregisterGroup`,
`Activatable/*`, …).

**Empirical proof (two runs, this environment):**

- *Probe* (`scratchpad/jtreg-probe/`): a minimal test with the same `@run shell cp.sh main/othervm/… Probe`
  shape whose main writes a marker file. jtreg reported `TEST RESULT: Passed. Execution successful /
  passed: 1` while the main **never ran** (no marker, no stdout).
- *Real baseline* (`scratchpad/baseline-accessControl.log`, qa‑fix branch + baseline dist): jtreg
  `build`+`compile` actions **Passed** (the earlier compile‑rot fixes work), then the `shell` action
  **Passed in 0.006 s** with STDERR `classpath.sh: 1: !#/bin/sh: not found` and **zero** runtime markers
  (no `RMID: starting rmid`, no `HTTPD:`, no `Activation`, no access‑control assertion output).
  `TEST RESULT: Passed. passed: 1`.

Because the code under test never executes, **baseline PASS and any treatment PASS are the identical
non‑run** — the test has zero power to detect the phoenix‑dl change. Reporting that jtreg "passed" would be
the fabricated‑pass trap this workstream is explicitly guarding against.

### 1b. The activation bring‑up the test declares is itself rotted against the current versioned‑jar layout

Even once the main is launched (WI‑1), `AccessControl.main` → `RMID.createRMID(true).slowStart()` cannot
start phoenix, because the qa activation testlibrary hardcodes a legacy **unversioned** jar layout and a
dead launch mechanism:

- `qa/jtreg/testlibrary/RMID.java:137‑140` — `getDefaultLocation()` = `jsk.home + "/lib/phoenix.jar"`
  (unversioned); `:142‑146` `getDefaultGroupLocation()` = `jsk.home + "/lib/phoenix-group.jar"`;
  `:76‑96` the download codebase names `phoenix-dl.jar` and `jsk-dl.jar` (unversioned). The dist produces
  only versioned names and **no `jsk-dl.jar` at all**.
- `qa/jtreg/testlibrary/RMID.java:121‑126` — the constructor selected by `createRMID(true)` launches
  phoenix as `java … -jar <jsk.home>/lib/phoenix.jar <rmid.config>`, **but** the dist
  `phoenix-4.0.0-SNAPSHOT.jar` manifest is bare (`Manifest-Version: 1.0` — **no `Main-Class`**), so `-jar`
  cannot work.
- `qa/jtreg/testlibrary/HTTPD.java:84‑85` — unsubstituted Maven placeholders as **Java string literals**:
  `classserver-${river.version}.jar` and `jgdms-lib-dl-${river.version}.jar`. The codebase server's
  classloader points at files literally named `${river.version}`.
- **`jsk.home` is used with two contradictory meanings** in the testlibrary: `RMID.getDefaultLocation()`
  (`jsk.home/lib`) and `HTTPD.getDefaultDir()` (`HTTPD.java:135‑138`, `jsk.home/lib-dl`) imply `jsk.home`
  is the **dist image dir** (`…/JGDMS-4.0.0-SNAPSHOT`, which has `lib/`+`lib-dl/`), while
  `HTTPD.java:84` (`jsk.home/JGDMS/tools/classserver/target/…`) implies `jsk.home` is the **repo root**.
  One value cannot satisfy both.

These are the reasons the prior end‑to‑end validation returned **INCONCLUSIVE**: the test could neither
confirm nor refute the phoenix‑dl change because it never exercised the code.

---

## 2. Locked decisions (approved by Peter)

| # | Decision | One‑line rationale |
|---|----------|--------------------|
| A | Convert **only** `accessControl`'s tag to a real `@run main/othervm/…`; leave `classpath.sh` and the rest of the shell‑test family untouched. | Narrowest blast radius; jtreg `-cpa` already supplies what `classpath.sh` set. (B — fixing the shared `classpath.sh` — would activate the whole dormant family and is explicitly out of scope.) |
| 2(a) | Repoint `RMID` to the `-cp … org.apache.river.phoenix.Activation` launch; **do not touch the shipped phoenix jar.** | Keeps the change in test infra; avoids making the product jar executable just for a test. |
| — | `test.props` `jsk.home` = **dist image dir**; repoint HTTPD's tools/lib jars to `jsk.home/lib*/…`. | Resolves the contradictory `jsk.home` semantics to the meaning `lib/`+`lib-dl/` already assume. |
| — | Pin **`jsk-dl.jar` → `jgdms-lib-dl-4.0.0-SNAPSHOT.jar`** in the rmid codebase. | Confirmed by the harness logical→real map (see §5). |

---

## 3. Work items

### WI‑1 — Convert the `@run` action (DONE, on this branch)
- **File:** `qa/jtreg/net/jini/activation/ActivationSystem/accessControl/AccessControl.java:27‑28`
- **Change (applied):**
  `@run shell classpath.sh main/othervm/timeout=240/policy=security.policy -Djava.protocol.handler.pkgs=net.jini.url AccessControl`
  → `@run main/othervm/timeout=240/policy=security.policy -Djava.protocol.handler.pkgs=net.jini.url AccessControl`
- `classpath.sh` is left in place (unused by this test; still referenced by the untouched siblings).

### WI‑2 — RMID versioned jar names
- **File:** `qa/jtreg/testlibrary/RMID.java`
- **Changes:**
  - `:137‑140` `getDefaultLocation()`: `phoenix.jar` → `phoenix-4.0.0-SNAPSHOT.jar`.
  - `:142‑146` `getDefaultGroupLocation()`: `phoenix-group.jar` → `phoenix-group-4.0.0-SNAPSHOT.jar`.
  - `:76‑96` download codebase: `phoenix-dl.jar` → `phoenix-dl-4.0.0-SNAPSHOT.jar`, `jsk-dl.jar` → `jgdms-lib-dl-4.0.0-SNAPSHOT.jar` (both under `lib-dl/`; see WI‑5 for `jsk-dl`).
- **Recommendation:** read the version (and the `jsk-dl`/`phoenix*` real names) from `test.props` (add a
  `jgdms.version` / logical‑name keys) rather than hardcoding `4.0.0-SNAPSHOT`, so a version bump does not
  re‑rot the fixture. Hardcoding is acceptable as a faster fallback if reading is judged over‑engineering.

### WI‑3 — Launch‑mechanism change (decision 2(a)); the substantive item
- **File:** `qa/jtreg/testlibrary/RMID.java:121‑126` (the `RMID(boolean useHttpmd)` constructor that
  `createRMID(true)` — `AccessControl.java:438` — routes to).
- **Current:** `super(getDefaultLocation(), makeOptions(useHttpmd) + " -jar", makeArgs(), …)` ⇒
  `java <opts> -jar phoenix.jar rmid.config` (dead — no `Main-Class`).
- **Target:** mirror the existing `RMID(String classpath, boolean)` constructor (`:128‑135`, which already
  runs `org.apache.river.phoenix.Activation` via `-cp`): launch main class
  `org.apache.river.phoenix.Activation` (confirmed present in `phoenix-4.0.0-SNAPSHOT.jar`) with a `-cp`
  built from the **phoenix server (lib‑side) runtime jars** and the appropriate `-Dphoenix.location`.
- **Detail the implementer must assemble (this is the real work, not a rename):**
  - The phoenix server classpath: `phoenix`, `phoenix-common`, `phoenix-group`, `phoenix-init`, plus
    `jgdms-jeri`, `jgdms-platform`, `jgdms-collections`, `jgdms-lib`, `jgdms-activation`,
    `jgdms-activation-parameters`, `jgdms-der`, `jgdms-pref-class-loader`, `jgdms-url-integrity`,
    `high-scale-lib`, `asm*` (i.e. the dist `lib/` set). `Runtime.exec` does **not** glob, so the list must
    be built explicitly in Java (e.g. enumerate `jsk.home/lib/*.jar`).
  - `-Dphoenix.location`: confirm what phoenix uses it for (group‑VM codebase derivation) and point it at
    the correct versioned phoenix/phoenix‑group jar.
  - Confirm `org.apache.river.phoenix.Activation.main(String[])` accepts the `rmid.config` file argument
    (`makeArgs()` = `<test.src>/rmid.config`).
- **Escalation flag:** if phoenix's `Activation` turns out to require executable‑jar semantics (manifest
  `Class-Path`/launcher) and cannot start via plain `-cp`, this WI escalates to a **build change** (emit an
  executable phoenix jar), which would breach the "test‑infra‑only" premise — see Risks.

### WI‑4 — HTTPD `${river.version}` → real paths, consistent with decision jsk.home = dist image dir
- **File:** `qa/jtreg/testlibrary/HTTPD.java:81‑85`
- **Changes:**
  - `toolsJar` (`:84`): drop the `/JGDMS/tools/classserver/target/classserver-${river.version}.jar`
    (repo‑root assumption) → `jsk.home/lib/classserver-4.0.0-SNAPSHOT.jar` (exists in dist image `lib/`).
  - `jsklibJar` (`:85`): `jgdms-lib-dl-${river.version}.jar` → `jsk.home/lib-dl/jgdms-lib-dl-4.0.0-SNAPSHOT.jar`.
- `getDefaultDir()` (`:135‑138`, `jsk.home/lib-dl`) already matches decision jsk.home = dist image dir; no change.
- Same version‑source recommendation as WI‑2.

### WI‑5 — `jsk-dl` → `jgdms-lib-dl` codebase wiring + `test.props` jsk.home
- **`test.props`** (generated by `qa/build.xml:208` as `jsk.home=<value>` for jtreg): set
  `jsk.home` = `<worktree>/JGDMS/dist/target/JGDMS-4.0.0-SNAPSHOT` (dist image dir) so `jsk.home/lib` and
  `jsk.home/lib-dl` resolve. (Read via `TestLibrary.getExtraProperty("jsk.home", …)`.) NB: when driven by
  hand rather than `qa/build.xml`, `test.props` must be written manually with this value.
- **Codebase name** (`RMID.java:76‑96`): `jsk-dl.jar` → `jgdms-lib-dl-4.0.0-SNAPSHOT.jar` (served from
  `lib-dl/` by HTTPD). This is the download jar the rmid/phoenix subprocess fetches for proxy classes.

---

## 4. Sequencing / dependencies

1. **WI‑1** — independent, done.
2. **WI‑5 (`test.props` jsk.home)** — foundational: WI‑2/3/4 all resolve paths against `jsk.home`; set this first.
3. **WI‑2** (versioned names) and **WI‑4** (HTTPD paths) — parallel; both depend only on the jsk.home meaning (WI‑5).
4. **WI‑3** (launch mechanism) — depends on WI‑2 (jar names it will reference) and WI‑5 (classpath root). Highest‑risk; do last and iterate.
5. **WI‑5 (codebase name)** rides with WI‑2.

Practical order: WI‑1 (done) → WI‑5(test.props) → {WI‑2, WI‑4} → WI‑3 → validation.

---

## 5. Harness logical→real name map — confirmation (decision check)

Both authoritative maps agree; **no discrepancy** — the pin is safe:

- `qa/harness/policy/securitypolicywriterpath.properties`:
  `:16 jsk-dl.jar=jgdms-lib-dl-4.0.0-SNAPSHOT.jar` · `:42 phoenix.jar=phoenix-4.0.0-SNAPSHOT.jar` ·
  `:43 phoenix-group.jar=phoenix-group-4.0.0-SNAPSHOT.jar` · `:46 phoenix-dl.jar=phoenix-dl-4.0.0-SNAPSHOT.jar`
- `qa/src/org/apache/river/qa/resources/qaDefaults.properties`:
  `:17 jsk-dl.jar=jgdms-lib-dl-${jgdms.version}.${file.ext}` · `:44 phoenix.jar=phoenix-${jgdms.version}.jar` ·
  `:45 phoenix-group.jar=…` · `:48 phoenix-dl.jar=…`

Note the real harness treats `jsk-dl.jar` and `phoenix-dl.jar` as **distinct** download jars (both appear in
the `sharedGroup.classpath` of `qa/src/.../impl/start/ClasspathTest.td` etc.); the pin preserves that.

---

## 6. Validation approach & acceptance criteria

Experimental design (clean attribution):

1. **Baseline** — this branch (`fix/jtreg-activationlibrary-jrmp-rot`, ProxyTrust present) + WI‑1..5.
   Build dist, run `accessControl`. **Expected: genuine PASS.**
2. **Treatment** — combined worktree = baseline + merge `task-proxytrust-stage2-phoenix-dl`
   (the `getProxyTrustIterator()` deletion). Rebuild the affected phoenix jars + dist. Run `accessControl`.
3. **Compare:** baseline PASS + treatment PASS ⇒ phoenix‑dl **validated** (ProxyTrust removal does not break
   activation access control). Baseline PASS + treatment FAIL ⇒ phoenix‑dl **breaks** accessControl —
   capture the exact failure (do not soften). Baseline FAIL ⇒ **inconclusive**; the test has independent
   problems and phoenix‑dl is not implicated.

**HARD guardrail (acceptance criterion — a jtreg "passed: 1" is NOT sufficient):**
A run counts as a real PASS only if its log shows genuine runtime execution markers, at minimum:
- `RMID: starting rmid` (phoenix subprocess launched),
- `HTTPD:` (codebase server up),
- the access‑control assertion traffic — successful `Security.verifyObjectTrust` for client2 and the
  denial (`SecurityException`) path for client1 (per `AsClient1`/`AsClient2` in `AccessControl.java`).

A sub‑second `shell`/`main` action with none of these markers (as in `scratchpad/baseline-accessControl.log`)
is a non‑run and must be reported as such. **The baseline's genuine assertion output must be shown**, so
baseline‑vs‑treatment is a comparison of two real runs, not two green checkmarks.

Reference jtreg invocation (direct; the `qa/build.xml` jtreg ant taskdef is unusable — no
`com.sun.javatest.regtest.Main$Ant` in jtreg 8.1):

```
/usr/share/jtreg/bin/jtreg \
  -jdk:/home/user/GitHub/DirtyChai/build/linux-x86_64-server-release/images/jdk \
  -cpa:<JTlib-tmp jars> -timeout:16 \
  -Djtreg.dir=<qa/jtreg> -Dtest.src=<qa/jtreg> \
  -Dsun.rmi.registry.registryFilter='*' -Dsun.rmi.transport.dgcFilter='*' \
  -Djava.rmi.server.useCodebaseOnly=false \
  -Djava.rmi.server.RMIClassLoaderSpi=net.jini.loader.pref.PreferredClassProvider \
  -ignore:quiet -xml:verify \
  net/jini/activation/ActivationSystem/accessControl/AccessControl.java
```
(`test.props` with `jsk.home=<dist image dir>` must exist in `qa/jtreg/`; `JTlib-tmp` populated per
`qa/build.xml:216‑252`.)

---

## 7. Risks / unknowns

- **R1 — DirtyChai SM bootstrap (item 6).** A hand‑launch of `AccessControl.main` on the DirtyChai JDK died
  at `AccessControl.java:430` with `ExceptionInInitializerError` → `ThreadLocalRandom.seeder is null` during
  `RandomSupport.<clinit>` (SecurityManager install triggering a class‑init cycle). This was a manual‑harness
  artifact (`-Djava.security.manager=allow`), not necessarily reproducible under jtreg's
  `main/othervm/policy=security.policy` SM setup — but it is unproven until jtreg drives it. If it recurs,
  the fix is in how the othervm SM is enabled (a jtreg/JDK‑interaction issue), independent of phoenix‑dl.
- **R2 — WI‑3 may escalate to a build change.** The "test‑infra‑only" premise is *testable*: acceptance =
  phoenix `Activation` starts via `-cp … org.apache.river.phoenix.Activation` with no `Main-Class`. If it
  cannot (needs executable‑jar/launcher semantics), WI‑3 becomes a build change (emit an executable phoenix
  jar) and must be re‑approved — this is the one WI that could breach the boundary in §8.
- **R3 — cascade of further fixture rot.** Once phoenix actually launches, later stages (group VM spawn via
  `groupOptions` in `rmid.config`, SSL handshake with the keystore fixtures, `ActivationGroupImpl`) may
  surface additional rot not visible statically. Budget for iterate‑debug, not a single clean run.
- **R4 — `rmid.security.policy` has a hardcoded Windows codebase grant**
  (`file:/C:/Users/peter/Documents/GitHub/JGDMS/…`). Currently harmless because the file also has a
  catch‑all `grant { AllPermission }`, but it is dead/misleading and should be noted (out of scope to fix
  unless it interferes).
- **R5 — `-cpa` version drift.** dist ships `asm-9.10.1` (not the `9.7.1` hardcoded in `qa/build.xml`'s
  `-cpa`); build the `-cpa` from actual `JTlib-tmp` contents.

---

## 8. Scope boundary

- **In scope:** `net/jini/activation/ActivationSystem/accessControl` only — WI‑1..5 above, in the qa test
  infra (`AccessControl.java` tag, `RMID.java`, `HTTPD.java`, `test.props`).
- **Out of scope (follow‑on):** fixing the shared `classpath.sh` (option B) and thereby resurrecting the rest
  of the activation shell‑test family (`ensureRestart`, `unregisterGroup`, `Activatable/*`, `bootstrapAttack`,
  …); any change to shipped product jars (unless R2 forces re‑scoping WI‑3); the broader "resurrect the whole
  activation suite" effort.

---

## 9. Effort / model recommendation

- **Implementation loop:** hand WI‑2..5 + validation to a **Sonnet / high** subagent — it is a mechanical‑plus
  build→run→fix loop, the class of work suited to delegation while the coordinator keeps the commit/decision
  authority.
- **Not Fable** for this workstream: it is a security access‑control test, precisely where the fabricated‑pass
  risk is unacceptable.
- **Guardrail baked into the brief:** the §6 acceptance criterion verbatim — no PASS may be claimed without the
  `RMID: starting rmid` / `HTTPD:` / `verifyObjectTrust`/`SecurityException` markers; a sub‑second green action
  is a non‑run.
- **Board review** of the implementer's diffs *and* run logs (both baseline and treatment) before anything is
  declared validated. Nothing committed/pushed/merged without Peter's word.
