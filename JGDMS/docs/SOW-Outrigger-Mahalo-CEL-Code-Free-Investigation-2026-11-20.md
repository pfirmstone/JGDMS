# SOW: can client-side and server-side CEL remove code download from Outrigger and Mahalo?

- **Status:** scope for an investigation and design proposal. Nothing in it is a decision.
- **Date:** 2026-11-20. **Written by:** Claude Fable 5.2, proxy-board review session, at the operator's request.
- **Reviewer of the result:** the same session (the operator passes the result back).
- **Output:** one file, `docs/RESULTS-outrigger-mahalo-cel-investigation.md`. No other file is created or changed.
- **Companion:** `docs/SOW-Lookup-CEL-Code-Free-Investigation-2026-11-20.md` and its result
  `docs/RESULTS-lookup-cel-investigation.md`. This scope reuses that result; it does not repeat it.

## 1. The question

The operator asked for the same work for Outrigger (the tuple space) and Mahalo (the transaction manager)
that was done for the lookup service: can CEL on the server and on the client remove the need for a client
to download code, with a new service API or a redesign of the existing interfaces where that helps, and
does DETERMINISTIC CEL need additions that are safe to make.

Three kinds of downloaded code must be kept apart in every answer:

| Kind | Example | Already ruled |
|---|---|---|
| P. Proxy code | `SpaceProxy2`, `TxnMgrProxy`, `ProxyVerifier`, the stub's interfaces in the `-dl` jars | Design memo section 12 rulings 2, 3, 6 and section 13 answer 1: a new record-style `Remote` interface plus a resident wrapper. CEL is not what removes this. |
| E. Entry classes | An application's `Entry` subclass, loaded by `EntryRep.entry()` | Section 13 answer 4: existing clients rely on it; it stays, digest-gated. A class-free entry read is for code-free hosts only. |
| L. Application logic carried by entries | An entry class with methods a taker calls (the master and worker pattern) | Not ruled. Not yet measured. |

Answers wanted:

1. For Outrigger: which client operations a code-free host can do with CEL as it stands (the filter that is
   already built), which need a transform or a new operation, which need a language addition, and which
   cannot be done with CEL at all. Separately for kinds E and L.
2. For Mahalo: whether CEL has any role at all. The starting hypothesis is that it has none, because the
   proxies are pure forwarders. The work is to confirm or refute that, not to find a use.
3. For each candidate language addition: is it safe under the STD-011 theorem, at what cost, and is there a
   way to meet the need without it. Start from the lookup result's section D and F; re-open an item only
   where an Outrigger use is found.

## 2. Read first

| File | Why |
|---|---|
| `docs/RESULTS-lookup-cel-investigation.md` sections 2, B, D, F, 4 | What CEL is as built, the verdict on each candidate addition (R1 to R9), the needle limit (F9), the cost-profile finding (F2), -0.0 (F4). Treat as RELAYED until re-opened. |
| `docs/INVESTIGATION-code-free-outrigger-2026-10-31.md`, `docs/INVESTIGATION-code-free-mahalo-2026-10-31.md` | The two service reports (Sonnet, static reading). |
| `docs/REVIEW-code-free-service-reports-opus-2026-10-31.md`, `docs/REVIEW-code-free-service-reports-fable-2026-10-31.md` | Corrections to those reports: Opus F2 (Mahalo equality across restart), F3 (`PREFERRED.LIST` shadows a resident copy), F10 (priming is marginal); Fable F0, F1. |
| `docs/DESIGN-class-free-lookup-and-registrar-split-2026-10-31.md` sections 11, 12, 13, 14 | The rulings that already fix the shape for the other services. Check each cited ruling against the claim register's current status before relying on it. |
| `docs/DESIGN-Outrigger-CEL-Filter-B1.md`, `docs/DESIGN-Outrigger-CEL-Filter-B3.md` | The filter that exists: envelope, admission, applicability, entitlement gate, per-evaluation ceiling. |
| `docs/DESIGN-SpaceChannelAuthorization.md` | Principal-level access control for the space (provisional). |
| `docs/JGDMS-STD-006-Appendix-EntryRep-v2-DRAFT.md` | The entry body and its schema chain. |
| `docs/JGDMS-STD-011-CEL-Filter-Expression-Format-v0.1-DRAFT.md` | The language. The file name says v0.1; the text on trunk is v0.2 with bindings. Cite by section number on trunk. |
| `docs/SOW-Smart-Proxy-Isolation-Architecture-Overview.md` section 1a | What CEL must not become: no state, no I/O, no iteration. |
| `services/mahalo/mahalo-service/GranolaAPIProposals.md` | Proposed Mahalo API changes (optimisations 4 and 5). |
| `services/outrigger`, `services/mahalo`, `jgdms-cel`, `jgdms-cel-authoring`, `jgdms-lib-dl/src/main/java/net/jini/space` | The code. |

