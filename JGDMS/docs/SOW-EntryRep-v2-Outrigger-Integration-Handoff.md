# EntryRep-v2 — Outrigger integration handoff (unit 2)

Prereq (DONE, committed `fdc2d3306` on `feat/entryrep-v2`): the STD-006 amendment
(`JGDMS-STD-006-Appendix-EntryRep-v2-DRAFT.md`) + the jgdms-der codec core
(`au.net.zeus.jgdms.der.entry.EntrySchemaGenerator`, `EntryRepV2Codec`) + the
match-contract proof (`EntryRepV2MatchContractTest`, 19/19; der suite 617/0/0). The crux
(context-free slices) is proven at the codec level. This handoff wires that proven codec
into Outrigger under the ratified flag-day.

This is the wire/persistence/index rework across **outrigger-dl (release 8)**,
**outrigger-service (release 21)**, **outrigger-snaplogstore**. It is deliberately a
separate unit: it touches many sites and invalidates ~7 v1-asserting tests, so it should
land and be reviewed on its own, on top of the proven codec.

## Architecture (the release-8 / JDK25 bridge — settled)

`outrigger-dl` targets **release 8**; `jgdms-der` is JDK25 and is **test-scope only** in
outrigger-dl. So `EntryRep` cannot name `EntryRepV2Codec`. Mirror the exact pattern
`net.jini.io.MarshalledInstance` uses for DER today (`MarshalFactoryProvider` +
`ServiceLoader`, `jgdms-der/src/main/resources/META-INF/services/net.jini.io.MarshalFactoryProvider`):

1. **New release-8 SPI interface** `org.apache.river.outrigger.proxy.EntryRepV2Support`
   in outrigger-dl (compiles at release 8 — only `byte[]`/`Object`/`Class`/`String`
   signatures, no jgdms-der symbols):
   ```java
   public interface EntryRepV2Support {
       Encoded encodeReflective(Class<?> entryClass, Object entryInstance) throws IOException;
       Encoded encodeSerialEntry(String className, String[] superNames,
               String[] wireNames, Class<?>[] wireTypes, Object[] orderedValues) throws IOException;
       Decoded decode(byte[] body) throws IOException;              // fail-closed, validates §A.9
       Object decodeFieldValue(byte[] slice, Class<?> type, Decoded ctx)
               throws IOException, ClassNotFoundException;
       final class Encoded { public byte[] body; public byte[][] sliceBytes; public byte[] entrySchemaDigest; }
       final class Decoded { public byte[] entrySchemaDigest; public byte[][] sliceBytes;
                             public boolean[] absent; /* + opaque schemaTable handle */ }
   }
   ```
2. **Provider lookup** `EntryRepV2Providers.get()` — `ServiceLoader.load(EntryRepV2Support.class)`,
   cached; throws a LOUD `IllegalStateException` when absent (a v2-born space requires a
   DER-capable JVM — the flag-day fail-closed).
3. **Impl in jgdms-der** `au.net.zeus.jgdms.der.entry.DerEntryRepV2Support implements
   EntryRepV2Support`, delegating to `EntryRepV2Codec`/`EntrySchemaGenerator`, plus a
   `META-INF/services/org.apache.river.outrigger.proxy.EntryRepV2Support` line. Add
   `encodeSerialEntry` to the codec: build the entry chain from the explicit
   `(wireNames, wireTypes)` via `AtomicSerialSchemaRecord` (§7.7.1 form) instead of
   reflection, then `EntryRepV2Codec.encode(...)`.

`Decoded` must expose enough for `decodeFieldValue` to find a value's `chainBytes` by
digest — carry the `Map<String,byte[]>` schemaTable inside an opaque field (release-8 sees
it as `Object`; the impl casts).

## `EntryRep` rework (outrigger-dl `.../proxy/EntryRep.java`)

