claude-sonnet-5-5
# Survey: transient-execution exposure of the candidate SOM cores (2026-11-02)

Method: WebFetch returns a small-model summary, not raw text. For PDFs I ran pdftotext locally on the saved bytes and read the text myself (marked PDF-TEXT). OPENED = fetched and read; SEARCH-ONLY = seen only in a search snippet. "(mine)" = my inference. Quotes are short. No gap filled from memory.

## Summary
- Partly. Arm's own documents say in-order cores have "insufficient speculative execution" for the Spectre v1 cache attack (whitepaper v2.5, June 2020), and that any core absent from its table "has been analyzed and deemed not susceptible" (FAQ, Mar 2022). The Arm TF-A advisory (updated to 2018) names Cortex-A53 and A55 as not vulnerable to v2/v3. I could NOT open Arm's current per-core table, so A53/A55 status for v1, 3a, 4, BHB, SLS is by absence/secondary only.
- Still exposed (mine): a peer-reviewed 2020 paper claims A53 speculative leakage (SiSCloak) and says Arm confirmed a related class; ordinary cache timing (Prime+Probe) is architecture-independent; neither vendor SoC page I opened mentions Spectre/Meltdown at all.
- Real-time cores: M33 and R5F have in-order, operand-dependent divide; NXP says keys in ELE "cannot be exported"; ELE, not the M cores, is the vendor's key holder. M7 shares the 512 kB L3 and the DRAM with the A55s per the block diagram (mine).

## Table (core | variant | status | source | date | opened?)
| Core | Variant | Status | Source | Date | Opened |
|---|---|---|---|---|---|
| A53, A55 | Spectre v2 (CVE-2017-5715) | NOT AFFECTED (listed as "not vulnerable") | https://trustedfirmware-a.readthedocs.io/en/v2.5/security_advisories/security-advisory-tfv-6.html | 3 Jan 2018, upd. to 7 Jun 2018 | OPENED (summary) |
| A53, A55 | Meltdown v3 | NOT AFFECTED ("only Arm CPU vulnerable ... is Cortex-A75") | same | same | OPENED (summary) |
| A53, A55 | Spectre v1 (CVE-2017-5753) | NOT LISTED in the advisory ("no vulnerable patterns ... in upstream TF code"). Whitepaper: in-order cores lack enough speculation | whitepaper below | Jun 2020 | OPENED |
| A53, A55 | v3a, v4 | NOT LISTED. FAQ names v3a cores A15, A57, A72; v4 cores A57, A72, A73, A75 | FAQ below | 8 Mar 2022 | PDF-TEXT |
| A53, A55 | Spectre-BHB, SLS, Spectre-BSE | NOT LISTED in anything opened. Arm's per-core table not read. Linux treats A53/A55 as BHB "unaffected" (see Linux) | - | - | not opened |
| A53 | SiSCloak (speculative leak) | AFFECTED per paper abstract: "affected ... even if ... not support out-of-order" | https://arxiv.org/abs/2007.06865 | v2 17 Jul 2020 | OPENED (summary) |
| A53 | CVE-2020-13844 (OpenCVE lists A53) | SEARCH-ONLY, third-party | https://app.opencve.io/cve/CVE-2020-13844 | - | SEARCH-ONLY |
| Arm table | all variants, all cores | NOT READ: developer.arm.com/documentation/110280 redirects to support.arm.com, returned title only | https://support.arm.com/documentation/110280/latest | - | failed |

Arm documents opened (PDF-TEXT):
- Cache Speculation Side-channels v2.5, June 2020: https://documentation-service.arm.com/static/61f904cafa8173727a1b7286 . Says out-of-order cores are the likely exposure; "For Arm processors that perform their execution in-order, there is insufficient speculative execution" for the v1 approach. Defers to arm.com/security-update for the list. Names CSDB, SSBS, CSV3.
- Speculative Processor Vulnerability FAQ (102587_0101): https://documentation-service.arm.com/static/6287b49b41e28926d04306e8 , issue 0101 dated 8 Mar 2022. Says shorter-pipeline, in-order processors "do still use speculation" but attacks are "unfeasible"; "Any processor not on the table has been analyzed and deemed not susceptible"; Arm "cannot comment on architecture partner implementations". SLS section: risk "low", no practical exploit. SCSB: Arm not affected.
- Spectre-BHB whitepaper v1.8-r0p3: https://documentation-service.arm.com/static/679cb12427eda361ad4f1ffb , no date read. Says FEAT_CSV2 does not protect against BHB. It points to "Arm CPU" pages for affected cores; no A53/A55 named in the text I searched.

## Vendor statements per SoC
- NXP i.MX95 (Cortex-A55 x6): data sheet IMX95CEC Rev. 8, 7 Apr 2026 (PDF-TEXT) has no Spectre/Meltdown text. Search found no NXP bulletin for i.MX95; the only NXP speculation paper found is for PowerPC (SEARCH-ONLY). Statement: none found.
- TI AM64x (A53 x2): data sheet SPRSP56J rev June 2026 (PDF-TEXT): no Spectre text. AM62A: search-only (datasheet not opened). Statement: none found. No TI advisory page opened.
- Allwinner T536 (A55 x4): no vendor statement found; no datasheet opened.
- Ezurio, PHYTEC, TQ, iWave, Boardcon, Tria, Toradex, congatec: not searched for this topic.
- Arm says it cannot speak for partner implementations (FAQ above); for these SoCs the cores are Arm-designed (mine), but a vendor integration statement is absent.

