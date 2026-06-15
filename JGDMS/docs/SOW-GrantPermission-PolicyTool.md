# Scope of Work — GrantPermission Policy Development Tool (+ PERMISSIONS.LIST generation)

*Draft SOW — 2026-06-15. Grounded in `agent-authority-code-review-2026-06-14.md`.*

## 1. Objective
Remove, as far as possible, the hand-authoring burden on developers and policy writers for the
two least-automated terms of the authorization meet:

1. the **`GrantPermission` ceilings** that bound what a caller may dynamically grant to a proxy, and
2. the per-codebase **`META-INF/PERMISSIONS.LIST`** declared-needs manifest for proxy codebases.

A tool should derive both from an audited run, scoped to the narrowest form that the observed
behaviour justifies — extending the existing `polpAudit` / `SecurityPolicyWriter` workflow.

## 2. Why now / context
- The review confirms `GrantPermission` is enforced as a **ceiling** on both the dynamic-grant path
  (`DynamicPolicyProvider.grant(Class,…):604-605` builds `new GrantPermission(permissions)` +
  `checkGuard`) and the remote-push path (`AbstractPolicy.checkCallerHasGrants():79-89`). It is the
  meta-capability that bounds delegation depth, but it is authored by hand today.
- `polpAudit` (`SecurityPolicyWriter`) already generates scoped least-privilege **grant blocks**
  (codebase/digest/principal clauses + observed permissions, `SecurityPolicyWriter.java:437-550`),
  **but** it does not synthesise the `GrantPermission` ceilings that bound *dynamic* grants to
  proxies, and it does not emit `PERMISSIONS.LIST`. (Note the review's correction: the floor is
  "scoped grant blocks", *not* "scoped GrantPermission" — a `GrantPermission` line only appears if
  the audited code itself exercised a `DynamicPolicy.grant`.)
- `PERMISSIONS.LIST` is the third gating term of the effective grant (the declared-needs cap); it is
  currently a developer chore. PHILOSOPHY.md names this exact problem: "the greatest complexity falls
  on the policy writer."

## 3. The gap
There is no tool that, given an audited deployment, will:
- recognise which dynamic grants are **proxy-targeted**, and
- emit (a) the minimal `GrantPermission` ceiling authorising those grants, and (b) the
  `PERMISSIONS.LIST` for each proxy codebase.

## 4. Proposed approach
Extend / complement `au.zeus.jdk.authorization.tool.SecurityPolicyWriter`:

1. **Identify proxy-targeted grants.**
   - *Primary signal:* the **artefact / codebase name convention** for proxy JARs (align with the
     SCAP/STD-002 codebase naming and `httpmd`/digest codebase form). A grant whose codebase matches
     the proxy-artefact convention is proxy-targeted.
   - *Secondary signal:* the **permission set** granted (heuristic — proxy grants tend to be the
     `AccessPermission`/endpoint/`AuthenticationPermission` shape). Use as corroboration, not sole key.
2. **Synthesise the `GrantPermission` ceiling** for the granting caller/role — the minimal
   `GrantPermission(...)` that admits exactly the observed proxy grants (no more), emitted as a
   reviewable grant block scoped by `digest`/`principal`/`codebase` as appropriate. Must round-trip
   through `DefaultPolicyParser`/`SecurityPolicyWriter` → policy file.
3. **Generate `META-INF/PERMISSIONS.LIST`** per proxy codebase — the declared-needs manifest matching
   the proxy's exercised permissions, so the proxy's `ProtectionDomain` declares exactly what it needs
   (the third meet term). Honour the same fail-closed + property-substitution rules as `polpAudit`
   (`replaceValuesWithProperties()`, `${work.dir}` etc.).

## 5. Deliverables
- The tool (in/alongside `…/authorization/tool`), wired into the `polpAudit` workflow.
- Emits: scoped `GrantPermission` ceiling grant blocks **and** `PERMISSIONS.LIST` files.
- Docs + an example round-trip.

## 6. Acceptance criteria
- Round-trips: generated artefacts parse back via the existing policy parser/writer.
- **Minimality:** generated `GrantPermission` ceilings admit exactly the observed proxy grants, no
  broader; generated `PERMISSIONS.LIST` matches the proxy's exercised permissions.
- **Fail-closed:** an unexercised path yields no grant (consistent with `polpAudit` observe-only,
  `SecurityPolicyWriter.checkPermission():334-356`); a deployment test case is the remedy.
- Property substitution applied (paths/hosts generalised), with each substitution visible in the diff.

## 7. Dependencies
`SecurityPolicyWriter`/`polpAudit`; `DynamicPolicyProvider.grant` path; `GrantPermission`/`Implier`;
`DigestGrant`/`DigestCodeSource`; the SCAP/STD-002 proxy codebase naming convention.

## 8. Out of scope
Runtime policy enforcement changes; the AI-agent layer (see `SOW-AI-Agent-Authority-Support.md`).

## 9. Open questions
- Canonical proxy-artefact naming spec (confirm against STD-002 / the `httpmd` codebase form).
- Generate at audit-time (inside the SM) or as a post-process over recorded data?
- How aggressive should the permission-set heuristic be vs. the name convention as the hard key?
- Granularity of the generated `GrantPermission` ceiling per role vs per codebase.
