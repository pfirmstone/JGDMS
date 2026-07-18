---
name: jgdms-polp-audit-policy-gen
description: Generate or extend a least-privilege java.policy file for JGDMS/DirtyChai services and tests using DirtyChai's polpAudit SecurityManager (au.zeus.jdk.authorization.tool.SecurityPolicyWriter). Use when a test/service needs new permission grants added to a policy file, when policy-generation properties (polpAudit.path.properties) need setting, or when a manually-edited policy file's grants aren't taking effect. Triggers on "generate a policy file", "missing permission", "polpAudit", "SecurityPolicyWriter", "-Djava.security.manager=polpAudit", "least-privilege policy", "AccessControlException during test runs".
---

# Generating JGDMS/DirtyChai policy files with polpAudit

DirtyChai ships an audit-mode SecurityManager, `au.zeus.jdk.authorization.tool.SecurityPolicyWriter`
(`src/java.base/share/classes/au/zeus/jdk/authorization/tool/SecurityPolicyWriter.java` in the
DirtyChai repo), installed via `-Djava.security.manager=polpAudit`. It never denies anything — every
`checkPermission` call returns `true` — but it records every permission actually requested and, at
JVM shutdown, appends whatever isn't already covered by the currently-loaded policy to the policy
file. Use it to *derive* a least-privilege policy from a real test run instead of hand-guessing grants.

DirtyChai is AI-contribution-restricted (analyze/advise only — humans write DirtyChai source; see its
`CLAUDE.md`). This skill is about *using* the already-built tool from JGDMS/qa, not modifying it.

## Minimum setup

```
-Djava.security.manager=polpAudit
-Djava.security.policy=<path-to-policy-file>   # created if it doesn't exist
```

Run the service or test normally. On exit, the shutdown hook writes any newly-observed, not-yet-granted
permissions as `grant { ... }` blocks appended to the file.

## Making generated grants portable

The raw output is tied to the runtime that generated it — absolute file paths, the local hostname/IP
baked into `SocketPermission`/`URLPermission`. Two ways to handle this, per the tool's JavaDoc:

- **`-DpolpAudit.path.properties=<properties-file>`** — a `key=value` properties file where each
  `value` is a literal path/string fragment to search for and `key` is the property name to
  substitute; generated output gets `${key}` in place of that literal instead of the raw string.
  Entries in this file must not reference other properties declared in the same file.
  `java.io.tmpdir`, `java.home`, and (if set) `jsk.home`/`qa.home` are substituted automatically even
  without this file.
- Otherwise, **hand-edit after generation** before reusing the file elsewhere — this is expected,
  documented behavior, not a workaround.

## The append-only / widen-first workflow

Each run only *adds* permissions the current policy doesn't already imply — it never removes or
narrows anything. That means:

1. **Widen scope before your next integration-test run, not after.** If a generated `SocketPermission`
   is scoped to one literal local IP and the real deployment needs a broader range, edit the file to
   widen it first. Run more tests before widening and you just accumulate extra narrow entries
   alongside the one you'll eventually broaden — harmless but noisy, not wrong.
2. **Check the `net.jini.security.policy` logger (set to `Level.CONFIG`) after every manual edit.** A
   syntax error in the edited file causes the *entire* file's grants to be silently ignored by
   `Policy` — so the next run treats everything as still-missing and re-adds it, producing what looks
   like the tool misbehaving when the real cause is a typo in the last edit.
3. **A one-shot/ephemeral grant never gets baked into the generated floor.** If a permission is denied
   by the stable policy but allowed by a live ephemeral grant (e.g. a `LeasedPermissionGrant`
   `OneShot` escalation — see the `jgdms-leased-permission-grant` memory), `SecurityPolicyWriter`
   observes the operation as allowed and deliberately does *not* record the permission: recording it
   would silently promote a transient, human-gated escalation into a permanent baseline grant. If a
   permission genuinely belongs in the baseline, add it by hand.

## What it deliberately leaves out

Per the tool's JavaDoc: the generated file never includes the `ProtectionDomain` of the jar
containing `SecurityPolicyWriter` itself, nor platform/`java.*` module jars. It only captures what
your application/test code needed — not JDK-internal privileged operations. Treat the output as a
floor to build on, not a finished, deployable policy.

## Recognizing which SM is installed

`System.java`'s `-Djava.security.manager=` switch recognizes three built-in values:

| Value | Installs |
|---|---|
| `default` | `CombinerSecurityManager` (production) |
| `legacy` | plain `java.lang.SecurityManager` |
| `polpAudit` | `SecurityPolicyWriter` (this tool) |

Any other value is treated as a fully-qualified custom `SecurityManager` class name, loaded via
reflection.

## Cross-reference

Once you have raw recorded grants from a `polpAudit` run, the `jgdms-policy-condenser` skill
covers the next step: consolidating/minimizing them with `PolicyCondenser` and deriving proxy
`GrantPermission` ceilings and `PERMISSIONS.LIST` manifests with `ProxyPolicyGenerator`, via the
`qa/build.xml` `policy-update` targets.
