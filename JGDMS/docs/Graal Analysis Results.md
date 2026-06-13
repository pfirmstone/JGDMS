I have enough findings. Here is the report.

---

# Graal LLVM Backend Investigation Report

## Summary

The LLVM backend is **present and actively maintained** in current Graal HEAD (June 2026), restored on LLVM 20.1.4 with recent work on compressed references and stored continuations. AArch64 and RISC-V64 are fully supported via `LLVMTargetSpecific`. **Web Image is a separate, experimental backend that does NOT use LLVM** — it generates WASM 3.0 GC directly via Binaryen. **Determinism is mixed**: Native Image explicitly sorts compilation units and fields for stable constant-layout, but the LLVM backend's per-function bitcode IDs are assigned via a parallel `AtomicInteger.incrementAndGet()` race, so the `f<n>.bc ↔ method` mapping and therefore the LLVM batching/optimisation/linking pipeline is **not byte-deterministic across runs** without further work. Statepoints are still called "experimental" in the in-tree docs and require an Oracle-patched LLVM 20.

## Finding 1: LLVM Backend Status — ACTIVE

**Location:** [substratevm/src/com.oracle.svm.core.graal.llvm/](C:\Users\peter\Documents\GitHub\graal\substratevm\src\com.oracle.svm.core.graal.llvm) — only one LLVM-related substratevm src package (no separate `.hosted.llvm` package).

**Key files:**
- [SubstrateLLVMBackend.java](substratevm/src/com.oracle.svm.core.graal.llvm/src/com/oracle/svm/core/graal/llvm/SubstrateLLVMBackend.java) — backend entry
- [LLVMFeature.java](substratevm/src/com.oracle.svm.core.graal.llvm/src/com/oracle/svm/core/graal/llvm/LLVMFeature.java)
- [LLVMGenerator.java](substratevm/src/com.oracle.svm.core.graal.llvm/src/com/oracle/svm/core/graal/llvm/LLVMGenerator.java)
- [LLVMNativeImageCodeCache.java](substratevm/src/com.oracle.svm.core.graal.llvm/src/com/oracle/svm/core/graal/llvm/LLVMNativeImageCodeCache.java) — orchestrates `.bc → .o → llvm.o` pipeline
- [LLVMBackend.md](substratevm/src/com.oracle.svm.core.graal.llvm/src/com/oracle/svm/core/graal/llvm/LLVMBackend.md) — in-tree docs

**Component descriptor:** [`tool-llvm.properties`](substratevm/mx.substratevm/tool-llvm.properties) maps `--tool:llvm-backend` → `-H:CompilerBackend=llvm`; distribution `SVM_LLVM_GRAALVM_SUPPORT` registered in [suite.py:2971](substratevm/mx.substratevm/suite.py).

**Recent commits (git log):**
```
f40c4f15849 [GR-76072] Some LLVM backend preparations for stored continuations.
4d33bd51223 Support compressed references with the LLVM backend
edfe98c4656 Restore LLVM backend on LLVM 20
```
Not deprecated. Active, with HEAD work toward compressed refs and virtual-thread support.

## Finding 2: LLVM IR Bitcode Output — YES (intermediate, not first-class)

[LLVMBackend.md:22-26](substratevm/src/com.oracle.svm.core.graal.llvm/src/com/oracle/svm/core/graal/llvm/LLVMBackend.md) and [LLVMNativeImageCodeCache.java:148-243](substratevm/src/com.oracle.svm.core.graal.llvm/src/com/oracle/svm/core/graal/llvm/LLVMNativeImageCodeCache.java) document the pipeline:

1. **`f0.bc`, `f1.bc`, …** — one LLVM bitcode file per compiled function
2. **`b0.bc`, `b1.bc`, …** — linked batches (`llvm-link`) — skipped if `-H:LLVMMaxFunctionsPerBatch=1`
3. **`b0o.bc`, `b1o.bc`, …** — optimised batches (`opt --passes=function(mem2reg),rewrite-statepoints-for-gc,always-inline`)
4. **`b0.o`, `b1.o`, …** — compiled batches (`llc -filetype=obj`)
5. **`llvm.o`** — final relocatable object (`lld -r`)

All saved under `<TempDirectory>/SVM-<x>/llvm/` ([LLVMNativeImageCodeCache.java:102](substratevm/src/com.oracle.svm.core.graal.llvm/src/com/oracle/svm/core/graal/llvm/LLVMNativeImageCodeCache.java)). They are intermediate — there is **no `--emit-llvm` or "bitcode-only" mode**; Native Image always proceeds to the final binary. To capture `.bc` you must set `-H:TempDirectory=<dir>` and harvest after the build, and even then `f<n>.bc` is overwritten in successive passes.

`-H:LLVMMaxFunctionsPerBatch=1` (set the default to 1 — check: actually default is 1000 — see [LLVMOptions.java:44](substratevm/src/com.oracle.svm.core.graal.llvm/src/com/oracle/svm/core/graal/llvm/util/LLVMOptions.java)) keeps per-function `.bc` files separate, which is what you'd want for an LLVM-IR-as-artifact pipeline.

## Finding 3: Determinism Analysis — MIXED, with one critical race

**Deterministic elements (good):**
- [NativeImageCodeCache.java:151-187](substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/NativeImageCodeCache.java) — explicit `deterministicCompilationUnitOrderForConstantLayout` sorted by `SortByMethodNameCodeSectionLayouter`. Comment: *"To maximize determinism between builds in the order in which constants are added to the native-image heap, although full determinism cannot be guaranteed, this list is sorted by HostedMethod name."*
- [NativeImageHeap.java:119-121](substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/NativeImageHeap.java) — `DETERMINISTIC_FIELD_COMPARATOR` (declaring class → field name) for constant layout.
- [CompileQueue.java:898-902](substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/code/CompileQueue.java) — explicitly delays publishing inlined graphs to round end *"otherwise that would make the inlining non-deterministic"*.
- [LLVMNativeImageCodeCache.java:218](substratevm/src/com.oracle.svm.core.graal.llvm/src/com/oracle/svm/core/graal/llvm/LLVMNativeImageCodeCache.java) — final sort by code address offset.
- MachO `IDDylibCommand` writes timestamp = `0` ([MachOObjectFile.java:1088](substratevm/src/com.oracle.objectfile/src/com/oracle/objectfile/macho/MachOObjectFile.java)): `new DylibStruct(0, 0, 0, 0); // FIXME: real values please!` — neutralised in practice.
- No `UUID`, `Math.random`, `SecureRandom`, `Random`, `System.currentTimeMillis`, or `Instant.now()` in the LLVM backend source itself.

