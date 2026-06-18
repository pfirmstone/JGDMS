# Preferred-Class Analyzer — platform findings & relocation analysis

*Filed 2026-06-18. Follow-up to `SOW-PreferredList-Analyzer.md` and the
`tools/preferred-list-analyzer` build (branch `preferred-list-analyzer`).*

## 1. Key finding: jgdms-platform needs no PREFERRED.LIST

The preferred-class mechanism only takes effect when a jar is **served as a
downloaded codebase** (Warres SMLI TR-2006-149 §4.5). In the dist/qa,
`jgdms-platform` is a **local/shared classpath jar**: it is referenced only by
`file:` codebase grants (e.g. `qa/harness/policy/*.policy`), never `httpmd`-served
for download. So its `META-INF/PREFERRED.LIST` is **never consulted at runtime** —
flipping it to share-by-default is inert, and qa cannot exercise it (a run would
be pure DigestGrant/mahalo noise, see `jgdms-qa-digestgrant-bootstrap-gate`).

Consequence: the committed share-by-default flip + `--fail-on-drift` gate on
`jgdms-platform` are harmless but cosmetic. The analyzer's real value is:
1. **identifying genuine isolation/contention hazards** (it did — see §2); and
2. the **`-dl` modules**, whose jars *are* served as codebases, where the list
   actually governs download behaviour. The `--fail-on-drift` gate and finding
   #1's classpath cross-boundary resolution matter there, not here.

## 2. Hazard classes and the relocation remedy

For a class that genuinely needs **per-codebase isolation**, the architecturally
correct fix is not `Preferred: true` in a local jar (inert) but to place it in a
**served** jar (`jgdms-lib-dl`), where each downloading codebase gets its own
copy. A class can move downstream to `-dl` only if nothing in `jgdms-platform`
depends on it (an incoming dep would force a platform→-dl build edge, the wrong
direction).

Incoming intra-`platform` dependencies (`jdeps -verbose:class -filter:none`,
excluding a class's own inner classes):

| Class | Hazard (analyzer verdict) | Incoming platform deps | Relocation verdict |
|---|---|---|---|
| `net.jini.security.proxytrust.ProxyTrustExporter` | static `Executor` pool + mutable statics (**PREFER**) | **0** | **clean candidate** for `jgdms-lib-dl` |
| `net.jini.id.UuidFactory` | `synchronized(lock)` + `SecureRandom` in `generate()` (**PREFER**) | 1 — `org.apache.river.config.ConfigUtil`, which calls only `create(String)` (parse), **not** the hazardous `generate()` | near-clean; movable if `ConfigUtil`'s single ref is handled |
| `net.jini.core.constraint.DelegationAbsoluteTime` | `static synchronized getFormatter()` (**CONFLICT**, `@AtomicSerial` wire type) | 3 (`ConstraintTrustVerifier`, `DelegationRelativeTime`, `Plaintext`) | **stays** — must keep wire identity; remedy is lock-free (`ThreadLocal<SimpleDateFormat>`) |
| `net.jini.export.ServerContext`, `net.jini.security.policy.PolicyFileProvider`, `org.apache.river.api.security.DelegatePermission` | benign bare-lock cache / (a) (**review**) | 0 | movable but **not motivated** (no real isolation hazard) |
| `Constants`(8), `ClassLoading`(5), `Security`(15), `Service`(9), `DiscoveryV2`(1), `ObjectStreamClassContainer`(3), `OSGiServiceIterator`(1) | review | ≥1 | stay (core infra) |

## 3. Open question (needs JGDMS-topology judgement)

Relocating to `jgdms-lib-dl` isolates instances loaded through a **downloaded
codebase** (per-codebase classloader). `ProxyTrustExporter`'s shared-`Executor`
hazard, however, is largely a **server-side** concern — the exporter is used to
*export* a proxy, not downloaded by clients. So it is not obvious that lib-dl
placement isolates the hazardous usage; the real remedy there may be lock-free /
per-instance state regardless of which jar it ships in. This call needs the
download-topology expertise and is the gate before any move.

## 4. Recommended next steps

1. **Decide the platform list**: remove `jgdms-platform`'s `PREFERRED.LIST`
   (needs none) and retarget the analyzer + `--fail-on-drift` gate at the `-dl`
   modules; or leave the inert flip as-is.
2. **`ProxyTrustExporter`**: confirm the topology question (§3); if relocation is
   the right remedy and no reflection/config-string ref exists (jdeps sees only
   static bytecode refs), move it (+ inner classes) to `jgdms-lib-dl`, fix poms,
   verify.
3. **`UuidFactory`**: assess moving with the one `ConfigUtil.create()` ref; or
   leave (its hazardous `generate()` path is unused within platform).
4. **`DelegationAbsoluteTime`**: apply the lock-free `getFormatter()` fix
   (SOW §2 conflict bucket — independent of any move).
5. **Roll the analyzer across the `-dl` modules** (SOW §7) with finding #1's
   fuller cross-boundary resolution (classpath supertype lookup) in place.
