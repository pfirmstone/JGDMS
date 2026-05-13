# JGDMS — Restrictive Policy Files — AI Agent Context

**Date:** 2026-05-13  
**Branch:** `copilot/create-kubernetes-container-jgdms-services`  
**Relates to:** `deploy/policy/host{1-5}.policy`, `SecurityPolicyWriter`, DirtyChai `polpAudit`

This document records the full analysis of over-permissive JRE module grants in the
JGDMS deploy policy files, explains the correct strategy for tightening them using the
`SecurityPolicyWriter` (DirtyChai `polpAudit`) tooling, and documents per-module
permission requirements discovered during this session.  Future agents should start
here before touching any `deploy/policy/*.policy` or QA policy file.

---

## 1. Problem Statement

Every `deploy/policy/host{1-5}.policy` file contains a block like:

```
grant codebase "jrt:/java.base"              { permission java.security.AllPermission; };
grant codebase "jrt:/java.logging"           { permission java.security.AllPermission; };
grant codebase "jrt:/jdk.localedata"         { permission java.security.AllPermission; };
grant codebase "jrt:/java.security.jgss"     { permission java.security.AllPermission; };
grant codebase "jrt:/jdk.crypto.cryptoki"    { permission java.security.AllPermission; };
grant codebase "jrt:/jdk.crypto.ec"          { permission java.security.AllPermission; };
grant codebase "jrt:/java.xml.crypto"        { permission java.security.AllPermission; };
```

These grants have two problems:

1. **`jrt:/java.base` is a no-op.** The bootstrap classloader's ProtectionDomains are
   fully trusted by the JVM and are never evaluated against the policy.
   `ConcurrentPolicyFile.implies()` never sees a request from a `java.base` class. The
   grant is harmless but adds noise and gives a false impression that `java.base` needs
   explicit policy treatment.

2. **Other `jrt:/` module grants are wider than necessary.** `AllPermission` gives those
   modules unconstrained access.  Under DirtyChai's `ConcurrentPolicyFile`, all
   `jrt:/`-codebase ProtectionDomains **are** evaluated against the policy (they have a
   real `CodeSource` with a `jrt:` URL).  If their `AllPermission` grants are removed,
   `ConcurrentPolicyFile.implies()` will enforce the principle of least privilege on
   them just as it does on application JARs.

Additionally, `grant codebase "file:${jgdms.lib}/*" { permission java.security.AllPermission; }`
on all five hosts means every JGDMS service JAR is unconditionally fully trusted.  This
is a reasonable bootstrap posture but should be replaced with minimal specific grants
once the actual permission needs are profiled.

---

## 2. How `ConcurrentPolicyFile` Handles `jrt:` Modules

Key path in
`org.apache.river.api.security.ConcurrentPolicyFile.implies(ProtectionDomain, Permission)`:

```java
// line 449 — static AllPermission short-circuit
PermissionCollection staticPC = domain.getPermissions();
if (staticPC != null) {
    Enumeration<Permission> e = staticPC.elements();
    while (e.hasMoreElements()) {
        Permission p = e.nextElement();
        if (p instanceof AllPermission) return true;   // ← early exit
        ...
    }
}
```

A ProtectionDomain's *static* permissions (baked in at construction by the
ClassLoader) are checked before the policy grants.  For most application classes the
static PermissionCollection is `null` or empty, so the policy governs.  For JRE module
classes loaded by the **bootstrap** (`null`) ClassLoader (e.g. `java.base`,
`java.security`) the static PermissionCollection typically carries `AllPermission`,
which causes the early-exit at line 449 regardless of what the policy says.

For JRE modules loaded by the **platform** ClassLoader (e.g. `java.logging`,
`jdk.crypto.cryptoki`, `java.xml.crypto`) the static PermissionCollection is generally
*empty*, so `ConcurrentPolicyFile` **does** evaluate them against the policy.  Removing
or narrowing their `jrt:` grants therefore has real effect.

**Practical implication:** you can safely remove `grant codebase "jrt:/java.base"` lines
with no effect.  You can replace other `jrt:/ AllPermission` grants with specific
permissions — the policy engine will enforce them.

---

## 3. `SecurityPolicyWriter` / DirtyChai `polpAudit` — How It Works

