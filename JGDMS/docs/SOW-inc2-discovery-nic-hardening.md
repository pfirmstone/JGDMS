# SOW — Inc2: Deterministic interface binding + WiFi/health-aware retry

**Status:** IMPLEMENTED (reduced) + COMPILES (DirtyChai JDK27, BUILD SUCCESS). NOT committed. Empirical qa verification blocked (see §10).

## 0. REVISION 07-11 — reduced after a 4-agent review board

The first implementation (coarse socket-global liveness + re-selection + burst re-arm)
was reviewed by a 4-lens board. Outcome:
- **Backward-compat:** SAFE.
- **Concurrency:** FIX-FIRST — all 3 findings (terminate() TOCTOU thread-leak, re-arm
  thrash, no health-interval floor) lived entirely in the coarse-liveness code.
- **Semantics/efficacy:** FIX-FIRST + **LOW confidence it greens the RED tests** — the
  static filter only drops inert `lo`; coarse (socket-global) liveness cannot detect a
  silently black-holing interface. The real fix needs **per-interface liveness**
  (two-arg `joinGroup(group,nic)` + one socket per NIC).
- **Build-verify:** ENVIRONMENT-BLOCKED (qa harness can't bootstrap — `defaulttest.policy`
  is empty pending regeneration). Found `wlo1` lacks the kernel MULTICAST flag → AUTO
  selects a much smaller set than the board assumed.

**Peter's call:** *reduce* Inc2 to the confident, superseded-proof core, and do the real
fix as **Inc2.1**. So this SOW's coarse-liveness (D5a) content below is SUPERSEDED:
- **REMOVED from Inc2:** `reselectMulticastInterfaces`, `rearmRequestorBurst`, the run-loop
  liveness block, `lastPacketReceivedTime`/`lastReselectTime`, `multicastInterfaceHealthInterval`
  config+field; `nics` reverted `volatile`→`final`. (Eliminates B1/M1/Mi2 outright.)
- **KEPT in Inc2:** `NICS_USE_AUTO` mode, `multicastInterfacePolicy` config entry, the
  static filter `selectMulticastInterfaces()`, AUTO wired into the 3 switch sites.
- **FIXED per board:** MAJOR-2 — family read now `Constants.IPv6` (privileged single source
  of truth), not an un-privileged `Boolean.getBoolean` (SM-safe, no family divergence);
  MAJOR-3 — loopback NO LONGER excluded (a multicast-capable `lo` is retained; a
  non-multicast `lo` is already dropped by `supportsMulticast()`), so the default flip
  cannot regress a single-host box whose only path is `lo`. Javadoc notes fail-closed
  `ConfigurationException` on an unrecognized policy.

**Inc2 is now: config-surface + static health filter + AUTO default — safe hardening
groundwork, NOT the WiFi test fix.** The WiFi fix is **Inc2.1** (§11).

---

**Original status:** DESIGN / decisions being locked. No code written yet.
**Scope owner:** `net.jini.discovery.AbstractLookupDiscovery` (the *discovering* side).
**Origin:** `SOW-discovery-transport-scoping.md` §9 task T1; empirically confirmed as the
real fix for the flaky-WiFi discovery-test failures (see memory
`jgdms-ipv6-multicast-discovery`, `discovery-transport-lan-vs-mesh`). Independent of the
4.0.0 deser/DER work and of the discovery-transport SPI (Inc1); lands on its own.

---

## 1. Problem (re-grounded in the code, not the SOW's assumption)

`AbstractLookupDiscovery` selects multicast interfaces via one config entry,
`multicastInterfaces` (`NetworkInterface[]`), collapsed into four modes at
lines 1492–1529:

| Config value | Mode | Behavior |
|---|---|---|
| **absent** | `NICS_USE_ALL` (default) | enumerate **every** NIC; send + join on all |
| `null` | `NICS_USE_SYS` | OS-default single interface |
| `length == 0` | `NICS_USE_NONE` | multicast disabled |
| `length > 0` | `NICS_USE_LIST` | exactly those interfaces |

The same `nicsToUse` switch drives three sites: join-announce-group
(`AnnouncementListener` ctor, ~444), send-request (`sendPacketByNIC`, ~1987),
and retry (`retryBadNics`, ~540).

**The failure model is wrong for WiFi.** "Bad NIC" is defined *only* by an
`IOException` thrown from `setNetworkInterface` / `joinGroup` / `sendPacket`
(452–457). WiFi multicast failure throws **nothing**: the join succeeds, the send
succeeds, and the access point silently drops the frames. So a flaky WiFi
interface is *permanently* classified healthy and never enters `retryNics`.
Symmetrically, `lo` on the dev box cannot loop IPv6 multicast but also throws
nothing. `NICS_USE_ALL` sprays onto both, declares success, and discovery quietly
fails. **No environment hack fixes this because the failure is invisible to an
exception-based health model.** Inc2 must add a *positive liveness* signal.

