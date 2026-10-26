# ADVICE — Where the discovery Subject should come from

**Author:** sub-agent `SubjSource` (claude-opus-5), 2026-08-29.
**Scope:** Q1–Q6 posed by the operator. Branch `fix/reggie-discovery-backend-proxy`, uncommitted tree.
**Evidence tags:** `[M]` measured (file:line verified in this session) · `[H]` hypothesis · `[A]` assumption.

> A ruling settles a decision, never a fact. Everything tagged `[M]` is a fact I read at a cited
> location. Everything tagged `[H]`/`[A]` is for the operator to rule on. Where I could not establish
> something, I say so rather than guessing — see §9.

---

## 0. Executive summary

| Q | Answer | Confidence |
|---|--------|-----------|
| Q1 | **Both, layered — but not symmetric.** Primary source = the owning component's login Subject, established **at the call site**. `DiscoveryCredentialProvider` stays as an *override* SPI for the client side only. Do **not** invent a server-side provider config entry. | `[H]` design; `[M]` facts |
| Q2 | Seam belongs in `RegistrarImpl$Announce.announce(String[])` around the encode block (`RegistrarImpl.java:3248–3268`), **not** around `run()`. The shape does **not** transfer to fiddler/mercury/norm/mahalo/outrigger — none of them announce. But **four services share a different, related defect** — see §2.4. | `[M]` |
| Q3 | **CONFIRMED.** Return `null`, document why. This also refutes candidate 1 in `DESIGN-discovery-credential-resolution.md` §11.6. | `[M]` |
| Q4 | **NO — refuted.** `callAs(Subject, Callable)` has no read-only check; the check at `:599` is exclusive to the `callAs(Callable, UserSubject...)` varargs form. A `LoginContext` subject is a plain mutable `Subject` and is **already** passed to this exact method on the live reggie path. **Your seam works.** | `[M]` |
| Q5 | **Risk is real but currently dormant.** Four silent-null paths. The `SecurityManager` branch cannot fire today because **no qa configSet installs a SecurityManager** — but it will fire the moment polpAudit is enabled, and `defaultsecurereggie.policy` is an emptied 3-line stub. | `[M]` |
| Q6 | **Do not do it as a fix for this bug.** And the liveness assumption is **REFUTED**: JERI's accept loop already runs on *virtual* (therefore daemon) threads, so JERI supplies **nothing** that would keep a service JVM alive. | `[M]` on the refutation |

### The three things that most change the picture

1. `[M]` **The seam you wrote does not fix the qa run.** No qa config sets
   `discoveryCredentialProvider`, so `getDiscoverySubject()` returns `null`, the passthrough branch
   runs, and the X.500 provider still sees no Subject. A green compile is not a fix. (§1.1)
2. `[M]` **This is a four-service defect, not a reggie defect.** reggie, fiddler, mercury and norm all
   contain near-identical comments asserting that "a Thread captures the active scoped subject at
   construction", and all four wrap thread construction in `Subject.callAs` on that basis. DirtyChai
   `cc09a47009c` made that premise false for platform threads. All four are now silently identity-less.
   (§2.4)
3. `[M]` **JERI's accept loop is already virtual.** `TcpServerEndpoint.LH.startAccepting()` submits to
   `systemThreadPool`, which is `org.apache.river.thread.ThreadPool` backed by
   `Executors.newVirtualThreadPerTaskExecutor()`. Virtual threads are daemon. So the *only* non-daemon
   threads in a reggie JVM are the six the constructor creates — the very ones Q6 proposes to convert.
   (§6.2)

---

## 1. Q1 — Where should the discovery Subject come from?

### 1.1 The facts

`[M]` `DiscoveryCredentialProvider` is a one-method SPI returning a `Subject`
(`JGDMS/jgdms-platform/src/main/java/net/jini/discovery/DiscoveryCredentialProvider.java:33–39`).
Its javadoc says "or `null` to use the current call context unchanged".

`[M]` It is read from configuration in exactly one place: the `Initializer` of
`AbstractLookupDiscovery` (`jgdms-platform/.../net/jini/discovery/AbstractLookupDiscovery.java:1488–1492`),
component `net.jini.discovery.LookupDiscovery`, defaulting to `NoOpDiscoveryCredentialProvider.INSTANCE`.

`[M]` `NoOpDiscoveryCredentialProvider.getSubject()` returns `null`
(`.../NoOpDiscoveryCredentialProvider.java:37–40`).

`[M]` **No qa config sets `discoveryCredentialProvider`** — grep over `qa/harness/configs/**` returns
nothing. The only identity configured anywhere is `loginContext`:
`qa/harness/configs/jsse/reggie/reggie.config:70` (`new LoginContext("org.apache.river.Reggie")`) and
`qa/harness/configs/jsse/test.config:82` (`new LoginContext("org.apache.river.Test")`).

**Therefore `[M]`: in the failing run the provider path contributes nothing.** `getDiscoverySubject()`
(`AbstractLookupDiscovery.java:3184–3196`) returns `null`, `runWithDiscoverySubject` takes its
null-passthrough branch (`:3217–3219`), and `X500Provider.getPrivateCredentials()` still finds no
Subject. **The seam alone does not fix the tester side.** This is the single most consequential fact in
this document — please do not read a clean compile as a fix.

`[M]` Reggie, by contrast, already holds its login Subject and already calls `Subject.callAs` with it,
twice:
- `RegistrarImpl.java:526` — `Subject.callAs(loginContext.getSubject(), init::run)` around `Initializer.run()`.
- `RegistrarImpl.java:658–663` — captures `init.loginContext.getSubject()` and wraps **thread construction**.