`SecurityPolicyWriter` (`JGDMS/tools/security-policy-debug/…/SecurityPolicyWriter.java`)
extends `CombinerSecurityManager`.  It overrides `checkPermission(ProtectionDomain, Permission)`
to record every `(ProtectionDomain → Permission)` pair seen at runtime into a concurrent
map.  On JVM shutdown its hook writes any pair **not already implied by the current
baseline policy** into the output policy file.

The filter is at shutdown-hook line 376:

```java
if (POLICY != null && POLICY.implies(pd, p) || pc.implies(p)) continue;
```

**Critical insight:** If the baseline policy already grants `AllPermission` to
`jrt:/java.logging`, then `POLICY.implies(loggingDomain, anyPermission)` returns `true`
for every permission, and `SecurityPolicyWriter` writes **nothing** for `java.logging`.
You will only get the minimal specific grants written if you **first remove the
`AllPermission` baseline grant** for that module.

DirtyChai's `polpAudit` flag activates the same mechanism via the DirtyChai JVM option
`-polpAudit`, which is equivalent to:

```
-Djava.security.manager=org.apache.river.tool.SecurityPolicyWriter
-Djava.security.policy=<output-policy-file>
```

### 3.1 The correct iterative workflow

```
# Phase 1: baseline — verify removal of java.base lines is safe
#   Remove jrt:/java.base grant from every deploy policy file.
#   Run integration tests.  No regressions expected.

# Phase 2: profile one module at a time
#   a. Remove: grant codebase "jrt:/java.logging" { permission java.security.AllPermission; };
#   b. Start service with polpAudit / SecurityPolicyWriter active.
#   c. Exercise all code paths (log rotation, level changes, handler registration…).
#   d. SecurityPolicyWriter now records what java.logging actually checked.
#   e. PolicyCondenser merges duplicate entries.
#   f. Repeat for each module.

# Phase 3: apply the same to service JARs
#   Remove AllPermission from file:${jgdms.lib}/*.
#   Run full QA with polpAudit.
#   SecurityPolicyWriter writes minimal grants per JAR.
```

The existing `ant -Dpolicy.update=true policy-update` target
(`qa/build.xml`) already automates Phase 3 for QA policy files.  The same pipeline can
be adapted for the `deploy/policy/` files.

---

## 4. Known Per-Module Permission Requirements

These are the permissions each JRE module is known to check at runtime in a typical
JGDMS deployment.  They should be used as the starting point for manual policy
construction when a full `polpAudit` run is not yet available.  Values here are
minimum-required; environments with file-based log handlers or PKCS#11 hardware tokens
may need additions.

### 4.1 `jrt:/java.base`

**Remove the grant entirely.** This module is loaded by the bootstrap ClassLoader;
its ProtectionDomains carry static `AllPermission` and are never evaluated by
`ConcurrentPolicyFile`.  Any `grant codebase "jrt:/java.base"` line is a harmless
no-op that should be deleted to reduce confusion.

### 4.2 `jrt:/java.logging`

```
grant codebase "jrt:/java.logging" {
    permission java.util.logging.LoggingPermission "control";
    permission java.io.FilePermission "${java.home}${/}conf${/}logging.properties", "read";
    /* If file handlers are used — add the log directory: */
    /* permission java.io.FilePermission "${log.dir}${/}-", "read,write"; */
    permission java.lang.RuntimePermission "setContextClassLoader";
    permission java.lang.RuntimePermission "getClassLoader";
    permission java.lang.RuntimePermission "accessDeclaredMembers";
    permission java.lang.reflect.ReflectPermission "suppressAccessChecks";
};
```

### 4.3 `jrt:/java.management` and `jrt:/jdk.management`

```
grant codebase "jrt:/java.management" {
    permission javax.management.MBeanServerPermission "createMBeanServer";
    permission javax.management.MBeanServerPermission "findMBeanServer";
    permission javax.management.MBeanTrustPermission "register";
    permission java.lang.RuntimePermission "createMBeanServer";
    permission java.lang.RuntimePermission "getMBeanServer";
    permission java.lang.RuntimePermission "accessDeclaredMembers";
    permission java.lang.RuntimePermission "setContextClassLoader";
};

grant codebase "jrt:/jdk.management" {
    permission javax.management.MBeanServerPermission "createMBeanServer";
    permission javax.management.MBeanTrustPermission "register";
    permission java.lang.RuntimePermission "accessDeclaredMembers";
};
```

