# SOW: can client-side and server-side CEL remove code download from the lookup service?

- **Status:** scope for an investigation and design proposal. Nothing in it is a decision.
- **Date:** 2026-11-20. **Written by:** Claude Fable 5.2, proxy-board review session, at the operator's request.
- **Reviewer of the result:** the same session (the operator passes the result back).
- **Output:** one file, `docs/RESULTS-lookup-cel-investigation.md`. No other file is created or changed.

## 1. The question

The operator asked whether the new lookup service (Derek, `RemoteRegistrar`) can use CEL on the server
and on the client so that a client of the lookup service downloads no code, and whether DETERMINISTIC CEL
needs additions that are safe to make, "such as string concatenation and pattern matching".

Two answers are wanted:

1. Which lookup operations can be done with CEL as it stands, which need a language addition, and which
   cannot be done with CEL at all.
2. For each candidate addition: is it safe under the STD-011 security theorem, at what cost, and is there
   a way to meet the need without it.

## 2. Read first

| File | Why |
|---|---|
| `docs/DESIGN-class-free-lookup-and-registrar-split-2026-10-31.md` sections 0 to 10 and 14 | The lookup design. Decisions D4 to D14, board findings B2, B4, B9, round-2 findings R1 to R17. Nothing in it is built. |
| `docs/JGDMS-STD-011-CEL-Filter-Expression-Format-v0.1-DRAFT.md` sections 1, 3, 4.4 to 4.7, 5.4, 6.5, 6.6, 7, 8, 9.4 to 9.6, 10, 15 | The language, its theorem (3.2), what the theorem does not claim (3.3), the exclusions (5.4) and the open list (15 item 7). |
| `docs/SOW-Smart-Proxy-Isolation-Architecture-Overview.md` section 1a | What CEL must not become: no state, no I/O, no iteration. |
| `docs/DESIGN-Outrigger-CEL-Filter-B1.md`, `docs/DESIGN-Outrigger-CEL-Filter-B3.md` | How a filter is carried, verified and evaluated today in Outrigger. |
| `jgdms-cel`, `jgdms-cel-authoring` | The code. |

## 3. Rules for this work

1. **Read-only.** No production code, test, standard or design file is changed. Existing tests may be run.
   A throwaway probe may be written outside the repository; if one is used, the results file gives its full
   text and its output.
2. **No commit and no push** unless the operator says so.
3. **Do not write to the claim register.** Report findings; the reviewing session registers them.
4. **Label every factual statement:** VERIFIED (you opened the line or ran the command in this session, with
   file, line and anchor text), RELAYED (from a sub-agent, not re-opened), INFERRED, or NOT CHECKED.
   A sub-agent's report is a lead, not a fact.
5. **Record the commit** (`git rev-parse HEAD`, branch name) at the top of the results file.
6. **No addition without a use.** A language feature is assessed only if section A or C shows a lookup need
   for it, or the operator named it (string concatenation, pattern matching).
7. **The extension rule of STD-011 section 1.3 stands:** a construct not covered by the section 3.2 argument
   is not proposed without the text of the extension to that argument.
8. **Stop and report** instead of guessing if a file in section 2 is missing, if the code and STD-011
   disagree on a point this work depends on, or if a section cannot be finished.
9. Plain language. Short sentences. Tables where items are compared.

## 4. Sections of work

Agent types are recommendations. "Sonnet", "Opus" and "Fable" name the model; "Explore" and
"general-purpose" name the Claude Code sub-agent type. The session that runs this scope coordinates and
writes the results file; it should be Opus or Fable.

### A. Survey: what lookup clients filter and match on today
**Agent: Sonnet, Explore (very thorough). Can be split by directory and run in parallel.**

Search `JGDMS/JGDMS` and the repository-level `qa` directory.

1. Every implementation and anonymous use of `ServiceItemFilter`, and every construction of a
   `ServiceTemplate` with attribute templates. For each: file, line, and what it tests.
2. Classify each test by the operation it needs: equality, wildcard, numeric range, substring, prefix or
   suffix, case-insensitive compare, pattern or regular expression, string building, service type test,
   `ServiceID` test, access to a collection or array field, a call on the service proxy, anything else.
