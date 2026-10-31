claude-sonnet-5-5

# Landscape survey: high-security stacks comparable to JGDMS + DirtyChai (as of 2026-11-01)

HOW THIS WAS DONE: I ran 15 web searches (mode "extended"). I read only the search-result summaries
returned by the search tool. I did NOT open or fetch any page. Every date below is as reported in
those summaries; none was checked against the project's own page. Several results were
secondary blogs; those are marked SINGLE-SOURCE or low confidence. Page text was treated as data.

## Summary

Closest: no project found that combines the whole stack (permission model + canonical wire format
+ workload identity + hardware-level separation). Searches for a retained-SecurityManager fork other
than DirtyChai found none. For Jini/River, the results name only JGDMS as a living fork; Apache River
itself is reported inconsistently (Wikipedia: retired 2022; board minutes: still reporting).

Nearest by idea, one area each (none is a substitute):
- Capability OS/hardware: seL4 / CHERI-seL4 and CHERI hardware, Genode (most active OS-level capability work).
- Timing isolation: UNSW/Trustworthy Systems time-protection research, fence.t on CVA6 (research, not product).
- Tenant isolation product: Oxide (hardware rack, strongly active); Nitro and confidential VMs (widely used,
  with published attacks).
- Code to the data: GraalVM UNTRUSTED sandbox policy, WASM runtimes, Snowflake clean rooms (custom Python code).
- Identity / expressions: SPIFFE/SPIRE (graduated CNCF), CEL in Kubernetes (GA).
Furthest: Fuchsia, OpenPiton/OpenSPARC (no 2026 information found), Cap'n Proto (serialization/RPC only,
no permission or identity layer found in what I read).

## Table (confidence: H = vendor/standards-body-style source or multiple results agree; M = one reasonably
authoritative result; L = single blog/aggregator)

