# Document findings for the 24x i.MX 95 / I350 relay design

Conventions. Page numbers are PDF page numbers, which equal the printed page numbers in all three PDFs.
- Doc1 = NXP i.MX 95 Data Sheet for Industrial Products, IMX95IEC, Rev. 8, 7 April 2026 (125 pp).
- Doc2 = NXP i.MX95_1P21N Mask Set Errata, Rev. 1, 10 November 2026 (62 pp).
- Doc3 = Intel Ethernet Controller I350 Datasheet, Revision 2.6 (994 pp).
- Doc4 = Arm 101381 r4p0. **Could not be retrieved (see section D).**
- "(not from the documents)" marks background knowledge. "NOT FOUND in <doc>" means I searched the extracted text and the item is not there.

## Key findings for the security design

1. **MCTP over PCIe: the I350 documents SMBus only.** Doc3 says it supports "NC-SI over MCTP protocol over SMBus" (p876), and the feature table lists "DMTF MCTP protocol over SMBus" Y (p16). PCIe vendor-defined messages are rejected or dropped (Table 3-6, p74): VDM type 0 gets Unsupported Request and VDM type 1 is silently dropped. There is one wrinkle. Table 10-46 footnote 1 (p883) says two MCTP control commands are "supported only for MCTP over PCIe", which contradicts the rest of the datasheet. Treat MCTP over PCIe as unsupported, but confirm with Intel or by test.
2. **The I350 has a firmware-bearing management controller that is patched from the NVM.** "Firmware patch code" is loaded from EEPROM after power-up or firmware reset (p790, p855, p89). The host can write the EEPROM through EEC/EERD unless the protection mechanism is armed (word 0x12 bit 4 plus the read-only range in words 0x2C/0x2D, p95). The host can also write the external Flash (p101-102). Arm EEPROM write protection in the image, and note that the Flash path is separate from it.
3. **Manageability can be disabled in NVM, but a sideband recovery path remains.** Common Firmware Parameters 1 has Manageability Mode 0x0 = None and a Redirection Sideband Interface field (SMBus / NC-SI / MCTP) (p262). EEPROM-less mode is "Non-manageability mode only" (p97). However, a "Release EEPROM" SMBus message to address 0xC8 is accepted "in all modes" whenever an EEPROM with a valid signature is present (p96). Firmware accepts it only from PCIe reset until a function reaches D0a. This is moot if the SMBus pins are not wired.
4. **There is no per-port power figure.** Doc3 gives only device totals. The 4-port, 1000BASE-T copper, active-link row is 3278 mW typical and 3871 mW max without SVR/LVR, and 3717 / 4372 mW with SVR/LVR (Table 11-5, p901). The cover page says "4.2W (max) in quad port mode" (p1).
5. **The i.MX 95 data sheet gives no ppm tolerance for the 24 MHz crystal.** Table 29 (p30) gives only 24 MHz, 12 pF load, 100 uW drive level and 120 ohm ESR. The only ppm figures are indirect: the USB 2.0 reference clock is +/-300 ppm (Table 116, p106) and the PCIe reference clock is +/-300 ppm (p108). A 24 MHz crystal-drift temperature side channel therefore cannot be bounded from the data sheet.
6. **Timer errata affect the A55 generic timer and the system counter.**
   - ERR052995: if BUS_AON_CLK_ROOT is 24 MHz, TSTMR2 freezes and no longer tracks SYS_CTR. Keep it >= 48 MHz "when using the A55 generic timer" (Doc2 p44).
   - ERR052966: TSTMR1 can return an incorrect timestamp with no validity indication (Doc2 p44).
   - Doc1 does not state the SYS_CTR or generic-timer frequency or clock source.
7. **The Cortex-M7 and its isolation are covered by errata.**
   - ERR052335: the MU run/stop event for the M7 can fire unexpectedly (Doc2 p14).
   - ERR053263: a TRDC-denied read of the CM33 TCM raises a spurious parity fault (Doc2 p15).
   - ERR051968: AON TRDC MDAC 39 does not exist (Doc2 p15).
   - Arm M7 errata ERR011573 (speculative accesses to MPU-unmapped memory) and ERR051046 (CTI interrupts even when DBGENCTRL[CDBGEN] is low) are listed in Doc2 Table 2, p5. No description text is given for Arm errata.
8. **Cache and isolation hardware in the errata.**
   - Doc1 says the DSU has a 512 kB shared L3 (p2).
   - Arm DSU errata ERR050919 and ERR050918 are about incorrect ordering after a cacheability change and of cache maintenance operations.
   - Several Arm MMU-700 (SMMU) errata concern MPAM, which implies an MPAM-capable SMMU. ERR052764 / ERR051564 covers "FIXED bursts can bypass hypervisor Force Write-Back controls" (Doc2 Table 2, pp1-6).
   - ERR052222: the NXP cache performance monitor (CMX_PERFMON) cannot separate user from privileged events (Doc2 p16).
   - Doc4, the L3 partitioning manual, was not retrievable, so DSU L3 partitioning is unconfirmed.
9. **Debug and boot.**
   - Doc1 shows "Secure JTAG" and "Tamper Detection" only as block-diagram boxes (p10). It names no tamper pin and gives no debug-disable fuse statement.
   - ERR051899 (Doc2 p27) says debug must be re-authenticated after an MDM_AP reset.
   - Boot is an M33-only "Low Power Boot" with Normal, Internal Fuse and Serial Download modes. Serial download goes through USB1/USB2 (Doc1 pp110-111).
   - Doc2 has no ELE, AHAB or fuse errata.
10. **The errata cover only silicon "Rev B1 (mask 1P21N)"** (Doc2 p1). Doc1's ordering table lists silicon revisions A = Rev 1.0 (A0), B = Rev 2.0 (A1) and C = Rev 3.0 (B0) (p6). It does not mention B1. Check which revision your SMARC modules carry.

---

## A. i.MX 95 data sheet (Doc1)

### A1. Cortex-A55 operating points and temperature (industrial)

- Doc1 does not use the name "OPP table". The modes are low drive, nominal and overdrive for VDD_ARM (Table 22, pp25-26), with maximum frequencies in Table 25 (p28).
- The pairing of voltage mode to frequency is by mode name, since Table 25 has Low Drive / Nominal Drive / Overdrive columns.

