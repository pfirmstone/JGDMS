# JGDMS-STD-007: Codebase Compatibility Interchange

*Standard JGDMS-STD-007 — Version 0.4-DRAFT — June 2026*

> **Editorial note (v0.4-DRAFT):**
>
> **Changes in v0.4 (from v0.3):** Graal repository investigation complete.
> All ten investigation findings incorporated. Open questions resolved or
> updated accordingly.
>
> - §2.4 — Pack200 normalize step specified: `Normalize.normalize()` from
>   `pfirmstone/Pack200-ex-openjdk` must be called before SCAP hashing.
>   Resolves ZIP mtime non-determinism. API and options documented.
> - §2.3 — LLVM IR is intermediate only. No `--emit-llvm` or bitcode-only
>   mode exists. Bitcode captured via `-H:TempDirectory`; intermediate `.bc`
>   files harvested after build.
> - §3.2 — Determinism blocker identified: `LLVMNativeImageCodeCache.java:150`
>   `AtomicInteger.incrementAndGet()` inside parallel runnables assigns
>   `f<n>.bc` IDs in thread-scheduling order. One-line fix specified.
>   Additional HashMap audit required in `LLVMSymtab`, `LLVMObjectFile`,
>   `NodeLLVMBuilder`. Must be upstream patch or local carry.
> - §4 — Pipeline updated: Normalize step added; WASM target corrected to
>   Web Image (no `wasm32` in `LLVMTargetSpecific` — LLVM→WASM not supported);
>   Oracle-patched LLVM 20.1.4 pinning specified; build-from-source requirement
>   noted; `BitcodeOptimizations` flag references removed (flag no longer
>   exists in current HEAD).
> - §8 — Open questions updated: Q1 (determinism) now has specific fix;
>   Q2 (WASM) resolved to Web Image; Q3 (LLVM IR) updated to intermediate-only
>   model; Q5 (GC) closed — SubstrateVM GC, addrspace statepoint convention;
>   Q6 (statepoints) updated — still "experimental" per in-tree docs but
>   shipping in Oracle GraalVM GA; new Q8 (build infrastructure) added.

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
- Any current or future LLVM target architecture (RISC-V64 confirmed present in
  `LLVMTargetSpecific.java` in current Graal HEAD)

**LLVM IR is an intermediate, not a first-class output.** There is no `--emit-llvm`
or bitcode-only compilation mode in the current GraalVM Native Image LLVM backend.
The pipeline always proceeds to a final native binary. LLVM IR bitcode (`.bc` files)
is captured by setting `-H:TempDirectory=<path>` and harvesting the intermediate
`f<n>.bc` (per-function) and `b<n>o.bc` (optimised batches) files after the build
completes. These are valid LLVM bitcode and can be processed further by standard
LLVM tooling.

**WebAssembly is not a supported LLVM backend target.** The `LLVMTargetSpecific.java`
registry in current Graal HEAD contains only `LLVMAMD64TargetSpecific`,
`LLVMAArch64TargetSpecific`, and `LLVMRISCV64TargetSpecific`. There is no
`wasm32` or `wasm64` entry. The WASM target for this standard is provided by
**Web Image** — a separate, experimental backend that generates WASM 3.0 GC directly
via Binaryen, independently of the LLVM backend (§4).

### 2.4 Pack200 for Bytecode Transport and Normalisation

Pack200 (JSR 200) is the bytecode transport format in the SCAP pipeline (STD-002).
It is maintained as a standalone library at `pfirmstone/Pack200-ex-openjdk` —
updated for new bytecodes through Java 27, GPL-2.0 with Assembly Exception.

**Normalisation is required before SCAP hashing.** A raw JAR produced by a build
tool is not byte-reproducible: per-entry modification timestamps, entry ordering,
ZIP extra fields, and constant-pool layout all vary between builds of the same
source. Two semantically identical JARs produce different SHA-256 digests.

`pfirmstone/Pack200-ex-openjdk` provides `net.pack200.Normalize` — a two-step
canonicalisation pipeline:

**Step 1 — Pack200 round-trip** (`EFFORT=1`, `SEGMENT_LIMIT=-1`): drives classfiles
to their Pack200 fixed point. Canonicalises constant-pool layout, attribute ordering,
and inner-class ordering. After this step, `unpack(pack(jar)) == jar` byte-for-byte.
`EFFORT=1` is significant — it minimises encoding choices, stabilising output across
Pack200 library versions.

