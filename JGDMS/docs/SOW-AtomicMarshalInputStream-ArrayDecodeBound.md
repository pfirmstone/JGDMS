# Scope of Work — `AtomicMarshalInputStream` array-decode allocation bound: closing an allocate-before-validate decode-DoS

- **Drafted:** 2026-07-19.
- **Status:** DRAFT — task breakdown for review, no implementation started.
- **Origin:** An adversarial reviewer, examining an unrelated change (a new attacker-reachable
  `String[]` field on `org.apache.river.api.io.ProxySerializer`), found and **reproduced** a real
  decode-time denial-of-service bug in the framework beneath it —
  `AtomicMarshalInputStream.readNewArray`. The bug is **not** specific to the field that exposed it;
  it is framework-level, in `AtomicMarshalInputStream` itself, and every `@AtomicSerial` array-typed
  field decodes through it. This SOW scopes the fix.
- **Companions:**
  - `JGDMS-Board-Reviewer-Guidance.md` §2.2 (Adversarial Security) **hazard 3 — Decode-surface DoS**,
    sub-shape *"unbounded/uncapped allocation"*, and its stated invariant: **"bytes-in-flight bounds
    are not decoded-structure bounds — a transport window caps bytes, and a small number of bytes can
    decode into a huge structure."** This bug is a textbook instance of that hazard: the only bound on
    the wire-declared array length today is a *running byte-count budget* (`arrayLenAllowedRemain`),
    which is precisely a bytes-in-flight bound being (mis)used as a decoded-structure bound.
  - `SOW-Smart-Proxy-Isolation-Wiring.md` T3 and `SOW-SubProcessDynamicPolicy.md` — both already
    require, as acceptance criteria, that new `@AtomicSerial` wire fields "carry a bounded count of
    elements each of bounded length, enforced during decode." Those SOWs assume the *framework decode
    path* enforces such bounds; this SOW is the framework-level work that makes that assumption true
    for array fields generally, rather than field-by-field.
  - `recurring-bug-fix-discipline` (bundled reviews under-scrutinize siblings): there are **three**
    allocation sites in this one file gated only by `arrayLenAllowedRemain` (`readNewArray` line 1927,
    plus two `readNewString`/block-data paths at lines 742 and 2851). This SOW treats them as a family
    so the fix is not applied to the reported site alone while its siblings keep the same weakness.
- **No production code was written or modified in producing this document.** It is a task breakdown
  only; all task-level status below is DRAFT / not started.

---

## 1. The bug, confirmed against source (not taken on faith)

File: `jgdms-platform/src/main/java/org/apache/river/api/io/AtomicMarshalInputStream.java`.

**The vulnerable method — `readNewArray` (lines 1900–1927):**

```
int size = input.readInt();                                    // 1915  wire-declared length
if (size < 0 || size > arrayLenAllowedRemain) {                // 1918  the ONLY bound
    close();
    throw new IOException("...invalid or excessive length: " + size);
}
arrayLenAllowedRemain = arrayLenAllowedRemain - size;          // 1924
Class<?> componentType = arrayClass.getComponentType();
Object result = Array.newInstance(componentType, size);        // 1927  ALLOCATE — before any element
```

The allocation at line 1927 happens **before element 0 or EOF is ever read** (the element-reading
loops begin at line 1935). The sole gate is line 1918.

**Why the gate does not stop the attack.** `arrayLenAllowedRemain` is initialized to
`MAX_COMBINED_ARRAY_LEN = Integer.MAX_VALUE - 8` (defined line 151; set at line 3011 when the
outermost `readObject` begins, `nestedLevels == 1`; reset to 0 on unwind at line 3033). It is a
*budget of total array elements across the whole object graph*, not a per-array structural bound and
not a function of bytes actually present. A single top-level array can consume nearly the entire
~2^31 budget. So a wire-declared length of, e.g., 300,000,000 passes the check trivially, and
`Array.newInstance(String.class, 300_000_000)` commits a ~300M-reference array (~1.2–2.4 GB) before a
single byte of payload is validated.

**The reviewer's reproduction (independently consistent with the code above):** a serialized
`String[]` with its 4-byte length prefix patched to `300000000` and a truncated one-element payload
forced the 300M allocation → `OutOfMemoryError: Java heap space` under `-Xmx128m`, before element 0 or
EOF was reached. A ~5-byte length prefix can demand up to a ~2-billion-element (~8–17 GB) allocation.
This matches the guidance's "a small number of bytes can decode into a huge structure" invariant
exactly.

**The good model already in this file — `readNewProxyClassDesc` (lines 2262–2275):**