| Mode | VDD_ARM (A55) min / typ / max | VDD_SOC min / typ / max | A55 max freq | DSU max freq | Source |
|---|---|---|---|---|---|
| Overdrive | 0.85 / 0.90 / 0.955 V | 0.85 / 0.90 / 0.955 V | 1800 MHz | 1500 MHz | Tables 22, 25 |
| Nominal | 0.80 / 0.85 / 0.905 V | 0.80 / 0.85 / 0.905 V | 1404 MHz | 1170 MHz | Tables 22, 25 |
| Low drive | 0.76 / 0.80 / 0.85 V | 0.76 / 0.80 / 0.85 V | 900 MHz | 750 MHz | Tables 22, 25 |
| Suspend | VDD_ARM 0 V (power gated, footnote [2]) | 0.61 / 0.65 / 0.70 V | n/a | n/a | Table 22 |

- Other maximum frequencies by mode (low / nominal / overdrive), Table 25 p28:
  - Cortex-M7: 400 / 667 / 800 MHz
  - Cortex-M33: 166.67 / 250 / 333.33 MHz
  - EdgeLock Secure Enclave: 133.33 / 200 / 250 MHz
- Table 22 footnote and Table 23 footnote [3] (p27): voltages above Vtyp x 1.05 but below Vmax are supported only with a PMIC that has Automatic Voltage Positioning (AVP).
- The 2.0 GHz part ("Q" frequency code) is "available in i.MX 95 19x19 package (VZ) with Commercial Qualification only" and needs a thermal solution holding 0-95 C Tj in "Super Overdrive mode" (p6, footnote 8). It is not an industrial operating point. The 1.8 GHz "N" code is the ordering-code maximum (p6).
- Industrial junction range (Table 23, p27): Tj -40 to 105 C. The ambient row gives only a minimum of -40 C (no maximum is given in the table). Footnote [1]: the minimum Tj at startup is where Tj = Ta. The ordering key says "C Industrial: -40 C Ta to 105 C Tj" (p6).
- The data sheet also gives thermal resistance (Table 13, p16; Table 14, p17):
  - 15x15 mm: RthetaJA 15.7 C/W, RthetaJC 0.2 C/W.
  - 19x19 mm: RthetaJA 13.4 C/W (bare die) or 12.3 C/W (lidded), RthetaJC 0.2 / 0.6 C/W.
  - The data sheet cautions these are for package comparison only, not application prediction (p17).

### A2. Crystals and clock input

- **24 MHz (XTALI_24M / XTALO_24M).**
  - Table 29 (p30): fXTAL 24 MHz, Cload 12 pF (typ), drive level 100 uW max, ESR 120 ohm max. Footnote [1] says the crystal "must be rated for an allowable drive level of at least 100 uW".
  - **Frequency tolerance and stability in ppm: NOT FOUND in Doc1.** Table 29 has no ppm row.
  - Indirect constraint: "Care must be taken that the resulting oscillation frequency complies with the utilized serial interface standards such as PCIe and USB" (Table 3, p11).
  - Those standards give +/-300 ppm for USB 2.0 reference clock FREF_OFFSET (Table 116, p106) and +/-300 ppm for the PCIe reference clock (p108).
  - "The XTAL input is used to synthesize all of the clocks in the system" (p29).
  - The 24 MHz XTAL is supplied from VDD_ANA_1P8 (Table 16, p20).
  - **External clock input mode for the 24 MHz reference: NOT FOUND in Doc1.**
    - Section 4.1.10.1 (p29) says "External clock can be injected into RTC_XTALI", which is the 32 kHz pin only.
    - Table 3 describes XTALI_24M / XTALO_24M only as a crystal connection (p11).
    - CLKIN1 / CLKIN2 are listed as input pins without pulls (p11) but are not described as the 24 MHz source.
- **32.768 kHz RTC (RTC_XTALI / RTC_XTALO).**
  - Table 30 (p30): 32.768 kHz, Cload 12.5 pF, ESR 90 kohm max, drive level 0.5 uW max. **No ppm figure.** Table 3 (p11) says to account for pin parasitic capacitance "to match the trimmed crystal assumptions for frequency accuracy".
  - An external clock is allowed on RTC_XTALI with RTC_XTALO disconnected or driven complementary. The level must not exceed VDD_ANA_1P8 and the frequency must be < 50 kHz (Table 3, p11).
  - Table 28 (p29): f 32.768 kHz, VIH min 0.9 x NVCC_BBSM_1P8, VIL max 0.1 x NVCC_BBSM_1P8, duty 45-55 %. (Table 3 mentions VDD_ANA_1P8 while Table 28 uses NVCC_BBSM_1P8. The data sheet is not consistent, so use the stricter.)
- Related clock facts:
  - The FRO internal oscillator is 200-400 MHz with accuracy +/-2.0 % typ / +/-4.0 % max (Table 31, p30).
  - The audio EXT_CLK maximum is 133 / 200 / 200 MHz (Table 27, p29).

### A3. Temperature monitoring

- Count: "2x Temperature Sensor (TEMPSENSE)" (Table 1, p4).
- Accuracy (Table 24, p28): -4 to +4 C over -40 to 125 C, with voltage trim at room temperature (25 C).
- Supplies: the sensor is listed under VDD_ANA_0P8 / VDD_ANA_1P8 supplies (Table 16, p20; power diagram, p19).
- Block diagram (p10): "Temperature Sensor" is drawn in the Low-Power Real-Time Domain system-control block.
- **Location on die, what the sensors connect to, and thermal throttling or shutdown behaviour: NOT FOUND in Doc1.** These are presumably in the Reference Manual.

### A4. System counter and timers

- Doc1 only lists the blocks: 1x System Counter (SYS_CTR), 1x Timestamp Timer (TSTMR), 1x GPT, 6x TPM, 2x LPIT, 2x LPTMR, 5x WDOG (Table 1, p4).
- **The generic timer / SYS_CTR frequency or its derivation from 24 MHz: NOT FOUND in Doc1.**
- Indirect information is in Doc2:
  - ERR052995 (p44) ties TSTMR2 to SYS_CTR and to BUS_AON_CLK_ROOT, and says to keep BUS_AON_CLK_ROOT >= 48 MHz for the "A55 generic timer".
  - It also says SYS_CTR_RDBASE is "accessible to all CPUs in the reference NXP BSP".

