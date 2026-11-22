<!-- CLAIM-HEADER (edit in place; body is append-only)
GOVERNING STATE: E:\Claude\claim_register\hypotheses\RELAYCASE\INDEX.md  (this file's claims: CL-RELAYCASE-0001 to CL-RELAYCASE-0031); rulings: rulings\JGDMS\INDEX.md (RL-JGDMS-0208 to RL-JGDMS-0226; also RL-JGDMS-0037, RL-JGDMS-0096, RL-JGDMS-0113) and rulings\DIRTYCHAI\INDEX.md (RL-DIRTYCHAI-0016, RL-DIRTYCHAI-0017)
LATEST ADDENDUM: "Addendum 6, 2026-11-23 early morning" at the end of this file
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

## Addendum 4, 2026-11-23

A hardware session after midnight, the operator shaping and the session checking parts:

- Network: an 8-port I350 card per eight boards was too many double-width cards; I350s on row
  boards, then Innodisk EGPL-G401 M.2 modules, then the network controllers on a backplane behind
  a PCIe switch with one cable to the relay. Parts checked from vendor pages: CL-RELAYCASE-0024
  (I350-AM4), CL-RELAYCASE-0025 (cable margin, untested), CL-RELAYCASE-0026 (EGPL-G401).
- Modules: i.MX 95 SMARC options and requirements (CL-RELAYCASE-0027); heatsinks and power
  (CL-RELAYCASE-0028).
- Decisions registered: RL-JGDMS-0215 (twelve top-loaded vertical cards of two staggered
  modules), RL-JGDMS-0216 (a controller port per module, PCIe switch, one cable), RL-JGDMS-0217
  (links unencrypted, slot known by its port; logged as replacing RL-JGDMS-0213), RL-JGDMS-0218
  (ducts and fans), RL-JGDMS-0219 (module requirements). Notes added to RL-JGDMS-0214 (slot pins
  now on the backplane plus a card strap) and RL-JGDMS-0209 (24 modules per case unchanged).
- DESIGN-security.md: K1 to K4 and K8 rewritten, K10 (cooling, with the thermal side channel)
  and K11 (module requirements) added, R16f note, open points 18 and 19 closed, 22 to 26 added.
- Drawings: new issue published (artifact version 1795359888-d982) and in the repository as
  JGDMS/docs/LAYOUT-proxy-host-board-2026-11-23.html; earlier issues stay in the repository.

Open items added:

15. Open points 22 to 26 in DESIGN-security (PCIe switch and controller parts, card pitch,
    thermal test, module vendor checks, relay board).
16. PROXY_HOST_BOARD_REQUIREMENTS.md in DirtyChai still describes caddies (C1 to C11, A1 to A3).
    It is not on CLAUDE.md's list of root documents an AI may edit; the operator decides who
    revises it.
17. The reply to Paul predates this hardware revision.

Note, 2026-11-23 01:12: at the operator's request the candidate network controller (Innodisk EGPL-G401, with alternatives and the parts set aside) is recorded as DESIGN-security K12 and named on drawing sheets 1 and 5 and in the notes; drawings republished as version 1795360287-888f and saved over JGDMS/docs/LAYOUT-proxy-host-board-2026-11-23.html.

Note, 2026-11-23 01:20: RL-JGDMS-0220 registered (modules stand on end, air crosses the 50 mm side); DESIGN-security K1 and K10 updated; the operator's suggestion of a 3U case is recorded in K1 as the target, not as a ruling, with open point 27 for the height check. Drawings sheets 2 to 4 redrawn and republished as version 1795360755-5ece, and saved over the repository copy.

Note, 2026-11-23 01:28: the operator's statement on a small relay board, supplies at the rear left and one x16 slot is quoted in DESIGN-security K1 (not registered as a ruling); open point 26 widened (two on-board LAN ports and a BMC port). Sheet 2 redrawn; drawings republished as version 1795361254-bb09 and saved over the repository copy.

Note, 2026-11-23 01:31: RL-JGDMS-0221 registered (on-board MCIO or SlimSAS connector on the relay board for the backplane link). The operator's statement that the chassis must be long, with the network controllers flat on the backplane, is quoted in DESIGN-security K1 (estimate 500 to 600 mm deep), with the 55 degree C concern and the front-of-backplane fallback; open points 26 and 27 updated. Drawings republished as version 1795361390-96d9 and saved over the repository copy.

Note, 2026-11-23 01:45: RL-JGDMS-0222 registered (network controllers and PCIe switch on a network card at the front of the relay duct; passive backplane; logged as replacing RL-JGDMS-0216; RL-JGDMS-0218 noted as still in force). DESIGN-security K1, K3, K10, K12 and open points 22, 24, 27 updated; the network modules lie three high and two deep on one face of the network card (the operator's wording, put into the document's terms at his invitation). The operator edited and resent a message mid-turn at 01:39, so one run of this session's edits landed before the run that reported them; he confirmed they were this session's. Drawings: sheets 1, 2 and 5 redrawn, sheet 6 (network card) added; republished as version 1795362183-2206 and saved over the repository copy.

## Addendum 5, 2026-11-23 wrap-up

State at the second compaction. The notes after Addendum 4 record each step; this is the summary.

Registered since Addendum 4: RL-JGDMS-0220 (modules stand on end), RL-JGDMS-0221 (on-board MCIO or
SlimSAS connector on the relay board), RL-JGDMS-0222 (network controllers and PCIe switch on a
network card at the front of the relay duct; passive backplane; logged as replacing RL-JGDMS-0216).
New claim CL-RELAYCASE-0029 (the EGPL-G401's LAN ports leave on two micro-coax connectors, one on
each face). Settled from sources at wrap-up: CL-RELAYCASE-0010 and CL-RELAYCASE-0011. Left as they
are: CL-RELAYCASE-0007 and CL-RELAYCASE-0012 (no source found tonight); CL-RELAYCASE-0009,
CL-RELAYCASE-0015 to CL-RELAYCASE-0019 and CL-RELAYCASE-0025 no longer bear on the design (VLANs,
TrustZone keys and row boards are gone) and could be withdrawn if the operator wishes.

Operator statements recorded in DESIGN-security K1 but not registered, because he worded them as
what could be done: a 3U case, a small relay board with the supplies at the rear left, and all
network modules on one face of the network card. At his invitation the last two are given in the
document's terms, not quoted; his words are in the transcript.

Open at compaction, in the order the operator was working:

18. The network controller route (DESIGN-security K12, open point 22): the operator proposed port
    boards per bank of three modules plugging into the backplane; the datasheet he supplied shows a
    LAN connector under each module, so port boards cannot mate. The session proposed EGPL-G401 with
    short micro-coax leads for the prototype and I350-AM4 laid out on the network card for
    production. Awaiting his decision; drawing sheet 6 is marked provisional. Questions for
    Innodisk are listed in K12.
19. Open points 22 to 27 in DESIGN-security (network card parts, card pitch, thermal test, module
    vendor checks, relay board, case size), and 12 to 15, 17, 20, 21 from earlier.
20. Whether to register the 3U case and the relay board and supply placement as rulings.
21. Commit: DESIGN-security.md, this handover, JGDMS/docs/LAYOUT-proxy-host-board-2026-11-22.html
    and -2026-11-23.html, and the register logs (JGDMS, DIRTYCHAI, RELAYCASE).
22. The reply to Paul predates the whole 2026-11-23 hardware revision.
23. PROXY_HOST_BOARD_REQUIREMENTS.md (DirtyChai) still describes caddies; not on CLAUDE.md's list
    of root documents an AI may edit.

Drawings at compaction: artifact version 1795363100-e21e, six sheets, same content as the
repository copy JGDMS/docs/LAYOUT-proxy-host-board-2026-11-23.html.

## Addendum 6, 2026-11-23 early morning

After the second compaction the operator reworked the network controllers, the relay board, the
controller and the cooling, then asked for the whole revision to be written up (03:04).

Registered: RL-JGDMS-0223 (network modules on a passive carrier on the right-hand wall, one MCIO
cable per module to its own CPU root port, no PCIe switch; LAN leads behind the carrier and under
the divider through a foam seal on a folded flange; logged as replacing RL-JGDMS-0221 and
RL-JGDMS-0222), RL-JGDMS-0224 (no network activity LEDs), RL-JGDMS-0225 (relay duct: front, middle
and rear fan in series, no flaps; card bank: front and rear fans; speeds may follow inlet
temperature in coarse steps, never load; logged as replacing RL-JGDMS-0218), RL-JGDMS-0226
(controller on an off-the-shelf board beside the supplies; backplane carries only firmware-free
chips and the write-protect gate; no wiring to the relay side; the controller owns the fans).
New claims: CL-RELAYCASE-0030 (the MSI D3061's MCIO connectors are fed straight from the CPU) and
CL-RELAYCASE-0031 (Xeon 6505P and 6507P are 150 W parts).

Recorded in DESIGN-security as proposals, not registered: EGPL-G401 for production as well as the
prototype, with I350-AM4 as fallback (K12); the MSI D3061 as candidate relay board, with the CPU at
a fixed frequency rather than power-capped, because a power cap reopens the Hertzbleed channel
(K13; the operator's "We can underclock the CPU." is quoted there); status and safe-to-remove
LEDs on the front panel (K1); boot-time check of cable and lead wiring through the image server
(K4); eFuses and staggered pins (K8); JTAG and module time (K11); controller details (K14); the
supplies' fans fixed over PMBus by the BMC (K10).

Done: DESIGN-security.md header paragraph, K1 to K4, K7, K8, K10 to K12 revised, K13 and K14
added, open points 13, 22, 24, 26, 27 revised and 28 to 30 added. Drawings sheets 1, 2, 5 and 6
redrawn (network carrier, controller, fans), notes and captions updated; republished as artifact
version 1795367739-2515 and saved over JGDMS/docs/LAYOUT-proxy-host-board-2026-11-23.html.

Correction to this session's own advice during the discussion: the operator said the motherboard
had a PCIe switch; the D3061's published specifications list none, and its MCIO connectors are on
CPU root ports (CL-RELAYCASE-0030), which is what made one cable per module possible.

Open items, replacing 18 to 20 of Addendum 5 (21 to 23 stand):

24. Innodisk's answers (K12, open point 22): connector and LED header pinout, magnetics, mating
    connector and custom leads, supply life, configuration memory.
25. Open points 22 to 30 in DESIGN-security, and 12 to 15, 17, 20, 21 from earlier.
26. Whether to register the 3U case, the supplies at the rear left and the EGPL-G401 for
    production as rulings; and whether the D3061 becomes the chosen relay board.
27. Commit now also covers the register logs for RL-JGDMS-0223 to RL-JGDMS-0226 and
    CL-RELAYCASE-0030 and CL-RELAYCASE-0031.