### 4.4 `jrt:/jdk.localedata`

This module contains locale data resources only.  It requires no additional runtime
permissions beyond class-loading in all tested configurations.

```
grant codebase "jrt:/jdk.localedata" {
    /* No special permissions required. */
};
```

If the grant block is empty it can be omitted entirely.

### 4.5 `jrt:/java.security.jgss`

Only relevant on hosts using Kerberos (`qa/harness/policy/defaultsecure*.policy`).
Not used in the SPIFFE-only `deploy/policy/` files; the grant can be removed from
non-Kerberos deployments.  On Kerberos hosts:

```
grant codebase "jrt:/java.security.jgss" {
    permission javax.security.auth.AuthPermission "getSubject";
    permission javax.security.auth.AuthPermission "getSubjectFromDomainCombiner";
    permission javax.security.auth.AuthPermission "doAs";
    permission javax.security.auth.AuthPermission "doAsPrivileged";
    permission javax.security.auth.PrivateCredentialPermission
        "javax.security.auth.kerberos.KerberosKey * \"*\"", "read";
    permission javax.security.auth.kerberos.ServicePermission "*", "initiate";
    permission java.lang.RuntimePermission "accessDeclaredMembers";
};
```

### 4.6 `jrt:/jdk.crypto.cryptoki`

PKCS#11 bridge.  Requires loading the native library.  The exact library path is
platform-dependent.

```
grant codebase "jrt:/jdk.crypto.cryptoki" {
    permission java.lang.RuntimePermission "loadLibrary.j2pkcs11";
    permission java.security.SecurityPermission "insertProvider";
    permission java.security.SecurityPermission "putProviderProperty.SunPKCS11";
    permission java.lang.RuntimePermission "accessDeclaredMembers";
    permission java.lang.reflect.ReflectPermission "suppressAccessChecks";
    /* Path to the PKCS11 configuration file: */
    /* permission java.io.FilePermission "/etc/pkcs11/config", "read"; */
};
```

If no PKCS#11 hardware token is used the grant can be omitted entirely.

### 4.7 `jrt:/jdk.crypto.ec`

OpenJDK 17+ ships a pure-Java EC implementation (`SunEC`).  No native library is loaded
on current OpenJDK builds.  The module requires:

```
grant codebase "jrt:/jdk.crypto.ec" {
    permission java.security.SecurityPermission "insertProvider";
    permission java.security.SecurityPermission "putProviderProperty.SunEC";
    permission java.lang.RuntimePermission "accessDeclaredMembers";
};
```

### 4.8 `jrt:/java.xml.crypto`

XML Digital Signature (used for `xmldsig` in some JGDMS security providers).

```
grant codebase "jrt:/java.xml.crypto" {
    permission java.security.SecurityPermission "insertProvider";
    permission java.security.SecurityPermission "putProviderProperty.XMLDSig";
    permission java.lang.RuntimePermission "accessDeclaredMembers";
    permission java.lang.reflect.ReflectPermission "suppressAccessChecks";
};
```

### 4.9 `jrt:/jdk.security.auth` (QA harness only)

```
grant codebase "jrt:/jdk.security.auth" {
    permission javax.security.auth.AuthPermission "getSubject";
    permission javax.security.auth.AuthPermission "doAs";
    permission java.lang.RuntimePermission "accessDeclaredMembers";
};
```

### 4.10 `jrt:/org.openjsse` (OpenJSSE, QA harness only)

```
grant codebase "jrt:/org.openjsse" {
    permission java.security.SecurityPermission "insertProvider";
    permission java.net.SocketPermission "*", "connect,accept,resolve";
    permission java.lang.RuntimePermission "accessDeclaredMembers";
    permission java.lang.reflect.ReflectPermission "suppressAccessChecks";
};
```

---

## 5. Service JAR (`file:${jgdms.lib}/*`) Grants

All five hosts currently grant `AllPermission` to every JAR under `${jgdms.lib}/`.
This is the right starting posture but violates the principle of least privilege.

To tighten:

1. Run the service under DirtyChai with `polpAudit` or `SecurityPolicyWriter` active,
   with `AllPermission` still in the baseline (to avoid production breakage).