`[M]` The comment at `RegistrarImpl.java:619–625` states the intent of `:663` verbatim: *"a Thread
captures the active scoped subject at construction. This block must therefore run inside
Subject.callAs, otherwise the threads inherit the ambient activation-group identity…"*. That premise
was true when written and is **now false for platform threads** — DirtyChai `cc09a47009c` sets
`this.scopedSubject = new Subject[0]` for platform threads (`java/lang/Thread.java:847`). So
`RegistrarImpl.java:658–663` is dead weight guarded by a misleading comment: it costs a `callAs` and
buys nothing for the six platform threads it constructs.

### 1.2 The asymmetry, named precisely

There is no server-side counterpart to `DiscoveryCredentialProvider` because there is no server-side
counterpart to `LookupDiscovery`.

`[M]` The announce side is `RegistrarImpl$Announce` (`RegistrarImpl.java:3167`), a private static
nested class reached only from reggie's own constructor. `[M]` `encodeMulticastAnnouncement` appears
exactly once across all of `services/` — `RegistrarImpl.java:3248`. **Reggie is the only announcer in
the codebase.**

So "configure a `DiscoveryCredentialProvider` for the announce side" would mean inventing a new config
entry, under a new component, read by `RegistrarImpl`, whose only job is to name a Subject that reggie
already holds in a field two scopes away (`RegistrarImpl.java:714`, `loginContext`).

### 1.3 Recommendation

> **Primary source = the owning component's login Subject, established at the call site.
> `DiscoveryCredentialProvider` = a client-side override, retained but demoted. No server-side SPI.**

1. **Announce side (reggie).** Take the Subject from `loginContext.getSubject()` and establish it
   around the encode (§2). Do **not** add a server-side provider config entry.

   *Reason:* the login Subject **is** the service's discovery identity by construction. Reggie's
   announcement must be signed as `CN=Reggie` — the same principal its JERI endpoint presents and that
   `ServerMinPrincipal` requires of it. A config entry permitting a *different* announce identity
   creates a split-identity failure mode with no use case I can find, and a new way for the two to
   silently disagree. `[H]`

   *Corroborating design signal `[M]`:* the codebase already states this convention elsewhere.
   `org.apache.river.thread.ThreadPool` deliberately builds its executor inside `Subject.callAs(null, …)`
   with the comment *"Explicitly clear any ambient subject… This neutralises any subject injected via
   DirtyChai's ACC snapshot mechanism or inherited from an enclosing Subject.callAs scope"*
   (`jgdms-collections/src/main/java/org/apache/river/thread/ThreadPool.java:105–117`), and
   `GetThreadPoolAction`'s javadoc says the system pool exists so "infrastructure code" gets "a clean
   system context". JGDMS's own convention is therefore: **infrastructure threads carry no caller
   identity; identity is established explicitly at the operation.** The call-site seam is the option
   consistent with that; a captured-at-construction Subject is the option that fights it.

2. **Client side (`AbstractLookupDiscovery`).** Keep the SPI, but make the resolution order:
   **configured provider Subject → else a Subject captured at `LookupDiscovery` construction → else null.**

   *Reason:* `LookupDiscovery` is a library used by many callers and owns no login of its own; its
   natural identity is the caller's. But `[M]` all five of its worker threads are platform threads
   constructed with `new Thread(this, …)` and `setDaemon(false)` — `Notifier` (`:313–319`),
   `AnnouncementListener` (`:450–464`), `ResponseListener` (`:665–673`), `Requestor` (`:747–762`),
   `AnnouncementTimerThread` (`:874–882`) — so "the caller's identity" must be captured at construction
   into a field. On the worker thread it is now guaranteed empty. `[M]` `AbstractLookupDiscovery` has a
   `discoveryCredentialProvider` field (`:289`) but **no captured-Subject field** — this is the missing
   piece.

   *In the qa run specifically:* the tester's `LookupDiscovery` is constructed by test code running
   under `Subject.callAs(loginContext.getSubject(), …)` for `org.apache.river.Test`
   (`qa/harness/configs/jsse/test.config:82`). `[H]` So a construction-time `Subject.current()` capture
   would pick up `CN=Tester` — but I did **not** verify that the harness actually constructs the
   `LookupDiscovery` inside that `callAs` scope, and that is exactly the kind of assumption that
   produced this bug. **Verify before relying on it.**

3. **Do not promote one abstraction across both sides.** The two differ in *lifecycle*, not merely
   location — your point 9. The server side has one long-lived identity fixed at login; the client side
   has a per-instance identity that legitimately varies. Forcing one SPI onto both makes the
   server-side implementation a constant function, which is the smell that the abstraction is wrong.

### 1.4 What this costs

- **A retained Subject reference in library code.** Capturing `Subject.current()` at
  `LookupDiscovery` construction and holding it in a field is precisely the "long-lived platform thread
  silently retains a caller's identity" pattern that DirtyChai `cc09a47009c` was written to prevent,
  and it cuts against the `ThreadPool` convention quoted above. `[H]` I judge it acceptable **only**
  because the retention is scoped to an instance whose lifetime the caller controls and terminates
  (`terminate()`), and because the identity is used for exactly one purpose — signing discovery packets
  — not for general authorization. **That judgement is the operator's, not mine.**

  *Fallback if rejected:* make `discoveryCredentialProvider` **mandatory** whenever a signing discovery
  format is configured, and fail loudly at `LookupDiscovery` construction if a signing format is
  selected and no provider is supplied. More configuration burden, but it can never fail silently —
  which is the property this whole investigation has been paying for the absence of.