Replace `private volatile MarshalledInstance[] values;` with:
```java
private volatile byte[] body;              // canonical EntryRepV2Body (wire + store)
private volatile transient byte[][] sliceBytes;  // per-field canonical slice bytes (match/index)
private volatile transient boolean[] absent;     // per-field wildcard/null marker
private volatile byte[] entrySchemaDigest; // routing/identity (32 bytes)
```
Keep `className/superclasses/hashes/hash/codebase/id` (routing/`isAtLeastA`/legacy hash).

- **Constructors** (`EntryRep(Entry, validate, MarshallingFormat)` L309, `marshalSerialEntry`
  L421): drop the per-field `new MarshalledInstance(...)` loop; instead gather the ordered
  field values (reflective path: the existing `getFields`/`usableField` loop, values only;
  `@SerialEntry` path: `putArg.getResult()`) and call
  `EntryRepV2Providers.get().encodeReflective(realClass, entry)` /
  `.encodeSerialEntry(...)`. Store `body`, `sliceBytes`, `absent`, `entrySchemaDigest`.
  Keep the `MarshalException` wrapping (§3.6 loud-failure) and the format check (the
  space is DER-only; a non-ATOMIC_DER format must fail loudly — reuse the existing
  `SpaceProxy2` DER-only guard). **PIN**: `EntryRep.getFields` order MUST equal
  `EntrySchemaGenerator.orderedFields` — add `EntrySchemaGeneratorFieldOrderTest`.
- **`serialForm()`/`serialize()`/GetArg-ctor** (L521-557): new serial fields
  `{body: byte[], entrySchemaDigest: byte[], className, superclasses, hashes, hash,
  codebase, id}`. Remove `values`. On GetArg decode, after reading `body`, call
  `decode(body)` to repopulate `sliceBytes`/`absent` (server side needs them for
  `matches`). The removal of `values` makes an old-proxy/new-service skew fail LOUDLY at
  GetArg (`arg.get("values", ...)` gone) — the §A.8 skew guard.
- **`matches(EntryRep)`** (L976): iterate `sliceBytes`; `absent[f]` (template wildcard) =
  skip; else `Arrays.equals(sliceBytes[f], other.sliceBytes[f])`. **`equals`** (L817):
  same over `sliceBytes` + `hash` precheck. This is v1-parity because slice bytes ARE the
  v1-compared payload bytes plus the value digest (§A.4).
- **`value(int)` (L927), `numFields()` (L934)**: production callers (EntryHandle/
  EntryFieldIndex/EntryHolder/WatchersForTemplateClass/OutriggerServerImpl — see the
  exploration map) use these only as **opaque** `MarshalledInstance` for `.hashCode()`
  (index) or `.equals()` (match). Replace `value(int)` with `byte[] sliceBytes(int)` (raw
  slice) and route index/match to it. `numFields()` = `sliceBytes.length`.
- **`entry()`/`entryViaSerialEntry`** (L650/L779): reconstruct field values via
  `decodeFieldValue(sliceBytes[i], declaredFieldType, decodedCtx)`; keep the per-field
  bad-field collection, integrity, and codebase-annotation handling (`CodebaseProvider`).
  Feed reflective set / `OutriggerGetEntryArgImpl` from the decoded values.
- **`store`/`restore`** (L1071/L1093): write `id/expires/codebase/className/superclasses/
  hash/hashes` + **`body`** (a v2 version tag lives inside `body` as `version=2`).
  `restore` reads them and **re-`decode(body)`** to repopulate `sliceBytes`; a non-v2 body
  is refused LOUDLY there (the recovery guard — never a silent skip).
- **`matchAnyRep`** (L105-124): give it a schema-less v2 form (empty `body`/`sliceBytes`,
  `absent` empty) with a comment that it is never marshalled and MUST NOT enter the DER
  guard path.

## Derived structures (outrigger-service)

- **`EntryHandle.hashForField`** (L248-254): today `rep.value(field).hashCode()`; change to
  `Arrays.hashCode(rep.sliceBytes(field))` (0 for an absent slice — `absent[field]`). The
  packed quick-reject hash (L180-207) and `descFor` mask (L214-239, `tmpl.value(i)!=null`
  → `!tmpl.absent(i)`) then flow through unchanged.