## 3. Rules for this work

1. **Read-only.** No production code, test, standard or design file is changed. Existing tests may be run.
   A throwaway probe may be written outside the repository; if one is used, the results file gives its full
   text and its output.
2. **No commit and no push** unless the operator says so.
3. **Do not write to the claim register.** Report findings; the reviewing session registers them.
4. **Label every factual statement:** VERIFIED (you opened the line or ran the command in this session, with
   file, line and anchor text), RELAYED (from a sub-agent or an earlier report, not re-opened), INFERRED, or
   NOT CHECKED. The 2026-10-31 reports and the lookup result are leads, not facts. Their line numbers are
   three weeks old; find the code by content.
5. **Record the commit** (`git rev-parse HEAD`, branch name) at the top of the results file. The scope was
   written against trunk `83c4d4c7a`.
6. **No addition without a use.** A language feature is assessed only if section A or C shows an Outrigger
   or Mahalo need for it. The lookup result's verdicts stand unless a new use is shown.
7. **The extension rule of STD-011 section 1.3 stands:** a construct not covered by the section 3.2 argument
   is not proposed without the text of the extension to that argument.
8. **A negative result is a result.** If CEL has no role in a service or an operation, say so and stop. Do
   not design a use to fill the section.
9. **Stop and report** instead of guessing if a file in section 2 is missing, if the code and a design memo
   disagree on a point this work depends on, or if a section cannot be finished.
10. Plain language. Short sentences. Tables where items are compared.

## 4. Sections of work

Agent types are recommendations. "Sonnet", "Opus" and "Fable" name the model; "Explore" and
"general-purpose" name the Claude Code sub-agent type. The session that runs this scope coordinates and
writes the results file; it should be Opus or Fable.

### A. Survey: how spaces and transactions are used today
**Agent: Sonnet, Explore (very thorough). Split into A1 to A4 and run in parallel.**

Search `JGDMS/JGDMS` (including `examples`) and the repository-level `qa` directory.

- **A1. Templates and local filtering.** Every call of `read`, `readIfExists`, `take`, `takeIfExists`,
  `notify`, `contents`, `registerForAvailabilityEvent` and the `FilteredTupleSpace` methods. For each: file,
  line, what the template fixes (which fields are non-null), and whether the caller tests the returned
  entry afterwards and reads or takes again (client-side filtering after the match). Classify each
  after-test by the operation it needs: equality, numeric range, substring, prefix or suffix,
  case-insensitive compare, pattern, comparison between two fields, arithmetic, collection access, a
  method call on the entry, other. Give counts.
- **A2. Entry classes.** Every `Entry` class used with a space. For each: field names and types, scalar or
  not; whether the class declares any method beyond constructors and accessors; whether any caller invokes
  such a method on an entry it read or took (kind L). Give three counts: entry classes, entry classes with
  a non-scalar field, entry classes whose methods are called by a taker.
- **A3. Read-compute-write sequences.** Every place a client takes or reads an entry, computes new field
  values and writes it back, with or without a transaction. For each: what is computed (increment, append,
  state change, other) and whether a transaction guards it.
