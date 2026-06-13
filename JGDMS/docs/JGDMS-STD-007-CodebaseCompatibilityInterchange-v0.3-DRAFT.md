# JGDMS-STD-007: Codebase Compatibility Interchange

*Standard JGDMS-STD-007 — Version 0.3-DRAFT — June 2026*

> **Editorial note (v0.3-DRAFT):**
>
> **Changes in v0.3 (from v0.2):**
> - New §3.6 — `@AtomicSerial` as the Compatibility Bridge: schema and code evolve
>   independently. LLVM artifacts use the `MarshalledInstance` embedded schema for
>   decoding, not the artifact's own compiled-in schema version. The `GetArg` layer
>   absorbs all schema/code divergence. Artifacts never need recompilation when
>   schema evolves.
> - §6 `CompatibilityEntry` — `serviceSchemaDigest` role clarified: advertisement
>   only, not the decoding schema. The decoding schema always comes from
>   `MarshalledInstance`.
> - §8 open question 7 updated: schema evolution does not require artifact
>   recompilation; closed as resolved by §3.6.

---

## 1. Purpose and Scope

This standard defines the **Codebase Compatibility Interchange** — a JGDMS service
that distributes language-native proxy implementations derived from SCAP-verified
Java proxy JARs. It enables non-JVM clients (Rust, C, Python, WebAssembly, embedded
devices) to call JGDMS services as first-class JERI peers without any Java
infrastructure.

STD-007 covers:

- The `CodebaseCompatibilityInterchange` Jini service interface and discovery model
- The Pack200 bytecode transport format for JAR input (consistent with SCAP/STD-002)
- The LLVM IR compilation pipeline: Pack200 JAR → LLVM IR → language-native artifacts
- Supported output artifact formats (shared library, WebAssembly, LLVM bitcode)
- Content-hash verification of output artifacts (SCAP-equivalent trust for non-JVM code)
- The `CompatibilityEntry` Jini Entry for artifact advertisement in the Lookup Service
- Relationship to STD-006 (`SchemaRegistry`, `MarshalledInstance`) for the data side
  of the language boundary

STD-007 does not cover:

- Java proxy generation (covered by the existing SCAP pipeline, STD-002)
- Data encoding (covered by STD-006)
- Schema distribution (covered by STD-006 §12)
- Language-specific runtime behaviour beyond method stub encoding/decoding

---

## 2. Motivation

### 2.1 The Language Boundary Problem

The JGDMS polyglot future (STD-006 §2) requires non-JVM implementations to
participate as first-class JERI peers. STD-006 solves the *data* side of the
language boundary: DER encoding, `AtomicSerialSchemaRecord`, and `MarshalledInstance`
make all field values recoverable without Java.

The *code* side remains. A non-JVM client discovering a Java service via the Jini
Lookup Service receives a `MarshalledInstance` containing a Java proxy. The client
can decode the proxy's data (via the embedded schema) but cannot execute it. To call
the service it needs a language-native proxy implementation: a Rust library, a C
shared library, a WASM module — code that knows how to encode method arguments as
DER, transmit them over JERI, and decode the response.

### 2.2 Why Automated Compilation Rather Than Manual Ports

Writing and maintaining separate proxy implementations for every language and every
service version is not feasible at scale. The `ServiceSpecRecord` path (STD-006
§7.7.4) handles the reverse direction (non-JVM service → JVM client) via a proxy
factory, but requires manual interface description. STD-007 takes a different
approach for the JVM-to-non-JVM direction: automated compilation from the
already-verified Java proxy bytecode via LLVM.

### 2.3 Why LLVM IR

LLVM IR is the universal intermediate representation for native code generation.
From a single LLVM IR artefact, the LLVM toolchain can target:

- x86-64 and ARM64 shared libraries (`.so`, `.dll`, `.dylib`) — callable from C,
  Rust, Python, Go, Swift, and any language with a C FFI
- WebAssembly (`.wasm`) — runnable in any WASM runtime (Wasmtime, Wasmer, WAMR on
  embedded, and browsers)
- Any current or future LLVM target architecture

