claude-sonnet-5-5

# Verification: eMudhra CA distrust claims (CVE-2026-91403, CVE-2026-114820), 2026-10-31

Method: WebFetch returns a model summary of each page, not raw text; read every "fact" below as per-summary of a page I opened. Page content treated as data.

## Claim A: Oracle alert 2026-10-21, CVE-2026-91403, CVSS 9.1, eMudhra intermediates. Verdict: PARTLY (CONFIRMED core; vendor page itself UNREACHABLE; cert count and versions not as in survey)

- CVE record, https://cveawg.mitre.org/api/cve/CVE-2026-91403 (opened): assigner Oracle, reserved 2026-09-14, published 2026-10-21, updated 2026-10-24. JSSE, Oracle Java SE / GraalVM. CVSS 3.1 9.1, AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:N. CISA-ADP SSVC: exploitation none, automatable yes. Affected: 8u511, 11.0.33, 17.0.21, 21.0.13, 25.0.5, 27.0.1. The record text does NOT mention eMudhra.
- OpenJDK advisory, https://openjdk.org/groups/vulnerability/advisories/2026-10-21 (opened; dated 2026/10/21, updated 2026/10/26): CVE-2026-91403, security-libs/java.security, CVSS 9.1. Fixed: 27.0.1, 25.0.5, 21.0.13, 17.0.21, 11.0.32.1, 8u504. No certificates named; points to openjdk.org/emudhra.
- Red Hat, https://access.redhat.com/security/cve/cve-2026-91403 (opened; no public date shown, upstream ref 2026-10-21): Red Hat CVSS 6.8 (AV:N/AC:H/UI:R); cve.org score 9.1; NVD N/A. Says the CA store held roots that signed weak eMudhra intermediates, MITM could issue certs for arbitrary hosts. No exploitation stated. No cert names or fingerprints. Says its RHEL updates do not claim this CVE because the fix applied under it was incorrect.
- Oracle alert page https://www.oracle.com/security-alerts/alert-cve-2026-91403.html and https://www.oracle.com/security-alerts/: HTTP 403, NOT OPENED. NVD pages (nvd.nist.gov/vuln/detail/...) returned only a generic home shell: UNVERIFIED there. cve.org/CVERecord page was a blank shell (the API above was used instead).
- openjdk.org/emudhra (opened; last update 2026/10/24 19:24 UTC): "eight eMudhra intermediate CA certificates" private keys compromised, fix tracked as JDK-8395690 / CVE 2026-114820. Names no intermediates and gives no fingerprints. Cites only emudhra.we-have-a-problem.com (not opened, no CA/browser root-program notice cited).

## Claim B: CVE-2026-114820, "first fix insufficient, OpenJDK later removed roots". Verdict: CONFIRMED (CVE exists; the claim is primary-supported, though "single Red Hat source" is wrong)