### A5. Tamper

- **Tamper pin names, electrical characteristics, and active or passive tamper behaviour: NOT FOUND in Doc1.**
- "Tamper Detection", "Secure Clock", "Secure Boot", "eFuse Key Storage" and "Random Number" are drawn as parts of the EdgeLock Secure Enclave in the block diagram (p10). BBNSM / BBSM (battery-backed secure module) are named as power-mode elements (p21). Table 1 describes the enclave only as "evolved on-die security with run-time attestation, silicon root of trust..." (p5).

### A6. JTAG and debug

- Doc1 gives "Arm CoreSight debug and trace", TPIU, and "4-pin (JTAG) and SWD debug interfaces" (Table 1, p5). "Secure JTAG" is a box in the Flex Domain (p10). JTAG timing is Table 43 (pp44-45): TCK max 50 MHz for debug and 10 MHz for boundary scan.
- **Any secure JTAG or debug-disable fuse statement: NOT FOUND in Doc1.**
- Boot section (pp110-111) mentions fuse-selected boot modes ("Boot from Internal Fuses", mode X000, Table 123) but not debug.
- Doc2 ERR051899 (p27) says that with the Secure Enclave reset "Debug is disabled" and the user must re-authenticate debug after an MDM_AP reset.

### A7. Ethernet

- **Doc1 does not name the ports ENETC0/1/2 or map them to interfaces.** The text mentions only "ENET2" (pins muxed with SAI4, p50), and NETC / NETCMIX in the power diagram (p19).
- Table 1 (p4): "2x 1 Gbps Ethernet ports with TSN", "1x 10 Gbps Ethernet port with TSN ... for 19 x 19 mm package only" (and ordering note 3, p6), IEEE 1588, EEE.
- Section 4.11.2 (p71) interface list:
  - 1.8 V / 3.3 V RMII
  - 1.8 V RGMII
  - SGMII (1G and 2.5G), USXGMII and XFI
  - ENET AVB, IEEE 1588 and EEE
- RGMII timing is valid only for 1.8 V I/O (p77). Footnote: RGMII input clock must not exceed the output clock by more than 300 ppm (p78).
- 10G SerDes: an external 156.25 MHz reference on ETH_REF_PAD_CLK_P/N is required. Table 87 (p81): reference frequency offset +/-100 ppm, RJ 1 ps (12 kHz-20 MHz band).
- IEEE 1588 interface (Table 97, p85-86):
  - TMR_1588_CLK_IN period >= 5 ns, duty 40-60 %, peak-to-peak jitter <= 250 ps.
  - Signals: TMR_1588_CLK_OUT, PULSE_OUT1/2, ALARM_OUT1/2, TRIG_IN1/2.
- The selectable timer clock source options are not in Doc1. Doc2 ERR053441 says they are "device specific, documented in the NETC bridge topic" (Reference Manual).

### A8. Power

- Table 32 (pp31-32), maximum supply currents, from "consumer standard benchmarks that are designed to be compute and graphic intensive" and "intended to be used as guidelines for power supply design":
  - VDD_ARM 5000 mA max
  - VDD_SOC 4400 mA max
  - VDD_ANA_1P8 225 mA
  - VDD_DDR_0P8 800 mA
  - VDD_ETH_0P8 58.99 mA and VDD_ETH_1P8 40 mA
  - NVCC_* GPIO 145 mA
- Low-power power figures (Tables 19-21, pp23-25): BBSM mode 0.132 mW typical / 0.196 mW max. These are "collected at 25 C Tj" (footnotes, p23-24).
- **Typical or maximum active power at a fixed A55 frequency: NOT FOUND in Doc1.** The data sheet gives no per-frequency active power. **AN14449 is not referenced anywhere in Doc1** (no match for "AN14449" or "application note" other than a lifetime-usage note on p27).

---

## B. Errata (Doc2)

**Coverage.** "This report applies to all i.MX95 part numbers for Rev B1 (mask 1P21N)", part numbers MIMX9596 / 9594 / 9586 / 9556 / 9554 / 9546 / 9536 / 9534 xxxxxxD (p1). Table 2 shows only the "B1" column and every Arm entry is "Yes". Table 3 and Section 2 give no per-erratum revision column, so all listed errata are taken as applying to B1. Every NXP erratum below has projected solution "No plans to fix". Revision history: Rev 1, 11/2026, initial revision (p1).

**Structure.** Table 2 (pp1-8) covers Arm errata with no workaround or description text, only a summary line. Table 3 (pp8-12) and Section 2 (pp13-59) cover NXP errata with description and workaround.

### B1. Cortex-A55, DSU, caches, speculation (Table 2, Arm errata, Rev B1: Yes; no description or workaround text is given)

| NXP ID | Arm SDEN (category) | Title | Where |
|---|---|---|---|
| ERR050839 | 1530923 (CatB) | Cortex-A55: speculative AT instruction using out-of-context translation regime could cause subsequent request to generate an incorrect translation | p7 |
| ERR052970 | 2441007 (CatB) | Cortex-A55: completion of affected memory accesses might not be guaranteed by completion of a TLBI | p6 |
| ERR050840 | 999993 (CatC) | Cortex-A55: multiple concurrent ECC errors might cause silent data corruption | p6 |
| ERR050843 | 1131793 (CatC) | Cortex-A55: stores might prevent progress of an exclusive loop | p6 |
| ERR050845 | 1401736 (CatC) | Cortex-A55: non-cacheable loads of mismatched size might not be single-copy atomic | p7 |
| ERR050838 | 1024718 (CatC) | Cortex-A55: update of DBM or AP bits without break-before-make might result in incorrect hardware dirty bit update | p7 |
| ERR052971 | 2109978 (CatC) | Cortex-A55: atomic store instructions might not report an External abort or System Error | p7 |
| ERR050844 | 1214720 (CatC) | Cortex-A55: VFP_SPEC PMU event might count incorrectly | p7 |
| ERR050841 | 1030596 (CatC) | Cortex-A55: cycle count value in timestamp packet might be incorrect | p7 |
| ERR050842 | 1030597 (CatC) | Cortex-A55: incorrect ETM timestamp value when timestamp and event generation happen on same cycle | p6 |
| ERR052972 / 052973 / 052974 | 2288055 / 2342475 / 2364958 (CatC) | A55 debug-state items: DTR flags not cleared; halting-step syndrome wrong on Load-Exclusive; unallocated debug/trace registers might be trapped by HCR_EL2.TIDCP | p7 |
| ERR050918 | 1162044 (CatB) | DSU: incorrect ordering of data cache maintenance operations | p6 |
| ERR050919 | 1314123 (CatC) | DSU: incorrect ordering after change in cacheability | p6 |
| ERR051941 / ERR051937 | 1741320 (CatB) | DSU: use of FUNC_RET power mode prevents thread wakeup in a multithreaded core | p6 |
| ERR051942 | 1933378 (CatC) | DSU: interconnect DErr on dirty data not reported in RAS registers | p6 |
| ERR051944 | 2832900 (CatC) | DSU: interconnect bus errors during write back not recorded in RAS registers | p6 |
| ERR051945 | 2976798 (CatC) | DSU: error record registers indicate pseudo-fault generation support in configurations without cache protection | p6 |
| ERR051943 | 2123467 (CatC) | DSU: no response to Debug APB access during a core reset, in Direct connect configuration | p6 |
| ERR053211 | 3828172 (CatC) | DSU: ELA trigger not seen by CTI | p6 |