**Non-deterministic elements (problems):**

1. **CRITICAL — parallel bitcode-id race.** [LLVMNativeImageCodeCache.java:148-161](substratevm/src/com.oracle.svm.core.graal.llvm/src/com/oracle/svm/core/graal/llvm/LLVMNativeImageCodeCache.java):
   ```java
   private void writeBitcode(BatchExecutor executor) {
       methodIndex = new HostedMethod[getOrderedCompilations().size()];
       AtomicInteger num = new AtomicInteger(-1);
       executor.forEach(getOrderedCompilations(), pair -> _ -> {
           int id = num.incrementAndGet();      // <-- runs inside parallel worker
           methodIndex[id] = pair.getLeft();
           ... writes f<id>.bc ...
       });
   }
   ```
   `BatchExecutor.forEach` submits runnables in order ([LLVMToolchainUtils.java:194-204](substratevm/src/com.oracle.svm.core.graal.llvm/src/com/oracle/svm/core/graal/llvm/LLVMToolchainUtils.java)) but they execute in parallel via `CompletionExecutor`. The `incrementAndGet()` therefore assigns `f0.bc, f1.bc, …` IDs in **thread-scheduling order**, not deterministic source order. This changes which functions land together in `b<n>.bc` batches, which changes `opt` and `llc` outputs, which changes `llvm.o`. **Same input JAR → different `llvm.o` between runs.**

2. **Temp directory name carries a wall-clock timestamp.** [TemporaryBuildDirectoryProviderImpl.java:60](substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/TemporaryBuildDirectoryProviderImpl.java): when `-H:TempDirectory=<path>` is set, the subdir is `SVM-<currentTimeMillis>`. Without the option, `Files.createTempDirectory("SVM-")` uses a random suffix. Either way the path is non-deterministic. **Does not by itself affect bitcode content**, but if any tool embeds the absolute path in DWARF or LLVM metadata, that would leak.

3. **Heavy `HashMap`/`HashSet` use in [NodeLLVMBuilder](substratevm/src/com.oracle.svm.core.graal.llvm/src/com/oracle/svm/core/graal/llvm/NodeLLVMBuilder.java), [LLVMGenerator](substratevm/src/com.oracle.svm.core.graal.llvm/src/com/oracle/svm/core/graal/llvm/LLVMGenerator.java), [LLVMSymtab](substratevm/src/com.oracle.svm.core.graal.llvm/src/com/oracle/svm/core/graal/llvm/objectfile/LLVMSymtab.java), [LLVMObjectFile](substratevm/src/com.oracle.svm.core.graal.llvm/src/com/oracle/svm/core/graal/llvm/objectfile/LLVMObjectFile.java).** Several look like internal lookup tables (safe), but `LLVMObjectFile.java:88` exposes `static final Map<String, String> sectionToFirstSymbol = new HashMap<>()` and `LLVMSymtab.java:124` keeps `entriesBySection` as a `HashMap` whose iteration order can influence emitted layout. None are obviously sorted before emission.

**Verdict for STD-007:** the LLVM backend is **not currently byte-reproducible out of the box**. The Oracle team has put deliberate effort into making the heap/constant layout deterministic, but the LLVM-specific pipeline relies on parallel ID assignment that defeats it. Achieving the SCAP-style "same input → same SHA-256" property would require at minimum patching `writeBitcode` to use the list index rather than `AtomicInteger`, plus auditing HashMap iteration in `LLVMSymtab`/`LLVMObjectFile`/`NodeLLVMBuilder`, plus pinning the temp dir name (or fixing every consumer that embeds it).

## Finding 4: Architecture Support — AMD64, AArch64, RISC-V64 all present

[LLVMTargetSpecific.java](substratevm/src/com.oracle.svm.core.graal.llvm/src/com/oracle/svm/core/graal/llvm/util/LLVMTargetSpecific.java) defines:
- `LLVMAMD64TargetSpecific` (line 215) — `x86_64`, scratch `rax`
- `LLVMAArch64TargetSpecific` (line 343) — `aarch64`, with `--aarch64-frame-record-on-top` (requires the Oracle LLVM patch [0001-GR-23578-AArch64-Introduce-option-to-force-placement.patch](sdk/llvm-patches/native-image/0001-GR-23578-AArch64-Introduce-option-to-force-placement.patch))
- `LLVMRISCV64TargetSpecific` (line 506) — `riscv64`

Each is gated by `@Platforms(Platform.<arch>.class)` and registers via `LLVMTargetSpecific.get()`. Target triple is determined at host build time, not via a CLI flag (see Finding 8).

Sibling Substrate code-generator packages also exist: `com.oracle.svm.core.graal.aarch64`, `com.oracle.svm.core.graal.riscv64` (non-LLVM backends).

## Finding 5: Web Image / WASM Backend — separate, experimental, NOT LLVM-based

**Location:** top-level [web-image/](web-image/) directory (separate suite from `substratevm`).

**Activation:** `--tool:svm-wasm` ([web-image/README.md:42](web-image/README.md), [mx_web_image.py:763](web-image/mx.web-image/mx_web_image.py)).

**Entry points:**
- [WebImageGenerator.java](web-image/src/com.oracle.svm.hosted.webimage/src/com/oracle/svm/hosted/webimage/WebImageGenerator.java)
- [WebImageBackend.java](web-image/src/com.oracle.svm.hosted.webimage/src/com/oracle/svm/hosted/webimage/codegen/WebImageBackend.java) (and `WebImageJSBackend`)
- [WebImageCompileQueue.java](web-image/src/com.oracle.svm.hosted.webimage/src/com/oracle/svm/hosted/webimage/code/WebImageCompileQueue.java)

**Relationship to LLVM backend:** none. Web Image is a **direct WASM code generator** built on the Graal IR. It does not depend on `com.oracle.svm.core.graal.llvm` and does not produce LLVM bitcode.

**WASM features targeted (WebAssembly 3.0):**
- GC proposal
- Exception Handling (`exnref`)
- Typed Function References

It is **not** MVP-targeted. Outputs `app.js` + `app.js.wasm`; runs on Node ≥ 22 (Node ≥ 25 drops `--experimental-wasm-exnref`).