- **A4. Class-load and unmarshal sites, both services.** For Outrigger start from the report's Q6
  (`EntryRep.entry()`, priming, `IteratorProxy`, `MatchSetProxy`, `OutriggerAvailabilityEvent.getEntry`,
  `ProxyVerifier`) and the `-dl` `PREFERRED.LIST`. For Mahalo start from `join` (the inbound participant
  stub and `participantPreparer`), `StorableObject` (the legacy JOSS reader), `ParticipantHandle` recovery
  and `JoinStateManager`. For each site: what is loaded, from where, on which side, and which of kinds
  P, E, L it is. Also every use of the transaction API in the tree (`TransactionFactory.create`, `join`,
  `commit`, `abort`, `getState`) and every `TransactionParticipant` implementation, with its module.

### B. The Outrigger filter as built
**Agent: Sonnet, Explore (medium).**

1. Which of the eight filtered operations evaluate a predicate on trunk today, and where
   (`FilterAdmission`, `FilterSet`, `FilterEval`, `EntryProjection`, `FilteredWatcher`). Is
   `EVALUATION_NOT_WIRED` still reachable from any operation?
2. The ceilings in force for Outrigger: the envelope and CEL wire limits, `MAX_TEMPLATES`,
   `MAX_PROJECTION_DECODE_BYTES`, and the STD-011 section 10.3 values the verifier applies. Which value of
   `maxScalarBytes` (S) does Outrigger's admission use, and is it enforced on the stored entry at write
   time, at evaluation, or both?
3. Whether admission accepts a transform. (B1 section 4 step 4 says a `Transform` is refused.)
4. What `CandidateProjection` exposes to an expression (`namespaceChain`, `declaresField`, `fieldValue`,
   `isUndecodable`) and whether a client could build the same projection from an `EntryRep` it holds,
   without the entry class.
5. What the authoring path needs: `EntryFilter.compile(rule, type)` takes the entry class. Is there a way
   to author a filter from a schema chain alone?
6. Run the existing filter tests in `outrigger-service`, `outrigger-dl` and `outrigger-cel-authoring` once
   and report the counts. Do not change them.

### C. Outrigger expressibility
**Agent: Opus, general-purpose. Needs A and B.**

1. For each after-test class found in A1: is it a predicate in CEL as built? Give the expression or the
   reason it is not. State how many of the surveyed uses the built filter already covers.
2. **The needle limit.** The lookup result (F9) says the total needle octets of `contains` in one predicate
   is at most 15 at S = 65536. Check that statement against `CostModel`, then say what it means for space
   queries, where substring search over application text is more likely than in lookup. If the limit is
   real, give the options: a lower S for the searched field, a declared per-field size bound in the schema,
   or leave it.
3. **Class-free matching.** Can a host that has no entry class express "entries of type T or a subclass,
   with field f equal to v" as a null template plus a predicate? What tests the type (`namespaceChain`, a
   marker, the schema digest)? What does B3's amended applicability rule do to a subclass candidate?
4. **Class-free reading.** What a code-free host does with a returned `EntryRep`: a generic record view
   decoded from the body's schema chain, with no class. State exactly which field types that view can
   decode and which it cannot (a field whose value is an arbitrary object). Note that this needs no CEL.
5. **Class-free writing.** What a code-free host needs to write an entry: field values plus a schema chain.
   Where the schema chain comes from when the class is absent, and how the server checks it.
6. **Kind L.** For each entry method that a taker calls (A2): is it a bounded, stateless computation over
   the entry's own scalar fields (a candidate for a transform carried as data), or not? Give the count on
   each side. Be strict: a loop, a call to another object, I/O or mutable state means not.
7. **Read-compute-write (A3).** For each computation: could a transform compute the new field values from
   the old ones? One transform returns one value, so say how many transforms an update needs.