- **Two seams to maintain**, and two places to get the DirtyChai propagation rule right.

- **The dead `callAs` blocks and their false comments must be removed or corrected in the same
  change** — in reggie *and* the three other services (§2.4). Leaving a comment that asserts the
  opposite of the truth, immediately beside a new seam that exists because it is false, is how this
  recurs. `[M]` on the comments being false.

- **`DiscoveryCredentialProvider`'s javadoc becomes wrong.** "or `null` to use the current call context
  unchanged" describes a call context that on a discovery worker thread is now *always* empty. Under
  this recommendation `null` means "fall back to the captured Subject", which is different. `[M]`

---

## 2. Q2 — The server-side seam for reggie's announce loop

### 2.1 Where exactly

`[M]` In `RegistrarImpl$Announce.announce(String[] groups)`:
- `:3248` `EncodeIterator ei = disco.encodeMulticastAnnouncement(…)`
- `:3257–3268` `while (ei.hasNext()) { … ei.next() … }` — **this** is where signing happens and where
  `X500Provider.getPrivateCredentials()` is reached. `encodeMulticastAnnouncement` returns a lazy
  iterator; the work is in `next()`. Both must be inside the seam.

`[M]` The whole encode block is cached behind
`if (dataPackets == null || !lastLocator.equals(reggie.myLocator) || !Arrays.equals(lastGroups, groups))`
(`:3234`). Two consequences:
- Subject cost is negligible — once per locator/group change, not once per announcement interval. Good.
- `[H]` **Stale-credential hazard:** if the signing credential rotates (SPIFFE SVID, ~hourly) or the
  X.509 cert expires, `dataPackets` is never re-encoded and reggie multicasts a packet signed with a
  dead key indefinitely, silently. Out of scope for your question, but it directly violates the
  "must never be captured for later use" rule in `Subject.java:44–54` — at the *packet* level rather
  than the Subject-reference level. Flagged as a separate defect (§8.5).

`[M]` `run()` (`:3205–3227`) is the perpetual loop; `announce()` is also invoked from `run()`'s
`finally` (`:3221`, the NO_GROUPS shutdown announce). **Wrapping `announce()` covers both call sites.**
Wrapping `run()` would hold a Subject binding across an unbounded
`announceCondition.await(reggie.multicastAnnouncementInterval, …)` — wasteful and semantically wrong:
identity should scope the operation, not the sleep.

### 2.2 Minimal patch sketch — NOT APPLIED

```java
// RegistrarImpl$Announce (:3167).  Resolve the subject lazily, NOT in the ctor -- see 2.3(1):
//     private Subject announceSubject() {
//         LoginContext lc = reggie.loginContext;          // assigned at :714
//         return (lc != null) ? lc.getSubject() : null;   // re-read: tolerates credential refresh
//     }

// In announce(String[] groups), wrap :3248-3268:
	    final Discovery fDisco   = disco;
	    final List      fPackets = packets;
	    final MulticastAnnouncement ann = new MulticastAnnouncement(
	        reggie.announcementSeqNo.getAndIncrement(),
	        myLocator.getHost(), myLocator.getPort(), groups, reggie.myServiceID);
	    Callable<Void> encode = () -> {
	        EncodeIterator ei = fDisco.encodeMulticastAnnouncement(
	            ann,
	            reggie.multicastAnnouncementConstraints.getMulticastMaxPacketSize(DEFAULT_MAX_PACKET_SIZE),
	            reggie.multicastAnnouncementConstraints.getUnfulfilledConstraints());
	        while (ei.hasNext()) {
	            try { fPackets.addAll(Arrays.asList(ei.next())); }
	            catch (Exception e) { DISCOVERY_LOGGER.log(
	                (e instanceof UnsupportedConstraintException) ? Levels.HANDLED : Level.INFO,
	                "exception encoding multicast announcement", e); }
	        }
	        return null;
	    };
	    Subject s = announceSubject();
	    if (s == null) { encode.call(); }
	    else { Subject.callAs(s, encode); }   // unwrap CompletionException exactly as in
	                                          // AbstractLookupDiscovery:3222-3231
```

The `CompletionException` unwrapping you wrote in `AbstractLookupDiscovery.runWithDiscoverySubject`
(`:3214–3231`) is the right shape and should be **lifted into a shared helper** rather than
copy-pasted into five services. `[H]` I did **not** verify a suitable shared module that both
`jgdms-platform` and `services/reggie` can depend on — treat "put it in a shared helper" as
unverified advice on placement, though the duplication argument stands regardless.

### 2.3 Why I did not apply it (two genuine ambiguities)

1. `[M]` **Ordering.** `Announce` is constructed at `RegistrarImpl.java:639`, inside
   `threadAction.run()`. `this.loginContext = init.loginContext` is not assigned until `:714`. An
   `Announce` constructor reading `reggie.loginContext` therefore gets `null`. Options: pass
   `init.loginContext` into the constructor (whose only argument today is `RegistrarImpl reggie`,
   `:3185`), or resolve lazily at first `announce()` — by which time `:714` has run. The sketch above
   uses lazy resolution because it also tolerates credential refresh, but **pin-at-construction vs
   re-read-per-use is a decision about rotation semantics, not a fact.** Yours to rule.

