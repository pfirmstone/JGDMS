# AI Context Document — JGDMS QA SSL Activation Debugging Session

## Session Overview

Debugging `AdminIFTest.td` (Mahalo) in the JGDMS QA harness under the `jsse` configuration. A long chain of failures was resolved one by one, leaving one final root cause identified but not yet fixed before the reboot.

---

## Repository & Environment

- **Workspace root:** `C:\Users\peter\Documents\GitHub\JGDMS\JGDMS\`
- **Git repo root:** `C:\Users\peter\Documents\GitHub\JGDMS`
- **Branch:** `copilot/the-granola-paper-benefits`
- **JDK:** Java 27 / DirtyChai
- **Build system:** Maven (jars) + Ant (`qa/`)
- **Test runner:** `cd qa && ant run-tests` (or similar), using `QARunner` with `fork="yes"`
- **Artifact dist dir:** `JGDMS/dist/target/JGDMS-3.1.1-SNAPSHOT/`

---

## What Was Fixed (Already Applied & Verified)

| # | File | What was changed |
|---|------|-----------------|
| 1 | `JGDMS/jgdms-platform/src/main/java/net/jini/security/Security.java` | DirtyChai-compatible `AccessControlContext.create(...)` |
| 2 | Various policy files | Restored malformed `grant` blocks |
| 3 | `qa/harness/configs/jsse/phoenix/phoenix.config` | Added `groupConfig` entry pointing to `sharedgroup.config` inside `jiniharness.jar` |
| 4 | `qa/harness/configs/jsse/sharedgroup/sharedgroup.config` | Added `org.apache.river.phoenix` config block with `loginContext`, `instantiatorExporter` using `SslServerEndpoint` + `AtomicILFactory`; corrected `InstantiatorPermission` to `org.apache.river.phoenix.group.InstantiatorPermission` |
| 5 | `qa/src/org/apache/river/qa/resources/qaDefaults.properties` | Added `phoenix-group.jar` to `testClasspath` and `altClasspath` so child test JVM can resolve config imports |
| 6 | `qa/build.xml` | Added `phoenix-group-${jgdms.version}.jar` and `phoenix-dl-${jgdms.version}.jar` to harness runner classpath |

All of the above were **rebuilt and verified** (`ant jars` succeeded; `jiniharness.jar` contents confirmed).

---

## Current State of Key Files

### `qa/harness/configs/jsse/phoenix/phoenix.config`
- Has `loginContext = new LoginContext("org.apache.river.Phoenix")`
- Has `groupConfig` pointing to `sharedgroup.config`
- Has `instantiatorPreparer` requiring `ServerMinPrincipal(principal.phoenix)` (i.e., `CN=Phoenix`)
- Has `instantiatorExporter` using `SslServerEndpoint.getInstance(0)` with `AtomicILFactory`
- `inheritGroupSubject = true`
- **`groupOptions` is currently commented out** — this is the root cause of the remaining failure (see below)

### `qa/harness/configs/jsse/sharedgroup/sharedgroup.config`
- `org.apache.river.phoenix` block has `loginContext = new LoginContext("org.apache.river.Phoenix")`
- `instantiatorExporter` uses `SslServerEndpoint.getInstance(0)` + `AtomicILFactory(groupConstraints, InstantiatorPermission.class, ActivationGroupImpl.class)`
- `org.apache.river.start.group` block has `loginContext = new LoginContext("org.apache.river.Test")`

### `qa/harness/trust/jsselogins`
- `org.apache.river.Phoenix` → `keyStoreAlias="phoenix"` → `CN=Phoenix`
- `org.apache.river.Test` → `keyStoreAlias="tester"` → `CN=Tester`

### `qa/harness/trust/spiffelogins`
- Already exists with entries for all roles including `org.apache.river.Phoenix` (serviceRole="phoenix") and `org.apache.river.Group` (serviceRole="group")
- `gen-spiffe-svids.sh` exists at `qa/harness/trust/gen-spiffe-svids.sh` but SVIDs have **not yet been generated**

---

## Root Cause of Remaining Failure

**The group JVM spawned by Phoenix is a fresh process and does NOT inherit system properties.**

The `configSet.properties` sets these global VM args for the Phoenix process:
```
-Djava.security.auth.login.config=<url:harness/trust/jsselogins>
-Djavax.net.ssl.trustStore=<file:harness/trust/truststore>
-Djavax.net.ssl.trustStorePassword=trustpw
-Dorg.apache.river.discovery.x500.trustStore=<url:harness/trust/truststore>
-Djava.security.properties=<url:harness/trust/dynamic-policy.properties>
-Djava.security.policy=...
```

But the **group JVM** (spawned by `Activation.buildGroupProcess()`) does NOT get these properties because `groupOptions` in `phoenix.config` is commented out. Without `java.security.auth.login.config`, the JAAS login in the group JVM fails silently — it can't find `org.apache.river.Phoenix`, loads no credentials, and the SSL handshake fails with `SSL_NULL_WITH_NULL_NULL`.

The error sequence in the last log:
1. `Group-0` → `SslListenHandle[null:56457]` → `Handshake failed: SSL_NULL_WITH_NULL_NULL` (group has no credentials)
2. Phoenix client → `Remote principal is not trusted` (server cert rejected, handshake aborted)

---

## The Fix to Apply Next

**Edit `qa/harness/configs/jsse/phoenix/phoenix.config`.**

Replace the commented-out `groupOptions` block (lines ~95–100):
```java
    //groupTimeout = 1200000;
    //groupOptions = new String[] {
    //    "-Djava.security.properties=/vob/qa/configs/jeri/phoenix/debug-security.props"
    //    ,
    //    "-Djava.security.debug=access,failure"
    //};