| Project | Area | URL | Date (as reported) | Status and how I know | Relation to stack | Conf |
|---|---|---|---|---|---|---|
| JEP 486 / Oracle JDK 24 docs | 1 | https://docs.oracle.com/en/java/javase/24/security/security-manager-is-permanently-disabled.html | JDK 24 (2025) | n/a, platform fact: SM permanently disabled, policy file and remote code downloading removed | Premise for DirtyChai; confirms upstream removed what you retain | H |
| JGDMS | 1 | https://github.com/pfirmstone/JGDMS | 2026 blog reports it | ACTIVE (inferred only from a 2026 blog; I did not see commit dates) | The stack itself | M |
| Blog "Security Baked Into the JVM: why fork Apache River and OpenJDK?" | 1 | https://blog.frankel.ch/security-baked-into-jvm/1/ | 2026 (day not seen) | n/a, third-party article | Independent write-up of JGDMS+DirtyChai; says JGDMS on bare OpenJDK is not supported | L |
| DirtyChai | 1 | https://github.com/pfirmstone/DirtyChai | repo, 2026 | ACTIVE per your own repo; result gave no date | The stack itself | M |
| Apache River | 1 | https://whimsy.apache.org/board/minutes/River.html | undated in summary | UNCLEAR: board minutes say parallel work happens in JGDMS and no JGDMS code is in River; Wikipedia says retired early 2022 | Parent project; not a successor | L |
| WildFly (security-manager subsystem) | 1 | https://docs.wildfly.org/37/wildscribe/subsystem/security-manager/index.html | WildFly 37 | ACTIVE project, but extension "will be removed"; not supported with Jakarta EE 11 | Shows ecosystem dropping SM, not keeping it | M |
| OpenSearch RFC "Replace Java Security Manager" | 1 | https://github.com/opensearch-project/OpenSearch/issues/1687 | old issue | unknown (issue; project ACTIVE generally, not checked) | Notes GraalVM sandbox lacks an authorization layer; suggests process isolation | M |
| GraalVM sandboxing (ISOLATED/UNTRUSTED policy) | 1,4 | https://graalvm.org/jdk25/security-guide/sandboxing | JDK 25 docs | ACTIVE (docs exist for jdk23/24/25 trees) | Isolation + resource limits + speculative-execution mitigations for guest code; no permission model like yours | M |
| gngr issue "Roadmap after SM removal" | 1 | https://github.com/gngrOrg/gngr/issues/285 | undated | unknown | Another project facing SM removal; nothing found on a fork | L |
| seL4 / CHERI-seL4 (CTSRD, CHERI Alliance) | 2 | https://github.com/CTSRD-CHERI/seL4 | PR merged 2026-10-19 (RVY 0.9.10 upgrade) | ACTIVE; Cambridge says experimental, not verified, not upstream; Morello variant unmaintained | Capability kernel + capability hardware; closest hardware-capability analogue | M |
| CHERI (survey) | 2 | https://dl.acm.org/doi/10.1145/3833870 | 2026 | n/a, survey | Background on CHERI | M |
| CHERIoT first silicon (SCI ICENI) | 2 | https://cheriot.org/silicon/2026/03/04/cheriot-first-silicon.html | 2026-03-04 | ACTIVE; silicon announced, up to 250 MHz | Capability hardware in microcontrollers; your plan uses in-order Arm boards instead | M |
| lowRISC Sonata / Sunburst | 2 | https://github.com/lowRISC/sonata-system | v1.1 release listed; date not seen | ACTIVE (releases page lists v1.1; date unseen) | CHERIoT dev board | M |
| Arm Morello | 2 | (via CHERI-seL4 page above) | n/a | Morello-specific CHERI-seL4 variant "no longer maintained"; Morello program status not confirmed | Prototype capability Arm hardware | L |
| Genode / Sculpt OS | 2 | https://genode.org/download/sculpt | Sculpt 26.10 on 2026-10-30; Genode 26.08 on 2026-08-25 | ACTIVE; recent releases | Capability-based component OS; now own microkernel (per Phoronix) | H |
| Fuchsia | 2 | none | none | COULD NOT CONFIRM | Capability OS | none |
| Capsicum (FreeBSD) | 2 | none | none | COULD NOT CONFIRM | Capability sandboxing | none |
| Endo / SES (Agoric) | 2 | https://github.com/endojs/endo | @endo/pass-style 1.8.1 on 2026-06-12 | ACTIVE by release date | Object-capability JS with a Capability Transfer Protocol; nearest to "capabilities over the wire" | M |
| Cap'n Proto | 2 | https://github.com/capnproto | 1.5.0 reported 2026-07-09 (Wikipedia infobox); 1.4.0 Mar 2026 | ACTIVE by release dates | Capability-style RPC and wire format; no identity or hardware layer seen | M |
| WebAssembly component model / WASI | 2,4 | none single source | WASI 1.0 "expected late 2026/early 2027" (one blog) | ACTIVE (indirect) | Capability-by-construction guests (Wasmtime deny-by-default FS, per a guide) | L |
| Oxide Computer | 3 | https://siliconangle.com/2026/10/09/oxide-computer-raises-445m-to-step-up-data-center-rack-production/ | 2026-10-09 ($445M Series D); Feb 2026 $200M | ACTIVE (funding news from several outlets) | Whole-rack own-hardware private cloud; physically separate machines, but not per-identity boards | H (funding), L (isolation claims; marketing) |
| AWS Nitro / Nitro Enclaves | 3 | https://dl.acm.org/doi/pdf/10.1145/3805690.3805729 | 2026 paper | ACTIVE service; paper reports LLC side-channel attacks on Enclaves | Dedicated-core isolation approach; shows shared cache still leaks | M |
| Apple Private Cloud Compute | 3 | https://www.ajayk.sh/blog/confidential-computing-tees-ai-agents-2026/ | blog says announced 8 June (year not stated) | ACTIVE (per blog) | Attested stateless compute; blog says it now also runs on Google TDX | L (single blog) |
| Intel TDX / AMD SEV-SNP | 3 | https://unmitigatedrisk.com/2026/04/confidential-computings-inconvenient-truth/ | 2026-04 | ACTIVE (shipping); blog reports interposer key extraction | Memory encryption against host; does not address co-tenant timing the way separate boards would | L |
| Arm CCA | 3 | none | none | UNCLEAR: sources conflict whether shipping | Arm-based realm isolation; directly relevant to your Arm choice | L |
| Time protection (UNSW Trustworthy Systems) | 3 | https://arxiv.org/abs/2310.17046 | 2023 paper | research; no 2026 item found | Proofs that time protection mechanisms work; the academic basis for your timing concern | M |
| fence.t on CVA6 (Wistoff et al.) | 3 | https://trustworthy.systems/publications/full_text/Wistoff_SGBH_20.pdf | 2020; DATE 2021 follow-up | research; no recent activity seen | Open-core hardware fix: temporal fence, ~2% overhead reported | M |
| OpenPiton / OpenSPARC | 3 | https://parallel.princeton.edu/openpiton/docs/micro_arch.pdf | ~2016 docs | COULD NOT CONFIRM current activity | Open silicon; no security use found | L |
| Snowflake Data Clean Rooms | 4 | https://docs.snowflake.com/release-notes/2026/other/2026-07-16-dcr | release notes 2026-03-05 and 2026-07-16 | ACTIVE by release notes | Provider code kept confidential from consumer, run inside clean room; "code to the data" analogue | H (vendor docs) |
| CEL in Kubernetes ValidatingAdmissionPolicy | 4 | https://www.pulumi.com/registry/packages/kubernetes/api-docs/admissionregistration/v1/validatingadmissionpolicy | GA in v1.30 | ACTIVE (feature GA) | CEL as a safe expression language with cost limits; same choice you made | M |
| Cloudflare Workers / Wasm edge platforms | 4 | https://zylos.ai/research/2026-02-05-webassembly-ecosystem-2026/ | 2026-02-05 | ACTIVE (blog description) | Code shipped to the edge in isolates/Wasm | L |
| SPIFFE / SPIRE | 5 | https://www.cncf.io/projects/spire/ | graduated 2022-08-22 | ACTIVE (graduated project; GitHub exists; 2026 release not seen) | Your workload identity layer | H |