2. `[H]` **`Subject.callAs` vs `Subject.doAs`.** DirtyChai's own javadoc (`Subject.java:71–77`) says:
   use `callAs` for *user* identity; use `doAs(Subject, PrivilegedAction)` for *workload* identity,
   because `doAs` "takes an ACC snapshot at call time so that the subject's principals are injected
   into all subsequent `AccessController.getContext()` calls … making them visible to `Policy`
   evaluation via ACC stack intersection." A service's own login identity is arguably workload
   identity.

   `[M]` For *finding the credentials*, `callAs` is sufficient:
   `X500Provider.getPrivateCredentials()` (`X500Provider.java:216–237`) reads the Subject via
   `getSubject(acc)` → `Subject.getSubject(acc)` (`X500Provider.java:209–211`) → on DirtyChai that
   delegates to `current()`, which reads the ScopedValue.

   `[M]` For *policy evaluation*, the question is moot **today**: no qa configSet installs a
   SecurityManager (§4.3), so no `AuthenticationPermission` check on the private key runs at all. But
   under polpAudit it becomes live, and whether principal-scoped grants for `CN=Reggie` resolve under
   `callAs` alone — without `doAs`'s ACC injection — **I could not establish.** Settle it before this
   lands, or the fix will pass today and regress the moment the SecurityManager comes back.

### 2.4 Does the same shape apply to fiddler / mercury / norm / mahalo / outrigger?

**The announce seam: no. A related and arguably worse defect: yes, in four services.** `[M]`

*The announce seam does not transfer.* `encodeMulticastAnnouncement` has exactly one call site in
`services/` (`RegistrarImpl.java:3248`). The others participate in discovery only as *clients*
(multicast requests via `LookupDiscovery`/`LookupDiscoveryManager` under `JoinManager`) and are fixed
by the `AbstractLookupDiscovery` seam — **provided** §1.3(2) supplies it a non-null Subject.

*But the construction-time-`callAs` antipattern is systemic.* `[M]` Four services carry near-identical
comments asserting the now-false premise, and wrap thread construction on the strength of it:

| Service | File:line | Comment asserts |
|---|---|---|
| reggie | `RegistrarImpl.java:619–625`, callAs at `:658–663` | "a Thread captures the active scoped subject at construction… otherwise the threads inherit the ambient activation-group identity" |
| fiddler | `FiddlerImpl.java:587–594`, callAs at `:636` | "These threads must therefore be created inside Subject.callAs, otherwise they inherit the ambient identity of whoever constructed this service" |
| mercury | `MailboxImpl.java:660–665`, callAs at `:693` | "These threads are therefore created inside Subject.callAs so they hold this service's own login identity" |
| norm | `NormServerBaseImpl.java:2025–2029`, callAs at `:2158` | "Run this inside Subject.callAs so the threads created here hold this service's login identity" |

All four are wrong on DirtyChai `cc09a47009c` for platform threads (`Thread.java:847`). All four
therefore now run their service threads with **no** identity, believing they have one. That is the
same root cause as your discovery bug, and it is *four times wider*.

`[M]` mahalo (`TxnManagerImpl.java:314`, `:466`), outrigger (`OutriggerServerImpl.java:604`) and group
(`SharedGroupImpl.java:278`, `:300–311`, `:351–353`) use `callAs` around **operations**, not around
thread construction. Those are correct and unaffected. Note `SharedGroupImpl.java:300` explicitly
documents an operation-scoped rationale ("so that `SslServerEndpoint.resolveSubjectIfNeeded()`…") —
that is the pattern the other four should adopt.

**Recommendation `[H]`:** treat this as one change with a single shape — *establish identity around
the operation that needs it, never around thread construction* — applied to all five sites (four
services plus `AbstractLookupDiscovery`), with the false comments deleted. Fixing only reggie's
announce leaves three services carrying a comment that will mislead the next reader into re-creating
the bug.

---

## 3. Q3 — Should `SpiffeDiscoveryCredentialProvider.getSubject()` return `null`?

**CONFIRMED.** `[M]` on every step.

- `SpiffeDiscoveryCredentialProvider.getSubject()` currently returns `Utilities.spiffeWorkerSubject()`
  (`jgdms-jeri/src/main/java/net/jini/jeri/ssl/SpiffeDiscoveryCredentialProvider.java:32–34`).
- `Subject.callAs(Subject, Callable)` throws `IllegalArgumentException` on a `WorkerSubject`
  (DirtyChai `Subject.java:556–559`): *"LocalWorkerSubject must not be passed to callAs() — local
  process identity is ambient"*. So the SPIFFE path **would** throw, as you found.
- `Subject.java:44–54`: `WorkerSubject` "cannot be passed to `callAs` or `doAs`, and must never be
  captured for later use — always obtain a fresh reference via `processWorker()`."
- `SpiffeSupport.processWorkerSubject()`
  (`jgdms-discovery-providers/.../org/apache/river/discovery/internal/SpiffeSupport.java:93–105`)
  resolves the worker subject **on demand at signing time** via the reflective probe at `:66–72`. It
  does **not** route through `DiscoveryCredentialProvider` and does not need to.

So the SPIFFE identity already has a correct ambient-resolution route that requires no binding.
Passing it through the binding seam is not merely awkward — it is **prohibited by the type's
contract**, and the prohibition is actively enforced.

**Do this:**