```

With:

```java
    //groupTimeout = 1200000;
    groupOptions = new String[] {
        "-Djava.security.auth.login.config=${java.security.auth.login.config}",
        "-Djavax.net.ssl.trustStore=${javax.net.ssl.trustStore}",
        "-Djavax.net.ssl.trustStorePassword=${javax.net.ssl.trustStorePassword}",
        "-Dorg.apache.river.discovery.x500.trustStore=${org.apache.river.discovery.x500.trustStore}",
        "-Djava.security.properties=${java.security.properties}",
        "-Djava.security.policy=${java.security.policy}"
    };
```

The `${...}` substitutions are evaluated by `ConfigurationFile` using system properties from the Phoenix JVM at parse time — exactly the values set by the harness global VM args.

---

## After Applying the Fix

1. `cd qa && ant jars` — rebuild `jiniharness.jar`
2. Rerun `org/apache/river/test/impl/mahalo/AdminIFTest.td`
3. If SSL handshake now succeeds but policy denials appear, check `qa/harness/policy/defaultsecuresharedvm.policy` and `defaultsecuregroup.policy` for missing `AuthenticationPermission` or `SocketPermission` grants for `CN=Phoenix`.

---

## Longer-Term Simplification (User's Suggestion)

The user noted: *"This will be a lot simpler with Spiffe JVM process principals."*

Infrastructure already exists:
- `SpiffeLoginModule`, `SpiffePrincipal`, `SpiffeCredentialManager` in `jgdms-jeri`
- `qa/harness/trust/spiffelogins` with entries for all roles
- `qa/harness/trust/gen-spiffe-svids.sh` to generate SVIDs

Path forward: generate SVIDs, switch `configSet.properties` to use `spiffelogins`, and replace `KeyStores.getX500Principal` principal lookups in configs with `SpiffePrincipal` constraints — eliminating the per-role keystore identity coupling.

---

*Good luck with the reboot! 🙂*

No problem! Let me try the edit again — the previous attempt failed due to CRLF line endings. Let me read the exact bytes first.
Good — now I can see the exact content. The file uses CRLF. I'll write the replacement via PowerShell directly:
Now verify the result looks correct:
The edit is applied correctly. Now rebuild:
Build successful. Now let's verify the updated `qaDefaults.properties` made it into the new `jiniharness.jar` and that `phoenix.config` is bundled correctly:
The fix is confirmed inside `jiniharness.jar`. The `groupOptions` entry is live at line 96. 

**Summary of what was done:**
- Uncommented and populated `groupOptions` in `qa/harness/configs/jsse/phoenix/phoenix.config`
- It now passes 6 system properties (`java.security.auth.login.config`, `javax.net.ssl.trustStore`, `javax.net.ssl.trustStorePassword`, `org.apache.river.discovery.x500.trustStore`, `java.security.properties`, `java.security.policy`) from the Phoenix JVM into the spawned group JVM
- `ant jars` rebuilt successfully and the new config is confirmed inside `jiniharness.jar`

You can now rerun `org/apache/river/test/impl/mahalo/AdminIFTest.td`. The group JVM should now load its credentials and the SSL handshake with Phoenix should succeed.