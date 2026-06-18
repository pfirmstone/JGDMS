# SOW — Runtime schema-compatibility override for preferred classes

*Scope for a fresh agent / design effort. Self-contained. Filed 2026-06-18.
Follow-up to `SOW-PreferredList-Analyzer.md` and `PreferredList-Relocation-Findings.md`.*

## 1. Purpose

Move the **version / schema-compatibility** dimension of the Jini preferred-class
mechanism from the static, build-time `META-INF/PREFERRED.LIST` (where it cannot
be decided correctly) to a **runtime check** in the class-loading path. With this
in place, static lists can safely default to *share* (the analyzer already shows
no module needs static isolation prefers), and correctness for class versioning
is restored at the only place it can actually be evaluated — at runtime, against
the classes that are actually present.

## 2. Background — why a static list cannot decide this

`Preferred: true` (load the downloaded codebase's copy in preference to a
same-named local class) serves **two orthogonal purposes**:

1. **Isolation / type identity** — give a downloaded proxy its own static state
   and its own `<C,L>` identity. This is a *local bytecode fact* and is the
   Preferred-Class Analyzer's domain (`tools/preferred-list-analyzer`). In
   `jgdms-platform` and every `-dl` module, the analyzer found **no** real
   isolation prefers once the hazards were fixed lock-free (`UuidFactory` shared
   `SecureRandom`, `ProxyTrustExporter` `Cleaner`, `DelegationAbsoluteTime`
   `DateTimeFormatter`).
2. **Version / schema compatibility** — make the client use the *service's*
   version of a class so the service's serialized objects deserialize correctly,
   instead of a stale or differently-shaped local copy.

For a **wire type these two conflict**:
- *Share* → a single type identity (no `ClassCastException` across loaders), but
  the **local** version is used — wrong if its schema differs from the wire data.
- *Prefer* → the **codebase's** (schema-correct) version is used, but it has a
  distinct identity per codebase — `ClassCastException` risk when an instance
  crosses the loader divide.

The correct choice depends on whether the local class is schema-compatible with
the codebase's — and **that is unknowable at compile time** (the analyzer sees a
single snapshot; what a client has locally is a deployment property). Therefore
no static list, analyzer-generated or hand-maintained, can decide the version
dimension. The legacy `Preferred: true` defaults are simply the *conservative*
answer ("always use the codebase's classes"): version-safe, but they break type
identity for any wire type that is also present locally.

## 3. Proposed mechanism

In the preferred resolution path, when a class `C` is **not** statically
preferred (share) **and** a same-named class exists in the parent/local loader,
compare the two schemas and **escalate share → prefer on mismatch**:

```
resolvePreferred(C):
    if static list says Preferred: true        -> prefer (unchanged)
    else if no local same-named C               -> moot (codebase copy loaded anyway)
    else if schema(codebaseC) == schema(localC) -> share  (use local; identities unify)
    else                                        -> prefer (use codebase; version-correct)
```

Properties:
- **One-directional**: only share → prefer. A statically-preferred class stays
  preferred. (Preferring is always version-safe; the override only *adds* safety
  to the share default.)
- **Fail-safe**: if a schema cannot be determined, default to **prefer** (the
  conservative, version-safe choice) — never silently share an unknown.
- Triggered only for **boundary classes** (those present in both the codebase and
  the parent), so it is off the hot path for the bulk of proxy-only classes.

## 4. What "schema" means and how to compute it (no class loading)

Compute a schema digest from **bytecode**, consistent with the analyzer's
no-class-loading design (avoids static-init side effects and the DirtyChai
embedded-copy gotcha):

- **`@AtomicSerial` types** — the canonical source is `public static SerialForm[]
  serialForm()` (field names + declared types). Digest the normalised
  `SerialForm`. This is the most precise notion of "serial schema" and JGDMS
  already defines it.
- **Plain `Serializable` types** — `serialPersistentFields` if declared, else the
  default serial fields (non-static, non-transient) + `serialVersionUID`.
