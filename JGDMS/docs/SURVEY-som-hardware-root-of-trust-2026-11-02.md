claude-sonnet-5-5
# Survey: hardware root of trust on the candidate ARM SOMs (2026-11-02; searched 2026-11-01)

Method note: WebFetch returns a small-model summary of each page, not the page. "Opened" means fetched
through WebFetch (summary) or, for PDFs, text extracted locally with pdftotext. Quotes are short. Anything
marked "(mine)" is my inference, not a document statement. No gap was filled from memory.

## Summary
- Candidate list taken from PROXY_HOST_BOARD_REQUIREMENTS.md section 6 and 6.1: Tria OSM-LF-IMX95, Toradex
  Aquila iMX95, congatec conga-SMX95, Boardcon PICOT536 (Allwinner T536), PHYTEC phyCORE-AM64x, TQMa65xx, iWave
  AM62A, Ezurio CarbonAM62/67. Only the i.MX95 family (NXP) was examined in depth; the others have little or none.
- i.MX95 documents say the SoC has an EdgeLock Secure Enclave (ELE) offering secure boot, "device unique identity
  and keys" and "device attestation", with AHAB secure boot, and Toradex documents secure debug and a closed
  lifecycle. None of the documents I could open say HOW attestation is done: no API, no key, no certificate
  chain, no named root CA, no statement of who can call it. That detail is probably in account-gated NXP documents.
- Toradex says (page of 14 Oct 2026) that its own secure-boot support for Aquila iMX95 is NOT yet supported:
  every secure-boot row is "under development" or "planned" (Early Access). The SoC capability exists; Toradex's
  software for it does not yet ship.
- A discrete TPM 2.0 (ST33KTPM2I3WBZA9) is an assembly option on Aquila iMX95; its full documentation is under
  an ST NDA. Tria's page mentions no TPM. Toradex's firmware-TPM (OP-TEE) row is "under development".
- Arm architecture: a Secure-world/EL3 monitor can use the PMU, and EL3 can trap normal-world PMU register access
  (MDCR_EL3.TPM). I could confirm this only from a third-party mirror of Arm's register page and TF-A docs; the
  Arm pages themselves rendered empty. No SOM vendor document says it reads PMU counters from the secure world.
- Standards consumers exist (PSA attestation, RFC 9334, DICE, SPIRE tpm_devid/x509pop), but SPIRE has no
  attestor for ELE, secure elements or PSA tokens; tpm_devid proves key residency in a TPM, not firmware state.