2. `SecurityPolicyWriter` captures nothing new (because `AllPermission` implies
   everything).
3. **Temporarily remove** `AllPermission` from `${jgdms.lib}/*` in a test environment.
4. Re-run with `SecurityPolicyWriter` — it will now write the minimal specific grants
   per JAR.
5. `PolicyCondenser` merges the resulting entries.
6. Replace `AllPermission` with the condensed specific grants in the permanent policy
   file.
7. Run QA with the tightened policy to confirm nothing regresses.

The QA `ant policy-update` target (`qa/build.xml`) already automates this for QA policy
files.  See `JGDMS/docs/AI_Agent_policy-update-context_2026-05-06.md` for the full
`PolicyUpdatePostProcessor` + `PolicyCondenser` pipeline details.

---

## 6. `DigestGrant` for Third-Party JARs

For JARs served from external URLs (e.g. libraries in `${jgdms.lib}` that are not
built from JGDMS source), use `DigestGrant` to pin the grant to the JAR's SHA-256
content hash:

```
grant digest "SHA-256:<hex>",
      codeBase "file:${jgdms.lib}/third-party-lib.jar" {
    permission java.net.SocketPermission "api.example.org:443", "connect,resolve";
};
```

This means a supply-chain substitution at the same path cannot inherit the grant.
`SecurityPolicyWriter` emits `digest` clauses automatically when the class was loaded
via a `DigestCodeSource` (DirtyChai `SecureClassLoader`).
See `JGDMS-STD-004` (`standard-policy-file-syntax.md`) for the full digest grant syntax.

---

## 7. Current State of `deploy/policy/` Files (as of 2026-05-13)

| File | JRE module `AllPermission` grants | `jrt:/java.base` grant |
|---|---|---|
| `host1-lookup.policy`  | `java.logging`, `java.management`, `jdk.management`, `jdk.localedata`, `java.security.jgss`, `jdk.crypto.cryptoki`, `jdk.crypto.ec`, `java.xml.crypto` | ✅ present (no-op) |
| `host2-bae.policy`     | `java.logging`, `jdk.localedata`, `jdk.crypto.cryptoki`, `jdk.crypto.ec`, `java.xml.crypto`, `java.security.jgss` | ✅ present (no-op) |
| `host3-registry.policy`| `java.logging`, `java.management`, `jdk.management`, `jdk.localedata`, `java.security.jgss`, `jdk.crypto.cryptoki`, `jdk.crypto.ec`, `java.xml.crypto` | ✅ present (no-op) |
| `host4-downloader.policy` | `java.logging`, `jdk.localedata`, `java.security.jgss`, `jdk.crypto.cryptoki`, `jdk.crypto.ec`, `java.xml.crypto` | ✅ present (no-op) |
| `host5-telemetry.policy` | `java.logging`, `jdk.localedata`, `java.security.jgss`, `jdk.crypto.cryptoki`, `jdk.crypto.ec`, `java.xml.crypto` | ✅ present (no-op) |

All five also grant `AllPermission` to `file:${jgdms.lib}/*`.

**Immediate safe change:** remove all `grant codebase "jrt:/java.base"` lines (five total,
one per file).  This is a no-op removal — zero risk.

**Phase-2 changes** (require `polpAudit` profiling run first): replace remaining
`jrt:/ AllPermission` grants with the minimal specific grants from §4.

---

## 8. Why `SecurityPolicyWriter` Does Not Output JRE Module Grants (By Default)

The Javadoc says: *"the file generated will not contain… Java platform jars."*

This is a consequence of the baseline filter at shutdown-hook line 376:

```java
if (POLICY != null && POLICY.implies(pd, p) || pc.implies(p)) continue;
```

If the baseline policy already grants `AllPermission` to a `jrt:` module, every
permission check from that module returns `true` from `POLICY.implies()` and is
silently skipped.  The module appears not to need anything because the baseline already
covers everything.

**To make `SecurityPolicyWriter` capture JRE module needs:** remove the module's
`AllPermission` grant from the baseline policy *before* running the profiling session.
`SecurityPolicyWriter` will then record only the permissions that module actually checks
and write them into the output file.  This is the correct two-pass approach:

