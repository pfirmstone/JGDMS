# Agent Context: Graal Repository Investigation
## LLVM Backend, Web Image, and Native Image Compilation Pipeline

**Task:** Investigate the checked-out Graal repository to answer specific questions
about the LLVM backend for Native Image, the Web Image (WASM) backend, and the
compilation pipeline. Findings will be used to finalise the open questions in
JGDMS-STD-007 (CodebaseCompatibilityInterchange standard).

**Repository:** Local checkout of `https://github.com/oracle/graal`

**Report format:** For each question, give a direct answer, cite the relevant source
file(s) with line numbers, and note any caveats. Where code is relevant, include
a short excerpt. Flag anything unexpected or that contradicts the GraalVM
documentation.

---

## Background

JGDMS-STD-007 defines a service that compiles SCAP-verified Java proxy JARs to
language-native artifacts (shared libraries, WebAssembly modules) for non-JVM
clients. The compilation pipeline uses GraalVM Native Image with the LLVM backend
to produce LLVM IR, which is then compiled to target-specific artifacts.

Key open questions from STD-007 that this investigation should resolve:

1. Is the LLVM backend still actively maintained and present in the current
   repository?
2. Can the LLVM backend produce LLVM IR bitcode (`.bc`) files as an intermediate
   artifact, independently of producing a final native binary?
3. Is compilation deterministic? Are there any timestamps, non-deterministic
   ordering, or non-reproducible elements in the LLVM IR output or the final
   artifacts?
4. What is the current status of AArch64 and RISC-V support via the LLVM backend?
5. What is the current status of the Web Image (WebAssembly) backend? Is it in
   this repository? How mature is it?
6. What LLVM version does the backend target? Are LLVM statepoints still described
   as experimental?
7. Is the `BitcodeOptimizations` flag still marked as potentially causing bugs?
8. Is there a cross-compilation path (build LLVM IR on one architecture, compile
   to native on another)?
9. What GC is used by SubstrateVM in the LLVM backend path? How are statepoints
   implemented?
10. Is the `native-image-llvm-backend` component still available for JDK 21+
    without building from source?

---

## Repository Structure

Start by mapping the relevant directories. Key locations to explore:

```
graal/
  substratevm/                      # Native Image / SubstrateVM
    src/
      com.oracle.svm.core/          # Core SVM runtime
      com.oracle.svm.core.graal.llvm/  # LLVM backend (if present)
      com.oracle.svm.hosted/        # Hosted compilation
      com.oracle.svm.hosted.llvm/   # Hosted LLVM (if present)
      com.oracle.objectfile/        # Object file handling
    mx.substratevm/                 # Build config, component descriptors
  compiler/                         # Graal compiler
  sdk/                              # GraalVM SDK
  wasm/                             # WebAssembly (Web Image candidate)
  web-image/                        # Web Image backend (if present)
  truffle/                          # Truffle framework
```

---

## Investigation Tasks

### Task 1: LLVM Backend Location and Activity

**Find** all directories and packages containing "llvm" (case-insensitive) in
`substratevm/src/`. List them all.

**Check activity:**
- When was the most recent commit to the LLVM-related packages? (`git log --oneline
  -10 -- substratevm/src/com.oracle.svm.core.graal.llvm/`)
- Is there a `CHANGELOG` or release note mentioning the LLVM backend being
  deprecated or removed?

**Key files to find and read:**
- The main LLVM backend entry point (likely `LLVMNativeImageCodeGenerator.java`
  or `LLVMBackend.java`)
- The component descriptor for `native-image-llvm-backend` (look in
  `mx.substratevm/` for `.component` or suite files)
- Any `README` in the LLVM packages

### Task 2: LLVM IR Bitcode Output

**Question:** Can the LLVM backend emit `.bc` (LLVM bitcode) files as a
first-class output, not just as a temporary intermediate?

**Investigate:**
- Look for `-H:TempDirectory` handling — does it save `.bc` files?
- Is there a flag to emit LLVM IR without proceeding to native compilation?
- Look for any `LLVMObjectFileCreationTask`, `LLVMBitcodeCreationTask`, or similar
- Search for `".bc"`, `"bitcode"`, `"llvm.bc"` in the LLVM package source files
- Is there a `--emit-llvm` or `-H:CompilerBackend=llvm-ir` option?

**Expected finding:** Report exactly which files are saved to the temp directory
during LLVM backend compilation, and whether `.bc` files are among them.