Generating LLVM IR from Java bytecode is feasible for JGDMS proxy code because
`@AtomicSerial` proxies have constrained characteristics (STD-001): no dynamic class
loading, no unbounded reflection, no heap cycles (acyclic object graph, STD-006
§3.7), fully static method dispatch. GraalVM Native Image exploits these properties
to close the world statically and emit LLVM IR.

### 2.4 Pack200 for Bytecode Transport

Pack200 (JSR 200) is the existing bytecode transport format in the SCAP pipeline
(STD-002). It achieves substantially better compression than ZIP/deflate for Java
class files by exploiting structural redundancy in constant pools, instruction
sequences, and method descriptors.

Pack200 was removed from standard OpenJDK in Java 14 (JEP 367). It is maintained
as a standalone library at `pfirmstone/Pack200-ex-openjdk`, which will be updated
to support new bytecodes and is the library used by SCAP. Both STD-002 and STD-007
depend on this library for JAR transport.

STD-007 reuses the Pack200 JAR transport from the existing SCAP pipeline. The
SHA-256 digest of the Pack200-encoded JAR bytes is the **primary key** for artifact
lookup in the interchange service (§5). This is the same digest used in the SCAP
`DigestGrant` — one consistent identity for the same JAR across both pipelines.

The output artifacts (WASM modules, shared libraries) are distributed in their
native packaging formats. LLVM IR bitcode uses LLVM's native bitcode format
(`.bc`) compressed with ZSTD. **[OPEN]** Confirm ZSTD as the preferred compression
for LLVM bitcode transport.

---

## 3. Design Principles

### 3.1 Bytes and Primitives Only

All `CodebaseCompatibilityInterchange` interface methods take and return only
`byte[]`, `String`, and primitive types. No service-specific objects. The client
may not have any service-specific classes loaded when it queries the interchange
service, for the same reason `CodebaseAccessor` uses only primitive return types.

### 3.2 Content-Hash Trust

Every output artifact is identified by the SHA-256 of its bytes. The interchange
service provides this digest alongside the artifact. Clients verify the digest before
executing any artifact. This extends the SCAP content-hash trust model (STD-002) to
non-JVM artifacts: the trust anchor is the content hash, not the source URL.

**Deterministic compilation guarantee:** The compilation pipeline MUST be
deterministic — identical Pack200 JAR input MUST produce identical LLVM IR output,
and identical LLVM IR MUST produce identical artifacts for a given target. This
ensures the digest of an output artifact can be independently verified by any party
that holds the input JAR.

**[OPEN]** Specify the exact GraalVM Native Image version and flag set required
for deterministic compilation. Non-determinism in native compilation toolchains
(embedded timestamps, non-deterministic layout) must be explicitly eliminated.

### 3.3 Input Verification Before Compilation

The interchange service MUST NOT compile a JAR that has not received a `SAFE` verdict
from the SCAP pipeline (STD-002). The input Pack200 JAR must carry a valid
`DigestGrant` before the compilation pipeline is invoked. Compilation is a
privileged operation that must not be triggered by unverified input.

### 3.4 Schema Provides Data; Interchange Provides Code

STD-007 provides the *behaviour* side of the language boundary. The *data* side
is provided by STD-006: the `AtomicSerialSchemaRecord` describes the DER structure
of method arguments and return values. A non-JVM client requires both:

- **From STD-007:** method stub code (how to call the service)
- **From STD-006:** schema definitions (how to encode/decode the data)

These are separate concerns from separate sources, consistent with `@AtomicSerial`'s
separation of wire contract from construction behaviour.

### 3.5 Append-Only Artifact Registry

Once an artifact with a given digest is registered, it is immutable and permanent.
Artifacts are never deleted. Old clients may reference old artifact digests
indefinitely; deletion would break the trust model for any client that cached
an artifact by digest before deletion.

### 3.6 `@AtomicSerial` as the Compatibility Bridge

Schema and code are independently evolvable artefacts that may diverge at any
time. The schema describes the structure of encoded data at the time of encoding.
The code (JAR, and its compiled LLVM artifact) describes what the service knows
how to process at the time of execution. These are not required to be the same
version.