## Combination (area 5)

I found no result describing a system that combines a capability-style permission model, a canonical
wire format, workload identity, and hardware-level isolation. The pieces exist separately:
Endo (capabilities + CapTP), Cap'n Proto (capability RPC), SPIFFE (identity), CHERI-seL4/Genode (capability
OS), Oxide/Nitro (hardware tenancy). This is a statement about what my 15 searches returned, not proof none exists.

## Widely reported vs single-source

Widely reported / vendor documented: JEP 486 effect, Genode releases, SPIRE graduation, Oxide funding,
Snowflake clean room release notes, CEL ValidatingAdmissionPolicy GA, GraalVM sandbox policy docs.
Single-source: the 2026 JGDMS/DirtyChai blog, Apple PCC on Google TDX, the interposer attack on TDX/SEV-SNP,
the Cloudflare/Deno item, Cloudflare Wasm usage percentages, WASI 1.0 timing, Cap'n Proto 1.5.0 date.

## Could not confirm

- Any fork or project other than DirtyChai that keeps the SecurityManager on JDK 24+; no agent- or
  module-based replacement project found (search returned only JEP/WildFly/OpenSearch material).
- Fuchsia status; Capsicum status; WASI 0.3 release facts; Arm CCA shipping status; Morello program status.
- Envoy/Istio CEL adoption; cel-go 2026 release; BigQuery clean rooms; Wasm-based UDF sandboxing.
- The Wikipedia "Cloudflare acquired Deno's team in October 2026" snippet: uncorroborated, not used in table.
- OpenPiton or OpenSPARC 2026 activity; any OpenPiton timing-isolation work.
- Last-commit dates for JGDMS, DirtyChai and Apache River (not read from the repositories).

## What I did not search

Per-tenant bare-metal Arm blade providers (e.g. Ampere/Arm bare-metal clouds); Google gVisor/Firecracker/
Kata; Intel SGX; Microsoft Azure confidential offerings; Java-specific tools such as Java agents, Panama/
module-based sandboxes, Jakarta/Quarkus plans; other Jini descendants (Rio, Blitz, etc.); OSGi; Wasmtime or
wasmCloud primary pages; Cloudflare Workers primary docs; V8 isolates internals; Spectre/Spectre-v2
mitigation research outside seL4; RISC-V open cores other than CVA6 (e.g. Rocket/BOOM security work);
SPIFFE with JWT-SVID adoption detail; DER/canonical-encoding comparators (ASN.1, CBOR deterministic
encoding, Protobuf canonicalization); conferences (seL4 Summit, USENIX) proceedings.