- Pass 1: full `AllPermission` baseline → stable service → no output for modules
- Pass 2: remove target module's `AllPermission` → run `polpAudit` → get minimal grants
- Replace `AllPermission` with the Pass 2 output → run QA to confirm no regressions

---

## 9. QA Policy Files — Existing `AllPermission` Grants

The QA harness policies under `qa/harness/policy/` follow a similar pattern.  The
`ant policy-update` target already replaces `AllPermission` with minimal grants for
application JARs via `SecurityPolicyWriter` + `PolicyCondenser`.  The JRE module
`AllPermission` grants in the QA policies are subject to the same tightening strategy
described above.

Protected QA policy files (those for negative-permission tests) are excluded from
automatic condensation by:
- `qa/harness/policy/policy-update-exclusions.properties` — hard-coded basenames
- `policy.no.update=true` in individual `.td` test descriptor files

See `JGDMS/docs/AI_Agent_policy-update-context_2026-05-06.md` §1 for details.

---

## 10. Open Work Items

| # | Item | Priority | Notes |
|---|---|---|---|
| W-1 | Remove `jrt:/java.base` grants from all 5 `deploy/policy/` files | High | Safe no-op removal; do first |
| W-2 | Replace `jrt:/java.logging AllPermission` with specific grants (§4.2) | High | Affects all 5 hosts |
| W-3 | Replace `jrt:/jdk.crypto.cryptoki AllPermission` with specific grants (§4.6) | High | Affects all 5 hosts |
| W-4 | Replace `jrt:/jdk.crypto.ec AllPermission` with specific grants (§4.7) | High | Affects all 5 hosts |
| W-5 | Replace `jrt:/java.xml.crypto AllPermission` with specific grants (§4.8) | Medium | Affects all 5 hosts |
| W-6 | Remove `jrt:/java.security.jgss` from SPIFFE-only hosts (1–5) | Medium | Only needed for Kerberos |
| W-7 | Remove `jrt:/jdk.localedata` entirely (no permissions needed) | Low | §4.4 |
| W-8 | Remove `jrt:/java.management` / `jrt:/jdk.management` from hosts 2, 4, 5 | Medium | Only host 1 and 3 use JMX |
| W-9 | Profile `file:${jgdms.lib}/*` grants via polpAudit → replace `AllPermission` | Low | Requires full integration test run |
| W-10 | Add `DigestGrant` for third-party JARs in `${jgdms.lib}` | Low | Requires DirtyChai `SecureClassLoader` deployment |
| W-11 | Apply same analysis to `qa/harness/policy/` JRE module grants | Medium | Blocked on W-2..W-8 being verified |

---

## 11. Files and Tools Referenced

| File / Tool | Location | Purpose |
|---|---|---|
| `SecurityPolicyWriter` | `JGDMS/tools/security-policy-debug/…/SecurityPolicyWriter.java` | polpAudit — captures runtime permissions, generates minimal policy |
| `PolicyCondenser` | `JGDMS/tools/policy-condenser/…/PolicyCondenser.java` | Merges and deduplicates policy entries |
| `PolicyUpdatePostProcessor` | `JGDMS/tools/policy-condenser/…/PolicyUpdatePostProcessor.java` | Post-processing pipeline for QA policy files after test run |
| `ConcurrentPolicyFile` | `JGDMS/jgdms-platform/…/ConcurrentPolicyFile.java` | Runtime policy engine; evaluates `jrt:` module domains |
| `host1-lookup.policy` | `deploy/policy/host1-lookup.policy` | Reggie (Lookup Service) deploy policy |
| `host2-bae.policy` | `deploy/policy/host2-bae.policy` | Bytecode Analysis Engine deploy policy |
| `host3-registry.policy` | `deploy/policy/host3-registry.policy` | Verdict Registry deploy policy |
| `host4-downloader.policy` | `deploy/policy/host4-downloader.policy` | Codebase Downloader deploy policy |
| `host5-telemetry.policy` | `deploy/policy/host5-telemetry.policy` | JFR Telemetry Service deploy policy |
| `JGDMS-STD-004` | `JGDMS/docs/standard-policy-file-syntax.md` | Policy file syntax reference |
| `policy-update context` | `JGDMS/docs/AI_Agent_policy-update-context_2026-05-06.md` | PolicyUpdatePostProcessor pipeline details |