**The `MarshalledInstance` embedded schema is always the authoritative decoding
schema** (STD-006 §7.8). It was captured at marshal time and is permanently correct
for the data it accompanies. When an LLVM artifact decodes a `MarshalledInstance`
payload, it MUST use the schema embedded in the `MarshalledInstance` to populate
`GetArg` — not the schema compiled into the artifact from the JAR's `serialForm()`.

**`@AtomicSerial`'s `GetArg` layer absorbs all schema/code divergence:**

- Data encoded with schema version A, decoded by artifact compiled against schema
  version B (B has more fields than A): artifact calls `arg.get("newField", default)`
  for fields absent from A; `GetArg` returns the declared default. Transparent.
- Data encoded with schema version B (B has more fields), decoded by artifact
  compiled against schema version A (A has fewer fields): artifact calls
  `arg.get()` only for fields it knows about; extra fields in `GetArg` are stored
  but never requested; released to GC after construction. Transparent.

In both cases, `@AtomicSerial`'s invariant checking (`check(GetArg)`) determines
whether the decoded state is valid. If defaults satisfy invariants, construction
succeeds. If not, construction throws — the correct and honest outcome for data
that cannot be faithfully processed by this version of the code.

**Consequence: artifacts never need recompilation when schema evolves.**
A compiled LLVM artifact remains valid indefinitely across schema versions, because
it relies on `GetArg` (populated from `MarshalledInstance`) rather than assuming its
own compiled-in schema version matches the data. Schema evolution is handled at
runtime by the `@AtomicSerial` layer, not at compile time by the artifact.

The `serviceSchemaDigest` in `CompatibilityEntry` (§6) and the `SchemaRegistry`
(STD-006 §12) exist for data access, inspection, and migration — not for selecting
which artifact to use. Artifact selection is by `javaCodeDigest`. Schema selection
is by `MarshalledInstance` content.

---

## 4. Compilation Pipeline

```
┌─────────────────────────────────────────────────────────────────┐
│                     Input: SCAP-verified JAR                    │
│              Pack200-encoded, DigestGrant present               │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                  GraalVM Native Image                           │
│   World closure over @AtomicSerial proxy code                   │
│   GC: conservative / SubstrateVM                                │
│   Output: LLVM IR bitcode (.bc)                                 │
└───────────────────────────┬─────────────────────────────────────┘
                            │
              ┌─────────────┴──────────────┐
              ▼                            ▼
┌─────────────────────────┐  ┌─────────────────────────────────┐
│  LLVM → Shared Library  │  │  LLVM → WebAssembly             │
│  x86-64 / ARM64 .so     │  │  .wasm (MVP or component model) │
│  C-compatible ABI       │  │  Runs: Wasmtime, Wasmer, WAMR   │
│  Callable via C FFI     │  │  browsers, embedded             │
└─────────────────────────┘  └─────────────────────────────────┘
              │                            │
              ▼                            ▼
┌─────────────────────────────────────────────────────────────────┐
│         Content-hash each artifact (SHA-256)                    │
│         Register artifact + digest in CodebaseCompatibility     │
│         Interchange service                                     │
└─────────────────────────────────────────────────────────────────┘
```

**[OPEN]** WASM component model vs MVP: the WASM component model (interface types)
provides richer cross-language interop but is less widely supported on embedded
platforms. Settle which WASM variant is the primary target.

**[OPEN]** LLVM IR bitcode as a distributable artefact: should the interchange
service also distribute the raw LLVM IR bitcode, allowing clients with their own
LLVM toolchains to compile to targets the interchange service does not support?
This would maximise flexibility at the cost of distributing a larger artifact.

---

## 5. Service Interface

**Lookup key: `javaCodeDigest`.**
The primary key for all artifact lookups is the SHA-256 digest of the Pack200-encoded
JAR — the same digest used in the SCAP `DigestGrant`. A service may provide multiple
JAR files (proxy JAR, utility JARs, etc.), each SCAP-verified with its own digest
and each requiring independent compilation. The JAR digest uniquely identifies the
code artefact to compile.