- **Non-serializable behaviour classes** — there is no serial schema; a class is
  not a wire type, so schema-mismatch deserialization is not the concern. Options:
  treat as share (behaviour classes rarely need version isolation), or fall back
  to a whole-`.class` digest if behavioural-version isolation is wanted. Decide
  in design (see §7).

Reuse existing infrastructure: STD-008 already carries a `schemaDigest` for
`MarshalledInstance` payloads, and `@AtomicSerial` `SerialForm` is the per-class
serial schema — the runtime check is largely *digest these and compare*.

## 5. Where it hooks

`net.jini.loader.pref.PreferredClassProvider` / `PreferredClassLoader` /
`PreferredResources` (in `jgdms-platform`). The decision point is where the
provider currently consults the static `PREFERRED.LIST` for a class; the override
augments that lookup with the local-class schema comparison. **Investigate the
exact resolution path** before implementing — in particular how the parent/local
class is located (to read its bytecode for the digest) without forcing its
initialisation.

## 6. Interaction with the static lists and the analyzer

- Static `PREFERRED.LIST` reduces to **isolation prefers only** — and the
  analyzer shows there are none (all hazards fixed lock-free) — so the lists
  become *share-by-default / empty*. "Never statically preferred" is the end
  state, as intended.
- The analyzer keeps its value: (a) prove no isolation prefers are needed (or
  surface any that are, for a lock-free fix); (b) audit existing lists for wire
  types wrongly statically preferred (e.g. `outrigger-dl/EntryRep`).
- The override file (`META-INF/preferred-overrides.txt`) remains the home for any
  residual human isolation judgement (e.g. the `UuidFactory` deliberate share).

## 7. Key open question — does `@AtomicSerial` already obviate this for wire types?

`@AtomicSerial`'s `GetArg` reads fields **by name with defaulting**, i.e. it is
designed for **schema evolution**: a local class may deserialize a service's
differently-shaped object via `SerialForm` even when the schemas differ. If that
tolerance is sufficient in practice, then for `@AtomicSerial` wire types
schema-mismatch is *already handled by the serialization layer*, and they can
safely **share** with no runtime override at all — leaving the override needed
only for **legacy plain-`Serializable`** types (and any behavioural-version
concern). This materially narrows the scope and must be settled first: audit
whether `@AtomicSerial` round-trips across schema differences for the relevant
types, and whether any wire types on the boundary are still plain `Serializable`.

## 8. Performance & safety

- Cache digests per `(codebase, class)` and per local class; compute lazily,
  only on the first boundary-class resolution.
- Fail-safe to **prefer** on any uncertainty (unreadable bytecode, unknown
  schema): never weakens version-correctness.
- No new blocking on the hot path; digesting is bytecode hashing, bounded.

## 9. Scope

**In:** the runtime schema-compat check + share→prefer escalation in the
preferred provider; the bytecode schema-digest computation (reusing
`SerialForm`/STD-008); the §7 audit that decides whether `@AtomicSerial` types are
in scope; caching; fail-safe behaviour.
**Out (separate):** the static analyzer (done); flipping the `-dl` static lists
(PAUSED — safe only once this runtime override exists, or once §7 shows
`@AtomicSerial` tolerance suffices); trust/codebase-identity gating (DigestGrant,
separate concern).

## 10. References
- `docs/SOW-PreferredList-Analyzer.md`, `docs/PreferredList-Relocation-Findings.md`.
- Runtime mechanism: `net.jini.loader.pref.PreferredClassProvider` /
  `PreferredClassLoader` / `PreferredResources`.
- Schema: `org.apache.river.api.io.AtomicSerial` `SerialForm`; STD-008
  `MarshalledInstance` `schemaDigest`.
- Warres, *Class Loading Issues in Java RMI and Jini*, SMLI TR-2006-149
  (preferred classes), in `jgdms-pref-class-loader/src/main/javadoc/`.