**Step 2 — ZIP canonicalisation**: drops directory entries, sorts entries
lexicographically (manifest first), pins all entry mtimes to a fixed instant
(default 1980-01-01 UTC, settable via `SOURCE_DATE_EPOCH`), forces DEFLATED
compression, strips ZIP extra fields, and clears the ZIP comment.

```java
// Reproducible default — call before SCAP hashing
Normalize.normalize(rawJar, canonicalJar);

// With SOURCE_DATE_EPOCH pinning
long sde = Long.parseLong(System.getenv("SOURCE_DATE_EPOCH"));
Normalize.normalize(rawJar, canonicalJar,
    Normalize.Options.reproducible().fixedTime(sde * 1000L));
```

**Pipeline position:** `Normalize.normalize()` is called once on the raw JAR before
the SCAP pipeline. The `javaCodeDigest` (SHA-256 of the normalised JAR) is then
computed and remains stable across rebuilds. The Graal compilation pipeline receives
the normalised JAR. Pack200 is not present in the Graal repository and requires no
awareness of Pack200 on Graal's side — Graal receives a plain normalised JAR.

The output artifacts (WASM modules, shared libraries, LLVM bitcode) use ZSTD
compression for transport where applicable.

### 2.5 The Purpose of Polyglot Support: AI as a First-Class Peer

Polyglot participation is not an end in itself. Its purpose is to admit **AI workloads** —
which are non-JVM by nature (Python orchestration, C++/CUDA kernels, native inference
runtimes, Rust agent tooling) — as first-class peers in a JGDMS federation. A system that
requires every participant to be a JVM excludes precisely the workloads that now most need
a trusted distributed substrate.

**Java is the trust integration layer, not the universal implementation language.** JGDMS
does not host AI computation; it provides the membrane at each trust boundary that
establishes *who* a participant is (STD-003) and *what it is authorised to do* (the
DirtyChai authorization model). The polyglot layer — STD-006 for data, STD-007 for code —
lets computation run where it must, natively, while identity, admission control, least
privilege, delegation, and audit remain in the Java trust layer.

This reframes the security model for AI participants. SCAP (STD-002) content-hash-verifies
a proxy or agent *harness*, but the behaviour of an AI participant lives in its inference:
non-deterministic, promptable, and unverifiable in principle — no content hash can certify
that a model will not attempt an action. SCAP therefore secures the *plumbing*; the
participant's *behaviour* can only be constrained by least-privilege authorization. Strong
identity is necessary but not sufficient: an authenticated AI participant remains an
untrusted actor whose every action is mediated by policy under default-deny, with no
ambient authority, and whose delegated authority can only *narrow* as it passes along a
chain — because a participant cannot be relied upon to limit itself.

Positioned this way, JGDMS is not merely "polyglot Jini." It is a **trust and authorization
fabric for multi-organisation, multi-agent AI**, approached from the mature
distributed-systems-security tradition — federated, leased, least-privilege,
discovery-based — rather than by retrofitting authorization onto a language runtime or a
model tool-call protocol.

> **Implementation status (as of 2026-06-15).** The *agent-authority layer* described
> above — the **baseline playpen** grant template, **checkpoint escalation**, the
> **notify-user** escalation-request channel, and the **leased async user→agent
> delegation** primitive — is **design-intent, not yet implemented**. There is no
> agent-authority class, no lease-bound user→SVID grant, no derived/narrowed JWT, and no
> notify-user channel in either the JGDMS or DirtyChai tree. These features are *buildable
> on* the primitives below, but no code yet exercises them.
>
> The underlying authorization **primitives** that this framing relies on *are* confirmed
> present in code:
>
> - **All-principals-present conjunction** — multi-principal grants are conjunctive
>   (`PrincipalGrant.implies` uses `containsAll`); every named principal must be present.
> - **`DigestGrant` / `DigestCodeSource` content-hash gate** — grants can be pinned to the
>   SHA-256 of the JAR, not merely its URL.
> - **`GrantPermission` ceiling on both grant paths** — a caller cannot dynamically grant
>   (or remotely push) beyond its own `GrantPermission`.
> - **Additive-only `SubjectDomainCombiner`** — `callAs` only makes principals *present*;
>   it never widens a domain's permissions, so authority can only narrow across a
>   delegation chain.
> - **GC-scoped per-proxy `DynamicPolicy` grants** — proxy-bound grants are voided when the
>   proxy's `ProtectionDomain` is garbage-collected.
> - **Peer-scoped network via `AuthenticationPermission`** — its `peer` clause pins the
>   remote identity on the authenticated SSL/JERI path.
>
> See `docs/agent-authority-code-review-2026-06-14.md` (§3A/§3D, §4.1) and
> `docs/SOW-AI-Agent-Authority-Support.md` for the gap analysis and the build-out plan.

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