3. Every `Entry` class in the tree (the `net.jini.lookup.entry` set and others). For each field: name, type,
   scalar or not. Give the count of entry classes that have a non-scalar field (the measurement the design
   memo's R15 asked for and nobody made).
4. Every place the lookup client path loads a class from a codebase or unmarshals an entry or proxy. Start
   from the memo's B2 (`ServiceDiscoveryManagerImpl`, `LookupCacheImpl`) and `EntryClass`, `EntryRep`,
   `Item` in Reggie. Confirm or correct the memo's locators.

Output: three tables (filters and templates; entry fields; class-load sites). No opinion.

### B. Inventory: DETERMINISTIC CEL as built
**Agent: Sonnet, Explore (medium).**

1. The node inventory and function registry in the code against STD-011 sections 7.2 and 11.1. List any
   difference.
2. The evaluator's entry points, the `CandidateProjection` methods, and every implementation of
   `CandidateProjection` in the repository.
3. Where each ceiling of STD-011 section 10.3 is enforced in code, with the line. In particular
   `maxScalarBytes`: is it enforced at decode on candidate fields, on expression literals, on both, or on
   neither? (STD-011 section 3.3 says bounded cost does not hold until it is enforced on both.)
4. Whether any code evaluates an expression over something other than a stored candidate entry, for example
   call arguments or a client-side record.
5. What `jgdms-cel` and `jgdms-cel-authoring` depend on, and which modules depend on them.
6. Run the `jgdms-cel` tests once and report the counts and the JDK used.

### C. Expressibility: write the surveyed needs in today's language
**Agent: Opus, general-purpose. Needs A and B.**

1. For each distinct operation class from A, write the predicate in DETERMINISTIC CEL as specified, or
   state exactly which rule of STD-011 stops it.
2. Give the general translation of a `ServiceTemplate` (service ID, service types, attribute templates with
   null wildcards) into one predicate. State what the candidate projection must expose for it to work:
   how service type names and entry class names are reached (`in` over a list-typed field, section 6.5, or
   the namespace chain, section 8), and whether an item with two entries of the same class can be
   expressed.
3. For event registration: can the three transition kinds (match to no match, no match to match, match to
   match) be computed by the server from one predicate evaluated before and after a change?
4. List what cannot be done by any pure expression (needs state, I/O or a call on the proxy). Those stay
   with a resident client class or with isolation.

Output: a table with one row per need: expressible today / needs addition X / not a CEL job.

### D. Language additions: safety analysis
**Agent: Fable, general-purpose. Needs C. This is the main design section.**

Candidates: string concatenation and pattern matching (named by the operator), plus any addition that C
shows a need for (likely candidates: case-insensitive compare, substring or code-point indexing, list
indexing, map-field lookup, list equality, number-to-string conversion, `floor`/`ceil`/`round`).

For each candidate give:

1. The exact semantics proposed, including errors, and at least one narrower alternative.
2. **Termination and node visits** (theorem claim 1).
3. **Cost:** the per-node cost formula in the terms of section 10.2, and what it does to `O(|E| x S)`.
4. **Allocation** (claim 4, section 3.2 iv). Today no node builds a string.
5. **Determinism across Java, Rust and Haskell** (claim 2): Unicode version, normalisation, case tables,
   number formatting. Anything that depends on a library version fails this test.
6. **Timing:** does evaluation time depend on candidate values more than it does today?
7. **Wire and verifier changes:** new node or function ids, new verification obligations (section 12).
8. A recommendation: add now, defer, or reject, with the draft text of the section 3.2 extension if "add".
9. A way to meet the need without the addition, if one exists.

Hypotheses from the scoping session to test. They are not findings; confirm or refute each with reasons:

- **H1 (concatenation, growth).** With bindings, `a = s + s`, `b = a + a` doubles the size at each step, so
  256 bindings give a size that no ceiling on node count limits. A safe form therefore needs a static
  size bound for every string-typed node, computed at verification from `maxScalarBytes` and literal
  sizes, and rejection when the bound exceeds `maxScalarBytes`.
- **H2 (concatenation, need).** A transform can already return a list (section 4.7: "delivery as a
  whole"). If a list literal of string parts can be a transform result, the resident client code can join
  the parts, and concatenation inside the language is not needed for display or for building a result.
  Check whether a list literal may hold field references and be the result of a transform.
- **H3 (concatenation, scope).** If concatenation is needed at all, it is needed only in the transform
  context and only to build a result, never as an operand of a comparison. Allowing it only at the root
  of a transform would keep predicates allocation-free.
- **H4 (pattern matching, glob).** A glob whose pattern is a **literal** with only `*` and `?` has cost at
  worst pattern length times subject length, the same class as `contains` with a literal needle
  (section 6.6), so it may fit the existing argument with a small extension. State the matching
  algorithm that gives that bound without backtracking blow-up, and the code-point rules.
- **H5 (pattern matching, regular expressions).** An RE2-class engine with a literal pattern is linear in
  the subject, but it is a large dependency that must give identical results in three languages. STD-011
  section 5.4 item 2 excluded it for that reason. Assess whether a small pinned subset (no backtracking,
  no back-references, no Unicode classes, bounded repetition counts that count towards cost) changes that,
  and what the compiled automaton's size bound is.
- **H6 (case-insensitive compare).** Unicode case folding depends on the Unicode version. An ASCII-only
  fold does not. Is ASCII-only useful for the surveyed needs?

### E. API proposal: `RemoteRegistrar` with CEL in both directions
**Agent: Fable, general-purpose. Needs A to D. Options with reasons, not decisions.**

1. **Server-side predicate (client to Derek).** Does a CEL predicate replace the template record on
   `lookUp`, the item-view lookup and `notiFy`, or sit beside it? How does the resident wrapper translate a
   legacy `ServiceTemplate` (from C.2)?
2. **Server-side transform (client to Derek).** Returning only named values in place of whole entries. What
   replaces `getEntryClasses`, `getServiceTypes` and `getFieldValues`. The effect on the aggregate decode
   ceiling (memo O8).
3. **Client-side expression (service to client).** The operator's idea: the client asks the service for CEL
   bytes and runs them over its own data, in a layout the service API defines, to filter and reduce the
   data before sending. Design: where the expression comes from (the authenticated service's bootstrap
   proxy, or the lookup service), how it is bound to the service's identity, what the argument projection
   is, who writes the loop over rows (resident code, never the expression), and the total cost bound
   (rows times the static cost).
4. **Disclosure rule.** Whatever a service-authored expression can read on the client can reach the
   service through the result. State the rule the client must follow when it fills the projection.
5. **Two-sided matching.** A service publishes a requirement predicate; the client evaluates it over its
   own capability record. Is this useful, and what does it leak?
6. **Evaluation on Derek and timing** (memo B9, STD-011 open item T7). Assess the rule "Derek evaluates a
   caller's predicate only over entries that caller is authorised to read in full". Does it remove the
   cross-tenant channel, or only narrow it? What else is needed (cost ceilings per call, rate limits)?
7. **Proxy kind.** A registration field that says whether the service proxy is dynamic or smart, so a
   code-free client can exclude smart proxies in the predicate. State what still enforces it on the
   client (the DER pin, denied download permission), since the field is only the registrant's claim.
8. **What remains outside CEL:** smart proxies with state or I/O (isolation), the legacy Reggie, JOSS
   clients.
9. **Preconditions** from the memo that this API depends on: D5 schema chains in Derek, the unwritten
   `@SerialEntry` branch of `EntrySchemaGenerator`, STD-011 section 8.2 and 8.5 (open), `maxScalarBytes`,
   the module that would hold client-side evaluation and its dependency on `jgdms-cel` (memo R13).

Give a method sketch for `RemoteRegistrar` and for the service-side accessor. Mark every name as
illustrative.

### F. Review of D and E
**Agent: Opus, general-purpose, fresh context, has not seen D or E being written.** (The producer is Fable;
the operator does not want same-model review.)

Using `docs/JGDMS-Board-Reviewer-Guidance.md`: attack each "add" recommendation in D (find an expression
that breaks the stated bound; find a cross-language difference), and each path in E (who authored the
expression, who evaluates it, under which Subject, what binds it to an authenticated party). Report
findings with a status: CONFIRMED, PLAUSIBLE or UNVERIFIED. The coordinator records them and D and E's
authors' replies; it does not edit findings away.

## 5. The results file

`docs/RESULTS-lookup-cel-investigation.md`, CRLF line endings like the other files in `docs`, in this order:

1. Commit, branch, date, the model and agent id of each section's author (self-reported is acceptable; say so).
2. A one-page answer to the two questions of section 1.
3. Sections A to F, each with its tables.
4. A list of every recommendation, one line each, with: add / defer / reject, and what the operator must
   decide.
5. Not determined: everything this work could not settle, and what would settle it.
6. Deviations from this scope, if any.

## 6. Not in this scope

- Building Derek, `RemoteRegistrar`, the wrapper or any CEL change.
- Editing STD-011 or the design memo.
- The proxy board, the relay and the rewriter (`docs/relay-isolation/`).
- Outrigger's filter, except as the existing example of how a filter is carried and evaluated.
- DirtyChai. Nothing in this scope touches that repository.
