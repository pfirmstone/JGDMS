---
name: jgdms-policy-condenser
description: Consolidate and minimize raw polpAudit-recorded policy grants into a clean, deployable JGDMS policy set using org.apache.river.tool.PolicyCondenser, PolicyUpdatePostProcessor, and ProxyPolicyGenerator (tools/policy-condenser module). Use when policy files need condensing/deduplicating after a polpAudit run, when deriving GrantPermission ceilings or META-INF/PERMISSIONS.LIST for proxy codebases, or when running the qa/build.xml policy-update targets. Triggers on "condense the policy", "PolicyCondenser", "policy-update target", "GrantPermission ceiling", "PERMISSIONS.LIST", "proxy policy generator", "policy file has too many redundant grants".
---

# JGDMS Policy Condenser & `policy-update` pipeline

The `tools/policy-condenser` module plus the `qa/build.xml` `policy-update` targets turn raw,
per-run permission grants recorded by DirtyChai's `polpAudit` (see the `jgdms-polp-audit-policy-gen`
skill) into a clean, minimal, deployable policy set. Three tools run together as one pipeline:

1. **`SecurityPolicyWriter`** (DirtyChai, `-Djava.security.manager=polpAudit`) — records raw
   per-run grants. Covered in `jgdms-polp-audit-policy-gen`.
2. **`org.apache.river.tool.PolicyCondenser`** — consolidates and minimizes those grants.
3. **`org.apache.river.tool.ProxyPolicyGenerator`** — derives a `GrantPermission` ceiling and
   `META-INF/PERMISSIONS.LIST` manifest for each proxy codebase found in the condensed policy.

All three are orchestrated by **`org.apache.river.tool.PolicyUpdatePostProcessor`**, which the
`qa/build.xml` `policy-update` targets invoke automatically.

## Running it

```
# Full QA suite
ant -Dpolicy.update=true policy-update

# Scoped to specific test categories
ant -Drun.categories=txnmanager,start policy-update-categories

# Scoped to a single test
ant -Drun.tests=org/apache/river/test/impl/mahalo/LeaseTest.td policy-update-tests
```

Each target runs the tests with `-Dorg.apache.river.qa.harness.securitymanager=polpAudit`
(`QAConfig.getGlobalVMArgs()` substitutes this into the child JVMs' `-Djava.security.manager`),
then runs `PolicyUpdatePostProcessor --qa-dir <qa-dir>`.

**Build dependency across build systems:** the ant target loads
`org.apache.river.tool.PolicyUpdatePostProcessor` from the Maven build output of
`tools/policy-condenser` (`mvn package` under that module first). Default jar location is
`JGDMS/tools/policy-condenser/target/policy-condenser-<version>.jar`; override with
`-Dpolicy.condenser.jar=<path>` if it's stale or elsewhere.

## `PolicyCondenser`: what "condense" means

For each policy file:

- Merges grant blocks that are `impliesEquivalent` (same codebase/principals/signers) into one,
  unioning their permissions.
- De-duplicates exact-equal permissions.
- Eliminates any permission implied by a broader one **within the same grant** — e.g. a narrow,
  per-run `FilePermission "<tmpdir>/run1234/-"` collapses under a broader
  `FilePermission "<tmpdir>/-"`; a captured `SocketPermission host:port` collapses under
  `SocketPermission "*"` if that's already granted. This is what actually shrinks the many
  narrow, run-specific grants `SecurityPolicyWriter` captures (fresh temp dirs, ephemeral ports)
  down to something reviewable.
- Reduction is conservative, never unsound: relative-path/non-absolute `FilePermission` entries
  (whose `implies()` is working-directory-dependent) are **never** eliminated, even if something
  else appears to imply them at condense time — over-keeping is always safe, dropping wouldn't be.
- Optional JWT principal filtering: `-DPolicyCondenser.jwt.roleClaims=group,role` drops any
  `net.jini.security.jwt.JwtPrincipal` entry whose claim name isn't in that list from the output.
  A grant block that loses *all* its principals this way is dropped entirely, so you never end up
  with an accidentally-unconstrained grant.

Can run standalone: `java -cp policy-condenser.jar:... org.apache.river.tool.PolicyCondenser
<policy-file> [<policy-file> ...]` — writes `<policy-file>.con` next to each input. It does not
replace the original in place; that atomic replace is `PolicyUpdatePostProcessor`'s job, not
`PolicyCondenser`'s.

## `PolicyUpdatePostProcessor`: don't let the condenser touch a negative-test policy file

Some `.policy` files are *intentionally* under-permissioned — a test expects a
`SecurityException`. If `polpAudit` were allowed to run against those and the resulting grants got
condensed in like any other file, the next normal run would grant the very permission the test
relies on it *not* having, silently breaking the test. `PolicyUpdatePostProcessor` protects these:

1. Builds an exclusion set of policy-file basenames from two sources: the hard-coded
   `qa/harness/policy/policy-update-exclusions.properties`, and every `.td` test descriptor with
   `policy.no.update=true` (its `testPolicyfile` property names the file).
2. For every excluded file found under the qa tree: `git checkout --` it, discarding whatever
   `SecurityPolicyWriter` wrote during the run.
3. For every other `.policy` file: run `PolicyCondenser` and atomically replace the original in
   place.
4. For each condensed file (unless `-Dpolicy.update.generate.proxy=false`): run
   `ProxyPolicyGenerator` — failures here are logged and swallowed, never fail the overall run.

**If you add a new test that expects a `SecurityException`, add it to the exclusion list (or its
`.td`'s `policy.no.update=true`) *before* running `policy-update`** — otherwise the run will
silently grant away the permission the test is checking for.

## `ProxyPolicyGenerator`: ceiling + manifest for proxy codebases

For each condensed grant block that looks proxy-targeted, derives two artefacts a developer would
otherwise hand-author:

- `<policy-file>.grantperm` — the minimal `GrantPermission` ceiling that admits exactly what the
  proxy exercised, no broader (bounds what may later be dynamically granted to that proxy).
- `<out>/<artefact>/META-INF/PERMISSIONS.LIST` — the proxy's declared-needs manifest (default
  `<out>` is `<policy-file>.proxy-permissions/`), permissions encoded via
  `AdvisoryPermissionParser` so they round-trip.

A grant block is recognized as proxy-targeted by **codebase shape, not permission content**:
either it carries a `digest` clause (content-addressed `DigestCodeSource` — the strongest signal),
or its `codebase` matches the default regex (`httpmd:` scheme, or a `*-dl.jar`/`*-dl-<version>.jar`
download-artifact filename) — overridable via
`-Dorg.apache.river.tool.ProxyPolicyGenerator.proxy.codebase.regex` or `--proxy-regex`. Permission
*shape* (`AccessPermission`, `AuthenticationPermission`, `net.jini.jeri.*` endpoint permissions) is
only used as a corroborating confidence note in the output — never as the classification key,
unless `--strict` is passed, which requires shape corroboration in addition to the codebase match.
`GrantPermission`/`UmbrellaGrantPermission`/`AllPermission` are excluded from `PERMISSIONS.LIST` —
they're policy-grant meta-capabilities, not a proxy's own declared needs.

Fail-closed: a proxy codebase that exercised no non-meta permission gets no manifest and no
ceiling at all — absence means "observed nothing," not "grant everything."

## Cross-reference

Upstream of this pipeline: the `jgdms-polp-audit-policy-gen` skill (DirtyChai's
`polpAudit`/`SecurityPolicyWriter` — what it records, and its one-shot-grant exclusion). This
skill is the next step once you have raw recorded grants to consolidate.