```
int count = input.readInt();                                   // 2268
if (count < 0 || count > Byte.MAX_VALUE) throw new ...(         // 2269  validate BEFORE allocate, tight bound (127)
    "Smells like a denial of service attack, ... proxy interface count: " + count);
String[] interfaceNames = new String[count];                   // 2272
```

That is validate-before-allocate with a tight, structurally-justified ceiling (a proxy cannot
plausibly implement >127 interfaces). It is the pattern to **generalize**, not reinvent — with the
caveat (§2) that `readNewArray`'s ceiling cannot be as tight as 127 because legitimate arrays here are
larger than that.

**Sibling sites (same weakness, same file), for fix-completeness:**
- Line 742 (`length < 0 || length > arrayLenAllowedRemain` → allocate) — string/char decode path.
- Line 2851 (same predicate) — block-data / string path.

These three sites share one weakness (allocation gated only by the byte-budget). Per bundled-review
discipline, the fix and its verification must cover all three, or explicitly justify excluding a
sibling.

---

## 2. Blast-radius survey

Scope of the decode path (representative, not exhaustive — enough to characterize):

- **516** `@AtomicSerial`-annotated classes in the tree.
- **~198** array-typed private (non-static) field declarations across those classes' service
  implementations. Every one of them, when it is an `@AtomicSerial` field of array type, decodes
  through this single `readNewArray` path. This is **dozens-to-hundreds of fields, framework-wide**,
  not a handful — confirming the fix belongs in `readNewArray`, not at any one call site.
- Component types observed span primitives and references: `String[]`, `LookupLocator[]`, `EntryRep[]`,
  `Entry[]`, `Uuid[]`, `long[]`, `int[]`, `byte[]`, `Object[]` (e.g. `RegistrarImpl`, `FiddlerImpl`,
  `AbortRecord`/mahalo, `ActivationDescImpl`, `AbstractJiniService`).

**Are any of these legitimately large?** Yes — and this is the load-bearing finding for choosing a fix
shape:

- Lookup-service payloads (`reggie` `RegistrarImpl`, `fiddler` `FiddlerImpl`) carry attribute-set and
  registration arrays: `Entry[]` / `EntryRep[] attrSets`, `Object[] marshalledAttrs`, `Uuid[]
  registrationIDs`, `long[] leaseExpTimes`. A busy registrar can legitimately hold **thousands** of
  attributes/registrations in one marshalled reply. These are far below 300M but **far above 127**.
- `byte[]` fields (`encodedCerts`, `codebaseDigestFlat`) are legitimately kilobytes+.

**Consequence:** a *tight universal* ceiling on the `readNewProxyClassDesc`-127 scale would break real
lookup/discovery traffic. The bound must be *loose enough for legitimate service arrays* yet *far
below `Integer.MAX_VALUE - 8`*. This rules out fix shape (b) as a standalone solution and points at (a)
or (c).

---

## 3. Candidate fix shapes and tradeoffs

**(a) Validate-before-allocate against bytes actually remaining / incremental read.**
Before `Array.newInstance`, require the stream to actually hold at least `size × (minimum bytes per
element)` more bytes; or read elements incrementally into a growing structure so committed heap tracks
bytes consumed. Minimum-bytes-per-element is well-defined per component type (byte=1, short/char=2,
int/float=4, long/double=8; an object element is ≥1 byte for even a null/handle reference). Closes the
bug generally for *any* legitimate size without an arbitrary cap.
- *Tradeoff / caveat found in source:* `input` is a `DataInputStream` over a `PushbackInputStream`
  (fields at lines 181/406). `available()` on such a stream is **not** a reliable count of total
  remaining bytes (it can under- or over-report), so a naive `available() >= size*minElem` pre-check
  is unsound as the *sole* mechanism. The robust realization of (a) is therefore **incremental /
  bounded-chunk allocation** (allocate in capped chunks and grow, or read into a bounded buffer and
  finalize to an exact array), so a truncated payload fails fast at the truncation point having
  committed only chunk-sized memory — never the full wire-declared size up front. **For primitive
  arrays this works cleanly (chunked `readFully`).**
  **For `Object[]` it does NOT generalize — corrected 2026-07-19 after adversarial re-review of this
  scoping (see the maintainer-flag resolution below): the array's handle is registered in the
  back-reference table *before* any element is read (line 1929), specifically so a cyclic element can
  legally point back at the array being filled mid-read. A Java array cannot be resized in place, and
  "grow a temp array then copy into the final one" breaks that identity — the registered handle would
  no longer be the array callers actually receive. Incremental growth is therefore largely infeasible
  for `Object[]` while preserving back-reference identity.** The realistic protection for reference-typed
  arrays is the coarse ceiling in (c) below, not an incremental mechanism — do not claim incremental
  growth closes the bug for `Object[]`.