```java
    /**
     * Always {@code null}: SPIFFE workload identity is <em>ambient</em>, not
     * thread-bound.  It is stamped into ProtectionDomains at class-load time and
     * rotates with the SVID, so it must not be captured, and {@code Subject.callAs}
     * rejects a {@code WorkerSubject} outright.  The SPIFFE discovery formats
     * obtain it on demand at signing time via
     * {@code SpiffeSupport.processWorkerSubject()}.  Returning null leaves the
     * ambient identity untouched, which is exactly what that path needs.
     */
    @Override
    public Subject getSubject() {
        return null;
    }
```

`[M]` This satisfies the stock-OpenJDK constraint trivially: no `WorkerSubject` type reference appears
anywhere, and `jgdms-platform` never sees the type. (It never did — `WorkerSubject` is reachable only
reflectively today, via `SpiffeSupport.java:66–72` and `net.jini.jeri.ssl.Utilities`.)

**Two caveats.**

1. `[M]` **This does not mean "SPIFFE is handled."** Per `DESIGN-discovery-credential-resolution.md`
   §11.6, `SpiffeMulticastClient`/`SpiffeMulticastServer`, the four format wrappers, and the
   `META-INF/services/org.apache.river.discovery.DiscoveryFormatProvider` registrations are **not yet
   written**, and `SpiffeSupport` is package-private. So today the null is correct *and* SPIFFE
   discovery signing is unreachable. Correctness of the null is unaffected; completeness is a separate
   open item.
2. `[M]` **This refutes candidate 1 in `DESIGN-discovery-credential-resolution.md` §11.6**, which lists
   "Reuse `DiscoveryCredentialProvider`" as an option for the SPIFFE signing subject. It cannot be
   reused: the SPI's contract is "return a Subject to bind", and a `WorkerSubject` cannot be bound.
   **Candidate 3 (reflection to `processWorker()`) is the right answer, and `SpiffeSupport` already
   implements it.** §11.6 should be updated to record candidate 1 as refuted, with this reason.

**Alternative considered and rejected `[H]`:** delete `SpiffeDiscoveryCredentialProvider` entirely,
since a provider that always returns `null` is behaviourally identical to
`NoOpDiscoveryCredentialProvider`. I recommend **keeping** it, because its existence plus that javadoc
is the only durable record of *why* SPIFFE does not use this SPI — delete it and someone re-adds it
wired to `spiffeWorkerSubject()` and reproduces the `IllegalArgumentException`. But be aware you are
deliberately keeping a class indistinguishable from the default; if you keep it, the javadoc is the
entire justification and must not be trimmed.

---

## 4. Q4 — Does `callAs(Subject, Callable)` require a read-only Subject?

**NO. Refuted. Your seam works on the X.500 path.** `[M]` — highest-evidence answer here.

### 4.1 There are two overloads and only one has the check

`[M]` DirtyChai `src/java.base/share/classes/javax/security/auth/Subject.java`:

| Overload | Line | Read-only check? |
|---|---|---|
| `public static <T> T callAs(Subject subject, Callable<T> action)` | `:555–565` | **No.** Body: `requireNonNull(action)`; reject `WorkerSubject`; `SecurityManager` check for `AuthPermission("callAs")`; `callNoCheck(...)`. |
| `public static <T> T callAs(Callable<T> action, UserSubject... subject)` | `:592–606` | **Yes** — `:599` `if (!subject[i].isReadOnly()) throw new IllegalArgumentException("Subjects must be read only");` |

The `:599` check you saw is inside the **varargs `UserSubject`** form only.

`[M]` `callNoCheck` (`:625–633`) is `ScopedValue.where(SCOPED_SUBJECT, subject).call(action::call)` —
no read-only check, no mutation, no defensive copy.

`[M]` The single-Subject overload's javadoc mentions read-only only as a *performance* note: "If
`subject` is read-only, its identity is efficiently available to authorization checks without
synchronization overhead." Not a precondition.

### 4.2 Would a JAAS `LoginContext.getSubject()` satisfy it anyway? No — and it doesn't need to

`[M]` DirtyChai `javax/security/auth/login/LoginContext.java:581–582`:
```java
        if (subject == null) {
            subject = new Subject();
```
A plain, **mutable**, non-read-only `Subject`. `LoginContext` never calls `setReadOnly()` (no hits for
`setReadOnly` in that file). So a login subject would **fail** the varargs form's check — further
confirmation that the varargs form is not the one to use here.

### 4.3 Empirical confirmation, and the one residual

`[M]` `RegistrarImpl.java:526` and `:663` **already** call `Subject.callAs(loginContext.getSubject(), …)`
on the live reggie startup path, under the qa jsse config that sets
`loginContext = new LoginContext("org.apache.river.Reggie")`
(`qa/harness/configs/jsse/reggie/reggie.config:70`). Reggie starts. So the single-Subject `callAs`
demonstrably accepts a mutable `LoginContext` subject in exactly the configuration in question. That
is measured behaviour, not inference.

**Residual — and it is a permission, not read-only.** `[M]` The single-Subject `callAs` does perform
`sm.checkPermission(AuthPermission("callAs"))` when a SecurityManager is installed
(`Subject.java:560–562`). I checked whether one is:

- `[M]` **No qa configSet installs a SecurityManager.** `-Djava.security.manager=default` is
  **commented out** in all five: `qa/harness/configs/jsse/configSet.properties:13`,
  `https/:12`, `kerberos/:22`, `spiffe/:21`, `der/:21`. `qa/build.xml:404` has
  `-Djava.security.manager=polpAudit` commented out too.