## Table (SoC/module | feature | URL | date | opened? | says / does NOT say)
| Item | Feature | URL | Date | Status | Says / does not say |
|---|---|---|---|---|---|
| i.MX95 (NXP) | ELE, secure boot, attestation | https://www.nxp.com/products/nxp-product-information/nxp-product-programs/edgelock-secure-enclave:EDGELOCK-SECURE-ENCLAVE | docs dated 2024-2026; footer 2026 | opened (summary) | Lists "device attestation", unique identity and keys, secure boot, secure debug, lifecycle, EdgeLock 2GO for "keys and digital certificates". Gives no mechanism, no cert chain, no root CA. Does not say which items are gated. |
| i.MX95 | ELE overview deck (six-pack) | https://www.nxp.com/docs/en/training-presentation/EdgeLock-Secure-Enclave-Technology-Six-Pack.pdf | March 2024 | opened (pdftotext) | ELE has "its own CPU core, immutable memory (ROM)", "physically isolated". Feature table has Core and Advanced profile columns with rows "Device Unique Identity & Keys" and "Device Attestation"; the text extract lost the check marks, so which profile has which is NOT known. Marketing level only. "Supported devices" lists i.MX9x. |
| i.MX95 | product page, security docs | https://www.nxp.com/products/i.MX95 | 2026; first 100k of 141k chars read | opened (summary) | Advanced Profile: "post-quantum hardware root-of-trust", hybrid ML-DSA/ECDSA for NXP-signed enclave firmware. No security reference manual listed. Attestation and device identity are not covered on this page. |
| i.MX95 | ELE command line | https://spsdk.readthedocs.io/en/latest/examples/ele/nxpele.html | undated | opened (summary) | get-info returns SoC ID, UUID, life cycle (example "OEM_CLSD"), "Attest API version"; fuse read/write ("one-time"). No attestation command documented, no certificate or debug-auth coverage. Shows a lifecycle with an OEM-closed state exists. |
| i.MX95 | secure boot (AHAB) in OEM fuses | NXP white paper TQ-IMX95-WHITEPAPER, AN14954, AN14579 (listed on product page) | 2026-04-20 / 2026-02-27 / 2025-02-24 | search/list only, NOT read | Not read. AN14954 (fuse usage) is listed "Public" but I did not open it. So whether the AHAB chain is rooted in OEM-controlled SRK fuses is NOT confirmed from NXP text. Toradex says generically that the public key is stored via "one-time eFuse" operations. |
| Toradex Aquila iMX95 | datasheet security | https://docs.toradex.com/117671-aquila_imx95_datasheet.pdf | Rev 0.6, 28-Sep-2026, "Preliminary" | opened (pdftotext) | Repeats NXP wording: enclave supports "secure boot, runtime attestation, trust provisioning, key management". Section 1.6.13: TPM 2.0 ST33KTPM2I3WBZA9 as assembly option; "Complete documentation is available only under an NDA". Datasheet is preliminary. No attestation procedure, no device cert. |
| Toradex Aquila iMX95 | secure boot status | https://developer.toradex.com/torizon/security/secure-boot-on-torizoncore/ | page updated 14 Oct 2026 | opened (summary) | Bootloader/kernel/rootfs authentication, OP-TEE, OP-TEE firmware TPM, fusing: all "under development"; container authentication "planned"; "Early Access". Data-at-rest TPM backend needs external or firmware TPM ("the SoC has no TPM"). Page has no row for measured boot or debug. |
| Toradex (i.MX9) | secure boot rooted in ELE; secure debug | https://developer.toradex.com/linux-bsp/os-development/security/introduction-to-meta-toradex-security/ ; https://github.com/toradex/meta-toradex-security/pull/198 | not seen | search snippets only | Snippet: chain "rooted in the on-chip EdgeLock Secure Enclave"; secure debug via ELE needs a credential signed with the secure-boot keys once closed. Not opened; so not confirmed. |
| Tria OSM-LF-IMX95 | security | https://www.tria-technologies.com/product/tria-osm-lf-imx95/ | no date; latest item 17 Aug 2026 | opened (summary) | Names the EdgeLock enclave (secure boot, runtime attestation, key management). No TPM, no secure element, no security document. Datasheet linked: https://www.tria-technologies.com/wp-content/uploads/Datasheets/OSM/TRIA-OSM-LF-IMX95.pdf?tria=5252643971 - NOT opened by me. |
| congatec conga-SMX95 | security | https://www.congatec.com/fileadmin/user_upload/Documents/Datasheets/conga-SMX95.pdf | PDF dated 17 Nov 2025; text says "Draft Rev. 1.0, November 17th, 2025" | opened (pdftotext) | Lists "NXP EdgeLock 2GO key management", "ARM TrustZone", "High Assurance Boot support", U-Boot. Inline ECC, 2x GbE listed. No TPM mentioned. No attestation text. A draft. |
| i.MX95 firmware | EL3 monitor present | https://docs.u-boot.org/en/v2026.04/board/toradex/smarc-imx95.html | v2026.04 | opened (summary) | Boot flow SPL, ATF (TF-A BL31 v2.12 from NXP imx-atf), U-Boot; ELE firmware 2.0.2 loaded. Says nothing on secure boot, fusing, or PMU. That BL31 runs at EL3 is implied, not stated (mine). |
| Allwinner T536 (Boardcon PICOT536) | security | https://mt-system.ru/upload/iblock/ac5/se37hrb6v00gti0e3uqkcy9gxddvyb89.pdf | 2024 copyright | search snippet only; third-party host; NOT opened | Snippet: crypto engine, Security ID, TrustZone, secure boot, 2048-bit eFuse. No official Allwinner datasheet found; no eFuse map. Nothing on attestation or device certificates. |
| TI AM64x (PHYTEC, TQMa65xx, iWave, Ezurio class) | secure boot | https://software-dl.ti.com/processor-sdk-linux/esd/AM64X/08_05_00_21/exports/docs/linux/Foundational_Components_Secure_Boot.html | tag 08_05_00_21, 2021 copyright, logs May 2022 | opened (summary) | ROM checks signed initial image on High Security devices; customer key `custMpk.pem` replaces a dummy key. No fuses, measured boot, attestation, identity cert, or debug lock text. HS-SE "customer keys burnt into eFuses": snippet from MCU+ SDK page (search only). Old SDK. TI's device-identity/HSM docs not found. I did not map any module vendor's page. |