Additional non-Arm items touching this area:
- **ERR052222** (NXP; Doc2 p16; no workaround, "There is no workaround."): CMX_PERFMON cannot distinguish user from privileged mode, so cache event counting in user-only or privileged-only modes (PMCR[CMODE]=10b/11) is not supported.
- **ERR050840 / ERR052341 / ERR052342** (ECC): see B7.

### B2. Timers, system counter, clocks, PLL, crystal oscillator

| ID | Title and one-line description | Workaround | Page |
|---|---|---|---|
| ERR052995 | TSTMR2 does not track SYS_CTR if BUS_AON_CLK_ROOT = 24 MHz; TSTMR2 registers freeze | Yes: read SYS_CTR_RDBASE directly; keep BUS_AON_CLK_ROOT >= 48 MHz for the A55 generic timer and CoreSight timestamps | 44 |
| ERR052966 | TSTMR1 may return an incorrect timestamp and gives no validity indication | Yes: use TSTMR2 or the system counter | 44 |
| ERR003777 | GPT: extra pulse on src_clk when switching clock sources | Yes: change the source only with the GPT disabled | 23 |
| ERR052845 | WDOG3/4/5 reset (A55 or M7 hang) gates bus_wakeup_clk_root for ~6 x 24 MHz cycles. LPIT2 and TPM3 miss counts; CMU_W2 may flag a false clock fault; data transfers on CAN, SAI, FlexIO, I3C, I2C, SPI, UART may be corrupted | Partial: System Manager clears the false CMU fault; none for other peripherals | 57 |
| ERR053549 | PCIe: internal 100 MHz reference from HSIO PLL may fail jitter compliance during GPIO toggling | Yes: use an external 100 MHz HCSL reference | 40 |
| ERR053441 | NETC: changing TCLK_PERIOD while TMR_CTRL[TE]=1 causes ns-scale jumps in current time and inaccurate FIPER intervals | Yes (full: timer clock giving TMR_ADD fraction between 0.0003 and 0.9997 ns) | 30 |
| ERR052782 | NETC: FIPER pulse inaccurate after changing TMR_OFF | Yes (sequence in doc) | 31 |
| ERR052518 | SRC: non-cooperative reset of Cortexmix may hang | Yes: enable the SSI LPCG timeout (System Manager implements it) | 44 |

No erratum was found on the 24 MHz crystal oscillator, the 32 kHz RTC oscillator, or the FRO.

### B3. ENETC / NETC / PTP timer

NETC items:
- Timer: ERR053441, ERR052782 (see B2).
- **ERR052025**: XGMII (10 GbE) 1-step timestamped Tx packets can be corrupted (preamble, SFD or first 2 bytes). Workaround: use 2-step timestamping (p38).
- **ERR053591**: a persistently blocked lossless Rx BD ring on one ENETC can delay or block receive traffic on other ENETCs in the same traffic tier. Workaround: give the lossless ENETC exclusive use of a priority tier (p38). This is a cross-port interference item.
- ERR052027: XGMII Tx statistics miscount one frame after a link-partner fault. No workaround needed (p38).
- ERR052133 (p32): MAC does not detect mVerify after a start fragment. Workaround: disable preemption verification in a closed system.
- ERR052264 (p32): iEP-RC RR[LOCK] warm reset field not cleared. Use soft reset instead.
- ERR051707 (p33), ERR051710 (p33): MAC merge and half-duplex Tx statistic counters inaccurate (workarounds partial).
- ERR052501 (p34): PCE reports the wrong memory ID for ECC faults. No workaround; software should know the ID is wrong.
- ERR052265 (p34): RCEC PME_REQ_ID unreliable with multiple PMEs. Workaround: read every function's PME status.
- ERR052134 (p35): SG_DROP_COUNT at a wrong bit offset in the Ingress Stream Count response. Workaround: adjust offset.
- ERR051338 (p35): L2 IPv4 multicast filter table returns inaccurate KEY_TYPE. Workaround: an extra query.
- ERR052288 (p36): first frame after SequenceRecoveryReset (FRER) is dropped and miscounted. System-level workaround.
- ERR011508 (p36): 802.1Qbv transmission-overrun counter not implemented. Avoid via gate schedule design.
- ERR051887 (p37): VLAN qualifiers missed in ingress classification lookups. Workaround options A/B.

### B4. PCIe