8. What a predicate or transform cannot do for a space: ordering, counting, aggregation, joins between two
   entries, blocking, leases, transactions. For each, which resident or server code does it.

### D. Language additions, for Outrigger only
**Agent: Fable, general-purpose. Needs C. Short if C finds no new use.**

Take the lookup result's section D and F as the starting position (glob safe and deferred; ASCII
case-insensitive compare safe and deferred; concatenation and regex rejected for lookup). For each item
where C shows an Outrigger use that lookup did not have, re-assess under STD-011 section 3.2 with the cost
row, the result-size bound and a cross-language note (Java, Rust, Haskell), exactly as the lookup scope's
section D required. In particular:

1. **Concatenation.** Lookup rejected it for want of a use. Does an update transform (C.7) create one? If
   so, does the "list of parts joined by resident code" answer still work when the result is stored by the
   server, not joined by a client? State the size bound on a stored result.
2. **Comparison between two fields of one entry** and **arithmetic in a transform**: are these already
   covered, and are overflow and NaN outcomes defined for a value that will be stored?
3. Anything else C found. If nothing, say "no addition is needed for Outrigger" and stop.

### E. Outrigger API proposal
**Agent: Fable, general-purpose. Needs A to D. Options with reasons, not decisions.**

The proxy shape is already ruled (a record-style `Remote` interface and a resident wrapper presenting
`JavaSpace05`, `TupleSpace` and `FilteredTupleSpace`). This section is about what the record interface
should carry so that a code-free host can use a space.

1. **Predicate.** Keep `byte[] filterEnvelope` as an operation parameter (B1 section 1), or fold template
   and predicate into one query record? How the wrapper translates a legacy `Entry` template. Whether an
   unfiltered call should remain on the new interface at all.
2. **Projection (server-side transform or named-field selection).** A read that returns chosen field
   values in place of the whole `EntryRep`. Compare named-field selection (the lookup result's R12) with a
   transform. What it saves: bytes, and disclosure of fields the caller did not ask for. Whether a `take`
   may project (the entry is removed; the unreturned fields are lost).
3. **Events.** An event that carries projected values or a record view in place of an `EntryRep` whose
   `getEntry()` loads a class. Apply the lookup result's F6 (the server's outbound listener call as a
   confused deputy) and F7 (who can read a standing predicate) to `notify` and availability events.
