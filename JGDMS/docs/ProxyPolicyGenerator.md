# ProxyPolicyGenerator — GrantPermission ceiling + PERMISSIONS.LIST tool

*Implements `docs/SOW-GrantPermission-PolicyTool.md`.*

`org.apache.river.tool.ProxyPolicyGenerator` removes two hand-authoring chores
from the policy writer by deriving them from an audited run:

1. the **minimal `GrantPermission` ceiling** that bounds what may be dynamically
   granted to a proxy, and
2. the per-codebase **`META-INF/PERMISSIONS.LIST`** declared-needs manifest.

It is a post-processor over the least-privilege policy produced by the
`polpAudit` workflow (`SecurityPolicyWriter` → `PolicyCondenser`). It lives in
the `policy-condenser` tool module, package `org.apache.river.tool`, alongside
`SecurityPolicyWriter`, `PolicyCondenser` and `PolicyUpdatePostProcessor`.

## Where it sits in the meet

The effective grant to a proxy is the conjunction of three terms. `polpAudit`
already produces the **scoped least-privilege grant blocks** (the floor). This
tool derives the other two least-automated terms:

| Term | Authored by | Produced here |
|------|-------------|---------------|
| Scoped grant block (floor) | `polpAudit` (`SecurityPolicyWriter`) | — |
| `GrantPermission` ceiling (delegation bound) | hand, today | **yes** (`<policy>.grantperm`) |
| `PERMISSIONS.LIST` (declared-needs cap) | hand, today | **yes** (`<artefact>/META-INF/PERMISSIONS.LIST`) |

## Usage

```
java -cp policy-condenser.jar:jgdms-platform.jar \
     org.apache.river.tool.ProxyPolicyGenerator \
     [--out <dir>] [--proxy-regex <regex>] [--strict] [--properties <file>] \
     <policy-file> [<policy-file> ...]
```

For each `<policy-file>` it writes:

* `<policy-file>.grantperm` — a policy fragment with the synthesised
  `GrantPermission` ceiling grant blocks, and
* `<out>/<artefact>/META-INF/PERMISSIONS.LIST` per proxy codebase
  (default `<out>` is `<policy-file>.proxy-permissions`).

In the QA harness it runs automatically as step 3 of `PolicyUpdatePostProcessor`
after each `ant -Dpolicy.update=true policy-update` run (disable with
`-Dpolicy.update.generate.proxy=false`).

## Proxy identification

The **hard key** is the codebase / artefact naming convention. A grant block is
proxy-targeted when:

* it carries a `digest` scope clause (a content-addressed `DigestCodeSource`
  grant — the strongest signal), **or**
* its `codebase` matches the proxy-artefact regex. The default matches the
  `httpmd:` integrity-protected scheme and the JGDMS download/proxy artefact
  convention `*-dl.jar` / `*-dl-<version>.jar` (e.g. `mahalo-dl.jar`,
  `reggie-dl-3.1.1.jar`). Confirmed against the modules that ship a
  `PERMISSIONS.LIST` today — they are exactly the `*-dl` download modules.

Override the regex with `--proxy-regex` or
`-Dorg.apache.river.tool.ProxyPolicyGenerator.proxy.codebase.regex=…`.

The **permission-set shape** (`AccessPermission`, `AuthenticationPermission`,
`net.jini.jeri`/`net.jini.export`/`net.jini.io.context` permissions) is used
only as a corroborating confidence annotation in the output — never as the sole
key. With `--strict`, a corroborating proxy-shape permission is additionally
*required* before a codebase is treated as proxy-targeted.

## Guarantees

* **Round-trip.** `PERMISSIONS.LIST` entries are encoded with
  `AdvisoryPermissionParser.getEncoded(...)` (so they parse back via
  `AdvisoryPermissionParser.parse`); each ceiling is validated by constructing a
  `GrantPermission` from its target string and the emitted grant block parses
  back via `DefaultPolicyParser`. (Note: policy fragments use `//` comments;
  `PERMISSIONS.LIST` uses `#`/`//`.)