| ID | One-line description | Workaround | Page |
|---|---|---|---|
| ERR051583 | Controller drops the next TLP with an ECRC error when the ECRC check is disabled | Yes: clear ECRC_CHK_EN before link-up | 39 |
| ERR051362 | PCIE_CAP_TRANS_PENDING takes up to ~512 ns to assert | Yes: wait 512 ns | 40 |
| ERR053549 | Internal 100 MHz refclk jitter (see B2) | Yes: external refclk | 40 |
| ERR051584 | LTSSM may not enter DISABLED from Recovery.Idle | Yes: clear and set link-disable, poll | 41 |
| ERR051382 | Read requests larger than 256 B cause long bus delays affecting other SoC applications | Yes: limit link-partner Max_Read_Request_Size to <= 256 B | 41 |
| ERR051368 | EP does not meet PM_PME retransmit timing in L1 sub-state | Yes | 41 |
| ERR051572 | Message TLPs with non-zero reserved length field are not processed properly | Yes: ensure reserved TLP fields are zero | 42 |
| ERR051573 | TLP with ECRC error not handled as Advisory Non-Fatal | Yes: do not enable ECRC check, or handle in the ISR | 42 |

### B5. Secure boot / ELE / AHAB / fuses / JTAG / debug

- **No ELE, AHAB, secure-boot or fuse erratum is listed in Doc2.** The search terms ELE, AHAB, fuse and secure boot found nothing in Table 3 or Section 2.
- **ERR051899** JTAG: a SW reset event cannot be cleared during a full SoC reset when SRDR[26]=1; the system is stuck in reset. Workaround: do not set SRDR26=1. Notes that Secure Enclave reset disables debug and debug must be re-authenticated (p27).
- Arm M7 **ERR051046** (arm 585224): "CTI might generate interrupts even when DBGENCTRL[CDBGEN] is low" (Table 2, p5). This is a debug-disable-related item.
- CoreSight items (Table 2, pp7-8, CatA / B / C): ERR009252, ERR009253, ERR009260, ERR009267, ERR009269, ERR009272, ERR050871, ERR050875, ERR050887, ERR052310. These cover trace and timestamp behaviour, the SWJ-DP parity bit in overrun mode (ERR009260), and the AHB-AP unaligned HADDR (ERR050875). They are not described beyond the title.

### B6. System Manager / TRDC / isolation / M7 / M33 / SMMU / GIC

- **ERR053263** TRDC: a denied read of CM33 TCM gives a spurious parity fault NCF[61] from AXBS_M33. Workaround: ignore NCF[61] when TRDC blocks the access (p15).
- **ERR051968** AON TRDC: MDAC 39 does not exist; do not read or write it (p15).
- **ERR052335** MU: the M7 run/stop event glitches (MU5 side A on AONMIX; MU7/MU8 side B on WAKEUPMIX). Workaround: do not enable the RUNIE/STOPIE event interrupts, read CSR0 RUN/STOP twice, check SR[MURIP] (p14).
- **ERR052444** General: non-cooperative reset of a Cortex-A55 can leave the GIC unresponsive. Workaround: System Manager (CM33) quiesces via GICR_WAKER with a software semaphore (p22).
- ERR052518 (B2), ERR052845 (B2), ERR052979 Safety: VFCCU parity faults must be SW-clearable only (p43), ERR051561 CMU automatic fault injection broken (p16).
- Arm Cortex-M7 (Table 2, p5): ERR011573 (speculative accesses to memory unmapped in MPU), ERR050727, ERR052277, ERR050729, ERR050728, ERR051167 (TCM bandwidth sharing between AHBS writes and software stores with TCM wait states).
- Arm Cortex-M33 and M0+ (Table 2, pp3-5): ERR051727 to ERR051734, ERR050501 to ERR050505, ERR051051, ERR006945, ERR010888, including ERR051727 / ERR051051 (partially completed VLLDM can leave Secure floating-point data unprotected, CatB).
- Arm MMU-700 (SMMU) (Table 2, pp1-4, all "Yes"); about 30 entries, many listed twice under two NXP IDs. Those most relevant to isolation are:
  - ERR051564 / ERR052764 (SDEN 2808817, CatB rare): a device that can issue FIXED bursts can bypass hypervisor Force Write-Back controls to read old memory contents.
  - ERR053120 (SDEN 3422699): CD.ASID value might affect caching of EL3 and some EL2 contexts.
  - ERR051565 / ERR052765 (SDEN 2288256): configuration and translation structures might be accessed by prefetch while SMMUEN=0.
  - ERR051563 / ERR052763 (SDEN 2214515) and ERR051354 / ERR052756 (SDEN 2133013): global entries not always invalidated; stage-1 DVM invalidation of TTB1 translations might not succeed.
  - ERR051350 / ERR052759 (SDEN 2268618) and ERR053066 (SDEN 4014544): TLB invalidation might not succeed or might under-invalidate.
  - ERR053575 (SDEN 4585573): CMD_CFGI_CD with SSID 0 causes over-invalidation.
  - MPAM-related: ERR051352 / ERR052758 (CMAX register write uses incorrect bits), ERR051562 / ERR052761 (MPAM monitor values incorrect if configuration changed while enabled), ERR052770 (incorrect MPAM_MSMON_IDR NO_HW_OFLW_INTR bit [30]).
- Arm GIC-700 (Table 2, pp5-6): (ERR052068, ERR052070, ERR052049, ERR052811, ERR051956, ERR051954, ERR052115, ERR052047, ERR052048, ERR051955, ERR052050). Notably ERR052047 (SPI wake requests missing when an SPI is deactivated while targeting a sleeping CPU) and ERR052048 (virtual group enables not restored after exiting GICR_WAKER.Sleep).

### B7. LPUART, GPIO, temperature sensor, DDR / ECC, boot / FlexSPI

- **LPUART**
  - ERR052900: DMA1 for LPUART1 transmit can overflow the Tx buffer; LPUART2 not affected. Workaround: use PIO from the M33 or A55 (p29).
  - ERR051629: STAT[TC] not set when CTS is negated, CTS enabled and the Tx FIFO is flushed. Workaround: clear TXCTSE and flush (p29).
- **GPIO / I/O**
  - There is no GPIO-peripheral erratum. The I/O-level items are:
    - ERR052263: glitch below 0.7 V on NVCC_BBSM_1P8 domain I/O (including PMIC_ON_REQ) during ramp-up. Use a PMIC with input-high above 0.7 V (p23).
    - ERR052302: glitches on NVCC_GPIO / WAKEUP / AON / SD2 / ENET / CCM_DAP I/O during ramp. Control slew rates and sequencing (p24).