4. **Server-side update.** A new operation: match by predicate, compute new field values by transform,
   replace the entry, atomically. Assess it honestly: what it replaces (a transaction around take and
   write, and the client's entry class), what it costs (evaluation under the entry lock; see B3 section 4),
   its lease and event semantics, its interaction with a transaction that holds the entry, and whether it
   breaks the space's contract that a stored entry is immutable. Recommend add, defer or reject.
5. **Write admission.** A predicate the space's operator configures that every written entry must satisfy
   (B1 calls the write path B4). Is this in scope for code-free, or only policy? If the wrapper evaluates
   the same predicate before sending, where does it get it, and how is it bound to the authenticated
   service (the lookup result's F5)?
6. **Client-side evaluation.** Does a space client have any use for running an expression locally: a
   re-check of the server's verdict over the returned record (the lookup result's R27), or filtering a
   `MatchSet` batch? State the use or say there is none.
7. **Kind L as data.** If C.6 found candidates: an entry field that holds a verified transform, evaluated
   by the taker's resident evaluator over the entry's own fields. Who authored it (another client of the
   space, so it is untrusted input), what it may read (only the entry it travels in), the cost bound, and
   what the taker learns or leaks by running it. If C.6 found none, say so and omit the design.
8. **Authorisation and timing.** Which entries a caller's predicate is evaluated over. Tie to B3's
   entitlement gate and to `DESIGN-SpaceChannelAuthorization.md`. A shared space evaluates one client's
   predicate over another client's entries by design; say what STD-011's open timing item (T7) means here
   and whether per-principal budgets are needed.
9. **Entry classes that stay (kind E).** For hosts that are not code-free, entry classes are still
   downloaded. State what the new interface must do so that a code-free host never reaches
   `EntryRep.entry()`'s class load (a mode, a different return type, or a wrapper with no such path), and
   what `PREFERRED.LIST` and the codebase annotation must become (Opus F3).
10. **Preconditions:** `maxScalarBytes` enforcement on both sides, the schema source for class-free
    authoring and writing, the module that would hold a client-side evaluator, and anything in the lookup
    result's F list that applies unchanged.

Give a method sketch for the record interface. Mark every name as illustrative.

### F. Mahalo
**Agent: Opus, general-purpose. Needs A4. Kept short on purpose.**

1. Confirm from the code on trunk: `TxnMgrProxy` and `TxnMgrAdminProxy` forward and do nothing else; the
   default exporter is now `AtomicDerILFactory`; what is registered with lookup; what `getAdmin` returns.
2. List every piece of code a Mahalo client or participant loads today, by kind. For each, say what
   removes it: a resident interface, a record interface plus wrapper, a DER lease record, or nothing yet.
   `TransactionParticipant` is a platform interface, so say whether a participant can be a DER dynamic
   proxy with no downloaded class, and what `join` must check (the format, the handler class, the
   preparer).
3. **Is there any role for CEL?** Test these, and reject each unless a real use is shown:
   (a) a predicate that authorises `commit`, `abort` or `join` (compare with the permission class the
   exporter can already carry; see design memo section 13 ruling 16 and the finding under it);
   (b) a commit condition evaluated by the manager over values the participants report;
   (c) anything in `GranolaAPIProposals.md` optimisations 4 and 5 that needs an expression.
   For each: what it would replace, and whether it removes any downloaded code. A feature that removes no
   code is out of scope; list it in one line and move on.
4. The record interface for Mahalo, as far as rulings already fix it: stable uuid identity in the record so
   that participant matching survives a restart (Opus F2), the lease as a record, the admin surface as a
   separately exported object or not (Fable F1). Options with reasons.
5. State the verdict in one sentence: CEL has a role in Mahalo, or it has none.

### G. Review of D, E and F
**Agent: for D and E (produced by Fable): Opus, general-purpose, fresh context. For F (produced by Opus):
Fable, general-purpose, fresh context.** The operator does not want same-model review.

Using `docs/JGDMS-Board-Reviewer-Guidance.md`: attack each "add" recommendation (an expression that breaks
the stated bound; a cross-language difference), each new operation in E (who authored the expression, who
evaluates it, under which Subject, over whose entries, under which lock, what binds it to an authenticated
party), and F's negative verdict (find the use it missed, or confirm there is none). For E.4 and E.7
specifically: try to show the operation is a confused deputy or a lock-hold denial of service. Report
findings with a status: CONFIRMED, PLAUSIBLE or UNVERIFIED. The coordinator records them and the authors'
replies; it does not edit findings away.

## 5. The results file

`docs/RESULTS-outrigger-mahalo-cel-investigation.md`, CRLF line endings like the other files in `docs`, in
this order:

1. Commit, branch, date, the model and agent id of each section's author (say which were checked and which
   are self-reported).
2. A one-page answer to the three questions of section 1, with Outrigger and Mahalo separate, and kinds P,
   E and L separate.
3. Sections A to G, each with its tables.
4. A list of every recommendation, one line each, with: add / defer / reject, and what the operator must
   decide.
5. Not determined: everything this work could not settle, and what would settle it.
6. Deviations from this scope, if any.

## 6. Not in this scope

- Building any interface, wrapper, operation or CEL change.
- Editing STD-011, STD-006 or any design memo.
- Mercury, Norm and Fiddler. They get the record interface and wrapper by the same rulings; nobody has
  asked whether CEL has a role in them.
- The lookup service, except as the source of the reused findings.
- The proxy board, the relay and the rewriter (`docs/relay-isolation/`).
- DirtyChai. Nothing in this scope touches that repository.