- **`EntryFieldIndex`** (insert/remove/candidates): buckets by `EntryHandle.hashForField`
  — unchanged once `hashForField` is repointed. `null`/wildcard handling maps to `absent`.
  `EntryFieldIndexTest` needs its `new EntryRep(e)` fixtures kept (they still build real
  entries) but its null-field assertions re-expressed via `absent`.

## snaplogstore recovery guard

`BackEnd.recoverSnapshot` (L296-317) already reads a version int + `entryFormat` marker and
`OutriggerServerImpl.recoverEntryFormat` (L3983-3999) throws `IncompatibleStoreException`
for a non-DER format. Add a v2 body-version probe: on `EntryRep.restore`, a `body` whose
`version != 2` throws LOUDLY (already enforced by `EntryRepV2Codec.decode`; surface it as
`IncompatibleStoreException` so the `PrivilegedExceptionAction` cleanup runs and the store
stays pristine — mirror `RecoveryFailCleanTest`).

## Golden serial-schema (compat gate — deliberate FAIL)

The `EntryRep` serial form changes; the compat gate WILL fail. Update the golden as an
explicit, commented diff (the reviewed act). See the api-compat tooling notes.

## Test migration (flag-day: these v1 tests are now invalid, rewrite)

- `EntryRepDerFormatTest` — rewrite for v2: byte-identical-for-equal-values (over `body`),
  slice byte-identity, wildcard/null parity (port the existing `new Note(null)` case),
  no-JOSS-fallback loud reject, the `@SerialEntry` and boxed-scalar round-trips. Delete the
  JOSS-vs-DER mixing test (no dual format under v2).
- `EntryFieldIndexTest`, `SnapshotRepAtomicSerialTest`, `OutriggerSerialEntryRoundTripTest`,
  `EntryFormatGetArgCompatTest`, `MatchSetWireSizeProbeTest`, `HandbackFormatRejectTest`,
  `BornFormatGuardTest`, `RecoveryFailCleanTest`, `DerOnlyConfigRejectTest` — re-point to
  the v2 API; keep their intent.
- **NOTE:** the `qa/` module is **absent from this checkout** (`JGDMS/qa/**` is empty), so
  "the full Outrigger qa matching suite green" cannot be run here — flag to whoever has qa.

## Flagged risks for the board (do not paper over)

- **F1 — proxy/downloadable field values.** `EntryRepV2Codec.encodeFieldSlice` does the two
  common branches (schema-separated `@AtomicSerial` graph; self-describing scalar/collection)
  but does **not** perform the `DynamicProxyCodebaseAccessor`/`ProxyAccessor` substitution
  that `DerMarshalInstanceOutput.writeObject` does for a live downloadable proxy held as a
  field value. Entries-holding-live-proxies is exotic for a JavaSpace (entries are data),
  but if supported it must be reconciled so a proxy field value's slice stays context-free.
  **Board: confirm scope** — either reject proxy-typed entry fields loudly, or extend the
  slice codec to substitute (and re-verify slice context-independence for the carrier).
- **F2 — semantic delta (§A.4.4).** v2 compares `(valueSchemaDigest ‖ payload)`; v1 compared
  payload only. Stricter for `@AtomicSerial` values (adds value-class identity). Intended;
  board sign-off requested.
- **F3 — field-selection duplication (§A.5 note).** `EntryRep.getFields`/`usableField`/
  `FieldComparator` (release 8) necessarily duplicate `EntrySchemaGenerator`'s rules; the
  two MUST agree, pinned by `EntrySchemaGeneratorFieldOrderTest`. Unavoidable given the
  release-8/JDK25 split.
- **F4 — PROPOSED ceilings** `maxSchemaTable=256`, `maxSlicePayload=1MiB`, `maxBodyBytes=8MiB`
  need Peter's ratification (§A.6).
```