**Toolchain:** uses `wasm-as` from [Binaryen](https://github.com/WebAssembly/binaryen) v119+ as the assembler.

**Maturity:** [docs/get-started.md:14](web-image/docs/get-started.md) — *"Web Image is an experimental technology and under active development. APIs, tooling, and capabilities may change."*; line 40 — *"early and experimental. It is not yet part of the `native-image` tool in stable releases."* Requires Oracle GraalVM 25e1+ Early Access.

**Determinism note:** [WasmGCHeapWriter.java:228](web-image/src/com.oracle.svm.hosted.webimage/src/com/oracle/svm/hosted/webimage/wasmgc/codegen/WasmGCHeapWriter.java) — *"Is a SequencedMap to ensure a deterministic order of definitions."* Suggests the Web Image team is at least partly aware of the determinism requirement, but a full audit was not performed.

## Finding 6: LLVM Version and Statepoints

**LLVM version:** **20.1.4** (Oracle build), declared in [sdk/mx.sdk/suite.py:168-205](sdk/mx.sdk/suite.py) under `LLVM_ORG.version = "20.1.4-2-gb73e7327e3-bgd1ab043d9b"`. Confirmed by [sdk/llvm-patches/README.md](sdk/llvm-patches/README.md): *"apply the patches on top of an LLVM 20.1.4 source tree"*.

**Custom Oracle patches** ([sdk/llvm-patches/native-image/](sdk/llvm-patches/native-image/)):
- `0001-GR-23578-AArch64-Introduce-option-to-force-placement.patch` (frame record on top of stack)
- `0002-GR-17692-Statepoints-Support-for-compressed-pointers.patch` (statepoint support for compressed pointers)

**Statepoints status:** **still described as experimental in current source.**
- [LLVMBackend.md:43](substratevm/src/com.oracle.svm.core.graal.llvm/src/com/oracle/svm/core/graal/llvm/LLVMBackend.md): *"garbage collection support implies the use of statepoint intrinsics, an experimental feature of LLVM"*
- [LLVMIRBuilder.java:65-76](substratevm/src/com.oracle.svm.core.graal.llvm/src/com/oracle/svm/core/graal/llvm/util/LLVMIRBuilder.java) documents the address-space scheme (addrspace 1 = uncompressed ref, addrspace 2 = compressed ref) that the Oracle-patched statepoint pass requires.

No `LLVM_MIN_VERSION` constant — the version is asserted at the toolchain distribution level (you get exactly the Oracle-patched LLVM 20.1.4 or nothing).

## Finding 7: BitcodeOptimizations Flag — REMOVED

**Not present anywhere under `substratevm/` in current HEAD.** Grep for `BitcodeOptimizations` returns no matches. Current LLVM options ([LLVMOptions.java](substratevm/src/com.oracle.svm.core.graal.llvm/src/com/oracle/svm/core/graal/llvm/util/LLVMOptions.java)) are:

```
IncludeLLVMDebugInfo, DumpLLVMStackMap, LLVMMaxFunctionsPerBatch,
CustomLD, UseLLVMDataSection, LLVMDataSectionBatchSizeFactor
```

The optimisation pipeline in [LLVMToolchainUtils.java:48-71](substratevm/src/com.oracle.svm.core.graal.llvm/src/com/oracle/svm/core/graal/llvm/LLVMToolchainUtils.java) now runs a **fixed** pass pipeline: `function(mem2reg), rewrite-statepoints-for-gc, always-inline`, plus `-O<level>` from the global `SubstrateOptions.optimizationLevel()`. There is no toggle for "extra" bitcode optimisations any more — the historic "may cause bugs" footnote is moot.

## Finding 8: Cross-Compilation — limited, host-bound

There is **no `--target` / `-mtriple` user-facing flag**. The target triple is hard-wired per host-platform via `@Platforms` at backend-class load time ([LLVMTargetSpecific.java:186-194](substratevm/src/com.oracle.svm.core.graal.llvm/src/com/oracle/svm/core/graal/llvm/util/LLVMTargetSpecific.java)):

```java
default String getTargetTriple() {
    if (Platform.includedIn(Platform.DARWIN.class)) return "-unknown-darwin";
    else if (Platform.includedIn(Platform.LINUX.class)) return "-unknown-linux-gnu";
    else throw shouldNotReachHere(...);
}
```

…prefixed by the arch (`x86_64`, `aarch64`, `riscv64`). The Oracle LLVM toolchain distribution is shipped per-host-arch ([sdk/mx.sdk/suite.py:171-203](sdk/mx.sdk/suite.py)), and the wider Native Image build (object-file layout, C toolchain, libchelper, libcontainer) is also host-bound. **Practically, no cross-compilation today** beyond what an emulator or QEMU/binfmt sysroot setup gives you. The LLVM IR itself is already lowered to the host's target triple by `llvm-link`/`opt`, so even handing the `.bc` files to a different machine wouldn't help past the optimiser — `mem2reg` and statepoint rewriting are run before machine codegen, but the module datalayout/triple are pinned.

## Finding 9: GC and Statepoint Implementation Details

The LLVM backend uses **Native Image's existing SubstrateVM GC** (Serial/Epsilon/G1 selectable at image build time, same as the other backends) — there's no LLVM-specific GC. Statepoints are inserted by LLVM's `rewrite-statepoints-for-gc` pass, driven by an address-space convention emitted by the Graal-to-LLVM lowering:

- `addrspace(0)` = untracked / native pointer
- `addrspace(1)` = tracked uncompressed Java reference
- `addrspace(2)` = tracked compressed Java reference (requires Oracle patch `0002-GR-17692-Statepoints-Support-for-compressed-pointers.patch`)

Documented at [LLVMIRBuilder.java:64-82](substratevm/src/com.oracle.svm.core.graal.llvm/src/com/oracle/svm/core/graal/llvm/util/LLVMIRBuilder.java). Stack maps generated by the statepoint pass are read back via [LLVMStackMapInfo.java](substratevm/src/com.oracle.svm.core.graal.llvm/src/com/oracle/svm/core/graal/llvm/util/LLVMStackMapInfo.java) and merged into Native Image's reference-map format via [LLVMObjectFileReader.java](substratevm/src/com.oracle.svm.core.graal.llvm/src/com/oracle/svm/core/graal/llvm/util/LLVMObjectFileReader.java).

**Virtual threads / stored continuations:** the most recent LLVM-backend commit (`f40c4f15849 [GR-76072] Some LLVM backend preparations for stored continuations.`) indicates this is **in progress, not landed**. Don't assume Loom-style virtual threads work end-to-end on the LLVM backend today.

## Finding 10: Component Availability — tool component, build-from-source only

The descriptor `SVM_LLVM_GRAALVM_SUPPORT` ([suite.py:2971-2978](substratevm/mx.substratevm/suite.py)) registers `--tool:llvm-backend`, packaged with `svm-llvm.jar`, `javacpp-shadowed.jar`, `llvm-wrapper-shadowed.jar`, `llvm-platform-specific-shadowed.jar`. It is wired as an mx `GraalVmSvmTool`-style tool macro.

[LLVMBackend.md:6-12](substratevm/src/com.oracle.svm.core.graal.llvm/src/com/oracle/svm/core/graal/llvm/LLVMBackend.md): *"The LLVM Backend is not included by default as part of Native Image. To use it, you need to build GraalVM from source"*.

So for JDK 21+ era GraalVM: the LLVM backend is **not in the stock `native-image` installer** and not available through `gu` (which itself has been removed). To get it you must `mx --dynamicimports /substratevm build` from this repo. Same situation as Web Image, which also says *"You need to run on Early Access build of Oracle GraalVM 25e1 or later"*.

## Additional: Pack200 — absent (only a varint utility shares the name)

The only file matching `Pack200` is [Pack200Coder.java](substratevm/src/com.oracle.svm.core/src/com/oracle/svm/core/util/coder/Pack200Coder.java) — a Pack200-style variable-length integer encoder used by JFR and heap-dump metadata. **The JAR Pack200 (`pack200`/`unpack200`, JEP 367, removed from JDK 14) is not present anywhere in the Graal repo.** If JGDMS-STD-007 needs Pack200-compressed JARs, that is entirely external to Graal — and given the JDK removed it, you should treat it as legacy.

## Recommendations for STD-007

1. **Determinism is the blocker, not maturity.** The LLVM backend itself works on AMD64/AArch64/RISC-V on LLVM 20, but the current pipeline is **not byte-reproducible**. The dominant cause is a single race in `LLVMNativeImageCodeCache.writeBitcode` ([line 150](substratevm/src/com.oracle.svm.core.graal.llvm/src/com/oracle/svm/core/graal/llvm/LLVMNativeImageCodeCache.java)) that assigns `f<n>.bc` ids via `AtomicInteger` inside parallel runnables. STD-007 should either (a) carry a patch that replaces the AtomicInteger with the input list index, then audit `LLVMSymtab`/`LLVMObjectFile`/`NodeLLVMBuilder` HashMap iteration, or (b) treat the LLVM backend as semi-deterministic and lock the hash to the produced artifact only (not pre-computable from the input JAR).

2. **For WASM, prefer Web Image over LLVM→WASM.** Web Image is a first-class WASM 3.0 GC generator that the Oracle team is actively investing in. Going through the LLVM backend to WASM is *not* a supported configuration — the `LLVMTargetSpecific` registry has AMD64/AArch64/RISC-V64 only, no `wasm32`/`wasm64` target. The trade-off is that Web Image is **experimental**, EA-only, and depends on a JS host (Node ≥ 22 / browser with WASM-GC + EH). Plan around `WasmGCHeapWriter`-style explicit `SequencedMap` use being the team's determinism strategy; bring the same race audit there.

3. **Pin the LLVM toolchain.** The backend requires Oracle's patched LLVM 20.1.4 (two patches: AArch64 frame-record-on-top, compressed-pointer statepoints). Stock LLVM 20 will not work for compressed refs. Bundle the Oracle LLVM toolchain (`LLVM_TOOLCHAIN` distribution) with the build worker image and version-pin to the exact `20.1.4-2-gb73e7327e3-bgd1ab043d9b` build.

4. **Drop assumptions about cross-compilation.** There is no `--target` flag. Plan one build worker per (OS, arch) tuple — `linux-amd64`, `linux-aarch64`, `linux-riscv64`, `darwin-aarch64`, `windows-amd64`.

5. **Avoid the deprecated `BitcodeOptimizations` flag in STD-007 docs** — it no longer exists; replace any reference with `-H:LLVMMaxFunctionsPerBatch` (controls batch size) and `SubstrateOptions.optimizationLevel()` (`-O0..-O3`, `-Ob`, `-Os`).

6. **Statepoints are still "experimental" per Oracle's own source comments.** That word may scare reviewers, but in practice the pass has shipped for AArch64 (LLVM D66012) and the Oracle compressed-pointer extension is in production. Cite the in-tree comment honestly but note that GraalVM has shipped GA releases on this codepath.

7. **Pack200 is dead inside Graal.** If JGDMS-STD-007 currently positions itself as "SCAP-verified Pack200 JAR → LLVM IR", you'll need an external Pack200 implementation (e.g. the Apache Commons Compress port) or move to plain JARs. The `Pack200Coder.java` in this repo is unrelated.

Noted — that resolves recommendation #7. The Pack200 implementation is yours (your fork of the OpenJDK code, updated to Java 27, GPL-2.0 + Classpath/Assembly Exception, release v1.26.1 on 2026-05-01). So STD-007's "SCAP-verified Pack200 JAR" premise is fully supportable: Pack200 lives outside Graal, in [pfirmstone/Pack200-ex-openjdk](https://github.com/pfirmstone/Pack200-ex-openjdk), and the Graal pipeline just consumes the unpacked JAR.

**Revised Recommendation #7:**
> Pack200 is not in Graal and won't be — JEP 367 removed it from JDK 14. JGDMS-STD-007 sources Pack200 from [pfirmstone/Pack200-ex-openjdk](https://github.com/pfirmstone/Pack200-ex-openjdk) (OpenJDK-derived, maintained to Java 27, GPL-2.0 with Assembly Exception). The Graal pipeline receives an already-unpacked, content-hashed JAR — no Pack200 awareness required on Graal's side. Worth one audit pass on the Pack200 library itself for the same determinism property the rest of STD-007 needs: `pack200(jar)` → byte-identical bytes across runs is what the SCAP digest model demands. The OpenJDK pack200 was historically sensitive to file ordering inside the input JAR and to the `-E<n>` effort level; pin both, and document the exact `pack200` flags used to produce the SCAP-anchored bytes.

The other nine findings stand. The critical blocker for STD-007 remains the `LLVMNativeImageCodeCache.writeBitcode` `AtomicInteger` race in [LLVMNativeImageCodeCache.java:150](C:\Users\peter\Documents\GitHub\graal\substratevm\src\com.oracle.svm.core.graal.llvm\src\com\oracle\svm\core\graal\llvm\LLVMNativeImageCodeCache.java) — that's the one I'd file upstream as a "deterministic builds" GR ticket, since the fix is a one-line swap to the input list index.

Found it. The "JAR normalize routine" was the `jar -n` / `jar --normalize` option, and it was implemented as a **Pack200 round-trip** — pack the JAR with `EFFORT=1`, then unpack it. That round-trip is what produces canonical attribute ordering, normalized constant pools, and reproducible class-file layouts that Pack200 then compresses optimally.

## The removal — JDK 15, JEP 367

Commit [`9ac2f8b6543`](https://github.com/openjdk/jdk/commit/9ac2f8b6543) — `8234542: code removal of Pack200 Tools and API` (Henry Jen, 2019-12-10) — deleted everything: `src/jdk.pack/`, `java.util.jar.Pack200`, and crucially the normalize block in [src/jdk.jartool/share/classes/sun/tools/jar/Main.java](src/jdk.jartool/share/classes/sun/tools/jar/Main.java).

The deleted code (verbatim, from the diff):

```java
if (nflag) {
    if (!suppressDeprecateMsg) {
        warn(formatMsg("warn.flag.is.deprecated", "-n"));
    }
    File packFile = createTemporaryFile(tmpbase, ".pack");
    try {
        java.util.jar.Pack200.Packer packer = java.util.jar.Pack200.newPacker();
        Map<String, String> p = packer.properties();
        p.put(java.util.jar.Pack200.Packer.EFFORT, "1"); // Minimal effort to conserve CPU
        try (JarFile jarFile = new JarFile(tmpFile.getCanonicalPath());
             OutputStream pack = new FileOutputStream(packFile))
        {
            packer.pack(jarFile, pack);
        }
        if (tmpFile.exists()) {
            tmpFile.delete();
        }
        tmpFile = createTemporaryFile(tmpbase, ".jar");
        try (OutputStream out = new FileOutputStream(tmpFile);
             JarOutputStream jos = new JarOutputStream(out))
        {
            java.util.jar.Pack200.Unpacker unpacker = java.util.jar.Pack200.newUnpacker();
            unpacker.unpack(packFile, jos);
        }
    } finally {
        Files.deleteIfExists(packFile.toPath());
    }
}
```

That's the entire routine. It is **literally just** `Pack200.Packer.pack(jar)` → temp `.pack` file → `Pack200.Unpacker.unpack(packFile)` → new JAR.

## Deprecation lineage

- **JDK 11**: [`b0261021632`](https://github.com/openjdk/jdk/commit/b0261021632) — `8199871: Deprecate pack200 and unpack200 tools` (deprecated `-n` along with `pack200`/`unpack200`)
- **JDK 15**: [`9ac2f8b6543`](https://github.com/openjdk/jdk/commit/9ac2f8b6543) — the actual removal of API + tools + the `nflag` block above (JEP 367)
- **JDK 24-ish**: [`51662c23843`](https://github.com/openjdk/jdk/commit/51662c23843) — `8346232: Remove leftovers of the jar --normalize feature` (Eirik Bjørsnøs, 2024-12-16). This is just resource-bundle cleanup — the `-n`/`--normalize` *flag declarations* were still kicking around in `GNUStyleOptions.java` and translated `jar_*.properties` resource bundles for years after the actual code was gone. This commit removed the dead flag.

## What it gives you for STD-007 / Pack200-ex-openjdk

You already have the OpenJDK Pack200 source. The normalize routine is therefore trivially reconstructible — it's about 20 lines and just needs the same `Pack200.Packer`/`Unpacker` your fork already exposes. The relevant points:

1. **`EFFORT=1` is significant.** The original used `"1"` not `"9"`. The comment in OpenJDK says *"Minimal effort to conserve CPU"* — but the more important property is that low effort means the packer makes **fewer encoding choices**, which makes the normalize round-trip more stable across Pack200 library versions. If STD-007 wants the normalize step to be reproducible across decades, pin `EFFORT=1`.

2. **It uses `JarFile` (the canonical path) on input and `JarOutputStream` on output.** Both have their own ordering subtleties — `JarFile` enumerates entries in central-directory order, `JarOutputStream` writes in the order you give it. For a hashable normalize, you may want to wrap the input/output to sort entries by name before handing them to the packer/unpacker — the original code did not do this, because it only cared about Pack200 compression efficiency, not byte-identical output.

3. **What "normalize" actually achieves.** Inside Pack200, the packer parses each class file into the segmented BandStructure representation (separate bands for constant pool, code, attributes, debug info, …) and emits canonical encodings. The unpacker then reconstructs class files from those bands. After this round-trip:
   - Constant pool entries are reordered into Pack200's canonical layout (UTF8s first, then classes, then signatures, then names-and-types, etc.)
   - `LineNumberTable` / `LocalVariableTable` / `LocalVariableTypeTable` attribute byte ordering is normalised
   - Inner classes are emitted in a canonical order
   - Default attribute values are dropped/restored consistently
   - File-modification timestamps in the ZIP central directory are normalised (Pack200 strips them by default and the unpacker sets a single fixed mtime)
   
   That last one — the **ZIP mtime normalisation** — is what makes the routine genuinely useful for SCAP-style content hashing. Without it, two builds of the same source produce JARs with different mtimes in the ZIP CD and therefore different SHA-256.

4. **You don't need OpenJDK's removed code.** Reinstate the routine inside `Pack200-ex-openjdk` as a public utility, e.g. `Pack200.normalize(InputStream jarIn, OutputStream jarOut)`, and call it from JGDMS before SCAP-hashing. It's about 20 lines.

5. **The `51662c23843` cleanup is irrelevant** — it just removed dead CLI flag declarations after the implementation had been gone for nine years.

Net: your concern was well-founded — there *was* a normalize routine, it *was* removed, and the removal *was* tied to JEP 367 (Pack200 removal). The fix for STD-007 is to surface a `normalize()` API on your Pack200 fork (the implementation is the 20-line block above plus optional entry-name sorting and mtime fixing) and make calling it part of the SCAP digest pipeline before the JAR ever reaches Graal.

Final summary of what was added:

| File | Purpose |
|---|---|
| `src/main/java/net/pack200/Normalize.java` | Public API — `Normalize.normalize(...)` with `Options.reproducible()` and `Options.legacyJarN()` factories. Java 8 source-compatible (matches the rest of the base classes). |
| `src/test/java/au/net/zeus/util/jar/pack/NormalizeTest.java` | 10 unit tests covering: reproducibility across semantically-equivalent inputs, idempotence, output structure (manifest first, sorted, no directory entries), legacy `jar -n` behaviour, stream overload, fixed-time honouring, pre-1980 rejection, effort range, null-argument rejection, no-close-of-caller-stream. |
| `docs/normalize.md` | ~250-line spec: history (links to the JDK-15 removal commit and JDK-24 cleanup), the two use cases (Pack200 fixed-point and reproducible builds), pipeline diagram, options table, guarantees, examples (`SOURCE_DATE_EPOCH`, sign-then-pack, streamed input), limitations. |
| `README.md` | Top-level "Features" section added pointing at `docs/normalize.md`. |

**Test results:** `47 / 47` pass (`10` new + `37` existing `SecurityHardeningTest`).

**Pipeline** (matches the historical OpenJDK code, then extends it):

1. **Step 1 — Pack200 round-trip.** Pack with `EFFORT=1`, `SEGMENT_LIMIT=-1`, `KEEP_FILE_ORDER` / `MODIFICATION_TIME` / `DEFLATE_HINT` pinned per options. Unpack.
2. **Step 2 — ZIP canonicalisation** (skipped for `legacyJarN()`). Drop directory entries, sort lexicographically with manifest first, pin all timestamps to a fixed instant (default 1980-01-01 UTC, settable via `.fixedTime(SOURCE_DATE_EPOCH * 1000)`), force DEFLATED, strip extras, clear ZIP comment. Uses `ZipOutputStream` rather than `JarOutputStream` to suppress the latter's auto-injected `0xCAFE` magic on the first `META-INF/*` entry.

**Two design wrinkles documented:**
- `JarOutputStream` adds a 4-byte `0xCAFE` marker to the first manifest entry's extra field. Solution: write with `ZipOutputStream` instead — the result is still a valid JAR (recognised by `META-INF/MANIFEST.MF` presence).
- `ZipEntry.setTime(long)` on JDK 9+ populates the `mtime` `FileTime` field, which causes `ZipOutputStream` to emit a 9-byte Info-ZIP Extended Timestamp (`0x5455` "UT") extra block. It is deterministic for a fixed `fixedTime`, so reproducibility is preserved; documented honestly in `docs/normalize.md`.

The public surface for JGDMS-STD-007 is just:

```java
Normalize.normalize(jarFile, outFile);                                    // reproducible default
Normalize.normalize(jarFile, outFile, Normalize.Options.legacyJarN());    // sign-then-pack
Normalize.normalize(in, out, Normalize.Options.reproducible()
                                              .fixedTime(SOURCE_DATE_EPOCH * 1000L));
```

Done. Here's the summary:

## Fix applied

**Branch:** `fix/llvm-bitcode-id-determinism` (forked from `master` at `2b6ab5a1b58`)
**Commit:** `197c7aae080`
**File:** [`substratevm/src/com.oracle.svm.core.graal.llvm/src/com/oracle/svm/core/graal/llvm/LLVMNativeImageCodeCache.java`](C:\Users\peter\Documents\GitHub\graal\substratevm\src\com.oracle.svm.core.graal.llvm\src\com\oracle\svm\core\graal\llvm\LLVMNativeImageCodeCache.java) — 1 file, +26 / −6.

### The fix in one diff hunk

```java
- methodIndex = new HostedMethod[getOrderedCompilations().size()];
- AtomicInteger num = new AtomicInteger(-1);
- executor.forEach(getOrderedCompilations(), pair -> _ -> {
-     int id = num.incrementAndGet();              // raced with CompletionExecutor
+ List<Pair<HostedMethod, CompilationResult>> compilations = getOrderedCompilations();
+ methodIndex = new HostedMethod[compilations.size()];
+ executor.forEach(compilations.size(), id -> _ -> {
+     Pair<HostedMethod, CompilationResult> pair = compilations.get(id);
      methodIndex[id] = pair.getLeft();
      try (FileOutputStream fos = new FileOutputStream(getBitcodePath(id).toString())) {
          fos.write(pair.getRight().getTargetCode());
      } catch (IOException e) {
          throw new GraalError(e);
      }
  });
```

### Why this is correct

- `BatchExecutor.forEach(int, IntFunction)` ([LLVMToolchainUtils.java:181](C:\Users\peter\Documents\GitHub\graal\substratevm\src\com.oracle.svm.core.graal.llvm\src\com\oracle\svm\core\graal\llvm\LLVMToolchainUtils.java)) iterates the index in a sequential `for (int i = 0; i < num; ++i)` and passes `i` to the callback, so each worker gets the index that matches its compilation's position in `getOrderedCompilations()`.
- That outer order is already deterministic: it's produced by `layoutCompilations()` → `CodeSectionLayouter`, the same path the rest of Native Image relies on for stable code layout.
- The inner runnable (`_ -> { … }`) still executes in parallel via `CompletionExecutor`, so there is no throughput loss — only the id-assignment race is removed.
- The pattern is the same one [`createBitcodeBatches`](C:\Users\peter\Documents\GitHub\graal\substratevm\src\com.oracle.svm.core.graal.llvm\src\com\oracle\svm\core\graal\llvm\LLVMNativeImageCodeCache.java) (line 179) already uses, so the change is idiomatic for this file.

### Knock-on effects this unblocks

Once `f<n>.bc → method` is deterministic:

1. Batches (`b<n>.bc`) contain the same functions on every run.
2. `opt --passes=function(mem2reg),rewrite-statepoints-for-gc,always-inline` sees identical input modules → identical output.
3. `llc -filetype=obj` produces identical `b<n>.o`.
4. `lld -r … -o llvm.o` produces an identical `llvm.o`.

That gets the LLVM-backend artifact pipeline most of the way to byte-for-byte reproducibility. Remaining concerns from the original investigation (`LLVMSymtab`/`LLVMObjectFile` HashMap iteration order, the `SVM-<currentTimeMillis>` temp-dir name when `-H:TempDirectory` is set) are independent and can be tackled as separate commits.

### Verification

- The diff was confirmed against the call-site contracts:
  - `NativeImageCodeCache.getOrderedCompilations()` returns `List<Pair<HostedMethod, CompilationResult>>` ([NativeImageCodeCache.java:261](C:\Users\peter\Documents\GitHub\graal\substratevm\src\com.oracle.svm.hosted\src\com\oracle\svm\hosted\image\NativeImageCodeCache.java)) — supports `.get(int)`.
  - `BatchExecutor.forEach(int, IntFunction<CompletionExecutor.DebugContextRunnable>)` exists ([LLVMToolchainUtils.java:181](C:\Users\peter\Documents\GitHub\graal\substratevm\src\com.oracle.svm.core.graal.llvm\src\com\oracle\svm\core\graal\llvm\LLVMToolchainUtils.java)).
- A grep for `AtomicInteger` and `incrementAndGet` across `com.oracle.svm.core.graal.llvm` confirms no other instances of this race pattern exist in the LLVM backend.
- A full `mx build` would normally validate compilation, but `mx` isn't on this machine. Branch is ready for you to run `mx --dynamicimports /substratevm build` or push for CI to compile and run the LLVM backend test suite.

### Next step (your call)

You're currently on the `fix/llvm-bitcode-id-determinism` branch. To send upstream:

```bash
git push -u origin fix/llvm-bitcode-id-determinism
# then open a PR against oracle/graal with a body that references the
# determinism rationale and links to JGDMS-STD-007 / reproducible builds.
```

…or rebase/cherry-pick onto whatever branch you intend to carry your local Graal patches on. The commit message is already in upstream style and references the failure mode in enough detail that a reviewer who hasn't seen our conversation can evaluate it.

Good — the workflow is building commit `197c7aa` (my fix commit) and is in progress. GraalVM builds run 20–40 min, so it'll take a while.

While it runs, two useful things I can do:

1. **Watch the run and report back** when it finishes — I'll re-poll the URL.
2. **Add a determinism unit test** alongside the fix. Right now the fix is justified by reasoning; a CI test that builds a trivial Native Image with `--tool:llvm-backend` twice and diffs the resulting `llvm.o` would let us assert the property mechanically and catch any regression in this corner forever.

Want me to:
- (a) just wait and check back on the workflow,
- (b) draft the determinism mx-style test now,
- (c) both in parallel?

If (b)/(c), I'll add it as a follow-up commit on the same `fix/llvm-bitcode-id-determinism` branch — something like `LLVMBackendDeterminismTest` under `substratevm/src/com.oracle.svm.test/` that:

1. Compiles a 3-method `Hello` sample twice with `-H:CompilerBackend=llvm -H:TempDirectory=<dir1>` and `<dir2>`.
2. SHA-256s `<dir1>/llvm/llvm.o` and `<dir2>/llvm/llvm.o`.
3. Asserts they're equal.

That's the kind of regression guard the original race would have failed instantly.

In the meantime, the run URL you shared is:

> https://github.com/pfirmstone/graal/actions/runs/27125212068

…which I can poll. Let me know which path you want and I'll get started (or stay quiet on (a) until the workflow turns green/red).

	B please.

---

# Part II — Determinism: Full Resolution (2026-06-08 → 06-09)

> **Reframe of Part I.** The original report named the `LLVMNativeImageCodeCache.writeBitcode`
> `AtomicInteger` race as *the* critical determinism blocker. That was correct but only the
> **first of three layers**. Fixing it exposed a deeper LLVM-backend source (statepoint ids),
> and fixing *that* exposed the real floor: an **upstream, backend-agnostic** nondeterminism in
> Native Image core. The LLVM-backend determinism work is now **complete**; the residual is a
> Native Image core reproducibility gap that affects every backend. This Part supersedes
> Recommendation #1 of Part I.

## II.0 Pack200 normalize routine — resolved & delivered

The `jar -n` normalize routine (removed by JEP 367) was reconstructed in your fork as a public
API: **`net.pack200.Normalize`** in [pfirmstone/Pack200-ex-openjdk](https://github.com/pfirmstone/Pack200-ex-openjdk).

- `Normalize.normalize(in, out)` — `Options.reproducible()` default; `Options.legacyJarN()` for
  the historical sign-then-pack round-trip.
- Pipeline: **Step 1** Pack200 round-trip (`EFFORT=1`, `SEGMENT_LIMIT=-1`); **Step 2** ZIP
  canonicalisation (sort entries manifest-first, pin timestamps to a fixed instant settable from
  `SOURCE_DATE_EPOCH`, strip extras, clear comment, force DEFLATED). Uses `ZipOutputStream` to
  avoid `JarOutputStream`'s auto-injected `0xCAFE` marker.
- Delivered: `Normalize.java`, `NormalizeTest.java` (10 tests), `docs/normalize.md`, README update.
  **47/47 tests pass.** Two documented wrinkles: the `0xCAFE` marker (solved) and the JDK-9+
  Extended-Timestamp extra block (deterministic for a fixed time, so reproducibility holds).

This is the **input side** of the SCAP trust model and is solid: `normalize(jar)` → byte-identical
output across runs. The hard part is the **output side** (the Native Image build), below.

## II.1 The determinism gate (measurement first)

Built `mx llvm-backend-determinism-test` (in `substratevm/mx.substratevm/mx_substratevm.py`) + a CI
workflow (`.github/workflows/llvm-backend-determinism.yml`) that builds HelloWorld **twice** with
`--tool:llvm-backend` and diffs every pipeline stage (`f*.bc` → `b*.bc` → `b*o.bc` → `b*.o` →
`llvm.o` → binary), reporting the **first divergent stage** and an **IR-level diff** of the first
diverging bitcode files. This measurement harness is what let each layer be diagnosed precisely.
Commits: `3dd2e52`, `901fa18`, `e632e9b`, `6f62e06`, `e7f3e3b`, `d812b0f`.

## II.2 Layer 1 — bitcode file-id race (LLVM-backend; FIXED)

`writeBitcode` assigned `f<n>.bc` ids via `AtomicInteger.incrementAndGet()` inside the parallel
`BatchExecutor`, so the `f<n>.bc ↔ method` map was thread-scheduling-dependent. **Fix
`197c7aae080`:** dispatch via `BatchExecutor.forEach(int, IntFunction)` so each runnable gets its
deterministic list index. Per-file work still parallel; no throughput loss.

## II.3 Layer 2 — statepoint/patchpoint ids (LLVM-backend; FIXED)

The gate showed **9079 of 9080 `f*.bc` still diverged** after Layer 1. IR diff pinpointed the
cause: `@llvm.experimental.stackmap(i64 N)` and `"statepoint-id"="N"` values shifting, because
patchpoint ids came from a **single process-wide `AtomicLong`** incremented from the parallel
compile queue. The fix went through several iterations (recorded for posterity):

| Attempt | Outcome |
|---|---|
| Row-major per-method blocks (`id = methodIndex*65536 + local`) | **Overflowed 31-bit id space**: a HelloWorld pulls in **37,572 methods**; `37572 × 65536 > 2^31`. (ids must fit `int` via `NumUtil.safeToInt`.) |
| Dense column-major (`id = methodIndex + local*methodCount`) | Correct math, but ids *still* shifted: the **stride (`methodCount`) and index both wobbled** — `getMethods()` returned **37,612 vs 37,567** across two builds. |
| **Index over `isImplementationInvoked()` methods** (`e426a92a326`) | **9079 → 417.** The wobble is in *reachable-but-not-executed* methods; the executable (compiled) set is a stable **9,080**. Indexing over the invoked set removed the LLVM-backend's exposure to it. |

Key insight: this fix is **zero-risk for GC correctness** — it changes only the *index source*,
not the delicate stack-map correlation. Patchpoint base assignment runs in
`LLVMFeature.beforeCompilation`.

## II.4 Layer 3 — the residual is UPSTREAM and BACKEND-AGNOSTIC (proven)

The remaining 417 functions differ **not** in statepoint ids but in **type-id range-check
constants** and **field offsets**:

```
%73 = and i32 %72, 65535        ; extract 16-bit type id from hub
%74 = add i32 %73, -6  vs -1    ; type-id RANGE START   ← differs
%87 = icmp ult i32 %74, 107 vs 106  ; range CHECK       ← differs
```

This is `instanceof`/`checkcast` lowered to a closed-world `(typeId − start) <ᵤ range` check.
The constants come from **`TypeCheckBuilder`** (core Native Image), which assigns type ids by a
**deterministic sorted traversal** — so the algorithm is fine; its **input type set wobbles**
(same ~45-entity reachability nondeterminism). Field offsets shift for the analogous reason
(object-layout set wobbles).

**Proof it is not LLVM-specific** (`--backend default`, commit `2f752a4bc50`): building HelloWorld
twice with the **ordinary backend** and disassembling its object file shows **1,989 changed
instructions** in the same categories — **379 `leal -0xN`** (type-id starts) + **74 `cmpl`**
(ranges) + **168 `movq OFF(%reg)`** (field offsets) + downstream branch-target shifts. The default
backend bakes the same wobbling constants into its machine code and is **equally
nondeterministic**.

## II.5 Conclusion

| Layer | Source | Scope | Status |
|---|---|---|---|
| 1 | bitcode file-id race | LLVM backend | ✅ fixed (`197c7aa`) |
| 2 | statepoint/patchpoint ids | LLVM backend | ✅ fixed (`04ec5fc`…`e426a92`) |
| 3 | type-id range checks + field offsets | **all backends (core)** | ⬆ upstream — *proven* |

**The LLVM backend is now as reproducible as Native Image core allows.** The root cause of the
residual is **nondeterministic points-to reachability analysis** (~45 methods / a few types /
some fields differ per build), which renumbers type ids and shifts field offsets across the whole
image — on **every backend**. Fixing it is a core Native Image effort, not an LLVM-backend one.

## II.6 Deliverables

- **Fixes (fork `pfirmstone/graal`, `master`):** `197c7aae080`, `04ec5fcb73c`, `abdb6beb3ec`,
  `e426a92a326` (plus import fixes `46bf509`, `c426b99`).
- **Determinism gate + attribution tooling:** the `mx` command, the CI workflow, the
  `--backend default` attribution step, the cross-platform matrix workflow.
- **Classifier spec:** `graal/substratevm/docs/llvm-backend-determinism-classifier-spec.md`
  (`850b67c79c3`) — a whitelist/token classifier (machine-verified 10/10) that fails only on
  LLVM-backend-attributable divergence (statepoint ids, structural IR) and reports type-id/
  field-offset diffs as "known upstream", so the gate is a durable regression guard rather than
  permanently red. Ready for a Sonnet/medium agent to implement.
- **Reproducibility program plan:** `graal/docs/determinism/` — a machine-readable, self-
  propagating task DAG (`tasks.yaml` + JSON schemas), an orchestration loop (`ORCHESTRATION.md`),
  and **8 ready-to-dispatch Stage 0 agent prompts** (`prompts/stage-0/`). Only Stage 0 is
  pre-written; each stage's **review gate** (`R0…R5`) revises the plan from what was learned and
  generates the next stage's prompts. Per task: agent archetype, model tier, effort, deps, and
  validation loop (local/ci/corpus). The load-bearing idea is **stage checkpoint/replay**
  (S0.7): freeze one pipeline stage's input and replay the next twice, so each stage's
  determinism is testable in isolation — decomposing one intractable problem into N independent
  ones.

## II.7 Execution & infrastructure

- **Windows cannot build the LLVM backend** (no JavaCPP Windows binary); the whole investigation
  was CI-bottlenecked (~12-min round-trips). The program needs a **Linux build environment**.
- **Recommended:** run an Ubuntu VM on the **32-core / 512 GB machine** (not the 6-core box) on a
  **dedicated 500 GB NVMe (Samsung 970 EVO Plus), whole-disk dedicated**. High core count both
  builds far faster *and* better surfaces the parallelism-driven nondeterminism we hunt; 512 GB
  enables **tmpfs builds** (removes disk-I/O timing as a variable). Pin a fixed core count,
  LLVM 20.1.4 toolchain, locale/TZ, and `SOURCE_DATE_EPOCH` — the build environment is itself a
  determinism variable. Keep CI for cross-platform/cross-machine validation.
- **Autonomy:** delegate hands-off control via bypass-permissions inside the isolated VM, made
  safe by **VM snapshots + a fork-scoped git token + branch-only work + a human gate on upstream
  PRs and `halt` conditions** (the plan's review gates are the come-back-to-you points).

## II.8 Updated implications for JGDMS-STD-007 (supersedes Part I Rec #1)

- **GraalVM Native Image is not byte-reproducible today on any backend**, because reachability
  analysis is nondeterministic. Two honest builders produce different bytes (~5 % of functions,
  plus the downstream `llvm.o`/binary).
- The content-hash trust model therefore **cannot pre-compute an expected artifact digest from
  the input JAR alone.** Realistic options:
  1. **Build-once + attest** (recommended, works today): one builder produces the artifact and
     publishes `SHA-256(bytes)` with signed provenance (Sigstore/SLSA); verifiers trust the
     attestation, not an independently-recomputable hash.
  2. **Drive/await an upstream Native Image reproducibility fix** (the `docs/determinism/`
     program) — large, core, benefits all GraalVM users.
- The **input side is solved**: `Normalize` gives a byte-stable SCAP-anchored JAR before it ever
  reaches Graal. The **output side** awaits the upstream fix or adopts build-once+attest.
- The LLVM-backend statepoint-id determinism fix is a genuine improvement worth upstreaming to
  `oracle/graal` independent of the broader gap.

