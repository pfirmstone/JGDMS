<!-- CLAIM-HEADER (edit in place; body is append-only)
GOVERNING STATE: E:\Claude\claim_register\hypotheses\RELAYCASE\INDEX.md  (this file's claims: CL-RELAYCASE-0001 to CL-RELAYCASE-0066); rulings: rulings\JGDMS\INDEX.md (RL-JGDMS-0208 to RL-JGDMS-0240; also RL-JGDMS-0037, RL-JGDMS-0096, RL-JGDMS-0113) and rulings\DIRTYCHAI\INDEX.md (RL-DIRTYCHAI-0016, RL-DIRTYCHAI-0017)
LATEST ADDENDUM: "Addendum 18, 2026-11-25 14:15 wrap-up" at the end of this file
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

Note, 2026-11-23 03:30 wrap-up: CL-RELAYCASE-0020 settled from U-Boot sources (gpio read and gpio input in boot scripts). CL-RELAYCASE-0007 and CL-RELAYCASE-0012 left as they were: no source found tonight. The claims listed in Addendum 5 as no longer bearing on the design are unchanged, awaiting the operator. Drawings at this point: artifact version 1795367739-2515, same content as the repository copy JGDMS/docs/LAYOUT-proxy-host-board-2026-11-23.html.

## Addendum 7, 2026-11-23 midday

After the operator committed (03:28) and compacted, the session reviewed the design, then the relay
board's management module (MSI MGT1), the D3061 user guide v1.2 the operator uploaded, the relay's
outward network and off-the-shelf MCIO-to-M.2 adapters (the XIC 0582 brief he uploaded). The
operator asked for the write-up at 13:40.

Registered: RL-JGDMS-0227 (the outward NIC on its own MCIO connector and root port, the OCP 3.0 slot
empty; order of preference: MCIO-to-PCIe slot adapter with an I350-T4, then XIC 0582 with
EGPL-G401-C2, then U.2 or SlimSAS), RL-JGDMS-0228 (the custom carrier stays for production; XIC 0582
adapters for the prototype bench; revisit if the card pitch settles near 22 mm), RL-JGDMS-0229
(every MCIO cable built without SMBus, USB, FlexIO or standby lines; SMBus also left unconnected on
the boards). New claims CL-RELAYCASE-0032 to CL-RELAYCASE-0044: D3061 facts read from the user guide
(needs its DC-SCM module to boot, no LAN controller of its own, an NC-SI path through the OCP slot,
the MCIO sideband lines, BMC JTAG and At-Scale Debug lines and the intrusion header, dimensions and
DIMM banks, and the open x4x4 and B12/PESTI questions); I350 SMBus pass-through and the open
MCTP-over-PCIe question; XIC 0582 and EGPL-G401 facts; the duct-width estimate. CL-RELAYCASE-0030
has a new log line from the full block diagram.

Recorded in DESIGN-security as proposals, not registered (K15): BMC hardening (dedicated port only;
Redfish host interface, KVM, virtual media, IPMI over LAN and At-Scale Debug off; ROT1 fitted; the
relay's own lid switch on the intrusion header); a root-port check at boot (link width, subsystem
vendor ID, MAC prefix; no interface comes up on a mismatch), which catches a swap between a module's
cable and the outward NIC's; the outward cable in a different length or colour; the prototype bench
and its tests.

Done: DESIGN-security.md header paragraph, the case-topology ruling list, K1, K2, K4, K12 and K13
revised, K15 added, open point 26 revised and 31 to 33 added. Drawings sheets 1 and 2 (outward NIC,
MGT1 and ROT1, OCP slot empty, cables without SMBus), lede, captions and notes; republished as
artifact version 1795405594-f216 and saved over JGDMS/docs/LAYOUT-proxy-host-board-2026-11-23.html.

Corrections to this session's own advice during the discussion: it proposed three XIC 0582 boards
around the duct (right-hand wall, divider and floor) before checking the width; beside the card bank
the duct is wide enough only at a card pitch near 22 mm (CL-RELAYCASE-0044). It also placed an XIC
0582 for the outward NIC behind the DIMMs, where the board leaves about 100 mm, less than the
adapter's 145 mm; the chosen outward NIC, an I350-T4 on a slot adapter, does not need that space.

Open items, replacing 27 of Addendum 6 (the operator committed at 03:28; 24 to 26 stand):

28. Questions for MSI (open point 31): OpenBMC for the MGT1, loading the operator's keys into the
    AST1060 and refusing unsigned updates, At-Scale Debug, the Redfish host interface, x4x4 on
    JMCIO1-8, link training with B12 alone, booting without the MGT1. For the operator: whether the
    node identity key is sealed in the TPM, and recording trust in the BMC firmware if OpenBMC is
    not offered.
29. A cable house for the seven MCIO cables without SMBus (open point 32); whether the I350 carries
    MCTP over PCIe.
30. The prototype bench (open point 33).
31. Commit: DESIGN-security.md, this handover, the layout file, and the register logs for
    RL-JGDMS-0227 to RL-JGDMS-0229 and CL-RELAYCASE-0032 to CL-RELAYCASE-0044 (with the new
    CL-RELAYCASE-0030 line).

Note, 2026-11-23 14:25 wrap-up: no claim settled at this wrap-up. CL-RELAYCASE-0038, CL-RELAYCASE-0039 and CL-RELAYCASE-0044 wait on MSI and the card pitch; CL-RELAYCASE-0041 was searched again (Intel forum quoting the I350 datasheet names MCTP over SMBus only; the I210 datasheet names MCTP over SMBus/PCIe; neither settles the I350) and is left as it was. CL-RELAYCASE-0007 and CL-RELAYCASE-0012 are still without a source. The first save of the layout file at 13:46 left the old content on disk; it was saved again at 13:58 and now matches artifact version 1795405594-f216 byte for byte.

## Addendum 8, 2026-11-23 afternoon

Registered: RL-JGDMS-0230 (a host sharing the case's rack runs its fans at speeds that never follow its
load), from the operator's message at 14:26 and his "Yes" at 14:30; CL-RELAYCASE-0045 (a proxy counting
between edges of a time that follows the relay's clock reads its module's temperature through the
module's oscillator).

Proposals of this session, in DESIGN-security.md K10, K11 and open points 25, 34 and 35: the host's
supply fans fixed too and its BMC fan curve off; blanking panels and sealed rail gaps, the case below
the host, separate PDU feeds, rubber-isolated rails; inlet steps set so the host's swing never crosses
one; no microphone, codec, accelerometer, audio or ADC driver on the modules; a temperature-compensated
or oven oscillator, or an external reference, as a vendor question; a rack test with the host cycling.

Answer given to the operator's question at 14:30: the case's fans should hold a speed, not a
temperature, because a loop holding a temperature moves the fans with the load in the case, which
RL-JGDMS-0225 rules out; a fixed temperature belongs at the oscillator only. Written into K10 as a
proposal.

Done: DESIGN-security.md header paragraph, the case-topology ruling list, K10 (two bullets), K11 (one
bullet), open point 25 revised, 34 and 35 added. Drawings not changed.

Open items (28 to 30 of Addendum 7 stand; 31 is replaced):

31. Commit: DESIGN-security.md, this handover, the layout file, and the register logs for RL-JGDMS-0227
    to RL-JGDMS-0230 and CL-RELAYCASE-0032 to CL-RELAYCASE-0045 (with the new CL-RELAYCASE-0030 line).

32. For the operator: fan steps or one fixed speed (open point 35), after the rack test (open point 34).

## Addendum 9, 2026-11-23 later afternoon

The operator uploaded NXP AN14581 (TSN and PTP benchmarking on i.MX 9x, Rev. 2.0) at 14:51 and AN14542
(NETC virtualization on i.MX 95, Rev. 4.0) at 14:59, and at 15:02 asked for both to be written up.

Registered: CL-RELAYCASE-0046 and CL-RELAYCASE-0048 (facts read from the two notes); CL-RELAYCASE-0047
(socket timestamp options and ioctls as a fine clock for a proxy holding a socket); CL-RELAYCASE-0049
(System Manager able to give the Cortex-M7 nothing and deny the Cortex-A control of it);
CL-RELAYCASE-0050 (module kernel without the NETC timer driver, Ethernet still working). No rulings.

Proposals of this session, in DESIGN-security.md: R20 refuses the socket timestamp options and ioctls
and fences the PTP clock device; K11 keeps the Ethernet with the Cortex-A without NETC virtualization,
runs nothing on the Cortex-M7, and lists the drivers left out of the module kernel (uio, vfio, M7
remoteproc and RPMSG, audio, ADC, and the NETC timer if time is coarse); open point 36 recommends coarse
time steps over PTP for the modules.

Done: DESIGN-security.md header paragraph, R20 (dated paragraph, two bullets), K11 (time bullet revised,
three bullets added), open points 36 and 37. Drawings not changed. That the I350 timestamps PTP in
hardware is the session's belief, not checked, and is marked so in open point 36.

Open items (28 to 30 of Addendum 7 and 32 of Addendum 8 stand; 31 is replaced):

31. Commit: DESIGN-security.md, this handover, the layout file, and the register logs for RL-JGDMS-0227
    to RL-JGDMS-0230 and CL-RELAYCASE-0032 to CL-RELAYCASE-0050 (with the new CL-RELAYCASE-0030 line).

33. For the operator: module time by PTP or coarse steps (open point 36); the System Manager
    configuration (open point 37).

## Addendum 10, 2026-11-23 15:15

The operator uploaded NXP UG10210 (i.MX 95 Hardware Design Guide, Rev. 2.0) at 15:08 and at 15:14 asked
for it to be written up, and what else to look for in documentation.

Registered: CL-RELAYCASE-0051 and CL-RELAYCASE-0052 (facts read from the guide: the crystal-only 24 MHz
reference and other clock inputs; the System Manager's always-on domain, logical machines, audio pins,
tamper pin and thermal models); CL-RELAYCASE-0053 (a console debug monitor in the EVK System Manager
builds, from recall); a new CL-RELAYCASE-0049 line citing the guide. No rulings.

Correction to this session's own advice: at 14:30 and in Addendum 8 it proposed a
temperature-compensated or oven-controlled oscillator on each module as a way to close CL-RELAYCASE-0045
at its source; the guide says the i.MX 95 takes a crystal only. K10 and open point 25 are revised to say
so.

Done: DESIGN-security.md header paragraph, K10 (speed-not-temperature bullet), K11 (sensor bullet, new
System Manager pins bullet, driver list), open points 23, 25, 36 and 37. An edit script left a doubled
bullet marker on three K11 and K10 bullets; fixed before this note.

Open items: 28 to 30 of Addendum 7, 32 of Addendum 8 and 31 and 33 of Addendum 9 stand, with 31 now covering CL-RELAYCASE-0032 to CL-RELAYCASE-0053.

## Addendum 11, 2026-11-23 16:30

The operator uploaded the SGET SMARC Design Guide 2.0 at 15:34, then at 15:38 the i.MX 95 industrial
data sheet, the i.MX95_1P21N errata, the Intel I350 datasheet and a link to Arm document 101381 r4p0,
asking for a Sonnet subagent to capture the relevant information. The subagent answered a fixed question
list with page citations; its report is /home/claude/research/doc-findings.md in the session's cloud
workspace (not on the operator's disk). This session checked its main points against the extracted text
before registering them. The Arm page could not be reached (support.arm.com returns a script shell and
is blocked by the egress proxy); nothing from it is recorded. At 16:27 the operator asked for everything
to be written up.

Registered: CL-RELAYCASE-0054 (SMARC 2.0 pins and boot select), CL-RELAYCASE-0056 (i.MX 95 operating
points, sensors, crystal, revisions), CL-RELAYCASE-0057 (errata), CL-RELAYCASE-0058 (I350 manageability,
NVM, SR-IOV, proxying, 1588, power); untested CL-RELAYCASE-0055 (RTC against the 24 MHz crystal as a
module thermometer), CL-RELAYCASE-0059 (I350 NVM protection against the host driver), CL-RELAYCASE-0060
(no /dev/mem, no mapped system counter); a new CL-RELAYCASE-0041 line. No rulings.

Done: DESIGN-security.md header paragraph; K1 card bullet; K4 boot-source bullet; K11 fixed-frequency
bullet, M7 errata, driver list; K12 NVM-at-commissioning bullet; K15 root-port check; R20 RTC and
memory-mapped counter bullets; open points 25, 32, 36 and 37 revised, 38 and 39 added.

Open items: 28 to 30 of Addendum 7, 32 of Addendum 8 and 33 of Addendum 9 stand; 31 now covers CL-RELAYCASE-0032 to CL-RELAYCASE-0060.

34. For the operator: the BOOT_SEL2# strap (open point 38), which refines RL-JGDMS-0219.

35. Arm 101381 r4p0 as a PDF, and the other documents in open point 39.

## Addendum 12, 2026-11-23 20:30

At 19:27 the operator supplied Arm's DSU-110 manual (101381 r4p0), copied from Arm's website; it covers
Armv9 clusters and does not apply to the i.MX 95. At 20:17 and 20:24 he supplied the original DSU manual
100453 at r0p2 and r4p1. The subagent's report was sent to him at 19:28 as doc-findings.md. On being
told that L3 partitioning would matter only if a module hosts proxies of more than one service, he wrote
at 20:29: "That could be useful, it allows more untrusted third parties." Taken as the go-ahead to write
up; the remark is quoted in the design, not registered as a ruling.

Registered: CL-RELAYCASE-0061 (the i.MX 95 uses the original DSU), CL-RELAYCASE-0062 (cluster registers
closed to EL0, EL1 writes gated by EL3 and EL2), CL-RELAYCASE-0063 (L3 partitioning from r1p0); untested
CL-RELAYCASE-0064 (the i.MX 95's DSU revision has partitioning) and CL-RELAYCASE-0065 (the snoop filter
is not partitioned). No rulings.

Correction to this session's own advice: at 19:27 it said from recall that the original DSU partitions
its L3 through scheme IDs; the r0p2 manual shows CLUSTERPARTCR unused in that revision, and r4p1 shows
partitioning was added in r1p0.

Done: DESIGN-security.md header paragraph; R16l (services sharing a module: L3 partitions fixed by
firmware, per-service JDK copies, per-service link scheduling, what stays shared); K11 cluster-registers
bullet; open point 39 revised; 40 added.

Open items: as in Addendum 11, with 31 now covering CL-RELAYCASE-0032 to CL-RELAYCASE-0065, and:

36. For the operator: whether modules host more than one service (open point 40); the i.MX 95's DSU
    revision (CL-RELAYCASE-0064).

## Addendum 13, 2026-11-23 21:00

Registered: RL-JGDMS-0231 (proxies sharing a module are grouped by the identity providing their
services), from the operator's messages at 20:29 and 20:58; RL-JGDMS-0232 (at most three server
certificate identities per module, two cores each), from 20:59. The 20:29 remark quoted in Addendum 12
is now part of RL-JGDMS-0231's note.

Proposals of this session in the revised R16l: the fourth L3 way group goes to a scheme ID no core uses,
since a shared group would let identities evict each other's lines; the grouping key is the server
certificate identity the relay authenticated, never what the proxy or codebase states; partitions change
only with a re-image, and pass between identities only after scrubbing; each identity gets its own
Ethernet rings with interrupts on its own cores.

Done: DESIGN-security.md header paragraph, the case-topology ruling list, R16l rewritten by identity,
open point 40 revised (grouping and count settled; partitioning on the part, kernel housekeeping,
placement and hand-over still open). An edit script's header anchor failed once on a wrapped line;
nothing was written by the failed run.

Open items: as in Addendum 12, with 31 now also covering RL-JGDMS-0231 and RL-JGDMS-0232, and 36 narrowed to the rest of open point 40 and CL-RELAYCASE-0064.

## Addendum 14, 2026-11-23 21:10

At 21:01 the operator asked whether the cores must be pinned (yes: a core's scheme ID fixes its
partition) and suggested four identities rather than leave a way group unused; the session showed six
cores do not divide four ways and that a core with no private group falls back to group 0. At 21:03 he
proposed one core per identity with the remaining three for the system, and at 21:06 confirmed that an
identity's JVMs share its core, that Leyden lets one identity's JVMs share memory, and that each
identity needs its own user.

Registered: RL-JGDMS-0233 (three identities, one core each, three system cores with the fourth
partition), RL-JGDMS-0234 (memory shared only within an identity, through Leyden), RL-JGDMS-0235 (an
operating-system user per identity, at least three per module); RL-JGDMS-0232 marked superseded by
RL-JGDMS-0233.

Proposals of this session in the rewritten R16l: cpusets per identity and for the system, the cgroup
filesystem hidden and sched_setaffinity refused; each identity's Ethernet rings with interrupts on a
system core of its own; a separate copy per identity of the JDK files and the AOT cache, since one file
mapped by several identities shares page-cache pages; a uid per JVM within a group per identity; R16b's
spreading of instances over cores dropped within an identity.

Done: DESIGN-security.md header paragraph, case-topology ruling list, R14, R16l rewritten, open point 40
revised.

Open items: as in Addendum 13, with 31 now also covering RL-JGDMS-0233 to RL-JGDMS-0235 and the RL-JGDMS-0232 status line.

## Addendum 15, 2026-11-23 21:15

Registered: RL-JGDMS-0236 (JDK memory never shared between processes of different uids), from the
operator's reply at 21:09 to the session's proposal of a uid per JVM within a group per identity, which
it replaces. With RL-JGDMS-0234, an identity's JVMs therefore share one uid; R14's "own uid" now means
one per identity.

Proposal of this session: processes of one uid cannot trace or read each other (Yama ptrace scope 3,
syscall filter refusing ptrace and process_vm_readv/writev).

Done: DESIGN-security.md header paragraph, case-topology ruling list, R14, the R16l user-identities
bullet, open point 40.

Open items: as in Addendum 14, with 31 now also covering RL-JGDMS-0236.

Note, 2026-11-23 21:12: the operator agreed at 21:11 to the ptrace proposal of Addendum 15; registered as RL-JGDMS-0237 and cited in R16l. Item 31 now also covers RL-JGDMS-0237.

Note, 2026-11-24 14:20: the operator confirmed he sent the earlier reply to Paul (his scoped-container
email of 2026-11-21: three questions, DirtyChai's extra checks, hardware update), closing that open
item. On 2026-11-23 at 21:11 the session drafted a link-free design update to Paul answering his first
reply's points (the split, the four attacks, interpreter-only) with the case, module sharing, timing and
management paths; it exists only in the conversation, and sending is the operator's.

## Addendum 16, 2026-11-25

On 2026-11-25 the operator asked whether memory could be segregated between identities; the session set
out capacity limits, separate kernels (System Manager logical machines or a partitioning hypervisor),
DRAM bank partitioning and bandwidth regulation, then at his request the risk of not doing each. He
noted that each JVM can have a memory limit (the session added a kernel cgroup limit behind it), and
that a breakout risks the attacker's identity for no gain, so its best option is timing from outside;
the session agreed with three caveats (cheap identities, silent exploits, a shared module raising the
prize) and pointed to the relay as the remaining shared component. He then ruled one identity per
module.

Registered: RL-JGDMS-0238 (one server certificate identity per module; L3 partitioning only between the
system and the proxies); RL-JGDMS-0231, RL-JGDMS-0233 and RL-JGDMS-0235 marked superseded by it.
RL-JGDMS-0234, RL-JGDMS-0236 and RL-JGDMS-0237 stand.

Proposals of this session in the rewritten R16l: four proxy cores with three way groups and two system
cores with one (the split is the operator's, open point 40); each proxy JVM pinned to one core; Ethernet
interrupts on system cores; JVM limits plus a cgroup memory.max per JVM under one proxy cgroup. Offered
but not written: a section on the relay as a resource shared by every module's identity (outbound
messages on the tick, per-identity queues and workers on pinned relay cores, the relay's L3
partitioned).

Done: DESIGN-security.md header paragraph, case-topology ruling list, R14, R16l rewritten as one
identity per module, open point 40 rewritten.

Open items: as in Addendum 15, with 31 now also covering RL-JGDMS-0238 and the three status lines; 36 replaced by open point 40 (the core and way-group split, CL-RELAYCASE-0064) and the unwritten relay section.

## Addendum 17, 2026-11-25 14:00

At 14:00 the operator wrote "You proposals are sound and accepted." The session took this as accepting
the R16l proposals of Addendum 16 (layout, pinning, interrupts, memory, placement) and registered them
as RL-JGDMS-0239; the relay section, which had only been offered, was written as proposals (K16), not as
a ruling.

Registered: RL-JGDMS-0239; untested CL-RELAYCASE-0066 (the relay's Xeon 6 has Intel cache and bandwidth
allocation with enough classes of service, from recall).

Done: DESIGN-security.md header paragraph, case-topology ruling list, R16l marked as ruled, open point
40 settled in part, K16 added after K15 (outbound messages on the tick, per-module workers on reserved
relay cores, relay L3 and bandwidth allocation, QAT queues and the outward link per module,
constant-time cryptography, shared throughput), open point 41 added. K16 was first inserted before the
R-section's open points by an anchor error and moved before this note.

Open items: as in Addendum 16, with 31 now also covering RL-JGDMS-0239 and CL-RELAYCASE-0066; the relay section is written (open point 41), and whether its proposals stand is the operator's.

## Addendum 18, 2026-11-25 14:15 wrap-up

At 14:10 the operator accepted K16 ("Your proposal is accepted."), registered as RL-JGDMS-0240 and cited
in K16 and the header paragraph, and asked for the wrap-up.

Claims settled from sources read at this wrap-up: CL-RELAYCASE-0007 has a new line from OpenJDK master
source (init.cpp, interpreter.cpp, templateInterpreter.cpp, stubs.cpp, codeCache.cpp,
memoryReserver.cpp, os_linux.cpp, read from GitHub); it was read in upstream, not in the DirtyChai fork.
CL-RELAYCASE-0012 has a new line from the Linux kernel's arch/arm64/kernel/proton-pack.c; Arm's own page
was not read. No other claim was settled: those added on 2026-11-23 and 2026-11-25 wait on bench tests,
vendor answers or documents named in open points 25, 37, 39, 40 and 41.

Uncommitted, all on the operator's disk: docs/relay-isolation/DESIGN-security.md, this handover,
docs/LAYOUT-proxy-host-board-2026-11-23.html, and the register logs for RL-JGDMS-0227 to RL-JGDMS-0240
and CL-RELAYCASE-0032 to CL-RELAYCASE-0066 with the status lines on CL-RELAYCASE-0007, -0012, -0030,
-0041 and -0049 and on RL-JGDMS-0231, -0232, -0233 and -0235. The drawings were not changed after
2026-11-23 midday, so they still show the network carrier and outward NIC but not module sharing,
pinning or K16.

Open items for the next session:

37. The design update to Paul (drafted 2026-11-23 21:11, in the conversation only) predates
    RL-JGDMS-0238 to RL-JGDMS-0240: its "sharing a module" section describes three identities per module
    and must be revised to one identity per module, with the relay as the shared resource, before it is
    sent.

38. Open points 38 (BOOT_SEL2# strap), 40 (bench checks of L3 partitioning) and 41 (relay cores, classes
    of service, tick latency, QAT, rate limits) are the operator's or the bench's.

39. The drawings could show the module's core and cache split and K16 if the operator wants them
    updated.

Note, 2026-11-25 14:18: at the operator's request, K15's MCIO cables bullet in DESIGN-security.md gained one sentence stating why SMBus is cut (a BMC with SMBus to an I350 can use its ports unseen by the relay), citing CL-RELAYCASE-0040 and CL-RELAYCASE-0058. Nothing else changed.