**(b) A tight universal per-array ceiling mirroring `readNewProxyClassDesc`'s `Byte.MAX_VALUE` model.**
Simplest, one-line-ish, matches an in-file precedent.
- *Tradeoff:* the blast-radius survey shows legitimate arrays exceed any *tight* (127-scale) ceiling
  (lookup attribute/registration sets run to thousands). A tight universal cap would regress real
  traffic. A *loose* ceiling (e.g. a few million) is viable as **defense-in-depth**, but on its own it
  still permits an allocate-before-validate spike up to that ceiling (e.g. a few-million-element
  `Object[]` = tens of MB committed from a few wire bytes) — better than 2 GB, not fully closed.

**(c) Combination — coarse safety ceiling PLUS incremental/chunked allocation (RECOMMENDED).**
A coarse ceiling well below `Integer.MAX_VALUE - 8` (fail-fast on absurd lengths, cheap, mirrors the
in-file precedent, gives a clear audit signal), **and** incremental/bounded-chunk allocation under
that ceiling (so even a value below the ceiling never commits memory ahead of the bytes that justify
it). This closes the bug generally (via the incremental mechanism, which needs no reliable stream
length) while retaining a simple, reviewable hard stop for pathological values, and does not regress
legitimate large arrays (the ceiling is set above realistic service payloads; the incremental path
imposes no size cap at all below it).

**Recommendation: (c).** Reasoning: (a) alone is correct but its soundest form (incremental) is the
harder-to-get-right half, and a coarse ceiling adds near-zero cost, a fail-fast for obviously-hostile
values, and an auditable "smells like DoS" signal consistent with `readNewProxyClassDesc`. (b) alone
is insufficient given the survey. The **ceiling value itself is a security-relevant decision requiring
explicit sign-off** (§5 T-DECIDE) — it must be justified against the largest legitimate service array
this codebase actually marshals (checked empirically, see §4), not guessed. The same fix must be
applied to the two sibling sites (lines 742, 2851).

*Flag for the maintainer:* whether an incremental `Object[]` grow-and-finalize interacts with the
existing `registerObjectRead(result, newHandle, unshared)` back-reference registration (line 1929 —
the array handle is registered *before* elements are read, so cyclic references can point back into
the array mid-read) is a correctness subtlety I cannot fully resolve from reading alone. If elements
can legally hold a back-reference to the array being filled, the incremental store must preserve
identity/stability of the final array object across the grow. This is the one point where a
maintainer's knowledge of the handle-table contract is needed to choose between "grow a temp then
copy" (breaks identity) and "over-allocate-in-chunks into the final array" (preserves identity but
needs a different growth strategy). T-DECIDE must resolve it.

---

## 4. Compatibility risk and what to check

- **Could tightening break legitimate wire traffic?** Only if the chosen coarse ceiling is set below a
  real payload. The incremental mechanism imposes no cap, so it cannot regress size; only the ceiling
  can. Mitigation: derive the ceiling from measurement, not intuition.
- **What to check/test before landing:**
  1. Run the existing QA harness serialization round-trips for the large-array services (reggie,
     fiddler lookup/attribute paths; mahalo) and confirm no `IOException`/ceiling trip on realistic
     loads — ideally instrument to log the largest `size` seen through `readNewArray` across a full QA
     run, and set the ceiling with generous headroom above that observed maximum.
  2. Confirm cyclic/back-reference arrays still deserialize correctly (handle-table identity, §3 flag).
  3. Confirm the two sibling string/block-data sites (742, 2851) still decode legitimate long
     strings.
- **Wire-compat:** this is a *decode-side* tightening only — it changes what inputs are *rejected*, not
  the bytes produced. No serialized-form/schema change, so the `japicmp` + serial-schema CI gate is not
  triggered by the fix itself (but the adversarial-verification task must still run against built
  classes on the correct toolchain, per G3).

---

## 5. Execution plan — task breakdown (agent type · effort · risk)

Security lens applied while drafting, not deferred: this is `JGDMS-Board-Reviewer-Guidance.md` §2.2
hazard 3 (decode-surface DoS, uncapped-allocation sub-shape). Per that guidance, a stated bound is not
enough — the ceiling and the incremental mechanism must both be *built and run against adversarial
input*, and the fix traced through every sibling site. Effort is **XHIGH** on the decision,
implementation, and verification tasks: this is a security-critical decode path reachable by hostile
bytes from any peer, the same risk class this session assigned to the smart-proxy-isolation SOWs.