**Determinism requirement and known blocker.** The same normalised input JAR MUST
produce byte-identical LLVM IR and compiled artifacts across independent builds.
Without this, artifact digests cannot be pre-computed or independently verified.

A Graal repository investigation (June 2026) identified one critical non-determinism
in the current LLVM backend: `LLVMNativeImageCodeCache.java:150` assigns `f<n>.bc`
function-bitcode IDs via `AtomicInteger.incrementAndGet()` inside parallel worker
runnables. Because workers execute in thread-scheduling order, the same input
produces different `f<n>.bc ↔ method` mappings between runs, changing batch
composition, optimisation output, and the final `llvm.o`.

**Required fix** (one line): replace the `AtomicInteger` with the deterministic
index from the ordered input list. This fix MUST be applied to the Graal checkout
before use in this pipeline, either as an upstream contribution (file as a GR
deterministic-builds ticket) or as a carried local patch.

**Secondary audit required**: `LLVMSymtab.java`, `LLVMObjectFile.java`, and
`NodeLLVMBuilder.java` use `HashMap`/`HashSet` in contexts that may influence
emitted layout. These must be audited and any order-sensitive iteration replaced
with sorted or `LinkedHashMap`-backed collections before the pipeline can be
declared deterministic.

**Normalisation prerequisite**: `Normalize.normalize()` (§2.4) MUST be called on
the input JAR before SCAP hashing. Without it, ZIP mtime variance produces
different `javaCodeDigest` values for semantically identical JARs.

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

### 4.1 Prerequisites

**Build infrastructure.** The GraalVM LLVM backend is not included in standard
`native-image` distributions for JDK 21+. The `gu` updater has been removed.
The pipeline requires building GraalVM from the `oracle/graal` repository source
using `mx --dynamicimports /substratevm build`. The Web Image backend (WASM target)
similarly requires building from source.

**LLVM toolchain.** The LLVM backend requires Oracle's patched LLVM 20.1.4 exactly
(`20.1.4-2-gb73e7327e3-bgd1ab043d9b`). Two custom patches are applied:
`0001-GR-23578-AArch64-Introduce-option-to-force-placement.patch` (AArch64 frame
record) and `0002-GR-17692-Statepoints-Support-for-compressed-pointers.patch`
(compressed reference statepoints). Stock LLVM 20 will not work for compressed
references. The `LLVM_TOOLCHAIN` distribution from the Graal SDK must be bundled
with the build worker image, version-pinned to the exact Oracle build string.

**Determinism patch.** The `LLVMNativeImageCodeCache.java:150` `AtomicInteger` race
(§3.2) MUST be patched before use. Apply the determinism fix to the local Graal
checkout and rebuild before running the pipeline.

**One build worker per (OS, arch) tuple.** There is no cross-compilation support.
The target triple is hard-wired per host platform at build time. Required workers:
`linux-amd64`, `linux-aarch64`, `linux-riscv64`, `darwin-aarch64`, `windows-amd64`.

### 4.2 Pipeline Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│  Step 0: Normalisation                                          │
│  Normalize.normalize(rawJar, canonicalJar)                      │
│  Pack200 round-trip (EFFORT=1) + ZIP canonicalisation           │
│  → byte-reproducible JAR; compute javaCodeDigest = SHA-256      │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│  Step 1: SCAP Verification                                      │
│  BAE analysis → SAFE verdict → DigestGrant on javaCodeDigest    │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│  Step 2: GraalVM Native Image + LLVM backend                    │
│  --tool:llvm-backend (built from source)                        │
│  Oracle-patched LLVM 20.1.4 toolchain                           │
│  -H:TempDirectory=<path> -H:LLVMMaxFunctionsPerBatch=1          │
│  Determinism patch applied                                      │
│                                                                 │
│  f0.bc, f1.bc, … (one per function)                             │
│  → llvm-link → b0.bc                                            │
│  → opt (mem2reg, rewrite-statepoints-for-gc, always-inline)     │
│        → b0o.bc  ← capture here for LLVM IR distribution        │
│  → llc -filetype=obj → b0.o                                     │
│  → lld -r → llvm.o                                              │
│  → native linker → final shared library                         │
└──────────┬──────────────────────────┬───────────────────────────┘
           │ Shared library target    │ WASM target
           ▼                          ▼