## Architecture features per core
Cortex-A55 r2p0 TRM, 100442_0200_00, copyright 2016-2018 (PDF-TEXT, https://documentation-service.arm.com/static/5e7e09f6a3736a0d2e862d2f):
- ID_AA64PFR0_EL1 CSV2 = 0x0, CSV3 = 0x0 ("reset value"). The AArch32 register text says the device "does not disclose" whether branch targets in one context affect another. (mine) A55 r2p0 does not claim FEAT_CSV2/CSV3.
- ID_AA64PFR1_EL1 SSBS = 0x1: PSTATE.SSBS exists without MSR/MRS access.
- DIT and MTE: no occurrence in the TRM text (mine: not implemented in r2p0; absence only). Later A55 revisions not checked.
- Cortex-A53 TRM: NOT FOUND/not opened.
- Cortex-M33 r1p0 TRM Issue 07 (PDF-TEXT): "in-order issue pipeline"; "Integer divide unit with support for operand-dependent early termination"; no DIT text. M7 TRM, R5F TRM: not opened.
- Arm mitigations named in opened docs: CSDB, SB (SLS paper via search; "DSB+ISB" fallback), SSBS, CSV2/CSV3, DIT register exists in Arm ARM (search snippet only, A-profile DDI0601 2026-06; page body failed). MPAM/cache partitioning: not searched.

## Linux status
- https://docs.kernel.org/admin-guide/hw-vuln/spectre.html (OPENED, summary, v7.3.0, undated): no arm64/A53/A55 text; retpoline default on vulnerable CPUs. It cites the two Arm cache-speculation papers.
- Patch v2 Dec 13 2024 (patchew, OPENED summary): "the only CPU IDs added to the 'unaffected' list are ARM Cortex A35, A53, and A55"; unknown CPUs reported affected. 5.10.239 changelog (SEARCH-ONLY): A35/A53/A55/A510/A520 on the "safe" list. Mainline proton-pack.c not opened. Per-CPU sysfs defaults on A55: not confirmed.

## Real-time cores as a constant-time key holder
1. Cores and caches. i.MX95 (IMX95CEC Rev. 8, PDF-TEXT): Cortex-M7 up to 800 MHz, 32 kB I + 32 kB D L1 cache (parity), 512 kB TCM; Cortex-M33 up to 333 MHz, 16+16 kB cache (ECC), 512 kB TCM/OCRAM; the M33 runs System Manager. TI AM64x (SPRSP56J June 2026, PDF-TEXT): R5F in dual-core subsystems, 32 kB I + 32 kB D cache and 64 kB TCM each, ECC; one M4F with 256 kB SRAM. M33 in-order per Arm TRM; M7/R5F pipeline: not opened. T536: search found RISC-V E907 (600 MHz) and E902 (SEARCH-ONLY, cnx-software/linux-sunxi). AM62A: R5F 32+32 kB cache, 64 kB TCM (SEARCH-ONLY). Ezurio, others: not searched.
2. Sharing. i.MX95 sheet has "512 kB L3 cache (shared across all cores)" and a block diagram showing a coherent L3 and the LPDDR; the cache table lists the M7 and A55 together (mine: M7 is not clearly outside that L3; sheet unclear). Isolation: "Trusted Resource Domain Controller (TRDC) ... up to 16 resource domains", TrustZone. XRDC under that name: not found; i.MX95 reference manual not opened. TI: DDR is shared between A53 and R5F; "configurable memory partitioning" of SRAM (data sheet). Firewall page (TISCI 22.01.02, OPENED summary): region firewalls, per-master entries for R5F and A53; broad "everyone" regions overlap; no M4F region; strict isolation not confirmed.
3. Key holder. ELE HSM API RM00284 Rev. 4.5, 24 Sep 2026 (PDF-TEXT, covers i.MX95): key store holds symmetric and private keys; "Secret keys cannot be exported under any circumstances"; key usage bits SIGN_MSG, SIGN_HASH; signature generation API exists; sessions route over an MU with a DomainID; both "MU RTD" and "MU APPS" are named (mine: real-time and application cores can each reach ELE). Plaintext-key sign flag also exists ("PLAINTEXT_KEY") (mine: confirm it is off). TI: DMSC-L "Security co-processor ... for key and security management", crypto engine and PKA (data sheet). Neither vendor names the M core or R5F as the key holder in documents I opened.
4. Data-independent timing. M33: divide has "operand-dependent early termination" (Arm TRM). DIT register: A-profile only per the Arm ARM snippet (SEARCH-ONLY). M7, R5F: not found.
5. Bus contention timing: no vendor statement found.

## Could not confirm
- Arm's current Speculative Processor Vulnerability table (page body empty); per-core v1/v3a/v4/BHB/SLS/BSE status for A53/A55 from Arm.
- A53 TRM; A55 revisions later than r2p0; FEAT_DIT/MTE absence only inferred.
- Any NXP, TI, Allwinner statement on Spectre/Meltdown for these SoCs.
- Whether Arm acknowledged SiSCloak (abstract silent); CVE-2020-13844 core list.
- Mainline Linux defaults for A55; XRDC/TRDC configuration of M7 versus A55; M7/R5F pipeline and DIT; ELE sign constant-time behaviour; ELE firmware side channels.

## What I did not search
Rowhammer/DRAM attacks, GPU/NPU side channels, prefetcher (DMP/GoFetch-type) attacks on A55, MPAM, PMU, CVE databases beyond one aggregator, Ezurio/PHYTEC/TQ/iWave/Boardcon/Tria/Toradex/congatec documents, T536 and AM62A datasheets in full, Zephyr/TF-M.