Second failure: the requestor sends a **finite burst** — `multicastRequestMax`
requests 5 s apart, one 2-minute wait, then the thread exits (772–791). After that
discovery depends entirely on the lookup's ~2-min announcements. On a link that
silently drops both directions, the node goes permanently deaf with **no re-arm**.

## 2. Decisions locked (Peter, 07-11)

1. **Default selection → auto-healthy.** Absent-config flips from `NICS_USE_ALL`
   to a new `NICS_USE_AUTO`: enumerate, statically filter, prefer interfaces
   actually delivering traffic; fall back to all-filtered only if none qualify.
   No-op on single-NIC hosts; the mesh/WiFi node gets what it needs.
2. **Health = static prefilter + dynamic liveness.** Static flags alone do not fix
   the box (WiFi passes them all). Track received traffic and treat silence as
   degraded.
3. **New config entry `multicastInterfacePolicy`** ∈ {`auto`,`all`,`system`,`list`,
   `none`}, precedence over the legacy overload; `multicastInterfaces` still
   supplies the list. Backward compatible.
4. **Scope = discovery client side (`AbstractLookupDiscovery`) first.** reggie's
   announce-send / request-respond side is a fast-follow (Inc2b) reusing the shared
   helper.

## 3. Further decisions (RESOLVED, Peter 07-11)

- **D5 = (a) coarse global liveness, single socket.** Per-interface attribution
  (one-socket-per-NIC) deferred to Inc2.1.
- **D6 = accept recommended timing:** keep 5-min `multicastInterfaceRetryInterval`
  default; add `multicastInterfaceHealthInterval` ~30 s; silence threshold =
  2 × `multicastAnnouncementInterval`; re-arm `Requestor` burst on recovery.

Original analysis retained below for rationale.

### D5 — Liveness *attribution* → socket model. **(biggest fork)**
Today there is **one** `MulticastSocket`; it `joinGroup`s on each interface and
`setNetworkInterface`s in a loop to send. On receive, a plain `DatagramPacket`
tells you the *sender*, not the *receiving interface* — so **per-interface**
liveness cannot be attributed with the current single-socket model. Options:

- **(a) Coarse global liveness (RECOMMENDED for Inc2).** Track "did the discovery
  socket receive *anything* within the window." On global silence past threshold,
  re-run selection: re-enumerate, re-apply the static filter (drops interfaces that
  lost carrier), re-join, and re-arm a request burst. No per-interface attribution,
  no socket-model change. On this box (effectively one viable interface) this is
  sufficient to abandon a dead interface and re-select. Small, confident.
- **(b) One `MulticastSocket` per selected interface.** Clean per-interface
  attribution and receive; lets AUTO shrink to exactly the live interface(s) and is
  the natural "pin to one" model. But it rewrites the socket lifecycle (N sockets,
  N receive paths or a selector) — larger blast radius, more review.

*Recommendation:* ship **(a)** in Inc2 for a green, confident fix; note **(b)** as
Inc2.1 / the model Inc1's transport SPI will want anyway.

### D6 — Health/retry timing + burst re-arm.
- `multicastInterfaceRetryInterval` default is **5 min** (1536) — too slow for WiFi
  churn, but it is a *named public entry*; keep its default (compat) and add a
  distinct, shorter **health re-probe interval** for AUTO (new entry
  `multicastInterfaceHealthInterval`, default ~30 s) used only for liveness
  re-evaluation.
- **Re-arm the request burst on recovery.** When selection transitions a candidate
  set from degraded→live (or re-selects), issue a fresh `Requestor` burst rather
  than waiting for the next announce cycle. Directly fixes "missed the burst, link
  came back." Low risk (reuses the existing `Requestor`).
- Liveness silence threshold: default tie to announcements — `2 ×
  multicastAnnouncementInterval` (~4 min) to declare a candidate set degraded
  (`AnnouncementTimerThread` already uses `N_INTERVALS = 3` for per-lookup discard;
  stay conservative to avoid thrashing).

*Recommendation:* accept the above defaults; all are new/opt-in except the behavior
change already covered by D1.

## 4. API / surface impact

**Public config surface (new, additive, documented in `LookupDiscovery` javadoc):**
- `multicastInterfacePolicy` — `String` ∈ {`auto`,`all`,`system`,`list`,`none`}.
  Absent → resolved from the legacy `multicastInterfaces` overload, **except** the
  all-absent default is now `auto` (the D1 behavior change).
- `multicastInterfaceHealthInterval` — `long` ms, default 30000; AUTO liveness
  re-probe cadence.
- (existing) `multicastInterfaces`, `multicastInterfaceRetryInterval`,
  `multicastAnnouncementInterval` — **unchanged semantics and defaults.**

**Internal (not public API):** new `NICS_USE_AUTO` mode constant (value 4); the four
`switch(nicsToUse)` sites extended; a candidate-selection helper
(`selectMulticastInterfaces()`); a liveness timestamp + re-selection path.

**No changes** to `DiscoveryManagement` / `LookupDiscovery` / constructors / wire
format. Purely internal behavior + additive config.