- **Temperature sensor / thermal**: **no erratum found** (terms TEMPSENSE and temperature absent from the errata). ERR052780 BBNSM (p15): a pending Time Alarm Event has priority over the ONOFF long press, so the system might not power off. Workaround: external reset logic or PMIC watchdog.
- **DDR / ECC**
  - ERR052341: ECC scrubbing is not supported when DDRC REMAP is used (it is required with inline ECC). Workaround: disable scrubbing (p17).
  - ERR052794: DDRC self-refresh entry may violate spec (p17); workaround: use SR_FAST_WK_EN, avoid SR_IT, follow the sequence.
  - ERR052342: NPU TCM has limited ECC response support (p38).
  - Arm ERR050840 (A55 silent corruption on concurrent ECC errors).
- **Boot ROM / boot select / SPI boot**: no boot ROM or boot-select erratum found. FlexSPI items:
  - ERR053021: DLL lock may fail when MCR0[RXCLKSRC]=3. Workaround: set REFPHASEGAP or use override mode (p21).
  - ERR050272: DLL lock status bit not accurate. Wait 4 us (p22).
  - ERR050537: read-timing mismatch with some SPI NOR in dual/quad/octal. Use single-line mode (p22).
  - ERR053509: XSPI_FLR write with exactly six payload bytes may lose the final byte in DDRx8 mode. Workaround: split, combine or pad (p58).

---

## C. Intel I350 (Doc3)

### C1. Manageability

**MCTP over PCIe or SMBus only?** Documented as SMBus only.
- "The I350 supports NC-SI over MCTP protocol over SMBus." (10.7.1.2, p876)
- "The I350 enables reporting and controlling all information exposed in a LOM device via NC-SI using the MCTP protocol over SMBus. The MCTP interface will be used by the BMC to only control the NIC and not for pass through traffic." (1.7.4.4, p21; also 3.2.1, p87)
- Table 1-7 (p16): "DMTF MCTP protocol over SMBus": Y for I350, N for 82580, 82599 and Kawela.
- Section 10.7.1 (p876) says specifications "exist" for MCTP over PCIe (DSP0238) and over SMBus (DSP0237). That is background about MCTP, not an I350 capability claim.
- "This mode is relevant only for MCTP over SMBus traffic." (Simplified MCTP, 10.7.1.3, p877)
- PCIe message handling (Table 3-6, p74): Vendor_defined type 0 (code 0x7E) gets "Unsupported request" and Vendor_defined type 1 (0x7F) is "Silently drop".
  - (not from the documents) MCTP-over-PCIe uses PCIe Vendor Defined Messages, so these two rows are consistent with no MCTP-over-PCIe support. The datasheet itself does not draw that conclusion.
- **Contradictory text:** Table 10-46 (p883) marks "Prepare for Endpoint Discovery" and "Endpoint Discovery" as supported as responder with footnote 1: "These commands are supported only for MCTP over PCIe." I found no other MCTP-over-PCIe statement. I read this as inherited generic text, but it is not resolved in Doc3.
- Also: "MCTP over SMBus is not active while in Dr State. In this state, the I350 will not answer to SMBus commands." (p877)

**Management interfaces.**
- Two: SMBus and NC-SI, both optional (3.2, p87; 1.4.5 / 1.4.6, p12).
- NC-SI meets DMTF NC-SI Rev 1.0.0 as an integrated NC (p12). RMII-based NC-SI pins are Table 2-5 (p29): 50 MHz clock +/-100 ppm, TXD/RXD, CRS_DV, ARB_IN/OUT. The I350 can drive the NC-SI clock if the EEPROM bit allows (p88).
- SMBus pins: SMBD, SMBCLK, SMBALRT_N (Table 2-4, p29). They need external pull-ups (Table 2-29, p57).
- The SMBus interface exposes the four ports as four SMBus devices (10.5.3, p772). In MCTP mode all four are one endpoint (10.7.2, p877).

**How they are enabled or disabled.**
- EEPROM Common Firmware Parameters 1 (Global MNG offset 0x3 + word 0x1, 6.3.7.2, p262):
  - Bits 14:13 Redirection Sideband Interface: 00b SMBus, 01b NC-SI, 10b MCTP.
  - Bits 10:8 Manageability Mode: 0x0 None, 0x2 Pass Through (PT).
  - Bit 3 Proxying Capable, bit 2 OS2BMC Capable, bit 15 Enable Firmware Reset.
  - Bits 5/4: LAN1 / LAN0 Force TCO Reset Disable.
- Per-port traffic types (network-to-BMC, OS-to-BMC) are in 6.3.12.2 (p281) and map to MANC.EN_BMC2NET / EN_BMC2OS (p281).
- SMBus ARP and address-mode bits are in the SMBus configuration words (p274).
- **No hardware strap that disables manageability was found.** The strapping table (Table 2-30, pp59-60) covers LANx_DIS_N, AUX_PWR and the SDPx_1 PCIe-function-disable pins only. If NC-SI is unused, its pins need external pull-downs or pull-ups (Table 2-29 notes, pp57, 59).
- **Can manageability be fully disabled by NVM?**
  - Mostly yes. Manageability Mode = None (p262). An EEPROM of 128 Kbit suffices "if no manageability mode is desired" (p89). EEPROM-less operation is "Non-manageability mode only" (p97).
  - Dr disable mode requires "Pass-through manageability is disabled" (p199).
  - Exception: the "Release EEPROM" SMBus recovery message (below).

**What a BMC can do over SMBus / NC-SI.**
- Pass-through traffic: the BMC sends and receives LAN packets through the I350 (10.1, 10.5.10).
  - Over MCTP only control commands are supported, not pass-through traffic (Table 10-30 footnote 2, p821).
  - Over MCTP "Get Capabilities" still reports full filtering capabilities even though pass-through is unavailable (footnote 5, p821).
- Filtering: manageability receive filters, L2 / VLAN / IP / flex 128 filters, packet reduction (Table 10-33, p825-826).
  - These are not available over MCTP ("No").
- Control commands, SMBus: Transmit Packet, Request Status, Receive Enable, Force TCO, Management Control, Update Management Receive Filter Parameters, thermal sensor configure / action, plus read commands (Receive TCO packet, Read Status, Get System MAC, Read Management Parameters, thermal reads) (10.5.10, pp787-805).
- Force TCO command (10.5.10.1.4, p790):
  - TCO Reset clears the Rx/Tx data path and unloads the LAN driver ("DRV_LOAD" cleared).
  - TCO Isolate "will disable PCIe write operations to the LAN port" (if enabled in EEPROM).
  - Firmware Reset reloads manageability EEPROM words including "Firmware patch code".
