# Handover: DER codec performance, streaming encoder and admission work

**AS-OF 2026-10-18 (updated after A2, which landed after A1), branch `claude/focused-bohr-t6bwal`; originally written at `53c369511`, A1 and the target untrack landed after** (source: `git rev-parse --short HEAD`;
`git status -sb` showed the branch level with `origin/claude/focused-bohr-t6bwal` and a clean tree after restoring
tracked `target/` build files). Anything committed after `53c369511` is NOT reflected here: run
`git log --oneline 53c369511..HEAD` first. Written following `handover-composition` (pfirmstone/Cross-machine_Claude_Code_skills).

## 1. Where the truth lives

| Question | Authoritative file |
|---|---|
| What the first SOW (T0-T6) built, why T2/T3 differ from plan, post-closeout fixes | `JGDMS/docs/SOW-DER-Codec-Performance.md` section 8 |
| What to build next (Part A streaming encoder, Part B whole-stream admission), decisions, open questions | `JGDMS/docs/SOW-DER-Streaming-Encoder.md` |
| Benchmark before/after, caveats, top self-time frames | `JGDMS/jgdms-benchmarks/serialization/RESULTS.md` (second section, "after the DER codec performance work") |
| Review heuristics (board skill) | `.claude/skills/jgdms-board-review/SKILL.md` -> `JGDMS/docs/JGDMS-Board-Reviewer-Guidance.md` |
| Build recipe and tool-output trap | section 4 below |

## 2. State, with provenance

- **Tests (after A2):** 951 run, 0 failures, 0 skipped, in `jgdms-der` (source: `mvn -pl jgdms-der test`); the A1 line below is older. **Tests (after A1):** 933 run, 0 failures, 0 errors, 0 skipped, in module `jgdms-der` (source: `mvn -pl jgdms-der test` on DirtyChai 27; the A1 hook is now enabled). Before A1 it was 929 with 1 skipped. Population: `jgdms-der` (source: last full `mvn -pl jgdms-der test`
  on DirtyChai 27 after commit `53c369511`'s change; population: that module only, NOT `jgdms-jeri`/`jgdms-cel`, which were
  spot-run earlier with `-Dtest='*Der*,*DER*,*Cel*'` and passed). The 1 skipped test is the A1 hook
  `DerEncoderEquivalenceScaffoldTest.derEncoderMatchesReference` (deliberately `@Disabled`).
- **Golden fixtures:** 47 stream files after A2 (two insertion-ordered-map fixtures added; the 45 before are unchanged) plus 5 Entry files in `golden-entry/`; the older note follows: 45 files in `JGDMS/jgdms-der/src/test/resources/golden/` (source: `ls | wc -l`). The 31 oldest were
  generated from trunk `9adc8e6e3` before any of this work; a later run showed the first 46 (incl. the since-removed
  `list-70000`) were byte-identical to `9adc8e6e3`'s output. Fixture mismatch means STOP and report; regenerate only for a
  deliberate wire change (`-Dtest=DerGoldenBytesTest -DargLine=-Dgolden.generate=true`).
- **Benchmark numbers** (population: DER only, JMH 2 forks x 5 measurement iterations, DirtyChai 27, THIS Linux container):
  marshal `record` 0.43x, `batch100` 0.49x, `blob64k` 0.69x of baseline; unmarshal `record` 0.68x, `batch100` 0.91x,
  `blob64k` 1.04x (within noise). Source: `results-before.json` / `results-after.json` next to RESULTS.md. Scoring rule: a
  ratio is only a finding if the 99.9% intervals do not overlap and it exceeds the ~12% run-to-run drift shown by
  `JeriBenchmark.ping` (226.1 -> 198.4 us). **UNVALIDATED for the maintainer's workstation:** re-run there before quoting.

## 3. Commits this session (`git rev-list --count 9adc8e6e3..53c369511` = 24)

Code: `f7fc4d7a8` T0 oracle; `9d1c38de0` T0(a)+T2; `0e0705010` T1; `b558e5f32` T5; `ec5ad9c41` T4; `96af10bf4` T3 stage 0 (corpus);
`e8a0effb9` T3 stage 1; `613e978cc` T3 soundness test + SOW notes; `01b70a6a0` T6; `a7fff207d` fix non-canonical chain; `bba3081d3` fix T4 ceiling skip; `9171b568d` fix DGC flush on failed
decode; `ef2fdc6a9` strict UTF-8; `a0acb9c6d` A0 corpus; `53c369511` encoder rejects collections > 65 536.
Docs: the rest (SOW creation, board folding, decisions).

## 4. Build/test recipe and the output trap

- JDK: DirtyChai 27 at `/opt/dirtychai/jdk-27` (stock JDK 21 is NOT enough). In a fresh container re-download from the
  DirtyChai GitHub release `dirty-chai-latest` (`jdk-linux-x64.tar.gz` + `.sha256`), extract to `/opt/dirtychai`.
- `export JAVA_HOME=/opt/dirtychai/jdk-27 PATH=/opt/dirtychai/jdk-27/bin:$PATH`; Pack200-ex-openjdk 1.27.2 jar from its GitHub
  release installed with `mvn install:install-file ... -DgroupId=au.net.zeus.pack200-ex-openjdk -DartifactId=Pack200-ex-openjdk`;
  then `cd JGDMS && mvn -q -pl jgdms-der -am install -DskipTests`. Test: `mvn -pl jgdms-der test`.