## 5. Backward compatibility

- Existing `multicastInterfaces` semantics (null=system, empty=none, list=list)
  **preserved verbatim.**
- Only the *all-absent* default changes (ALL → AUTO). A single-NIC host is
  unaffected (AUTO selects the one NIC). A multi-homed host wanting literal-all sets
  `multicastInterfacePolicy = "all"` — one line, documented in the release note.
- Named entries keep their defaults; new tuning is opt-in.

## 6. Implementation steps

1. Add `NICS_USE_AUTO`; resolve config precedence
   (`multicastInterfacePolicy` → legacy overload → AUTO default) in the `Initializer`.
2. `selectMulticastInterfaces()` static filter: `isUp() && supportsMulticast() &&`
   has an inet address of the active family (IPv6 when `preferIPv6Addresses`) `&&
   !isLoopback()` (loopback only as sole-candidate fallback). **This alone should
   green the tests** by preferring the working interface over dead `lo`.
3. Wire AUTO into the three `switch` sites (join / send / retry) — behaves like
   ALL over the *filtered* set.
4. Add coarse liveness (D5a): received-timestamp on the announce socket; a
   re-selection path on silence-past-threshold that re-enumerates + re-joins +
   re-arms a `Requestor` burst.
5. Add `multicastInterfaceHealthInterval`; drive re-probe from the existing timer
   thread (no new ThreadLocal — VT-safe, use explicit fields / `ScopedValue` if
   context needed).
6. Javadoc the new entries in `LookupDiscovery`; note the default change.

## 7. Test plan

- Existing qa discovery suite on the WiFi box (the RED tests) → GREEN after step 2.
- New unit coverage: `selectMulticastInterfaces()` filtering (mock NICs: up/down,
  multicast/no, loopback, family match) — pure, no network.
- Re-selection path: simulate silence → assert re-enumeration + burst re-arm fires.
- Regression: `multicastInterfacePolicy=all|system|list|none` reproduce the legacy
  four modes exactly; absent config on a single-NIC host = same interface chosen.
- Full `-Djava.net.preferIPv6Addresses=true` path preserved (no IPv4 regressions).

## 8. Risks

- **Behavior change (D1).** Mitigated: single-NIC no-op; `policy=all` restores old
  behavior; release-noted.
- **Liveness thrash.** Conservative thresholds; re-selection is idempotent.
- **reggie announce side unaddressed in Inc2** — tests exercise both; if step 2 on
  the client doesn't fully green them, Inc2b (reggie) may be required sooner.
  Reuse the same `selectMulticastInterfaces()` helper (extract to a shared util).
- **VT constraint:** no `ThreadLocal` (memory `no-threadlocal-virtual-threads`).

## 10. Verification status (07-11)

Empirical qa signal is currently BLOCKED: the DirtyChai SecurityManager denies
`FilePermission read` on `jiniharness.jar` during main-class definition, killing every
qa test VM at bootstrap — because `defaulttest.policy` (and 15 sibling `default*.policy`)
were committed empty (`aef547340`) pending polpAudit regeneration. Until regenerated (or
temporarily granted), no discovery test can produce a pass/fail. Plan: run
`org/apache/river/test/spec/lookupdiscovery/Discovered.td` under the polpAudit **audit**
SecurityManager (captures+allows, so the VM bootstraps) — this simultaneously regenerates
the emptied policies AND exercises the reduced-Inc2 AUTO path for a real signal. The
regenerated policies are Peter's to verify (grep C:\, /home/, /tmp/, hostname, random
suffixes = zero) and commit.

Box facts (from the build-verify agent): `wlo1` lacks the kernel MULTICAST flag →
`supportsMulticast()` excludes it; `lo` is loopback (now retained only if it advertises
multicast). So on this box AUTO ≈ `{br0}` (+`lo` iff lo is multicast-capable). Since `br0`
is the interface proven to loop FF05 multicast, AUTO *may* help here by not spraying onto
`wlo1` — but this is unverified until the audit-mode run.

## 11. Inc2.1 — the actual WiFi fix (next increment, per board)

Root ambiguity: a single shared `MulticastSocket` + `setNetworkInterface()` loop + the
deprecated single-arg `joinGroup(InetAddress)` cannot attribute received traffic to an
interface, and its per-interface receive binding is platform-dependent. Inc2.1:
- Migrate to two-arg `joinGroup(SocketAddress, NetworkInterface)` / `leaveGroup(...)`.
- Per-interface handling (one socket per selected NIC, or NIC-tagged receive) enabling
  **true per-interface liveness** — abandon an interface that silently stops delivering,
  which coarse D5a structurally cannot do.
- Re-introduce liveness-driven re-selection + burst re-arm on that per-interface basis
  (re-applying the board's B1/M1/Mi2 fixes: terminate()-safe re-arm gated inside the
  `requestors` lock, gated on a real interface-set delta, with a health-interval floor).
- This is what makes the WiFi/mesh discovery tests pass; gated behind nothing in 4.0.0.