### Task 3: Determinism Analysis

**This is the most critical question for STD-007's trust model.**

**Search for non-deterministic elements:**

```bash
# Search for timestamp usage in LLVM-related code
grep -r "timestamp\|System.currentTimeMillis\|new Date()\|Instant.now\|LocalDateTime" \
  substratevm/src/com.oracle.svm.core.graal.llvm/ \
  substratevm/src/com.oracle.svm.hosted.llvm/ 2>/dev/null

# Search for random/UUID usage
grep -r "UUID\|Random\|Math.random\|SecureRandom" \
  substratevm/src/com.oracle.svm.core.graal.llvm/ \
  substratevm/src/com.oracle.svm.hosted.llvm/ 2>/dev/null

# Check for HashMap iteration (non-deterministic in Java)
grep -r "HashMap\|HashSet" \
  substratevm/src/com.oracle.svm.core.graal.llvm/ \
  substratevm/src/com.oracle.svm.hosted.llvm/ 2>/dev/null | head -30
```

**Check for sorted/ordered collections:**
- Are `LinkedHashMap`, `LinkedHashSet`, `TreeMap`, or sorted collections used
  where order matters for output?

**Check LLVM IR naming:**
- Are function names in the LLVM IR deterministic? Or do they include addresses
  or other runtime-derived identifiers?

**Report:** List every source of non-determinism found. If none found, state that
explicitly. This is the primary finding STD-007 needs.

### Task 4: Architecture Support Status

**AArch64:**
- Search for `AArch64` in the LLVM backend source files
- Is `SubstrateAArch64LLVMBackend.java` or equivalent present?
- Does it handle statepoints for AArch64?
- What flags are needed to enable AArch64 via the LLVM backend?

**RISC-V:**
- Search for `RISCV\|RiscV\|riscv` in the LLVM backend source
- Is the experimental RISC-V LLVM backend mode present?
- Any test files for RISC-V?

**Expected directory:**
```
substratevm/src/com.oracle.svm.core.graal.llvm/src/
  com/oracle/svm/core/graal/llvm/
    lowering/
    replacements/
    ...
```

### Task 5: Web Image / WebAssembly Backend

**Find the Web Image backend:**
```bash
find . -type d -name "*web*image*" -o -type d -name "*wasm*" 2>/dev/null | grep -v ".git"
find . -name "*.java" | xargs grep -l "WebImage\|web.image\|wasm.*backend" 2>/dev/null | head -20
```

**Questions:**
- Is there a `web-image/` or `wasm/` top-level directory?
- What is the entry point for the Web Image backend?
- What `native-image` flag enables it? (The docs mention `--tool:svm-wasm`)
- Is it in the same repo as the Native Image LLVM backend, or a separate module?
- What is the current state — alpha, experimental, early access?
- Does it use the LLVM backend internally, or is it a direct WASM code generator?
- What WASM features does it target (MVP, GC, component model)?
- Is there a `README` or documentation in the web-image directory?

**Read the main Web Image backend class** and summarise what it does and any
stability caveats in the source.

### Task 6: LLVM Version and Statepoints

**Find the LLVM version requirements:**
```bash
grep -r "llvm.*version\|LLVM_VERSION\|llvm-toolchain\|llvmVersion" \
  substratevm/ mx.substratevm/ --include="*.java" --include="*.py" | head -20
```

**Statepoints status:**
- Find the statepoint implementation files (search for `statepoint\|StatepointUtil`
  in the LLVM package)
- Is there any comment about statepoints being experimental or stable?
- What LLVM version do statepoints require?
- Is there a `LLVM_MIN_VERSION` constant or similar?

**Report:** The exact LLVM version required and whether statepoints are described
as experimental in the current source comments.

### Task 7: BitcodeOptimizations Flag Status

**Find the flag:**
```bash
grep -r "BitcodeOptimizations\|bitcodeOptimizations\|bitcode.opt" \
  substratevm/src/ --include="*.java" | head -20
```

**Questions:**
- Is `BitcodeOptimizations` still present?
- Is it still marked as potentially causing bugs in source comments?
- What does it do — what optimisation passes does it enable?
- Is there a test that enables it?

### Task 8: Cross-Compilation Support

**Find cross-compilation related code:**
```bash
grep -r "cross.compil\|CrossCompile\|targetPlatform\|target.architecture\|targetArch" \
  substratevm/src/com.oracle.svm.core.graal.llvm/ \
  substratevm/src/com.oracle.svm.hosted/ \
  --include="*.java" | head -20
```