* **Minimality.** A ceiling lists exactly the proxy's exercised non-meta
  permissions — no broader.
* **Fail-closed.** A codebase that exercised no non-meta permission yields no
  manifest and no ceiling; meta-capabilities (`GrantPermission`,
  `UmbrellaGrantPermission`, `AllPermission`) are excluded from
  `PERMISSIONS.LIST` and the ceiling.
* **Property substitution.** `--properties <file>` (property-name = literal
  value) replaces literal paths/hosts with `${property}` placeholders in emitted
  targets, longest value first.

## Worked example

Input `example.policy` (as produced/condensed by `polpAudit`):

```
grant codebase "file:/opt/jgdms/lib-dl/mahalo-dl.jar" {
    permission java.lang.reflect.ReflectPermission "newProxyInPackage.org.apache.river.mahalo", "";
    permission net.jini.security.AccessPermission "*";
    permission java.lang.RuntimePermission "accessClassInPackage.com.sun.proxy", "";
    permission net.jini.security.GrantPermission "net.jini.security.AccessPermission \"*\"";
};
grant codebase "file:/opt/jgdms/lib/mahalo.jar" {
    permission java.security.AllPermission;
};
```

Run:

```
java ... org.apache.river.tool.ProxyPolicyGenerator example.policy
```

Generated `example.policy.grantperm` (the non-proxy `mahalo.jar` grant is
ignored; the meta `GrantPermission`/`AllPermission` are not absorbed):

```
// <Apache licence header>
//
// GrantPermission ceilings synthesised by ProxyPolicyGenerator from
//   example.policy
// ...
// proxy codebase: file:/opt/jgdms/lib-dl/mahalo-dl.jar
// proxy-shape permissions present: true
grant codebase "file:/opt/jgdms/lib-dl/mahalo-dl.jar" {
    permission net.jini.security.GrantPermission "java.lang.RuntimePermission \"accessClassInPackage.com.sun.proxy\"; java.lang.reflect.ReflectPermission \"newProxyInPackage.org.apache.river.mahalo\"; net.jini.security.AccessPermission \"*\";";
};
```

Generated `example.policy.proxy-permissions/mahalo-dl/META-INF/PERMISSIONS.LIST`:

```
# <Apache licence header>
# Declared-needs manifest for proxy codebase:
#   file:/opt/jgdms/lib-dl/mahalo-dl.jar
# Generated by ProxyPolicyGenerator (do not edit by hand).
(java.lang.RuntimePermission "accessClassInPackage.com.sun.proxy")
(java.lang.reflect.ReflectPermission "newProxyInPackage.org.apache.river.mahalo")
(net.jini.security.AccessPermission "*")
```

The ceiling is scoped, by default, to the proxy codebase itself (per-codebase
granularity). Before deployment, **attach** each ceiling to the grant block of
the domain that actually calls `DynamicPolicy.grant` for the proxy (the service
/ exporter), re-scoping the `codebase`/`principal` clauses for that granting
role — the fragment's header comment says so.

## SOW open questions — decisions taken

1. **Canonical proxy-artefact naming.** Hard key = `*-dl.jar` / `httpmd:` /
   `digest` scope (matches the modules that ship `PERMISSIONS.LIST` today).
   Configurable via `--proxy-regex`.
2. **Audit-time vs post-process.** *Post-process* over the recorded
   least-privilege policy — testable, round-trips through the existing parser,
   and keeps `SecurityPolicyWriter` simple. Mirrors `PolicyCondenser`.
3. **Heuristic aggressiveness.** Name convention is the sole hard key;
   permission-set shape is corroboration/annotation only (opt-in `--strict`
   requires both).
4. **Ceiling granularity.** *Per proxy codebase*, scoped to that codebase by
   default, with a header note to re-attach to the granting role at deploy time.