- Benchmarks: `mvn -q -f jgdms-benchmarks/pom.xml -pl serialization package -DskipTests`; run with `sh run.sh ...` (not executable).
  `git checkout` the regenerated `dependency-reduced-pom.xml` afterwards.
- **Pipe maven through `grep ... | cut -c1-150`.** A failing golden assertion prints whole hex streams (cost ~10k tokens once).
- **Never `git checkout -- <file>` to undo a mutation test on a file that has uncommitted real edits**: it reverts the real edit
  too (this happened once on `ObjectCodec.java`; recovered by re-applying). Copy to a backup first.
- Tracked `target/` files show as modified after every build; restore with `git checkout -- <those paths>`, do not commit them.

## 5. Decisions already made (maintainer, Peter) and the board

- Whole-stream "read-ahead" = defensive pre-admission only (no pipelined decode, no write-ahead).
- Admission: two-stage; **fail-closed by default** for dispatcher-created streams; lazy-admission switch documented, NOT built.
- Strict UTF-8 both directions, no flag (done). Encoder rejects over-ceiling collections (done). DGC flush fix (done).
- B6 (hoist permission checks into phase 1): demoted, not dispatched.

## 6. Do-NOT-redo list (each conditional)

| Item | Rests on | Unblocks when |
|---|---|---|
| Dedup-while-encoding | EVIDENCE: Appendix C C.6.2 first occurrence follows wire element order and `SET OF` is octet-sorted (X.690 11.6; `ObjectCodec.encodeCollection` -> `CollectionWireTypes.octetSort`). Board seat 1 noted only subtrees under a sorted ancestor strictly need deferral. | C.6.2 is amended, or someone designs deferral only under sorted ancestors |
| Lazy-admission switch | RULING (Peter, 2026-10-18): wait for a real custom-dispatcher operator | such an operator appears |
| Lenient UTF-8 / compat flag | RULING + G1 (one encoding per value); all 3 board seats agreed | a ruling reverses it |
| B6 now | board verdict (Fable: marginal, domain-set drift; PLAUSIBLE, not run) | measurement shows per-object permission checks cost materially |
| Quoting benchmark numbers | EVIDENCE: container, not the workstation; `point` rows noise | workstation re-run |

## 7. Open items

1. **Next tasks (SOW effort in brackets):** B0 pin today's behaviour [medium, independent]; A2 migrate the object encoders (callee-first; sorted sets via `raw()`) [high]; then A4, B1 [high]; A3, B2, B5 [xhigh]. **A0, A1 and A2 are DONE** (A2: all object encoders write through `DerEncoder`; see the A2 OUTCOME in the SOW; next is A3 [xhigh] then A4 and A5) (A1: `DerEncoder` = `CompactDerEncoder`, benchmark in the SOW A.1.1). The maintainer has not yet reviewed the streaming-encoder SOW.
2. Ask the user (in bold) before an effort change; efforts are set by the user.
3. DGC fix was tested at the `DerMarshalInputStream` level only; B0 should confirm it through the real dispatcher.
4. ~~`JGDMS/jgdms-der/target/` and `dependency-reduced-pom.xml` are tracked in git (not mine)~~ CORRECTED 2026-10-18: the 569 tracked `jgdms-der/target/` files WERE mine - force-added by `git add -f JGDMS/jgdms-der` in `ef2fdc6a9` (trunk `9adc8e6e3` tracks none; source: `git ls-tree -r 9adc8e6e3 --name-only | grep -c jgdms-der/target/` = 0). Untracked in the commit after `a6c2679d6`; `.gitignore` already had `**/target/`. NEVER use `git add -f` on a whole module. `dependency-reduced-pom.xml` IS trunk's own tracked file: leave it, `git checkout` it after benchmark builds.
5. STD-006 now has the strict-UTF-8 sentence (4.2); STD-011 Appendix B B.5 item 10 cites it.

**A2 landed (after the paragraphs above were written):** commits `012bd348d`, `03e54527a`, `ab5676555`, `c1c60b29b`, then the Entry commit `81a6ef556`. Wrappers keep every old `byte[]` method; `DerEncoder` gained `utf8/octets/integer/bool/nul`, `beginOctetString`, `beginSortedMap`, and a failure latch. Open maintainer decision: an encode-side total-size cap. DO NOT remove the `byte[]` wrappers until A4 moves `DerObjectStreamCodec`.

## 8. Agent roster (all finished; resumable with `SendMessage` by agentId; read-only; own worktrees under /tmp/claude-0/board-*)

| agentId | Model | Seat | Holds |
|---|---|---|---|
| a0d493143a7120bbe | Opus | ASN.1/DER + dedup | found F1 (non-canonical chain regression); UTF-8 canonical-form view |
| acf480f8e90d2434d | Fable | adversarial security | verified fast path / ScopedValue sound; B6 critique; admission fail-closed trap |
| a6e9cd1a17d235581 | Sonnet | transport / fidelity | dispatcher order, DGC-on-close, accept-set change, measurement honesty |

## 9. CURRENCY CHECK (handover-composition, mechanical)

Keywords checked against this document at composition: `ceiling`, `UTF`, `DGC`, `fail-closed`, `A0`, `board`, `lazy`. Items
deliberately excluded: the individual bytes of every golden fixture (in the fixtures), full board report texts (in the agent
transcripts, not on disk), and the HANDOVER scratch copy at the old scratchpad path `/tmp/claude-0/-home-user-JGDMS/.../scratchpad/HANDOVER.md`,
which this file supersedes (struck in place: it predates the ceiling fix and the A0 corpus size).
