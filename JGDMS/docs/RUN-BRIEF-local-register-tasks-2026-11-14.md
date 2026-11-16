# Run brief: the three register tasks for a LOCAL session (2026-11-14)

**For a local Claude session on the maintainer's machine, separate from the session that runs the Windows measurements (`RUN-BRIEF-windows-tls-accept-2026-11-14.md`).**
It does what the cloud session that wrote the handover cannot: read the whole raw transcript on disk and the real register on `E:\Claude`.
The tasks are section 12 of `HANDOVER-focused-bohr-2026-11-14.md` (same directory); read that file first, especially sections 0, 7 and 9, and read `handover-composition/SKILL.md` (pfirmstone/cross-machine_claude_code_skills, `954ccb0`, its last section on the claim register).

## 0. Rules

- **Ask the maintainer before the first write** to the register, and again before any `sync`, `git commit` or `git push`. Say what will be written and where.
- **Nothing is edited in place.** Register logs are append-only (`claim_register.py new`, `status`); the handover gets an appended addendum section (add.02) and an in-place edit of the header's `LATEST ADDENDUM` line only.
- **Quote only what you found.** A "verbatim" quote is text you matched in a raw user message of the transcript, after normalising whitespace only. If you cannot find it, do not register that ruling: report it.
- **Write no DirtyChai source.** (Ruling in the handover, section 7 row 9.) This brief does not touch DirtyChai at all.
- If anything is unexpected, stop and report; do not improvise.
- Do **task 3 only if you are not the same model as the author** (`claude-sonnet-5-5`); otherwise tell the maintainer, who will get another model to do it.

## 1. Task 1: append the nine rulings (they were held back to avoid an ID collision)

1. In `E:\Claude`, check what is real: `git status -sb`, `git log --oneline -5`, `git fetch`, and read `claim_register/rulings/JGDMS/LOG.tsv` and `claim_register/rulings/DIRTYCHAI/LOG.tsv` (their last rows' IDs and dates), and compare with `origin/master`. The JGDMS trunk handover `JGDMS/docs/HANDOVER-2026-11-01-code-free-proxy-window.md` (add.4t) says another session recorded JGDMS rulings numbered 29 to 35; origin/master ended at 28 when the cloud session last looked (2026-11-14), and its DIRTYCHAI scope ended at 4.
   Report the numbers you find. **If another session may still be writing in the same scope, stop and ask the maintainer**: one scope has one writer at a time.
2. The nine rulings are the table in section 7 of the handover (statement, the maintainer's words, where the quote came from, locator). For each, find the maintainer's words in the raw transcript of cloud session `session_01C2445ZHSiqqCFhjuD9dFAS` (the maintainer says where it is on disk; if it is a `.jsonl`, user messages are the records of type user without an `isCompactSummary` flag; the first part of the file before the 2026-10-23 compaction holds the original wording of rows 1, 2, 6 and 9, which the cloud session only had through a summary). Record for each: the exact text found, its timestamp or line, or "not found".
3. With the maintainer's go-ahead, append each ruling whose words you found:
   `python -I E:\Claude\claim_register\claim_register.py new JGDMS IN-FORCE --kind ruling --statement "..." --locator "..." --by "<you: model, session>, for DERPERF (cloud session_01C2445ZHSiqqCFhjuD9dFAS)" --unblock "..." --note "<the words you found>"` (rows 1 to 6) and the same with `DIRTYCHAI` (rows 7 to 9).
   Use the statements of the handover table, which are already narrowed to what the words support; the part marked there as the author's reading stays out of the statement unless the maintainer's own words say it.
4. Run `lint` and report any error. Ask before `sync`/push. Then tell the maintainer the allocated IDs; the handover's section 7 gets a pointer in task 2's addendum.

## 2. Task 2: audit the self-error count and recover the agent IDs

**2a. The self-error count.** Section 0 of the handover names at least 10 errors by the author and calls the count an under-count that was never checked against the transcript. Build the checked count:
- Population: assistant statements in the cloud session that the maintainer or a later tool result overturned, and the author's own corrections ("I was wrong", "correction", "my mistake", "disclosed", strike-through, replacement of a register row). Source: the raw transcript only. Scoring rule: an item counts if a later record in the transcript shows the earlier statement was wrong; record that later record's timestamp or line.
- Output: a table (n, what was claimed, what overturned it, locator in the transcript), the total, and which of the handover's ten named errors you could confirm and which you could not find. The cloud session cannot see the part of the transcript before the 2026-10-23 compaction, so errors 1 to 3 in the handover need your check in particular.

**2b. The roster.** Section 9 of the handover lists the subagents the cloud session still knew. Missing are the S0 board seats of 2026-10-20 and 2026-10-21 and the Opus divergence-analysis agent of 2026-10-23, and possibly others. Agent calls in the transcript have a result that begins `Async agent launched successfully` followed by `agentId: a...`. List every distinct agent ID in the transcript with: the call's `description`, its `model` if given, its date, and whether the handover's roster has it. Do not guess an ID or a role.

**2c. The addendum.** With the maintainer's go-ahead, append `## 13. Addendum add.02 (local session, <date>)` to the end of `JGDMS/docs/HANDOVER-focused-bohr-2026-11-14.md` with the tables from 2a and 2b, the IDs from task 1, and what you could not find. Update only the `LATEST ADDENDUM` line of the claim header. Run `python -I E:\Claude\claim_register\claim_register.py lint <the file>` (it must report 0 errors), commit, and push to `claude/focused-bohr-t6bwal` after `git pull --rebase`; if the push is rejected, stop and report.

## 3. Task 3: a second review of the corrected handover (a model other than the author's)

The first version was reviewed by an Opus agent (section 10 of the handover) and corrected; the corrected file has not been reviewed. Check, with commands, and report findings ranked by severity (CONFIRMED = you ran or traced it; PLAUSIBLE = not):
- Every commit hash, test name, file path and heading the handover and the 19 rows `CL-DERPERF-0001` to `0019` quote (`git log -1`, `grep`); the arithmetic of every quoted count; the register's current row for each ID cited (use `show` and the scope's `INDEX.md`) against the handover's wording.
- The corrections of section 10 actually made in the sections above it (the emulation staleness strike in sections 3, 4 and 5; the S0 summary in 2a; the recomputed keyword counts in section 11; the roster; the rulings table).
- Appendix A against `git log --first-parent --reverse --format='%h %ad %s' --date=short 9465d4661..87953afea` (83 lines).
- The header: five lines, in the first 15, pointers only, no status next to any ID; `lint` output for the file.
- What would mislead a successor who has only the handover, the register and the repository.
Report what you did not check. Do not edit the handover for findings: send them to the maintainer, who has the cloud session append the corrections in place.

## 4. What to send back

For each task: what you did, every command and its output (not paraphrased), what you did not do or could not find, and anything that differed from this brief. Do not interpret beyond what you found.
