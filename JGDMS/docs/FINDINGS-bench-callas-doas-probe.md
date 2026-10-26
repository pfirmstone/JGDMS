# Bench probe: principal-scoped policy grant under Subject.callAs vs Subject.doAs (DirtyChai JDK)

Status: **COMPLETE**

Agent: Bench (independent empirical measurement — did not read
`docs/FINDINGS-combiner-callas-doas.md`, per instructions).

## Bottom line (see Interpretation for caveats)

In this specific DirtyChai build, a `grant principal ... { permission ... }`
policy entry **resolved identically** under `Subject.callAs(subject, ...)`
and under `Subject.doAs(subject, ...)`. No behavioral difference between the
two was observed in this probe. `Subject.current()` and
`Subject.getSubject(AccessController.getContext())` both showed the correct
Subject under both mechanisms, and both correctly reverted to `null` outside
the scoped block. A control condition (Subject with a *different* principal)
confirms the effect is genuinely principal-matched, not just "any Subject
present → permission granted."

This runs against **raw observations** below — read those first.

## Environment

- JDK under test: `/home/user/GitHub/DirtyChai/build/linux-x86_64-server-release/images/jdk`
- `java -version` output:
  ```
  openjdk version "27-internal" 2026-09-15
  OpenJDK Runtime Environment (build 27-internal-adhoc.user.DirtyChai)
  OpenJDK 64-Bit Server VM (build 27-internal-adhoc.user.DirtyChai, mixed mode, sharing)
  ```
- Policy provider: `sun.security.provider.PolicyFile` (from
  `conf/security/java.security`: `policy.provider=sun.security.provider.PolicyFile`).