The schema digest (`serviceSchemaDigest`) identifies the *data* contract and is used
for schema lookups in the `SchemaRegistry` (STD-006 §12). The JAR digest identifies
the *code* artefact and is used for artifact lookups here. These are separate keys
for separate concerns.

```java
/**
 * Distributes language-native proxy artifacts derived from SCAP-verified
 * Java proxy JARs.
 *
 * All method parameters and return types are byte[], String, or primitives.
 * The primary lookup key is javaCodeDigest — the SHA-256 of the Pack200 JAR,
 * consistent with the SCAP DigestGrant.
 */
public interface CodebaseCompatibilityInterchange {

    /**
     * Returns language target identifiers for which proxy artifacts are
     * available for the given JAR.
     *
     * @param javaCodeDigest SHA-256 of the Pack200-encoded JAR
     * @return array of target identifiers, e.g. {"shared-library/x86-64",
     *         "shared-library/aarch64", "wasm/mvp", "llvm-ir/bitcode"}
     */
    String[] getAvailableTargets(byte[] javaCodeDigest);

    /**
     * Returns the proxy artifact bytes for the given JAR and language target.
     * Returns null if no artifact is available for the combination.
     *
     * @param javaCodeDigest SHA-256 of the Pack200-encoded JAR
     * @param languageTarget target identifier from getAvailableTargets()
     * @return artifact bytes (shared library, .wasm, LLVM bitcode, etc.)
     */
    byte[] getProxyArtifact(byte[] javaCodeDigest, String languageTarget);

    /**
     * Returns the SHA-256 digest of the artifact for the given JAR and target.
     * Clients MUST verify this digest before executing any artifact.
     *
     * @param javaCodeDigest SHA-256 of the Pack200-encoded JAR
     * @param languageTarget target identifier
     * @return SHA-256 digest of the artifact bytes, 32 bytes
     */
    byte[] getProxyDigest(byte[] javaCodeDigest, String languageTarget);

    /**
     * Returns the format identifier for the artifact:
     * e.g. "shared-library/elf-x86-64", "wasm/mvp", "llvm-ir/bitcode-15"
     *
     * @param javaCodeDigest SHA-256 of the Pack200-encoded JAR
     * @param languageTarget target identifier
     * @return format identifier string
     */
    String getProxyFormat(byte[] javaCodeDigest, String languageTarget);

    /**
     * Registers an artifact produced outside the local compilation pipeline
     * (e.g. a manually maintained Rust proxy). The artifact must have a valid
     * SCAP DigestGrant before it will be served to clients.
     *
     * @param javaCodeDigest SHA-256 of the Pack200-encoded source JAR
     * @param languageTarget target identifier
     * @param artifactBytes  the artifact bytes
     * @param artifactDigest SHA-256 of artifactBytes (verified before registration)
     * @param format         format identifier string
     */
    void register(byte[] javaCodeDigest,
                  String languageTarget,
                  byte[] artifactBytes,
                  byte[] artifactDigest,
                  String format);

    /**
     * Returns the schema digests (SHA-256 of leaf AtomicSerialSchemaRecord)
     * for all @AtomicSerial classes implemented in the given JAR.
     * Provides the link between JAR digest (code identity) and schema digest
     * (data contract identity).
     *
     * @param javaCodeDigest SHA-256 of the Pack200-encoded JAR
     * @return array of schema digests, one per @AtomicSerial class in the JAR
     */
    byte[][] getSchemaDigests(byte[] javaCodeDigest);
}
```

---

## 6. CompatibilityEntry

A Jini `@SerialEntry` in the service's attribute set in the Lookup Service,
advertising that language-native proxies are available via the interchange service.
Carries both the JAR digest (for artifact lookup) and the schema digest (for data
structure lookup), linking the two concerns at discovery time.

A service with multiple JAR files registers a separate `CompatibilityEntry` for
each JAR. Each entry carries the specific JAR's digest alongside the schema digests
implemented by that JAR.