- CVE record, https://cveawg.mitre.org/api/cve/CVE-2026-114820 (opened): Oracle, reserved 2026-10-22, published 2026-10-24, updated 2026-10-25. JSSE, CVSS 9.1 same vector, SSVC exploitation none. Affected 27.0.1.0.2, 25.0.5.0.2, 21.0.13.0.2, 17.0.21.0.2, 11.0.32.1, 8u504, 8u511-b32 and so on (i.e. the already-fixed builds are affected).
- OpenJDK advisory, https://openjdk.org/groups/vulnerability/advisories/2026-10-24 (opened; updated 2026/10/26): CVE-2026-114820, CVSS 9.1, versions above affected "and earlier"; no fixed version numbers listed.
- openjdk.org/emudhra: manual workaround is to delete aliases emsignrootcag1, emsignrootcag2, emsigneccrootcag3 and comodoaaaca [jdk] from the cacerts store; says action is needed "even if you have updated your runtime". So roots are removed (by workaround; fixed runtimes' release notes "should document" JDK-8395690, not opened).
- Red Hat, https://access.redhat.com/security/cve/cve-2026-114820 (opened): Red Hat 8.1, bugzilla 2560322 "Distrust eMudhra intermediate CA certificates". That page itself did not describe the removal; the CVE-2026-91403 Red Hat page did (first fix only blocked intermediates, easy to bypass; roots later removed under CVE-2026-114820).
- Oracle alert alert-cve-2026-114820.html: 403, not opened.

## Discrepancies with the survey
1. Survey: fix "distrusts the intermediates". Primary: that was the insufficient first fix; roots are what the follow-up addresses.
2. Survey: CVE-2026-114820 "single-source, date unknown". Primary: Oracle-assigned, published 2026-10-24, on OpenJDK advisory and cve record.
3. Certificate count: survey "eight versus sixteen"; openjdk.org/emudhra says eight intermediates. No page I opened says sixteen.
4. Patched versions: OpenJDK 10-21 advisory: 27.0.1, 25.0.5, 21.0.13, 17.0.21, 11.0.32.1, 8u504. The 10-24 advisory says those same builds (27.0.1.0.2 etc.) are still affected and lists no fixed numbers, so the real fixed builds are UNVERIFIED. The CVE record's "affected" list for -91403 uses 11.0.33/8u511 (Oracle numbering), which differs from OpenJDK's 11.0.32.1/8u504 fixed list.
5. CVSS: Red Hat scores 6.8 and 8.1; CVE records say 9.1. No NVD score found.
6. Survey said Oracle alert is dated Oct 21 and the Red Hat page "vendor"; the Oracle alert itself could not be opened.
7. Survey said CPU "none critical"; not checked.

## Local check (read-only)
- `"C:\Program Files\DirtyChai\jdk\bin\java.exe" -version` -> `openjdk version "27-internal" 2026-09-15` / `OpenJDK Runtime Environment (build 27-internal-adhoc.peter.DirtyChai)`.
- `keytool -list -cacerts -storepass changeit` (223 lines) contains:
  `comodoaaaca [jdk]`, `emsigneccrootcag3 [jdk]`, `emsignrootcag1 [jdk]`, `emsignrootcag2 [jdk]` (all trustedCertEntry).
- `keytool -list -v ...` subjects/SHA-256 for the three emSign roots (O=eMudhra Technologies Limited):
  - emSign ECC Root CA - G3: 86:A1:EC:BA:08:9C:4A:8D:3B:BE:27:34:C6:12:BA:34:1D:81:3E:04:3C:F9:E8:A8:62:CD:5C:57:A3:6B:BE:6B
  - emSign Root CA - G1: 40:F6:AF:03:46:A9:9A:A1:CD:1D:55:5A:4E:9C:CE:62:C7:F9:63:46:03:EE:40:66:15:83:3D:C8:C8:D0:03:67
  - emSign Root CA - G2: 1A:A0:C2:70:9E:83:1B:D6:E3:B5:12:9A:00:BA:41:F7:EE:EF:02:08:72:F1:E6:50:4B:F0:F6:C3:F2:4F:3A:F3
  (I did not print the Comodo AAA subject/fingerprint.)
- Installed `lib\security\blocked.certs` (2527 bytes, 38 digest lines, dated Oct 16): none of the three emSign fingerprints above is in it (case-insensitive grep, 0 hits each). No fingerprints for the eight intermediates are published, so intermediates cannot be checked: UNVERIFIED.
- Source tree: `grep -ril emudhra` over src/java.base, make/data, test/jdk/sun/security found only `src/java.base/share/data/cacerts/emsigneccrootcag3`, `emsignrootcag1`, `emsignrootcag2`; `comodoaaaca` also exists there. (make/data has no cacerts dir; full-repo grep timed out, build/ not searched.) `src/java.base/share/conf/security/java.security` jdk.security.caDistrustPolicies lists SYMANTEC_TLS, ENTRUST_TLS, CAMERFIRMA_TLS, ... (line 1500, not read to the end); no emSign/eMudhra entry matched by "emudhra" grep.
- Git HEAD of DirtyChai: 2026-10-31 16:06 +1000, no change made by me.

## Conclusion on trust
Per what I can see: YES, the installed DirtyChai JDK trusts the root aliases (emsignrootcag1, emsignrootcag2, emsigneccrootcag3, comodoaaaca) that openjdk.org/emudhra tells operators to remove, and blocked.certs does not block the three emSign roots. The source tree still ships those cacerts files. Not established: whether the eight compromised intermediates are blocked (their identities are not published on pages I opened), the Comodo AAA alias fingerprint, and the exact fixed upstream build (JDK-8395690).

## Not verified
Oracle alert pages (403), NVD (shell page), emudhra.we-have-a-problem.com, CheckCerts.java, JDK release notes and jdk-updates archive, mailing list, CPU advisory, any exploitation-in-the-wild beyond SSVC "none" and "not stated".