- No interfering `~/.java.policy` (confirmed absent — `ls` returned "No such
  file or directory").
- Default `conf/security/java.policy` (always additionally consulted, since
  the probe uses single `-Djava.security.policy=` not `==`) grants only a
  fixed set of harmless unconditional read-only PropertyPermissions
  (java.version, os.name, java.vendor, etc., plus `SocketPermission
  "localhost:0","listen"`) — critically, it does **not** grant `user.home`
  read or `probe.test` read to anyone, so it cannot leak into either the
  permission-under-test or the sentinel check.
- `javap javax.security.auth.Subject` on this JDK shows DirtyChai-specific
  additions vs a stock JDK: a `UserSubject` subtype, `processWorker()`,
  `currentAllNoCheck()`, and a `callAs(Callable, UserSubject...)` overload —
  in addition to the standard `callAs(Subject, Callable)`, `doAs(Subject,
  PrivilegedAction)`, `doAs(Subject, PrivilegedExceptionAction)`, `current()`,
  and `getSubject(AccessControlContext)` (deprecated-for-removal but still
  present and functional). **Both `callAs` and `doAs` exist in this build.**
- The installed `-Djava.security.manager=default` SecurityManager is
  **`au.zeus.jdk.authorization.sm.CombinerSecurityManager`**, a DirtyChai
  custom class (confirmed both by exception stack traces at runtime and by
  `javap au.zeus.jdk.authorization.sm.CombinerSecurityManager`), not the
  stock `java.lang.SecurityManager`. This is a materially different code
  path from a vanilla OpenJDK build and should be kept in mind when
  generalizing this result.
- Getting a runnable probe past this JDK's bootstrap required granting,
  **unconditionally** (not tied to codeBase or principal — see policy file
  below), several custom DirtyChai permissions that have nothing to do with
  the property under test: `au.zeus.jdk.authorization.guards.LoadClassPermission`,
  `au.zeus.jdk.authorization.guards.DefineClassPermission` (needed for
  `LambdaMetafactory` to spin the lambda classes used as the `Callable`/
  `PrivilegedAction` arguments), `java.net.URLPermission "jrt:/jdk.localedata"
  "GET:"` (pulled in via `DigestCodeSource`'s file-digest computation → a
  `Collator`/CLDR locale lookup during class loading — surprising, but
  observed directly in the stack trace), and — most relevantly —
  `javax.security.auth.AuthPermission "getSubject"`, `"callAs"`, and `"doAs"`
  are themselves gated: code needs explicit permission just to **invoke**
  `Subject.callAs`/`Subject.doAs`/`Subject.getSubject` at all in this JDK,
  independent of whether the principal-scoped grant resolves inside them.
  These are documented as raw observations in "Bootstrap iteration" below.

## Probe design

Directory: `/tmp/claude-1000/-home-user-GitHub/7782d0b3-a7a8-4b99-b04a-6e22c5907fa2/scratchpad/bench/`

- `probe.policy` — grants `PropertyPermission "probe.test","read"` ONLY to
  principal `javax.security.auth.x500.X500Principal "CN=Probe"`, plus the
  unconditional bootstrap-infrastructure permissions above, plus a narrowly
  scoped codeBase grant (FilePermission read only, not AllPermission) so the
  probe's own class files can be loaded. Grants nothing else — in
  particular does NOT grant `user.home` read, or `probe.test` read, to
  anyone unconditionally or to any other principal.
- `src/Probe.java` — full source below.
- Conditions tested, each running: `Subject.current()` and
  `Subject.getSubject(AccessController.getContext())` visibility, a direct
  `SecurityManager.checkPermission(new PropertyPermission("probe.test","read"))`
  check, an indirect `System.getProperty("probe.test")` check, and a
  sentinel check (`PropertyPermission "user.home","read"`, granted to nobody
  anywhere — must be denied in ALL conditions or the probe is void):
  1. **baseline** — no Subject established
  2. **`Subject.callAs(subject, callable)`** where `subject` carries
     `X500Principal("CN=Probe")`
  3. **post-callAs** — visibility only, back outside the block
  4. **`Subject.doAs(subject, PrivilegedAction)`** — same subject
  5. **post-doAs** — visibility only, back outside the block
  6. **control: `Subject.callAs` with a Subject carrying `X500Principal("CN=NotProbe")`**
     (wrong principal — must be denied)
  7. **control: `Subject.doAs` with the same wrong-principal Subject**

### Probe source (`src/Probe.java`, final version — the one whose output is reported below)

```java
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.PropertyPermission;

/**
 * Bench probe: does a principal-scoped policy grant resolve for code
 * running under Subject.callAs(subject, action) in the DirtyChai JDK?
 *
 * Run with:
 *   java -Djava.security.manager=default \
 *        -Djava.security.policy=probe.policy \
 *        -cp classes Probe
 */
public class Probe {

    static void checkSM() {
        SecurityManager sm = System.getSecurityManager();
        System.out.println("[env] SecurityManager installed: " + (sm != null) + "  class=" + (sm == null ? "null" : sm.getClass().getName()));
    }

    /** Sentinel permission that is NOT granted to anyone, anywhere, in our policy.
     *  Must be denied in EVERY condition. If it ever succeeds, the SM/policy is
     *  not actually restricting anything and the whole probe is void. */
    static void checkSentinel(String label) {
        try {
            String v = System.getProperty("user.home");
            System.out.println("[" + label + "] SENTINEL(user.home) READ SUCCEEDED val=" + v + "  <<<< UNEXPECTED - policy not enforced, probe is VOID");
        } catch (SecurityException e) {
            System.out.println("[" + label + "] SENTINEL(user.home) READ DENIED as expected: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    /** The permission under test: granted only to principal X500Principal CN=Probe. */
    static void checkProbePermission_viaGetProperty(String label) {
        try {
            String v = System.getProperty("probe.test");
            System.out.println("[" + label + "] probe.test via System.getProperty(): SUCCESS val=" + v);
        } catch (SecurityException e) {
            System.out.println("[" + label + "] probe.test via System.getProperty(): DENIED " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    static void checkProbePermission_viaDirectCheck(String label) {
        SecurityManager sm = System.getSecurityManager();
        if (sm == null) {
            System.out.println("[" + label + "] probe.test via direct SM.checkPermission(): SKIPPED (no SM)");
            return;
        }
        try {
            sm.checkPermission(new PropertyPermission("probe.test", "read"));
            System.out.println("[" + label + "] probe.test via direct SM.checkPermission(): SUCCESS (no exception)");
        } catch (SecurityException e) {
            System.out.println("[" + label + "] probe.test via direct SM.checkPermission(): DENIED " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    static void reportSubjectVisibility(String label) {
        try {
            Subject s = Subject.current();
            System.out.println("[" + label + "] Subject.current() = " + describe(s));
        } catch (Throwable t) {
            System.out.println("[" + label + "] Subject.current() THREW: " + t);
        }
        try {
            Subject s2 = Subject.getSubject(AccessController.getContext());
            System.out.println("[" + label + "] Subject.getSubject(AccessController.getContext()) = " + describe(s2));
        } catch (Throwable t) {
            System.out.println("[" + label + "] Subject.getSubject(ACC) THREW: " + t);
        }
    }

    static String describe(Subject s) {
        if (s == null) return "null";
        return s.toString() + "  principals=" + s.getPrincipals();
    }

    static void runFullCheck(String label) {
        reportSubjectVisibility(label);
        checkProbePermission_viaGetProperty(label);
        checkProbePermission_viaDirectCheck(label);
        checkSentinel(label);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("========== BENCH PROBE: principal-scoped policy grant under callAs vs doAs ==========");
        checkSM();
        System.out.println();

        Subject subj = new Subject(
                false,
                Collections.singleton(new X500Principal("CN=Probe")),
                Collections.emptySet(),
                Collections.emptySet());
        System.out.println("[setup] built Subject with principal CN=Probe: " + describe(subj));
        System.out.println();

        // CONDITION 1: baseline, no Subject established at all
        System.out.println("=== CONDITION 1: baseline (no Subject established) ===");
        runFullCheck("baseline");
        System.out.println();

        // CONDITION 2: Subject.callAs
        System.out.println("=== CONDITION 2: Subject.callAs(subject, callable) ===");
        try {
            Subject.callAs(subj, () -> {
                runFullCheck("callAs");
                return null;
            });
        } catch (Throwable t) {
            System.out.println("[callAs] OUTER exception escaped callAs: " + t);
            t.printStackTrace(System.out);
        }
        System.out.println();

        // post-callAs: does Subject visibility revert?
        System.out.println("=== POST-callAs (back outside callAs block) ===");
        reportSubjectVisibility("post-callAs");
        System.out.println();

        // CONDITION 3: Subject.doAs
        System.out.println("=== CONDITION 3: Subject.doAs(subject, PrivilegedAction) ===");
        try {
            Subject.doAs(subj, (PrivilegedAction<Void>) () -> {
                runFullCheck("doAs");
                return null;
            });
        } catch (Throwable t) {
            System.out.println("[doAs] OUTER exception escaped doAs: " + t);
            t.printStackTrace(System.out);
        }
        System.out.println();

        System.out.println("=== POST-doAs (back outside doAs block) ===");
        reportSubjectVisibility("post-doAs");
        System.out.println();

        // CONTROL: a Subject with a DIFFERENT principal must NOT get the grant,
        // under callAs. Distinguishes "principal-matched grant resolved" from
        // "any Subject present -> permission granted".
        Subject wrongSubj = new Subject(
                false,
                Collections.singleton(new X500Principal("CN=NotProbe")),
                Collections.emptySet(),
                Collections.emptySet());
        System.out.println("=== CONTROL: Subject.callAs with WRONG principal (CN=NotProbe) ===");
        try {
            Subject.callAs(wrongSubj, () -> {
                runFullCheck("callAs-wrongPrincipal");
                return null;
            });
        } catch (Throwable t) {
            System.out.println("[callAs-wrongPrincipal] OUTER exception escaped callAs: " + t);
            t.printStackTrace(System.out);
        }
        System.out.println();

        System.out.println("=== CONTROL: Subject.doAs with WRONG principal (CN=NotProbe) ===");
        try {
            Subject.doAs(wrongSubj, (PrivilegedAction<Void>) () -> {
                runFullCheck("doAs-wrongPrincipal");
                return null;
            });
        } catch (Throwable t) {
            System.out.println("[doAs-wrongPrincipal] OUTER exception escaped doAs: " + t);
            t.printStackTrace(System.out);
        }
        System.out.println();

        System.out.println("========== PROBE COMPLETE ==========");
    }
}
```

### `probe.policy` (final version)

```
// Bench probe policy: grant a permission ONLY to a principal-scoped subject.
// Deliberately does NOT grant AllPermission or user.home read to anyone,
// so the "sentinel" check (user.home read) must be denied in every
// condition -- if it ever succeeds, the SecurityManager/policy is not
// actually restricting anything and the probe result is void.

// Infrastructure grant: unconditional (no codeBase/principal), needed to get
// past DirtyChai's DigestCodeSource class-loading bootstrap machinery, which
// (surprisingly) pulls in locale/CLDR module loading. Unrelated to the
// permission under test (probe.test) or the sentinel (user.home), so it does
// not compromise the differential measurement.
grant {
    permission java.net.URLPermission "jrt:/jdk.localedata", "GET:";
    permission au.zeus.jdk.authorization.guards.LoadClassPermission;
    permission au.zeus.jdk.authorization.guards.DefineClassPermission;
    permission javax.security.auth.AuthPermission "getSubject";
    permission javax.security.auth.AuthPermission "callAs";
    permission javax.security.auth.AuthPermission "doAs";
};

// Infrastructure grant: allow the probe's own classes to be loaded and run.
// Deliberately minimal (FilePermission read only) -- NOT AllPermission --
// so it cannot leak into the permission-under-test or the sentinel check.
grant codeBase "file:/tmp/claude-1000/-home-user-GitHub/7782d0b3-a7a8-4b99-b04a-6e22c5907fa2/scratchpad/bench/classes/-" {
    permission java.io.FilePermission "/tmp/claude-1000/-home-user-GitHub/7782d0b3-a7a8-4b99-b04a-6e22c5907fa2/scratchpad/bench/classes/-", "read";
    permission java.io.FilePermission "/tmp/claude-1000/-home-user-GitHub/7782d0b3-a7a8-4b99-b04a-6e22c5907fa2/scratchpad/bench/classes", "read";
    permission java.io.FilePermission "file:/tmp/claude-1000/-home-user-GitHub/7782d0b3-a7a8-4b99-b04a-6e22c5907fa2/scratchpad/bench/classes/", "read";
    permission java.io.FilePermission "file:/tmp/claude-1000/-home-user-GitHub/7782d0b3-a7a8-4b99-b04a-6e22c5907fa2/scratchpad/bench/classes", "read";
    permission java.lang.RuntimePermission "createClassLoader";
    permission java.lang.RuntimePermission "accessDeclaredMembers";
    permission java.lang.RuntimePermission "getClassLoader";
};

grant principal javax.security.auth.x500.X500Principal "CN=Probe" {
    permission java.util.PropertyPermission "probe.test", "read";
};
```

### Build & run commands (exact, reproducible)

```
JDK=/home/user/GitHub/DirtyChai/build/linux-x86_64-server-release/images/jdk
cd /tmp/claude-1000/-home-user-GitHub/7782d0b3-a7a8-4b99-b04a-6e22c5907fa2/scratchpad/bench
"$JDK/bin/javac" -d classes src/Probe.java
"$JDK/bin/java" -Djava.security.manager=default \
     -Djava.security.policy=probe.policy \
     -cp classes Probe
```

---

## Raw output

### Bootstrap iteration (raw evidence that SM/policy really gates class loading in this JDK)

The very first run attempt, with only the `grant principal ...` block in the
policy (no infrastructure grants), failed before `Probe.main` ever executed,
denied while trying to *load the Probe class itself*:

```
Error: A JNI error has occurred, please check your installation and try again
Exception in thread "main" java.security.AccessControlException: access denied ("java.io.FilePermission" "file:/tmp/.../bench/classes/" "read")
	at java.base/au.zeus.jdk.authorization.sm.CombinerSecurityManager.checkPermission(CombinerSecurityManager.java:333)
	at java.base/java.security.SecureClassLoader.getProtectionDomain(SecureClassLoader.java:362)
	...
```

After granting FilePermission on the classes directory, the next denial (still
before `main` runs) was much deeper — a locale/CLDR ServiceLoader chain
triggered by `DigestCodeSource.computeDigest()` needing `URLPermission
"jrt:/jdk.localedata" "GET:"`:

```
Caused by: java.security.AccessControlException: access denied ("java.net.URLPermission" "jrt:/jdk.localedata" "GET:")
	at java.base/au.zeus.jdk.authorization.sm.CombinerSecurityManager.checkPermission(CombinerSecurityManager.java:333)
	...
	at java.base/sun.util.cldr.CLDRLocaleProviderAdapter.<init>(CLDRLocaleProviderAdapter.java:83)
```

After granting that, next denial was a DirtyChai-custom
`au.zeus.jdk.authorization.guards.LoadClassPermission "ALLOW"`, required just
to define/load a class at all under this SM — even for internal JDK service
classes:

```
Caused by: java.security.AccessControlException: access denied ("au.zeus.jdk.authorization.guards.LoadClassPermission" "ALLOW")
	at java.base/au.zeus.jdk.authorization.sm.CombinerSecurityManager.checkPermission(CombinerSecurityManager.java:333)
	at java.base/java.security.SecureClassLoader.getProtectionDomain(SecureClassLoader.java:379)
```

After granting `LoadClassPermission` (and the earlier grants), `Probe.main`
finally started running, but the lambda expressions used for the `Callable`/
`PrivilegedAction` arguments failed to spin their hidden classes:

```
[callAs] OUTER exception escaped callAs: java.lang.InternalError: java.security.AccessControlException: access denied ("au.zeus.jdk.authorization.guards.DefineClassPermission" "ALLOW")
	at java.base/java.lang.invoke.InnerClassLambdaMetafactory.generateInnerClass(InnerClassLambdaMetafactory.java:362)
	...
Caused by: java.security.AccessControlException: access denied ("au.zeus.jdk.authorization.guards.DefineClassPermission" "ALLOW")
	at java.base/au.zeus.jdk.authorization.sm.CombinerSecurityManager.checkPermission(CombinerSecurityManager.java:333)
	at java.base/java.lang.invoke.MethodHandles$Lookup$ClassDefiner.defineClass(MethodHandles.java:2482)
```

After granting `DefineClassPermission`, the lambdas ran, but
`Subject.current()`/`Subject.getSubject(...)` themselves threw, denied on
`javax.security.auth.AuthPermission "getSubject"`:

```
[baseline] Subject.current() THREW: java.security.AccessControlException: access denied ("javax.security.auth.AuthPermission" "getSubject")
[baseline] Subject.getSubject(ACC) THREW: java.security.AccessControlException: access denied ("javax.security.auth.AuthPermission" "getSubject")
```
(baseline probe.test / user.home checks correctly denied in this same run —
first confirmation the sentinel design works.)

After granting `AuthPermission "getSubject"`, `Subject.callAs` and
`Subject.doAs` themselves threw, denied on `AuthPermission "callAs"` /
`"doAs"` respectively:

```
[callAs] OUTER exception escaped callAs: java.security.AccessControlException: access denied ("javax.security.auth.AuthPermission" "callAs")
	at java.base/au.zeus.jdk.authorization.sm.CombinerSecurityManager.checkPermission(CombinerSecurityManager.java:333)
	at java.base/au.zeus.jdk.authorization.sm.CombinerSecurityManager.checkPermission(CombinerSecurityManager.java:226)
	at java.base/javax.security.auth.Subject.callAs(Subject.java:562)
	at Probe.main(Probe.java:108)

[doAs] OUTER exception escaped doAs: java.security.AccessControlException: access denied ("javax.security.auth.AuthPermission" "doAs")
	at java.base/au.zeus.jdk.authorization.sm.CombinerSecurityManager.checkPermission(CombinerSecurityManager.java:333)
	at java.base/au.zeus.jdk.authorization.sm.CombinerSecurityManager.checkPermission(CombinerSecurityManager.java:226)
	at java.base/javax.security.auth.Subject.doAs(Subject.java:734)
	at Probe.main(Probe.java:126)
```

After granting `AuthPermission "callAs"` and `"doAs"` (final policy state,
shown above), the probe ran clean end-to-end. This full bootstrap chain is
itself a finding: in this JDK, **using `Subject.callAs`/`doAs`/`current`/
`getSubject` at all requires explicit `AuthPermission` grants that don't
exist in a default/vanilla setup** — a stricter posture than stock OpenJDK,
where these calls do not require any permission by default.

### Final clean run (`run9.out`, byte-identical to `run10.out`/`run11.out` on two repeat runs modulo object hashcodes)

Exit code: `0`

```
========== BENCH PROBE: principal-scoped policy grant under callAs vs doAs ==========
[env] SecurityManager installed: true  class=au.zeus.jdk.authorization.sm.CombinerSecurityManager

[setup] built Subject with principal CN=Probe: Subject:
	Principal: CN=Probe
  principals=javax.security.auth.Subject$SecureSet@557935be

=== CONDITION 1: baseline (no Subject established) ===
[baseline] Subject.current() = null
[baseline] Subject.getSubject(AccessController.getContext()) = null
[baseline] probe.test via System.getProperty(): DENIED java.security.AccessControlException: access denied ("java.util.PropertyPermission" "probe.test" "read")
[baseline] probe.test via direct SM.checkPermission(): DENIED java.security.AccessControlException: access denied ("java.util.PropertyPermission" "probe.test" "read")
[baseline] SENTINEL(user.home) READ DENIED as expected: java.security.AccessControlException: access denied ("java.util.PropertyPermission" "user.home" "read")

=== CONDITION 2: Subject.callAs(subject, callable) ===
[callAs] Subject.current() = Subject:
	Principal: CN=Probe
  principals=javax.security.auth.Subject$SecureSet@557935be
[callAs] Subject.getSubject(AccessController.getContext()) = Subject:
	Principal: CN=Probe
  principals=javax.security.auth.Subject$SecureSet@557935be
[callAs] probe.test via System.getProperty(): SUCCESS val=null
[callAs] probe.test via direct SM.checkPermission(): SUCCESS (no exception)
[callAs] SENTINEL(user.home) READ DENIED as expected: java.security.AccessControlException: access denied ("java.util.PropertyPermission" "user.home" "read")

=== POST-callAs (back outside callAs block) ===
[post-callAs] Subject.current() = null
[post-callAs] Subject.getSubject(AccessController.getContext()) = null

=== CONDITION 3: Subject.doAs(subject, PrivilegedAction) ===
[doAs] Subject.current() = Subject:
	Principal: CN=Probe
  principals=javax.security.auth.Subject$SecureSet@557935be
[doAs] Subject.getSubject(AccessController.getContext()) = Subject:
	Principal: CN=Probe
  principals=javax.security.auth.Subject$SecureSet@557935be
[doAs] probe.test via System.getProperty(): SUCCESS val=null
[doAs] probe.test via direct SM.checkPermission(): SUCCESS (no exception)
[doAs] SENTINEL(user.home) READ DENIED as expected: java.security.AccessControlException: access denied ("java.util.PropertyPermission" "user.home" "read")

=== POST-doAs (back outside doAs block) ===
[post-doAs] Subject.current() = null
[post-doAs] Subject.getSubject(AccessController.getContext()) = null

=== CONTROL: Subject.callAs with WRONG principal (CN=NotProbe) ===
[callAs-wrongPrincipal] Subject.current() = Subject:
	Principal: CN=NotProbe
  principals=javax.security.auth.Subject$SecureSet@3f3517ef
[callAs-wrongPrincipal] Subject.getSubject(AccessController.getContext()) = Subject:
	Principal: CN=NotProbe
  principals=javax.security.auth.Subject$SecureSet@3f3517ef
[callAs-wrongPrincipal] probe.test via System.getProperty(): DENIED java.security.AccessControlException: access denied ("java.util.PropertyPermission" "probe.test" "read")
[callAs-wrongPrincipal] probe.test via direct SM.checkPermission(): DENIED java.security.AccessControlException: access denied ("java.util.PropertyPermission" "probe.test" "read")
[callAs-wrongPrincipal] SENTINEL(user.home) READ DENIED as expected: java.security.AccessControlException: access denied ("java.util.PropertyPermission" "user.home" "read")

=== CONTROL: Subject.doAs with WRONG principal (CN=NotProbe) ===
[doAs-wrongPrincipal] Subject.current() = Subject:
	Principal: CN=NotProbe
  principals=javax.security.auth.Subject$SecureSet@3f3517ef
[doAs-wrongPrincipal] Subject.getSubject(AccessController.getContext()) = Subject:
	Principal: CN=NotProbe
  principals=javax.security.auth.Subject$SecureSet@3f3517ef
[doAs-wrongPrincipal] probe.test via System.getProperty(): DENIED java.security.AccessControlException: access denied ("java.util.PropertyPermission" "probe.test" "read")
[doAs-wrongPrincipal] probe.test via direct SM.checkPermission(): DENIED java.security.AccessControlException: access denied ("java.util.PropertyPermission" "probe.test" "read")
[doAs-wrongPrincipal] SENTINEL(user.home) READ DENIED as expected: java.security.AccessControlException: access denied ("java.util.PropertyPermission" "user.home" "read")

========== PROBE COMPLETE ==========
```

Verified reproducible: re-ran the identical command two more times
(`run10.out`, `run11.out`); `diff` against `run9.out` (after normalizing
`@<hexhashcode>` tokens, which vary run-to-run as expected for
`Object.hashCode()`-based `toString()`) showed **zero differences** — same
SUCCESS/DENIED pattern all three times.

## Interpretation

*(clearly separated from the raw data above; this is analysis, not
measurement)*

1. **The core question has a clean, reproducible YES**: in this specific
   DirtyChai build (custom `CombinerSecurityManager` + whatever policy
   engine backs `sun.security.provider.PolicyFile`/`ConcurrentPolicyFile`),
   a principal-scoped `grant principal X500Principal "CN=Probe" { ... }`
   entry **does resolve** for code running inside
   `Subject.callAs(subject, action)`, exactly as it does inside
   `Subject.doAs(subject, action)`. No asymmetry between the two was
   observed on any of the 5 signals checked (`Subject.current()`,
   `Subject.getSubject(ACC)`, direct `SecurityManager.checkPermission()`,
   indirect `System.getProperty()`, and the negative control with a
   mismatched principal).

2. **Caveat — this is not necessarily representative of a stock/vanilla
   OpenJDK.** The installed SecurityManager here is DirtyChai's own
   `au.zeus.jdk.authorization.sm.CombinerSecurityManager`, not
   `java.lang.SecurityManager`, and DirtyChai's `Subject.java` is visibly
   patched (extra `UserSubject`/`processWorker`/`currentAllNoCheck` API not
   present upstream). It is plausible — though *not measured here* — that
   this is precisely the point of the DirtyChai fork: making `callAs`
   behave like `doAs` for principal-scoped policy resolution, where a
   stock/upstream JDK (whose `callAs` is documented as ScopedValue-based per
   JEP 411, without installing a classic `SubjectDomainCombiner`) might
   not. This probe was run **only** against the DirtyChai build named in
   the task; it says nothing directly about stock OpenJDK behavior. Anyone
   reconciling this against a source-level/upstream-behavior argument
   should keep that scope boundary in mind.

3. **Caveat — the "unconditional" bootstrap grants are plausible but not
   individually proven inert.** The bootstrap permissions added to make the
   probe runnable at all (`LoadClassPermission`, `DefineClassPermission`,
   the `jrt:/jdk.localedata` URLPermission, and the three `AuthPermission`
   entries) are unconditional (no codeBase, no principal), so by
   construction they apply equally to the baseline, callAs, doAs, and
   wrong-principal conditions — they cannot be the source of the observed
   *difference* between the "CN=Probe" and "CN=NotProbe"/baseline
   conditions, since they're identical across all conditions and don't
   mention `probe.test` or `user.home`. But I have not independently
   verified each is strictly necessary and minimal (I added them
   reactively, one denial at a time, until the probe ran) — it's possible
   fewer would suffice. This does not threaten the core finding, since the
   differential signal (probe.test succeeds only under the right principal)
   is what's being measured, but it's noted for completeness.

4. **Notable independent finding, not the target question but observed
   directly**: `AuthPermission "getSubject"`, `"callAs"`, and `"doAs"` are
   themselves permission-gated in this JDK — plain code cannot call
   `Subject.current()`, `Subject.getSubject(...)`, `Subject.callAs(...)`, or
   `Subject.doAs(...)` at all without an explicit grant. This is a stricter
   posture than stock OpenJDK's `javax.security.auth.Subject`, where these
   calls are unguarded by default. Whether this is by design (matching
   DirtyChai's stated "polpAudit"/least-privilege authorization model per
   `CLAUDE.md`) or an artifact of running with no other grants than a
   minimal custom policy was not investigated further — it fell out of the
   probe construction rather than being the thing under test.

5. Not tested here: whether the result changes for a `Subject` obtained
   from a real login (`LoginContext`) rather than constructed directly, or
   for permission types other than `PropertyPermission` (e.g. `FilePermission`,
   a JGDMS-specific permission), or under nested/concurrent `callAs`
   invocations. Also not tested: whether `Subject.doAsPrivileged` (also
   present in this JDK's `Subject` API per `javap`) behaves the same way.