```java
@SerialEntry
public class CompatibilityEntry implements Entry {
    /**
     * SHA-256 of the Pack200-encoded JAR.
     * Primary key for CodebaseCompatibilityInterchange artifact lookup.
     * Matches the digest in the SCAP DigestGrant for this JAR.
     */
    public byte[]  javaCodeDigest;

    /**
     * SHA-256 of the leaf AtomicSerialSchemaRecord for the primary
     * @AtomicSerial class in this JAR.
     * Used for advertisement and SchemaRegistry lookup (STD-006 §12) only.
     * NOT the decoding schema — the decoding schema always comes from the
     * MarshalledInstance embedded schema (STD-006 §7.8, §3.6).
     * A JAR implementing multiple @AtomicSerial classes may register
     * multiple CompatibilityEntry instances, one per primary class.
     */
    public byte[]  serviceSchemaDigest;

    /** ServiceID of the CodebaseCompatibilityInterchange to query. */
    public byte[]  interchangeServiceId;

    /** Available language targets (comma-separated). */
    public String  availableTargets;

    /** Format: "JGDMS-STD-007". */
    public String  compatibilityFormat;
}
```

**JAR digest to schema digest mapping.** The `getSchemaDigests(javaCodeDigest)`
method on `CodebaseCompatibilityInterchange` (§5) returns the schema digests for
all `@AtomicSerial` classes in a given JAR, providing the complete link between the
two identity systems at runtime. The `CompatibilityEntry` carries the most important
schema digest for quick filtering at discovery time.

---

## 7. Relationship to Other Standards

| Standard | Relationship |
|---|---|
| STD-001 (@AtomicSerial) | `@AtomicSerial` constraints (no dynamic class loading, acyclic graph) make LLVM world closure tractable for proxy code |
| STD-002 (SCAP) | Input JAR must be SCAP-verified (`SAFE` verdict + `DigestGrant`) before compilation. Pack200 JAR transport reused from SCAP pipeline |
| STD-003 (Security/SPIFFE) | Interchange service authenticated via SPIFFE; artifact digests are trust anchors consistent with STD-003 identity model |
| STD-006 (DER Wire Format) | Schema from `SchemaRegistry` (§12) provides data structure definitions for method arguments and return values; `MarshalledInstance` (§7.8) carries data; STD-007 carries code |
| STD-006 §7.7.4 (`ServiceSpecRecord`) | Reverse direction: non-JVM service → JVM client. STD-007 handles JVM service → non-JVM client. The two are complementary |

---

## 8. Open Questions

1. **Deterministic compilation** — specify the exact GraalVM version, flag set, and
   toolchain configuration required for reproducible LLVM IR output from a given
   Pack200 JAR input. Non-determinism (embedded timestamps, non-deterministic layout)
   must be explicitly eliminated before the content-hash trust model can be applied
   to compiled artifacts.

2. **WASM target** — WASM MVP vs WASM component model as the primary
   cross-platform target. Component model provides richer interface types but
   lower embedded support. Recommendation: MVP primary, component model as an
   additional optional target.

3. **LLVM IR bitcode distribution** — whether to distribute raw LLVM bitcode as
   an artifact alongside compiled targets, for clients with their own LLVM toolchains.
   Distributing LLVM IR allows compilation to targets not supported by the
   interchange service at registration time.

4. **LLVM IR compression** — confirm ZSTD as the preferred compression format for
   LLVM bitcode transport (`.bc` + ZSTD).

5. **Manual proxy registration** — define the SCAP verdict requirement for manually
   registered artifacts (e.g. a hand-written Rust proxy) and how `DigestGrant`
   applies to non-JVM artifacts. The content-hash verification requirement applies
   regardless of whether the artifact was compiled automatically or written manually.

6. **GC model for shared library artifacts** — GraalVM's SubstrateVM uses a
   conservative GC. For shared library artifacts called from languages with their
   own GC (Python, Go), GC interop semantics must be specified. For WASM and C,
   memory is managed manually by the caller.

7. **Multiple JARs per service** — a service may provide several JAR files.
   Each is independently keyed by its Pack200 digest. Clients discover the relevant
   JAR digest(s) via `CompatibilityEntry` in the Lookup Service or via
   `CodebaseAccessor.getClassAnnotation()` on the live proxy. Define the canonical
   order when a client must load multiple JARs for a single service invocation.