- **Therefore the `AuthPermission("callAs")` check is a no-op in the failing run.** Your seam cannot
  fail on it today.

`[M]` **But it will fail later.** When polpAudit or `=default` is re-enabled:
- `qa/harness/policy/defaultsecurereggie.policy` is a **3-line emptied stub** with zero grants
  ("Emptied for clean cross-platform polpAudit regeneration"), and so is
  `defaultsecuresharedvm.policy`. Reggie has **no** `AuthPermission "callAs"` and **no**
  `AuthPermission "getSubject"`.
- `defaultsecuretest.policy` (3365 lines) has `AuthPermission "callAs"` **3 times** and
  `"getSubject"` 80 times. `defaultsecurephoenix.policy` (8333 lines) has `"callAs"` 93 times.
- So the tester/phoenix sides are broadly covered; **the service sides are not covered at all.**

**Action:** when the SecurityManager is re-enabled, `permission javax.security.auth.AuthPermission
"callAs";` must be granted to the code source containing `AbstractLookupDiscovery` (jgdms-platform)
and to reggie's own code source, or the new seam throws `AccessControlException`. This is a
*deferred* dependency, not a blocker today.

---

## 5. Q5 — Is the reflective probe a silent-failure risk?

**Yes — real, but currently dormant.** `[M]` on the mechanism and on dormancy.

`[M]` `SpiffeSupport.java:66–72`:
```java
    private static Method findProcessWorker() {
	try { return Subject.class.getMethod("processWorker"); }
	catch (Exception e) { return null; }
    }
```
`[M]` `:93–105`:
```java
    static Subject processWorkerSubject() {
	if (PROCESS_WORKER == null) return null;
	try { return (Subject) PROCESS_WORKER.invoke(new Subject()); }
	catch (Exception e) { return null; }
    }
```

**Four distinct routes to `null`, indistinguishable at the call site, none logged:**

1. `[M]` **Stock JDK** — `getMethod` throws `NoSuchMethodException`; `PROCESS_WORKER == null`. The
   *intended* case. Fine, but it should say so out loud.
2. `[M]` **`SecurityException` under a SecurityManager.** DirtyChai `Subject.java:298–304`:
   ```java
    public Subject processWorker() {
        java.lang.SecurityManager sm = System.getSecurityManager();
        if (sm != null) sm.checkPermission(AuthPermissionHolder.GET_SUBJECT_PERMISSION);
        return processWorkerNoCheck();
    }
   ```
   requires `AuthPermission("getSubject")`. The resulting `InvocationTargetException` wrapping the
   `SecurityException` is caught by `catch (Exception e)` and discarded.
   `[M]` **Dormant today** — no SecurityManager is installed (§4.3). `[M]` **Live under polpAudit**,
   and the SPIFFE service policies would need the grant; note `defaultsecurereggie.policy` currently
   has zero grants of any kind.
3. `[M]` **No SVID yet.** `processWorkerNoCheck()` (`:306–308`) is
   `SpiffeCredentialManager.getInstance().getSubject()`, documented at `Subject.java:288–291` to return
   `null` "if the SPIRE agent has not yet issued an SVID (fail-secure)". A legitimate transient that is
   **indistinguishable from permanent misconfiguration**.
4. `[H]` **`new Subject()` failing.** `Subject` is `sealed … permits WorkerSubject, UserSubject`
   (`Subject.java:100`). A sealed non-abstract class is still instantiable, and java.base itself does
   `new Subject()` at `LoginContext.java:582`, so this works today. But
   `PROCESS_WORKER.invoke(new Subject())` allocates a throwaway `Subject` purely to satisfy an
   instance-method receiver the implementation ignores (the `:96` comment says as much). If DirtyChai
   ever makes the constructor non-public or the class abstract, this silently becomes route 4.

`[H]` **Would this reproduce the bug you just spent a day on? Yes, exactly.** An identity resolver
returning "no credentials" with no diagnostic, manifesting downstream as
`DiscoveryProtocolException: no encoded requests` and 0 events. Same signature, different cause.

`[M]` `PROCESS_WORKER` is a `static final` resolved in a static initializer, so route 1 vs 2 is decided
**once per class load**, whereas routes 3/4 are per-call. Any diagnostic must therefore fire in both
places — a one-off log at class-init would miss the SVID transient entirely.

### How to close it `[H]`