### Summary

| Task | Deliverable | Implement (agent · effort) | Review | Depends |
|------|-------------|-----------------------------|--------|---------|
| **T1** · Fix-shape + ceiling decision (security sign-off) | Decide (a)/(b)/(c) — recommendation is **(c)** — AND fix the concrete coarse-ceiling value against measured largest-legitimate array (§4 check 1), AND resolve the handle-table-identity subtlety for incremental `Object[]` (§3 flag), AND confirm the sibling sites (742, 2851) are in scope. This is a **security-relevant decision requiring explicit maintainer sign-off**, not merely code review — it sets a decode-time rejection threshold on an attacker-reachable path. Output: a decision note appended here (chosen shape, ceiling value + its empirical justification, sibling-site scope, identity-preservation approach). | general-purpose (proposal) → **maintainer sign-off** · **XHIGH** | **parallel board**, adversarial mandate — challenge: is the ceiling above every real payload; does the incremental store preserve array identity for back-references; are all three sites covered | none — start immediately |
| **T2** · Implement in `readNewArray` + siblings | Implement the T1-decided shape in `AtomicMarshalInputStream.readNewArray` (validate-before-allocate coarse ceiling + incremental/bounded-chunk read for both primitive and `Object[]` component types, preserving handle-table identity), and apply the equivalent fix to the sibling allocation sites at lines 742 and 2851. Match the in-file `readNewProxyClassDesc` "smells like a denial of service attack" rejection idiom for the ceiling trip. No serialized-form change. | general-purpose · **XHIGH** (security-critical decode path, hostile bytes, cross-cutting all `@AtomicSerial` array fields) | **parallel board** — verify no fail-open path bypasses the new bound; verify chunked read fails fast on truncation; verify primitive and reference component types both covered | T1 |
| **T3** · Adversarial verification | Re-run the reviewer's **actual** reproduction (300,000,000-length-prefixed `String[]` with truncated payload) against the fixed classes under `-Xmx128m` and confirm it now **fails fast with a bounded `IOException`, no `OutOfMemoryError`**, before large allocation — build and run it, per G3/G13, do not reason about the bound. Also: (i) the ~2-billion / ~5-byte-prefix variant; (ii) hostile inputs against the two sibling sites; (iii) **legitimate large-but-reasonable arrays still deserialize** (drive the reggie/fiddler/mahalo large-array QA round-trips from §4, incl. a cyclic/back-reference array). Report the largest `size` observed vs the chosen ceiling (headroom evidence). | general-purpose · **XHIGH** (must reproduce the real OOM and the real legitimate loads, on the correct toolchain — see `mvn-single-instance-across-agents`, `jgdms-local-build-env`) | single reviewer confirms probes are real (built + run, not asserted) and legitimate-load regression coverage is genuine | T2 |
| **T4** · Closeout | Append the T1 decision note; update this SOW's status to reflect landed fix; add a one-line cross-reference from `JGDMS-Board-Reviewer-Guidance.md` §2.2 hazard 3 to this as a worked in-tree example if the maintainer wants it. Describe reality, not aspiration. | general-purpose · **MEDIUM** | single reviewer | T2, T3 landed |

### Sequencing

1. **T1** first and gating — the ceiling value and identity-preservation approach are security
   decisions that must be signed off before code is written; getting them wrong regresses live traffic
   (too-tight ceiling) or reintroduces the bug (identity break forcing a copy that re-pre-sizes).
2. **T2** implements the signed-off decision across all three sites in one pass (sibling discipline).
3. **T3** is the fail-closed gate on the commit: the fix does not land until the *actual* OOM
   reproduction no longer succeeds **and** legitimate large arrays still round-trip. Compile ≠
   validated; the probe must be built and run.
4. **T4** documents reality once landed.

### Notes

- **Do not weaken the existing `arrayLenAllowedRemain` budget** — it is a complementary cross-graph
  total-allocation cap and should remain. The new per-array structural bound is *additional*, closing
  the gap the budget alone leaves (a single array consuming most of the budget before any bytes are
  read).
- **Sibling-site discipline (`recurring-bug-fix-discipline`):** the reported site is line 1927, but the
  same allocate-gated-only-by-budget shape exists at lines 742 and 2851. Fixing 1927 alone would leave
  a known-equivalent hole; T2/T3 cover all three.
- **G3 independent verification:** T3 must reproduce on the correct toolchain, checking no other agent's
  `mvn` is running first (shared-toolchain wedge), and run adversarial input against *built* classes —
  empty search ≠ absence, compile ≠ validated.

---

*End of DRAFT. No production code was written or modified in producing this document.*