## Standards and SPIRE support
- PSA Certified Attestation API 2.0.0 (Arm IHI 0085), https://arm-software.github.io/psa-api/attestation/2.0/IHI0085-PSA_Certified_Attestation_API-2.0.0.pdf,
  opened (pdftotext): "Release Quality: Final", May 2026 history entry; "defines interfaces to provide an attestation
  service for the Root of Trust"; refers to RFC 9783. It is an API/profile for a PSA Root of Trust; it names no SoC.
  I found no statement that i.MX95 ELE or any candidate implements it (mine: it would need a vendor PSA RoT, e.g. TF-M/Trusted Services, not confirmed).
- RFC 9783 (PSA Attestation Token), https://www.rfc-editor.org/rfc/rfc9783, opened (summary). Informational, Independent
  Submission, 2025, "not a standard nor a product of the IETF". EAT profile, COSE_Sign1; claims instance ID, implementation ID,
  boot seed, software components (measurement hashes), security lifecycle; attestation key may be certified by a manufacturer CA (x5chain).
- RFC 9334 (RATS architecture), https://www.rfc-editor.org/rfc/rfc9334, opened (summary; last ~18k chars of 118k unread). Informational, 2023.
  Roles Attester, Verifier, Relying Party; Evidence and Attestation Results. An architecture, not a protocol.
- TCG DICE, https://www.trustedcomputinggroup.org/technologies/dice, opened (summary). "Lightweight hardware Root of Trust" deriving
  identities from measured layers; usable with or without a TPM. Specs named: Layering Architecture (a search result showed Rev 0.19,
  2020, unopened), Attestation Architecture (search snippet "updated Jan 29, 2026", unopened). Page gives no revisions. No candidate
  vendor document I opened says its boot ROM implements DICE.