- NC-SI commands over MCTP (Table 10-30, p821): Clear initial state, Get Version ID, Get Parameters, Get Link Status, Enable / Disable / Reset Channel, Set Link, AEN Enable, Select / Deselect Package, Get Capabilities, OEM.
- Intel OEM commands supported over MCTP (Table 10-33, pp825-826): Get System MAC Address, Perform TCO Reset, Get Controller information, thermal sensor get/set/perform action.
- Auto-ARP reply over SMBus is offered to the BMC (p16; 10.5.4, p773).
- **Generic register read/write and NVM read/write over the sideband:** I found no such command in the SMBus tables (10.5.10, pp787-805) or in the Intel OEM NC-SI command summary (Table 10-33, pp825-826). The only sideband action that changes NVM is the "Release EEPROM" recovery message. Firmware itself reads the EEPROM. The BMC may also load a new management EEPROM image through some other channel and then issue Firmware Reset (p855). I could not exhaustively confirm there is no hidden path.
- **EEPROM recovery message** (3.3.1.7, p96): SMBus block write, address 0xC8, command 0xC7, byte count 0x01, data 0xAA. Firmware "clears EEPROM in word 0x12 together with the signature". It is accepted from PCIe reset until a function moves D0u to D0a, "no matter what the content of the EEPROM is". It is described as limiting a "malicious software ... back door".
- Thermal sensor (3.6, pp109-110): the BMC or host can read on-die temperature (THMJT register); resolution 0.25 C, absolute accuracy +/-5 C, 13.1 ms conversion (Table 3-22, p109).
- OS-to-BMC traffic path exists (1.7.4.3, p21; 10.4, p768); it is enabled by the OS2BMC capable bit.

### C2. IEEE 1588 / 802.1AS

- Features: IEEE 1588 PTP, 802.1AS, per-packet timestamp (cover p1; feature table p15). Time sync works "in full duplex mode only" (7.9.1, p425).
- Hardware timestamps Tx and Rx PTP packets at a point "as close as possible to the PHY" (7.9.3.2, p428; Figure 7-27 p430). Rx timestamp can be latched in RXSTMPL/H or prepended to the receive buffer (SRRCTL.Timestamp) (p429). Supports PTP V1 and V2 (L2, UDP/IPv4, UDP/IPv6) (7.9.6, p433).
- Resolution: SYSTIM is a 72-bit counter (SYSTIMR/L/H). "updated periodically each 8 nS clock cycle" with "New SYSTIM = Old SYSTIM + 8 nS +/- TIMINCA.Incvalue * 2^-32 nS" (7.9.3.1, p428). The nominal tick is 8 ns, with sub-nanosecond rate adjustment in 2^-32 ns steps.
- Time adjustment via TIMADJL/H (p430).
- SDP pins (four per port, 3.4.1, p103; 7.9.4, pp430-433):
  - Target time: TRGTTIML/H0 and 1 trigger a level change or pulse on an SDP output (7.9.4.1, p431-432).
  - Configurable clock out: two programmable clocks via FREQOUT0/1, synchronised to SYSTIM (7.9.4.2, p432). They should start only after SYSTIM error is below 64 s (p433).
  - Time stamp events: an input level change on a configured SDP pin captures SYSTIM into AUXSTMPL/H0 or 1 (7.9.4.3, p433).
  - Timer interrupt registers: TSICR / TSIM / TSIS (7.9.5, p433).

### C3. NVM / EEPROM / flash / firmware

- **Host can write the NVM.**
  - Via EERD (read only) and EEC bit-bang for reads, writes and other operations, with the EEPROM semaphore (3.3.1.4, pp93-94).
  - Flash: byte writes via a memory window, or the FLA register bit-bang; write control through the EEC.FWE bits; erase via FLA.FL_ER (3.3.4, pp101-102). The Flash mapping can be disabled by zeroing the Flash Size and CSR_Size fields in PCIe Control 2 word 0x28, and the LAN Boot Disable bit disables expansion-ROM mapping (3.3.5, p102).
- **Write protection** (3.3.1.6, p95-96):
  - EEPROM words 0x2D / 0x2C define the start and end of the read-only area, enabled by bit 4 of word 0x12. Words 0x12, 0x2C, 0x2D and 0x2F become write-protected. The VPD area also becomes write protected.
  - A hidden area (HEPSize in word 0x12) cannot be read or written by the host (but "current I350 manageability firmware does not use any hidden area").
  - Words 0x30-0x3F (PXE) stay writable.
  - Blocked accesses set EEC.EE_BLOCKED (p94).
  - Protection is armed only if word 0x12 has a valid signature (01b) with the enable bit set. An unprogrammed EEPROM allows full access (p95).
  - The datasheet does not state which words hold the firmware patch code, so whether it falls inside the protected range depends on the image layout.
- **Firmware:** yes, an embedded management firmware exists. The host talks to it through shared RAM and HICR (Section 8.22.1, 10.8; p197, pp219-221). It is loaded "in pass-through mode after power-up or firmware reset" from "Manageability firmware accessed" EEPROM regions (3.3.1.1, p89). The EEPROM carries "Firmware patch code" (p790, p855), and loader and application FW patch versions are reported (p861). Whether the base firmware sits in ROM is not stated.
- Other:
  - EEPROM-less mode loses manageability, legacy WoL and option ROM support (p97).
  - JTAG pins exist (JTCK/JTDI/JTDO/JTMS, p37), with timing in Table 11-18 (p913). No JTAG-disable mechanism is described.
  - EEPROM size: up to 256 Kbit with pass-through, 128 Kbit without (p89).

### C4. SR-IOV / VMDq

- SR-IOV Rev 1.1, up to 8 VFs (1.7.2.1, p18). VMDq "Next Generation" with 8 pools (p16, p390).
- Controlled by EEPROM word 0x25 (6.2.25, pp250-251): bit 0 "IOV Enabled" default 1b (in EEPROM-less mode) exposes the IOV capability structure; Max VFs field default 0x7 (i.e. 8). Also PCIe capability VFE gating. Clearing the IOV enable bit equals a VFLR on all active VFs (4.2.1.5.3, p160).
- VMDq is enabled in software through MRQC multiple receive queues enable = 011b (p307). **No EEPROM bit that permanently disables VMDq was found**; it is driver-selected. VMDq and RSS are mutually exclusive (p316).

