claude-sonnet-5-5
# Opus findings 1 and 3 (refresh/JWKS/lint review): fixes (2026-11-02, base 50697bc18, nothing committed)
Evidence: my own runs, `JAVA_HOME` = the DirtyChai image, `mvn -o -pl jgdms-platform,tools/policy-condenser test -Dtest=...`, provider `ConcurrentPolicyFile`, Windows only. Nothing in jgdms-security-jwt, DirtyChai or E: was touched.
## Rule table (PolicyScope.file, javadoc has the same table)
Text after `file:`; a backslash is a slash; only the PATH part of a `file:` URL is percent-decoded (UTF-8, `%2F` and `%5C` are separators); a scheme-less path is not decoded.
| raw leading slashes | authority | path judged | result |
|---|---|---|---|
| 0 or 1 | none | the text, decoded | local unless it then starts with 2+ slashes |
| 2 | first segment | after the host, decoded | `localhost`: as the path rule (only ONE slash may follow); any other host: share |
| 3 | empty | from the third slash, decoded | as the path rule |
| 4 or more | a share | - | REMOTE_WILD |
| slashes only | - | the root | local (unchanged) |
| decoded path starts with 2+ slashes (also only slashes) | - | - | REMOTE_WILD |
| malformed escape (`%zz`, lone `%`, `%4`) in the path | - | - | REMOTE_WILD (fail closed) |
A percent-encoded host (`%6Cocalhost`) is not decoded and stays a host. `jar:` nests through the same rule.
## Changes per file (tools/policy-condenser)
- `PolicyScope.java`: `file()` rewritten without the loop (the `n == 3` branch no longer runs after `localhost`); new `share()` and `percentDecode()`; `file()` has a `url` flag.
- `PolicyLintGateTest.java`: 4 tests (`localhostIsLocalOnlyBeforeASinglePathSlash`, `percentEncodedSeparatorsAreDecodedBeforeTheSlashRule`, `ordinaryEscapesAreDecidedAfterDecoding`, `aMalformedEscapeInAFileCodeBaseIsAStranger`) plus `everySlashRunKeepsItsResult` (the old cases).
- `PolicyLintCorpusTest.java`: `Allow` now has files and a reason; `allowListProblems`, `staleReferences`; `judgeListed` checks the file list; dump of entry files (`LINT_CROSS_DUMP`, `#ENTRY` lines); tests `everyAllowEntryNeedsItsOwnSpecificReason`, `anAllowEntryCoversOnlyTheFilesItNames`, `aStaleFileReferenceFails`.
- Fixtures `corpus/n/n102..n107` (LF) and 6 rows in `corpus-expectations.tsv` (CRLF kept): n102 localhost///, n103 `%2F%2F`, n104 `%5C%5C`, n105 malformed (P1 D1), n106 jar localhost///, n107 `%61pp` (clean). n107 added to `cross-check-no-listing.tsv`.
- `cross-check-allow.tsv`: rewritten, 5 columns (class, name, actions, files, reason), LF.
## Allow-list numbers
Entries 49 (unchanged); distinct reasons 49 (before: 33 shared one boilerplate sentence); file references 49 (before 0): every entry has exactly one file, because the corpus yields one fixture per permission, except `ok2-fileread-dir`, which gives two entries. Rules enforced: reason at least 25 characters, no two equal, cites a stem of a named fixture; a file outside an entry's list fails; a name no file grants fails.
| broadest entry | file that grants it |
|---|---|
| `[A-Za-z]:[\\/]- read` (drive root) | r8/f18-cdrive-dash-read |
| `${user.home}[\\/]- read` | n/n26-home-read-high |
| `<<ALL FILES>> readlink` | r8/f05-allfiles-readlink |
| `SocketPermission *.com connect,resolve` | r8/s04-socket-subnet |
| `setIO`, `exitVM` (and `exitVM.0`) | r8/r14-rt-setIO; r8/r16-rt-exitVM (r17-rt-exitVM_0) |
| `modifyPublicCredentials`, `setLoginConfiguration`, `doAs` | r8/u07, r8/u06, r8/u02 |
All of these fixtures are UNSCOPED `grant { }`, so every stranger gets the permission and the default run exits 0; they are D5 by the catalogue.
## Fail-first
| test | before the fix | after |
|---|---|---|
| Gate localhost / percent / malformed / ordinary-escape tests | 4 failures on trunk PolicyScope (runs fail1, fail3) | pass |
| Corpus `corpusExitCodesAndKinds` n102-n106 | got 0/0 [] (n105: P1 only) on trunk | pass |
| Corpus tests with the old 49 lines converted to 5 columns | `cleanFiles...`: "boilerplate reason, identical to" (two ok2 entries shared a reason); with that fixed, "stale file reference" for every placeholder file | pass with the real lists |
The three allow-list unit tests have no trunk counterpart (new methods): their fail-first evidence is mutants M4 to M6.
## Mutants (private `git archive` copy plus my files; one at a time; script `lint7\tools\mut.py`; sha256 of each restored file equal, printed `True`)
Command: `mvn -o -pl jgdms-platform,tools/policy-condenser test -Dtest=PolicyLintGateTest,PolicyLintCorpusTest`
| id | mutant | Tests run | killed by |
|---|---|---|---|
| M1 | localhost followed by extra slashes local again | 39, 5 failures | Gate x4, Corpus n101 |
| M2 | percent-decoding removed | 39, 4 failures | Gate x3, Corpus n103 |
| M3 | malformed escape returns the text (local) | 39, 2 failures | Gate malformed, Corpus n105 |
| M4 | identical reasons accepted | 39, 1 failure | `everyAllowEntryNeedsItsOwnSpecificReason` |
| M5 | a file outside the list inherits the entry | 39, 1 failure | `anAllowEntryCoversOnlyTheFilesItNames` |
| M6 | stale file reference accepted | 39, 1 failure | `aStaleFileReferenceFails` |
## Flagged for the operator (reason is the catalogue rating, which I could not justify independently)
f18 `C:\-` read, n26 home read, f05 readlink, s04 `*.com` connect, r14 setIO, u02 doAs, u07 modifyPublicCredentials, u06 setLoginConfiguration, f14 relative `lib/-` write (documented ruling), c14 getDomainCombiner, r15 shutdownHooks. Also c12 `authProvider.*` and c04 `getProperty.*`: the old reasons said "exact name", but BasicPermission treats `.*` as a wildcard, so they are wildcards; I rewrote the reasons accordingly and the catalogue still calls them clean.
## Not verified
- The one-sentence meaning of each JDK permission in the 33 reasons is from my knowledge, not run (e.g. manageProcess, modifyThreadGroup, enableContextClassLoaderOverride).
- Whether ConcurrentPolicyFile matches these codeBases to code loaded from a share; Linux; full platform suite.
- Flake: `r6/a04`, `a33`, `r8/q03`, `z02` lacked their P1 in 2 of my runs that followed a recompile (expectations say P1) and had it in others, with and without my change; not investigated.
## Questions
1. Should an unscoped grant of u06/u07/u02, s04, r14 or f18 still exit 0 in the default run?
2. Should the allow-list lose `.*` BasicPermission wildcards (c12, c04) in favour of a catalogue rating?