- SPIRE server node attestors (https://spiffe.io/docs/latest/deploying/spire_server/, opened, summary, undated "Latest"): aws_iid, azure_imds,
  azure_msi, gcp_iit, join_token, k8s_psat, sshpop, tpm_devid, x509pop. No status label on any. Nothing for ELE, a secure element, PSA tokens or DICE.
- tpm_devid (https://github.com/spiffe/spire/blob/main/doc/plugin_server_nodeattestor_tpm_devid.md, opened, summary): needs a DevID
  certificate provisioned out of band, DevID CA bundle and TPM manufacturer (EK) CA bundle; proof of possession plus proof of residency
  in a TPM. The page does NOT describe quote or PCR verification (so it does not check firmware/OS measurements), states no maturity
  level, and warns validation does not establish the accuracy of certificate claims.
- x509pop (https://github.com/spiffe/spire/blob/main/doc/plugin_server_nodeattestor_x509pop.md, opened, summary): chain to trusted CA plus
  signature challenge; "says nothing about firmware or software" and nothing on key storage. (mine) Using it, the issuer of the
  certificate would have to do the attesting out of band; SPIRE would not.
- Arm PMU: MDCR_EL3 fields, third-party mirror https://arm.jonpalmisc.com/latest_sysreg/AArch64-mdcr_el3 (opened, summary; mirror says it is not
  Arm): SPME controls counting in Secure state and EL3; TPM bit traps EL2/EL1/EL0 PMU register accesses to EL3; SCCD, MPMX exist.
  TF-A guidance https://trustedfirmware-a.readthedocs.io/en/latest/process/security-hardening.html (opened, summary): SPME resets to 0, "by default general
  events should not be counted in the Secure world". (mine) The mechanism exists in the architecture; whether the A55 in i.MX95/T536/AM64x
  implements the needed PMUv3 features, and whether the vendor's firmware exposes counters to a monitor, is not documented in anything I read.

## Behind a login or NDA (could not read)
- NXP: Secure Boot white paper TQ-IMX95-WHITEPAPER (account); AN14898 key import; AN15018 fast boot; AN14968, AN14741, AN14586 EdgeLock 2GO provisioning
  (all account); AN14785 HSM using SPSDK; AN14615 lifecycle transition; AN14579 secure debug (account). Not login: AN14954 fuse usage, AN15034 SCEP,
  AN14721 TRDC, AN14641 (listed Public; I did not open them). The i.MX95 reference manual is listed public (not opened); no security reference manual listed.
  The page's "Secure" tab ("secure files") was unreadable; ELE API/attestation documentation may be there. EdgeLock 2GO availability: "contact NXP sales".
- STMicroelectronics ST33KTPM2I full documentation: NDA (per Toradex datasheet).
- Tria TechDoC and Support Center: "registered customer" (page wording); Tria datasheet not opened.
- Arm DDI0487 and register pages: dynamic pages returned nothing to the fetcher; not a login, but unreadable here.

## Could not confirm
- Whether any candidate's boot chain is rooted in fuses the OEM programs and controls (NXP SRK fuses): not read from NXP; Toradex says generic one-time eFuses.
- A device-unique key and certificate chain that the ELE (or any module) can sign an attestation with: the term appears; the key, certificate, and root CA do not.
- Whether a normal-world or OEM program can call ELE attestation, and whether the output is a PSA token, DICE, or a proprietary format.
- Measured boot and a signed quote: only via the optional discrete TPM (documentation NDA) or Toradex firmware TPM (not shipped). No module page documents a PCR/quote flow.
- Lifecycle and debug: "OEM_CLSD" shown in an NXP tool example; secure debug on ELE appears in snippets; JTAG lockdown on Toradex/Tria not read. Aquila datasheet lists a JTAG interface (section 5.9) - contents not read.
- PMU read from EL3 on these SoCs; any vendor statement about it.
- Tria OSM-LF-IMX95 security detail beyond marketing; congatec TPM presence (none found); the Boardcon/Allwinner, PHYTEC, TQ, iWave, Ezurio documents.
- TI AM64x device identity/HS-SE device certificate: not found.
- Any production SPIRE plugin for ELE or secure elements: none found in the SPIRE docs I opened.

## What I did not search
- Variscite, Compulab, Forlinx, ADLINK, SECO, Ezurio, iWave datasheets; Layerscape, Zynq UltraScale+, Renesas, MediaTek, NXP discrete EdgeLock SE05x.
- SPIRE plugin lists in the SPIRE repository beyond the pages named above, SPIRE issues/roadmap, third-party SPIRE plugins (e.g. for PSA), Keylime, and fTPM implementations.
- TF-M, Trusted Services, OP-TEE attestation services; Arm Cortex-A55 TRM (PMU/MDCR behaviour); GlobalPlatform; SESIP certificates.
- Pricing, lead time, lifecycle statements.
