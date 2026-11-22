<!-- CLAIM-HEADER (edit in place; body is append-only)
GOVERNING STATE: E:\Claude\claim_register\hypotheses\RELAYCASE\INDEX.md  (this file's claims: CL-RELAYCASE-0001 to CL-RELAYCASE-0023); rulings: rulings\JGDMS\INDEX.md (RL-JGDMS-0208 to RL-JGDMS-0214; also RL-JGDMS-0037, RL-JGDMS-0096, RL-JGDMS-0113) and rulings\DIRTYCHAI\INDEX.md (RL-DIRTYCHAI-0016, RL-DIRTYCHAI-0017)
LATEST ADDENDUM: "Addendum 3, 2026-11-22 late" at the end of this file
SUPERSEDED BY: none
FASTEST-AGEING SECTION: "Open items" - re-read the disk before acting on it
-->

# Handover: relay case session, 2026-11-21 to 2026-11-22

Session: Claude Opus 5.5, claude.ai session 018wGHqh27fcgnjdawqkewmq, linked to the operator's Windows
desktop (folders JGDMS, DirtyChai, claim_register). Hypothesis scope RELAYCASE was created by this session.
Nothing was compiled or run. Code statements below come from reading source.

## How it started

The operator shared Paul's reply to his email of 2026-11-20 about JGDMS, DirtyChai and the proxy board.
This session reviewed the reply against `docs/relay-isolation/DESIGN-security.md`, drafted the operator's
answer, then worked through timing attacks, JIT policy, exception propagation, trusted execution, and
the physical case.

## What was produced

- `docs/relay-isolation/DESIGN-security.md`, 2026-11-21 (committed by the operator in 8d3e31250): dated
  paragraphs in R8, R16, R16e and R20; new R16h, R16i, R16j, R16k; an amendment to R13; open points 10
  and 11.
- Same file, 2026-11-22 (NOT committed): section "Case topology (2026-11-22)", items K1 to K7 and open
  points 12 to 15; ruling IDs written into R16j, the header note and the section lead.
- Artifact "Proxy host board layouts" (https://claude.ai/artifact/HzDXjUZSZeVyz4mHXQWWm3) republished as
  version 1795322012-ade1 over 1794731293-bea6: revised sheets 1 to 5 for the relay layout; the first
  issue's sheets 1, 4 and 6 kept at the end as superseded. The render was not looked at.
- The reply to Paul exists only in the conversation. Its last version predates the case topology: it
  describes the relay and proxies on one board.

## Operator decisions registered

- RL-DIRTYCHAI-0016: HotSpot decides compilation and inlining by defining class loader (DESIGN R16j).
- RL-DIRTYCHAI-0017: no Zero build.
- RL-JGDMS-0208: relay and proxy boards in one case; hosts in cases of their own.
- RL-JGDMS-0209: one relay and 24 proxy boards per case.
- RL-JGDMS-0210: a SPIFFE certificate per proxy JVM, keys in the board's TrustZone. This session's
  reading, written into K3: the board's agent holds them and terminates one mutual-TLS link per JVM,
  so the proxy JVM still holds no key and keeps only its Unix socket (RL-JGDMS-0096, RL-JGDMS-0109,
  RL-JGDMS-0113 unaffected). The operator has not confirmed that reading.

Not registered because the operator's wording was "could": the relay running its own SPIFFE server, and
the boot images living on the relay. Both are quoted in the case-topology section and built into K3 and
K4 as proposals.

RL-JGDMS-0037 still says each proxy board is wired to the host directly and to its service over the
public network; RL-JGDMS-0208 routes both through the relay. Whether RL-JGDMS-0037 is superseded is the
operator's call; this session did not change it.

## Claims of this session

- CL-RELAYCASE-0001: the wire hand-off never rebuilds a hosted proxy's Throwable on the host.
- CL-RELAYCASE-0002: the hosted proxy's exception message reaches the host unsanitised.
- CL-RELAYCASE-0003: on a plain JERI hop the Error-to-ServerError wrap happens on the dispatcher side.
- CL-RELAYCASE-0004: the locator and the codec share DerReader's minimal-length checks.
- CL-RELAYCASE-0005: JGDMS mux allows 256 sessions per connection (corrects this session's "128").
- CL-RELAYCASE-0006: REVIEW-01 S2 and DESIGN-inventory question 3 are already answered by RL-JGDMS-0037.
- CL-RELAYCASE-0007: interpreter-only HotSpot still needs executable memory at start-up.
- CL-RELAYCASE-0008: JEP 544 gives no JIT-off mode and no integrity check on cached code.
- CL-RELAYCASE-0009: a firmware-free isolating switch does not stop MAC stealing within a row.
- CL-RELAYCASE-0010: the i.MX 95 boot ROM cannot boot over Ethernet.
- CL-RELAYCASE-0011: arm64 Linux lets user space read the generic timer counter by default.
- CL-RELAYCASE-0012: Arm lists the Cortex-A55 as unaffected by the speculation variants.
- CL-RELAYCASE-0013: one x86 relay can carry 24 boards.
- CL-RELAYCASE-0014: the aether-guest-languages README says a JVM cannot start under its sandbox preload.

## Corrections made during the session

- The session first said the design had "moved on" since the operator's email; the design file
  (2026-11-16) predates the email (2026-11-20). Fixed in the draft reply.
- The session first said a proxy could throw an OutOfMemoryError into the host; reading the hand-off
  code showed it cannot (CL-RELAYCASE-0001).
- The session first proposed interpreter-only proxy JVMs, then Zero; replaced by RL-DIRTYCHAI-0016 and
  RL-DIRTYCHAI-0017, and R16e was revised the same day.
- The session first said Leyden AOT could remove the compiler thread; JEP 544 keeps the JIT
  (CL-RELAYCASE-0008).

## Open items

1. Commit the 2026-11-22 changes to `DESIGN-security.md` (one line, in R16j, runs to 93 columns).
2. The reply to Paul: send as drafted, or add the case topology first.
3. Close REVIEW-01 S2 with a refusal before work order 02's inventory work (CL-RELAYCASE-0006).
4. The HotSpot change for RL-DIRTYCHAI-0016 is DirtyChai source: the operator writes it. AI-authored
   jtreg tests under Zone T only if he asks. Design points are in R16j.
5. Open points 12 to 15 in DESIGN-security, and whether RL-JGDMS-0037 is superseded.
6. Measurements on the prototype: open point 11 and K6 (CL-RELAYCASE-0013).
7. Whether OP-TEE on the i.MX 95 can hand signing to the EdgeLock enclave (K3); which SPIRE
   key-manager plugin fits the relay's HSM.

## Addendum 1, 2026-11-22 evening

Answers to open item 5's second half and to the session's reading of RL-JGDMS-0210:

- RL-JGDMS-0037: the operator's answer is logged on that ID; RL-JGDMS-0208 replaces it. Its
  no-call-back sentence is carried only by DESIGN-security K5, not by a ruling.
- RL-JGDMS-0211: no key in the proxy JVM; the relay is reached over the case's private network,
  not a Unix socket. Logged against RL-JGDMS-0096 as its replacement.
- RL-JGDMS-0212: asked who terminates TLS (option A, the JVM, or B, a board agent bridging to a
  Unix socket), the operator chose A and added that each JVM has a unique SPIFFE ID whose request
  its TrustZone key signs. Logged against RL-JGDMS-0113 as its replacement.
- New claims: CL-RELAYCASE-0015 (OP-TEE sees only uid or gid), CL-RELAYCASE-0016 (session key
  objects per client session), CL-RELAYCASE-0017 (SunPKCS11 over libckteec for JSSE).
- DESIGN-security.md: header note, trust-boundary row and paragraph, R16g, K3 (agent bridge
  replaced by the JVM's own mutual TLS, with five consequence bullets), K7, open points 16 and 17.
  The "Proxy host board layouts" drawings show the link per board and are not affected.

Open items replacing item 5:

8. Open points 12 to 17 in DESIGN-security. 16: one uid for all proxy JVMs (RL-JGDMS-0111) cannot
   keep each JVM to its own TrustZone key unless keys are per-session objects (CL-RELAYCASE-0015,
   CL-RELAYCASE-0016). 17: RL-JGDMS-0077, -0078, -0111, -0114, -0115 and -0116 still name the Unix
   socket.
9. Commit DESIGN-security.md, this handover, and the register's JGDMS and RELAYCASE logs.

## Addendum 2, 2026-11-22 night

The operator asked whether the relay belonged on the board (the session said no: off-board is the
stronger design), then proposed dropping encryption on the internal links. The session showed that
needs per-board links or one VLAN per switch port, and the operator chose the latter route:

- RL-JGDMS-0213: no encryption on the internal links; each switch port has its own VLAN and the
  relay knows the slot from it. Logged against RL-JGDMS-0210 and RL-JGDMS-0212 as their
  replacement. RL-JGDMS-0113 is logged as in force again, its replacement being gone.
  RL-JGDMS-0211 is untouched (no key in the JVM; relay over the private network).
- RL-JGDMS-0214: slot ID pins on the caddy backplane, preferred over per-VLAN DHCP.
- New claims: CL-RELAYCASE-0018 (firmware-free switch chips programmable for this),
  CL-RELAYCASE-0019 (per-port VLANs stop MAC stealing; at worst a drop on a shared address
  table), CL-RELAYCASE-0020 (U-Boot reads GPIO to set addresses). CL-RELAYCASE-0015 to
  CL-RELAYCASE-0017 no longer bear on the design; they are left as they are.
- DESIGN-security.md: header note, trust-boundary row and paragraph, K1 to K4 rewritten (slot
  VLANs, slot pins, boot without DHCP), K7 note, new K8 (hot swap), open point 16 closed,
  17 widened, 18 to 20 added.

Open items replacing item 8:

10. Open points 12 to 15 and 17 to 20 in DESIGN-security.
11. The "Proxy host board layouts" drawings now disagree with K1 and K3: sheet 4's
    MAC-stealing note and sheet 5's TrustZone label belong to the mutual-TLS design; the slot ID
    pins, per-port VLANs and per-caddy hot-swap controllers are not drawn.
12. The reply to Paul predates the whole case topology.

## Addendum 3, 2026-11-22 late

- The operator asked whether R16j's compiler restriction was needed, the JIT risk seeming low.
  The session set out recent C1 and C2 bugs (CL-RELAYCASE-0021) against the smaller blast radius
  of the case topology; the operator kept it. RL-DIRTYCHAI-0016 has a reaffirming log line with
  his words, and R16j a dated note.
- Asked whether Pack200 and SCAP cover it: no (CL-RELAYCASE-0022, CL-RELAYCASE-0023). SCAP's
  crash signal is proposed as a tripwire alongside: DESIGN-security K9 and open point 21.
- Drawings: revised for the VLAN-per-slot design in /home/claude/proxy-board-layouts.html of
  this session and as a PDF sent to the operator. Not republished: the artifact is shared
  publicly and the republish was refused by the permission check; the operator has not yet
  said whether to publish.
- Reply to Paul: a link-free reply to his scoped-container email (three questions, DirtyChai's
  extra checks, hardware update) was drafted and edited with the operator; sending is his.
  His earlier reply bounced, probably on its links.

Open items added:

13. Republish the drawings if the operator agrees to the public page changing.
14. Open point 21 in DESIGN-security.

Note, 2026-11-22 22:42: the operator agreed, and the drawings are republished as version 1795351360-1996; open item 13 is done.

Note, 2026-11-22 23:02: the drawings as republished are also in the repository as JGDMS/docs/LAYOUT-proxy-host-board-2026-11-22.html (uncommitted), beside the first issue of 2026-11-15.