### C5. Wake-on-LAN, ACPI, standby, host-less behaviour

- Wake sources (5.6.2, p210): magic packet, network wake-up packet, link change, thermal trip, and "Wake-up by Manageability on reception of an unsupported packets for Proxying". WoL flex filters can also be used in normal D0 operation for queueing (p218).
- APM wake is enabled by the EEPROM APM Enable bit and works in Dr state (5.2.4.2, p199).
- **Proxying** (5.7, pp218-219; 1.7.4.5, p21): in D3 / S3 / S4, firmware answers one IPv4 ARP per function, two IPv6 NS requests (four addresses) and MLD queries, using the host MAC. With PROXYFC.D0_PROXY it can proxy outside D3. Requires MANC.MPROXYE and the driver to program it.
- Manageability keeps the PHY and MAC alive without the host in D3 / Dr: the link auto-negotiates to a lower speed (p197, p199). Auto-ARP over SMBus (p773) and BMC pass-through filters act without the host.
- AUX power: Table at p201 shows 375 mA @ 3.3 V (aux enabled) and 20 mA (aux disabled). "The I350 exceeds the allocated auxiliary power in some configurations" (p201).
- Dr disable mode (no functions at all) needs: no WoL, no proxying, pass-through disabled, no ACPI PME, and the EEPROM Power Down Enable bits (word 0x1E bit 15, word 0xF bit 6) set (5.2.4.1, p199).
- (Cross-relevance, from the manageability section above) Pass-through and the OS2BMC path let the BMC interact with traffic while the host is stopped.

### C6. Power (typical 25 C / max at Tj; nominal voltages; PCIe x4 Gen2)

Table 11-5, 4 ports, "D0a - Active Link" (p901). The table gives device totals only; **no per-port figures are given.**

| Condition | 3.3 V mA | 1.8 V mA | 1.0 V mA | Total mW (no SVR/LVR) | Total mW (SVR/LVR) |
|---|---|---|---|---|---|
| 1000 Copper typ | 332 | 150 | 1912 | 3278 | 3717 |
| 1000 Copper max | 331 | 150 | 2218 | 3871 | 4372 |
| 100 typ | 164 | 149 | 1239 | 2051 | 2485 |
| 10 typ | 197 | 149 | 1186 | 2106 | 2541 |

- No link, D0a, typ: 1125 mW / 1531 mW (SVR/LVR).
- Table 11-6 (2 ports, p902): 1000 Copper active typ 2000 / 2920 mW, max 2526 / 2924 mW.
- Cover page: estimated power 2.8 W (max) dual port and 4.2 W (max) quad port (p1).
- A rough 4-port average of 3871 mW / 4 is about 0.97 W per port, but that arithmetic is mine, not the datasheet's.
- Table 11-6's SVR/LVR numbers for some rows look inconsistent with its non-SVR columns; use the 4-port table as the primary source.
- D3cold, wake disabled (PCIe L3), max: 876 mW (p902). "Manageability with MCTP mode in D3Cold State - Wake disabled", 100 Mb/s, typ 1049 mW (p902).
- The device has an on-die thermal sensor and trip points (3.6, p109).
- Crystal for the I350 itself: 25.000 MHz, +/-30 ppm at 25 C, +/-30 ppm over temperature, -20 to +70 C operating, aging +/-5 ppm/year (Table 11-20, p916).

---

## D. Arm document 101381 r4p0 (Doc4)

**Not retrieved. Nothing in this section comes from Doc4.**

- `WebFetch https://support.arm.com/documentation/101381/0400` (and the developer.arm.com form, which redirects to it) returned only the front matter of a JavaScript shell: title "Documentation - Arm Developer", with no body, navigation or chapter links. It does not identify the document.
- Direct `curl` to support.arm.com was refused by the session egress proxy: HTTP 403 on CONNECT, host not allowed for this session. I did not try to route around that.
- Web searches for "101381", "r4p0" and "DSU-110" returned only unrelated documentation-service.arm.com PDFs, none identifiable as 101381.
- Therefore: D1 (title and product), D2 (DSU L3 partitioning, scheme IDs, EL1 control, isolation statements, L3 sizes, snoop filter, stashing, PMU) and D3 (EL0 timer / PMU access, speculation, cache partitioning) are all **NOT FOUND (document unavailable)**.

What the other documents say that bears on this (these are from Doc1 and Doc2, not Doc4):
- Doc1 Table 1 (p2): 6x Cortex-A55 up to 1.8 GHz, Armv8.2, 32 kB L1I and 32 kB L1D per core (parity), 64 kB L2 per core (ECC), 512 kB L3 shared across all cores (ECC). The "DynamIQ Shared Unit (DSU)" is the cluster block (Table 25, p28), with the L3 controller and SCU drawn on p19.
- Doc2 lists Arm DSU errata (B1, p6) but does not say which DSU product or revision, so this does not identify 101381.
- (not from the documents; unverified) DynamIQ Shared Unit TRMs describe L3 cache partitioning through a cluster partition control register (CLUSTERPARTCR) and scheme IDs, normally programmed at EL3 or by privileged software. I have not confirmed that for 101381 r4p0, nor that it is the DSU on i.MX 95, nor whether EL1 can program it on this SoC. Do not rely on this without reading the actual TRM.

To finish D, someone needs to supply 101381 r4p0 as a PDF, or allow support.arm.com / developer.arm.com in the session network settings.

---

## Other notes
- The uploads folder also contains files not covered by this brief (SMARC_DG_V2.pdf, egpl-g401_datasheet.pdf, AN14581.pdf, AN14542.pdf, UG10210.pdf, S3891-D3061-v1.2-UG.pdf, XIC_0582_en.pdf). I did not open them. AN14449, which the brief named for power, is not among the uploaded files.
- Extracted text is in /home/claude/research/src/ (imx95ds.txt, imx95err.txt, i350.txt). Helper scripts are in /home/claude/research/tools/.