**Questions:**
- Is there explicit cross-compilation support in the LLVM backend?
- Can you specify a target triple (e.g., `arm-linux-gnueabihf`) to compile for a
  different architecture on an x86 host?
- Is the LLVM IR emitted as a target-independent intermediate, or is it already
  lowered to a specific target?

### Task 9: GC and Statepoint Implementation Details

**Read the statepoint implementation:**
```bash
find substratevm/src -name "*.java" | xargs grep -l "statepoint\|Statepoint" | head -10
```

**For each file found:**
- What GC interface does it use?
- Are statepoints inserted via LLVM intrinsics or via a separate mechanism?
- Is there a `GCProvider` or `GarbageCollector` interface in the LLVM path?
- What happens with virtual threads / carrier threads in the LLVM backend?

### Task 10: Component Availability for JDK 21+

**Check the component/suite configuration:**
```bash
# Find component descriptor files
find mx.substratevm/ -name "*.py" -o -name "suite.py" | head -10
grep -r "native-image-llvm-backend\|llvm.backend" mx.substratevm/ | head -20
```

**Questions:**
- Is `native-image-llvm-backend` still defined as a distributable component?
- Is there a GraalVM Updater (gu) component descriptor for it?
- What changed between GraalVM 22.x and GraalVM for JDK 21+ regarding component
  distribution?
- Is there a `components.py` or `registered_graalvm_components` that includes or
  excludes the LLVM backend?

---

## Additional Investigation: Pack200 Integration

While in the repository, check whether there is any Pack200-related code in the
Native Image build pipeline:

```bash
find . -name "*.java" | xargs grep -l "Pack200\|pack200\|JarPacker" 2>/dev/null | head -10
```

Report: Is Pack200 present in the Graal repository, or is it entirely external?

---

## Output Format

Structure the findings report as follows:

```
# Graal LLVM Backend Investigation Report

## Summary
[3-5 sentence overview of the most important findings]

## Finding 1: LLVM Backend Status
[Direct answer + source evidence]

## Finding 2: LLVM IR Bitcode Output
[Direct answer + source evidence]

## Finding 3: Determinism Analysis
[List of all non-deterministic elements found, or explicit "none found"]
[This is the most critical finding for STD-007]

## Finding 4: Architecture Support
[AArch64 and RISC-V status]

## Finding 5: Web Image / WASM Backend
[Location, maturity, relationship to LLVM backend]

## Finding 6: LLVM Version and Statepoints
[Version requirements, statepoint status]

## Finding 7: BitcodeOptimizations Flag
[Current status and any stability notes]

## Finding 8: Cross-Compilation
[Whether possible and how]

## Finding 9: GC and Statepoint Details
[GC model, statepoint insertion mechanism]

## Finding 10: Component Availability
[Whether native-image-llvm-backend is distributable in JDK 21+ era]

## Additional: Pack200
[Present or absent]

## Recommendations for STD-007
[Based on findings, which GraalVM version/path is most suitable for
deterministic artifact compilation. Note any blockers or alternatives.]
```

---

## Key Files to Prioritise (Read These First)

If time is limited, prioritise these files in order:

1. `substratevm/src/com.oracle.svm.core.graal.llvm/` — entire package structure
2. Any `suite.py` or component descriptor in `mx.substratevm/`
3. The main Web Image backend class (once located)
4. Any file with "statepoint" in the name
5. Any file with "deterministic\|reproducible\|timestamp" in content

---

## Context for the Investigator

This investigation supports the design of `CodebaseCompatibilityInterchange`
(JGDMS-STD-007), a service that:

1. Takes a SCAP-verified Pack200 JAR (content-hash-verified Java proxy code)
2. Compiles it to LLVM IR via GraalVM Native Image
3. Distributes LLVM IR bitcode + compiled artifacts (shared library, WASM module)
4. Each artifact is identified by SHA-256 of its bytes (SCAP-equivalent trust)

The **determinism requirement** is the most critical: the same input JAR must
always produce byte-identical LLVM IR and artifacts. Without this, the
content-hash trust model breaks — the artifact digest cannot be pre-computed or
independently verified.

The **WASM target** is the most important for cross-platform embedded clients.
If Web Image provides a more reliable path to WASM than LLVM→WASM, that changes
the pipeline design.