1. **Log the distinction at a level that is on by default.** Route 1 → `CONFIG` ("this JDK has no
   `processWorker`; SPIFFE discovery identity unavailable — expected on a stock JDK"). Routes 2–4 →
   `WARNING` with the throwable. A `SecurityException` here is a deployment defect, not an environment
   fact, and must never be silent. `[M]` **There is direct precedent in this very file's neighbour:**
   `X500Provider.java:249–254` logs a signing-credential failure at `WARNING` rather than
   `Levels.HANDLED`, with the comment *"this is our OWN signing credential being unusable, which
   silences discovery entirely… It must be visible by default."* Same class of bug, same reasoning —
   apply it here.
2. **Split the API so the ambiguity cannot exist.** `hasWorkerIdentity()` (capability) vs
   `workerSubject()` (runtime result), or return a reason alongside. A single `null` conflating an
   environment capability with a runtime failure *is* the defect.
3. **Fail closed and loud at format-registration time.** If a SPIFFE discovery format is selected but
   `processWorkerSubject()` returns null while `PROCESS_WORKER != null`, that is unrecoverable
   misconfiguration — throw at construction rather than emitting nothing. **General principle: a
   signing format that cannot obtain a signing identity must fail closed and loud, never quiet.**
4. **Add the grants before re-enabling the SecurityManager** — `AuthPermission "getSubject"` for the
   SPIFFE path, `AuthPermission "callAs"` for the seams (§4.3). `[M]` Do **not** rely on polpAudit to
   discover them: a swallowed `SecurityException` never reaches polpAudit's recorder as a failure, so
   route 2 is invisible to the very tool you would use to find it. This is a genuine blind spot.

---

## 6. Q6 — Virtual threads for the discovery threads, X.500-only

`[H]` **Recommendation: no — not as a fix for this bug.** One of your premises is refuted outright.

### 6.1 It would not, by itself, fix the bug

`[M]` DirtyChai `Thread.java:895` — virtual threads capture the scoped Subject at construction, and
`Thread.runWith:1714` re-establishes it around the task. So a virtual discovery thread constructed
inside reggie's existing `Subject.callAs(loginSubject, …)` at `:663` **would** carry `CN=Reggie`.
That is the genuine attraction.

But `[M]` it works only if the thread is *constructed* inside the `callAs`. Reggie's are
(`:626–644`). `AbstractLookupDiscovery`'s are **not** — they are constructed with no `callAs` around
them and no login Subject available there. So the client side still needs §1.3(2), and converting
threads fixes at most the reggie half.

### 6.2 The liveness premise is REFUTED

`[M]` The threads in question are explicitly non-daemon:
- `AbstractLookupDiscovery`: `:319`, `:464`, `:673`, `:762`, `:882` — five `setDaemon(false)`.
- `RegistrarImpl.java:645` and `:651` — `newThread` / `newInterruptStatusThread`, both `setDaemon(false)`,
  covering all six service threads.

Virtual threads are always daemon, so all of these stop holding the JVM up.

**Your tentative answer — "JERI's listener threads already provide it" — is false.** `[M]`:
- `TcpServerEndpoint.LH.startAccepting()` (`jgdms-jeri/.../net/jini/jeri/tcp/TcpServerEndpoint.java:737`)
  submits the accept loop to `systemThreadPool`.
- `systemThreadPool` is obtained via `AccessController.doPrivileged(new GetThreadPoolAction(false))`
  (e.g. `net/jini/jeri/connection/ConnectionManager.java:143–145`; same pattern in
  `UdsServerEndpoint.java:124` and `SelectionManager.java:90`).
- `GetThreadPoolAction(false)` returns `org.apache.river.thread.ThreadPool`
  (`jgdms-collections/src/main/java/org/apache/river/thread/ThreadPool.java`), whose executor is
  `Executors.newVirtualThreadPerTaskExecutor()` (`:105–117`).
- `[M]` The only `setDaemon` calls anywhere in `jgdms-jeri/src/main/java` are
  `StreamConnectionIO.java:100` and `:101`, both `setDaemon(true)` (mux writer/reader).

**Conclusion `[M]`: JERI contributes no non-daemon thread.** The six threads reggie's constructor
creates are the *only* thing keeping a non-activatable reggie JVM alive. Converting them wholesale to
virtual would make the JVM exit as soon as `main` returns — a spectacular regression that would
present as "reggie starts and vanishes", easily misattributed to something else.

Mitigations if you proceed anyway `[H]`:
- Convert **selectively**, leaving at least one non-daemon platform thread. `Unicast` (`:637`, the
  unicast discovery listener) is the natural keeper — it must exist for the service to be reachable.
- Or add an explicit non-daemon keep-alive owned by the service lifecycle, which is cleaner than
  depending on an incidental thread's daemon flag. This is arguably worth doing regardless of Q6:
  relying on `setDaemon(false)` on a *service expiry* thread for JVM liveness is accidental design.

### 6.3 Other costs

- `[M]` `RegistrarImpl` uses `InterruptedStatusThread` (`:637`, `:638`) — a **subclass of `Thread`**.
  Virtual threads cannot be created by subclassing `Thread`; that class needs redesigning as a
  `Runnable` wrapper. A real refactor, not a flag flip.
- `[M]` `Announce.run()` holds `announceLock` (a `ReentrantLock`) across
  `announceCondition.await(interval, MILLISECONDS)` (`:3207–3213`). That *is* virtual-thread-friendly —
  `ReentrantLock`/`Condition.await` unmount correctly. No pinning issue there.
- `[H]` The listener threads block in `receive()` on `MulticastSocket`/`DatagramSocket`. Modern
  `DatagramSocket` is NIO-backed and unmounts, but `MulticastSocket` with explicit `NetworkInterface`
  selection is a path I did **not** verify. A pinned carrier per multicast interface would be a silent
  scalability regression.
- **It ties the fix to DirtyChai.** `[M]` The virtual-thread propagation is a DirtyChai behaviour
  *deliberately divergent* from OpenJDK — `Subject.java:696–703` states "this propagation behaviour
  differs from the OpenJDK reference implementation". A fix resting on it would work on DirtyChai and
  **silently do nothing** on stock OpenJDK: the worst possible failure shape for precisely this bug.

**Verdict `[H]`:** the explicit `callAs`-at-the-seam approach is portable, local and readable.
Virtual-thread conversion is a larger change with a JVM-liveness landmine and a stock-JDK divergence.
If you want it, do it later on its own merits, with the seams already in place so the conversion
cannot regress identity.

### 6.4 Side finding — a probable no-op worth checking `[H]`

`[M]` `ThreadPool.newVirtualThreadPerTaskExecutor()` (`ThreadPool.java:105–117`) wraps
`Executors.newVirtualThreadPerTaskExecutor()` in `Subject.callAs(null, …)`, commented as clearing any
ambient subject so infrastructure tasks get a clean context.

`[H]` **I believe this does not do what it says.** The executor's virtual threads are constructed
per-task inside `es.submit(...)` (`ThreadPool.java:169`), i.e. **on the submitting thread**, not at
executor-construction time. Since DirtyChai captures the scoped subject at `Thread` construction
(`Thread.java:895`), each pooled virtual thread would capture whatever the *submitter* had bound — not
the `null` bound around the factory call. If so, JERI accept-loop and connection-handling tasks inherit
the submitter's identity, contrary to the stated intent, and the `callAs(null)` wrapper is dead code
that reads as a guarantee.

I have **not** verified this empirically and it is outside your six questions — but it is the same
family of error (an identity-establishment call placed at construction rather than at use) and it sits
in infrastructure that every service depends on. Worth a targeted check.

---

## 7. Cross-cutting: make the failure impossible to miss

`[M]` The recurring shape across this whole investigation:

- `X500Provider.getPrivateCredentials()` returns an **empty array** when no Subject is present
  (`X500Provider.java:227–234`, the `Collections.EMPTY_SET` branch) — no exception, no log.
- The encode iterator then yields nothing, and `RegistrarImpl.java:3259–3266` logs the resulting
  `UnsupportedConstraintException` at `Levels.HANDLED` — **below the default threshold**.
- `SpiffeSupport.processWorkerSubject()` returns `null` on any failure, silently (§5).
- `AbstractLookupDiscovery.getDiscoverySubject()` (`:3184–3196`) catches `RuntimeException`, logs at
  `WARNING`, and continues with `null`. **This one is correct and should be the model for the others.**

`[H]` **The invariant worth enforcing:** *a discovery format whose purpose is signing must never
silently produce zero packets.* If `getPrivateCredentials()` returns empty for such a format, that is
`WARNING` at minimum and arguably an `UnsupportedConstraintException` naming the missing identity. The
cost of the false positive (one log line where credentials are legitimately absent) is trivially below
the cost of the false negative, which you have now measured in days.

---

## 8. Open risks, restated

1. `[M]` **The seam does not fix the qa run.** No config sets `discoveryCredentialProvider`; the
   null-passthrough branch runs. §1.3(2) is the missing client-side piece. Do not infer a fix from a
   clean compile.
2. `[M]` **Four services carry the same false premise** (§2.4) — reggie, fiddler, mercury, norm. Fixing
   only reggie's announce leaves three misleading comments that will regenerate the bug.
3. `[M]` **`AuthPermission "callAs"` / `"getSubject"` are absent from the service policies**
   (`defaultsecurereggie.policy` and `defaultsecuresharedvm.policy` are 3-line emptied stubs). Dormant
   today because **no configSet installs a SecurityManager**; live the moment polpAudit returns.
   `[M]` And route 2 of §5 is *invisible to polpAudit* because the `SecurityException` is swallowed —
   so this cannot be discovered by the usual tool.
4. `[H]` **`callAs` vs `doAs`** for the announce seam is unresolved. `callAs` makes the Subject
   *visible* (measured, §2.3). Whether principal-scoped **policy grants** resolve without `doAs`'s ACC
   injection is unverified and only matters once the SecurityManager is back — but that means the fix
   could pass now and regress then.
5. `[M]` **Encode caching (`RegistrarImpl.java:3234`)** signs announce packets once and reuses them
   indefinitely. Correct for a static X.509 credential; **wrong for a rotating SPIFFE SVID**, and
   contrary to `Subject.java:44–54`. Separate defect.
6. `[M]` **SPIFFE discovery is unreachable** — formats unwritten and unregistered
   (`DESIGN-discovery-credential-resolution.md` §11.6). Q3's answer is correct but currently moot.
7. `[M]` **JVM liveness depends entirely on the six threads Q6 proposes to convert.** JERI supplies no
   non-daemon thread. Do not act on Q6 without addressing this.
8. `[H]` **`ThreadPool`'s `callAs(null)` is probably a no-op** (§6.4) — same family of error, in
   infrastructure every service uses.
9. `[M]` **`DESIGN-discovery-credential-resolution.md` §11.6 candidate 1 is refuted** by §3 and should
   be annotated, or someone will implement it and hit the `IllegalArgumentException`.

---

## 9. What I could not establish

Stated explicitly so nothing here is mistaken for measurement:

- **Whether principal-scoped grants resolve under `callAs` alone** without `doAs`'s ACC injection
  (§2.3(2)). Determines whether the seam survives re-enabling the SecurityManager.
- **Whether the qa harness constructs the tester's `LookupDiscovery` inside the
  `Subject.callAs(CN=Tester, …)` scope** (§1.3(2)). If it does not, a construction-time capture yields
  nothing and the client side needs an explicit provider instead.
- **Whether `MulticastSocket.receive` unmounts a virtual thread** when an explicit `NetworkInterface`
  is selected (§6.3).
- **Whether §6.4 is a real no-op** — reasoned, not tested.
- **Whether a shared module exists that both `jgdms-platform` and `services/reggie` can depend on** for
  the `CompletionException`-unwrapping helper (§2.2).

I did **not** run the qa suite, did **not** commit, stash or switch branches, did **not** modify
DirtyChai, and did **not** apply any patch to JGDMS. The only file I created is this one.