┌─────────────────────┐  ┌─────────────────────────────────────┐
│  .so / .dll / .dylib│  │  Web Image (--tool:svm-wasm)        │
│  C-compatible ABI   │  │  Separate web-image/ suite          │
│  C, Rust, Python FFI│  │  GraalVM 25 EA (build from source)  │
└─────────────────────┘  │  WASM 3.0 GC via Binaryen v119+     │
                         │  Node ≥ 22 / WASM-GC runtime        │
                         │  NOT LLVM-based; independent path    │
                         └─────────────────────────────────────┘
           │                          │
           └──────────────┬───────────┘
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│  Step 3: Content-hash each artifact (SHA-256)                   │
│  Register: (javaCodeDigest, languageTarget) → artifactDigest    │
└─────────────────────────────────────────────────────────────────┘
```

### 4.3 LLVM IR Bitcode as Distributable Artefact

The optimised batch bitcode files (`b<n>o.bc`) captured from the temp directory
after the Native Image build are valid LLVM 20 bitcode. They can be distributed
as an additional artifact target `"llvm-ir/bitcode-20"`, allowing clients with
their own LLVM 20 toolchains to compile to targets not served by the interchange
service. Distribution format: LLVM bitcode (`.bc`) compressed with ZSTD.

These files are post-optimisation (statepoints rewritten, `mem2reg` applied,
inlining done) and carry the host target triple. They are not target-independent.
Clients using them must target the same architecture as the build worker.

### 4.4 Statepoints

The LLVM backend uses LLVM's `rewrite-statepoints-for-gc` pass with an
address-space convention: `addrspace(0)` = untracked native pointer,
`addrspace(1)` = uncompressed Java reference, `addrspace(2)` = compressed Java
reference (requires Oracle patch `0002-GR-17692`). Stack maps from the statepoint
pass are read back and merged into SubstrateVM's reference-map format.

Statepoints are described as "experimental" in `LLVMBackend.md:43` in the current
source. In practice, the feature has shipped in Oracle GraalVM GA releases.
Virtual thread / stored continuation support is in progress (GR-76072) and not
yet complete on the LLVM backend path.

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

1. **Determinism patch — upstream or local carry.** The `LLVMNativeImageCodeCache.java:150`
   `AtomicInteger` race fix is a one-line change. File as a GR deterministic-builds
   ticket for upstream inclusion. Until merged, carry as a local patch applied to
   the Graal checkout. The secondary HashMap audit (`LLVMSymtab`, `LLVMObjectFile`,
   `NodeLLVMBuilder`) is a follow-on item once the primary fix is verified to produce
   stable output.

2. **Web Image WASM target maturity.** Web Image requires GraalVM 25 EA and targets
   WASM 3.0 GC — not MVP. Embedded WASM runtimes (WAMR) have varying WASM GC
   support. Assess WAMR's current WASM GC proposal support before committing Web
   Image as the embedded device target. If WAMR does not support WASM GC, the
   embedded WASM path needs a separate solution (compile to native shared library
   instead, or wait for WAMR WASM GC support).

3. **Multiple JARs per service.** A service may provide several JAR files. Each is
   independently normalised, hashed, and compiled. Clients discover the relevant
   JAR digest(s) via `CompatibilityEntry` in the Lookup Service or via
   `CodebaseAccessor.getClassAnnotation()` on the live proxy. Define the canonical
   load order when a client must use multiple JARs for a single service invocation.

4. **Manual proxy registration SCAP path.** The `register()` method allows
   hand-written language-specific proxies to be registered alongside compiled ones.
   Define how `DigestGrant` applies to non-Java artifacts — specifically, what
   constitutes the "source" for a manually written Rust proxy, and whether BAE
   analysis is applicable or an alternative attestation path is needed.

5. **Build worker provenance.** Each (OS, arch) build worker must itself be
   SCAP-verifiable — the worker image digest must be pinned and the compilation
   environment reproducible. Define the worker image specification and how its
   identity is recorded alongside the artifact digest in the interchange service.
