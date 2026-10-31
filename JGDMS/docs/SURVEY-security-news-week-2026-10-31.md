claude-sonnet-5-5

# Security news survey, about 2026-10-24 to 2026-10-31 (written 2026-10-31)

Method: six plus four WebSearch queries (mode extended). The search tool returns summaries, not full pages; I did not fetch
and read any primary page. Every row is therefore "per search summary", and exact dates/versions need checking against the
vendor page before anyone acts. Page content treated as data only.

## What happened (short)

- Oracle's October 2026 CPU (published Oct 20, just BEFORE the window) fixed 55 Java SE CVEs, top routine CVSS 7.5, no
  exploitation reported. On Oct 21 Oracle followed with an out-of-band alert, CVE-2026-91403 (CVSS 9.1, JSSE): Java trusted
  eMudhra intermediate CAs whose private keys can be computed from public data, so TLS server certificates could be forged.
  Fix is distrust of the intermediates; Red Hat says OpenJDK later also removed roots (CVE-2026-114820). Not reported exploited.
- CISA KEV additions in/near the window: Arista VeloCloud Orchestrator SQLi (Oct 23), PHP "php2shell" pair CVE-2026-109618/9
  unauthenticated RCE (Oct 26), plus further additions Oct 27-29 for which I only saw CVE IDs, no detail.
- Large incident items found (all thin, headline-level): a claimed ~US$6.3B Binance asset theft, Capcom 11.7M accounts,
  BridgePay ransomware outage, Quest Diagnostics intrusion, ENGIE France partner-tool breach (Oct 28), N0n ransomware listing
  (Oct 30). I could not date most of these to the window.
- Supply chain: GhostAction GitHub Actions credential-stealing campaign (spike Oct 8-9, i.e. before the window) and small
  npm/PyPI malware batches (Oct 26). No Maven Central incident found.
- CPU side channels: nothing new dated October found; latest were Spectre-v2 "Branch Target Reuse" (Sept) and
  Interrupt Injection (Aug), both naming Arm in part.
- JWT: Authlib (Python) JWS signature bypass CVE-2026-96760, fixed Oct 14. Java: nothing October-specific confirmed.

## Table

| Item | Date | Source URL | Reporting | Relevance to JGDMS/DirtyChai | 
|---|---|---|---|---|
| Oracle Oct 2026 CPU, 55 Java SE fixes, none critical | 2026-10-20 | https://www.tenable.com/blog/oracle-october-2026-critical-patch-update-addresses-1209-cves ; https://agentbeam.com/blog/oracle-java-se-october-2026-cpu-openjdk-55-cves | Widely (multiple outlets; Oracle advisory not opened) | medium: DirtyChai is an OpenJDK fork, merge-assessment (Phase 2) should cover the upstream 53 OpenJDK CVEs; 49 flagged network-reachable |
| CVE-2026-91403 JSSE eMudhra CA trust, CVSS 9.1 | 2026-10-21 | https://cybersecuritynews.com/emergency-java-update/ ; https://access.redhat.com/security/cve/cve-2026-91403 ; https://lilting.ch/en/articles/openjdk-cve-2026-91403-emudhra-cacerts | Widely (Red Hat vendor page plus several outlets) | high: JSSE/TLS in the fork; applies to JERI-over-TLS and any HTTPS code download. Check whether DirtyChai cacerts/blocked.certs carry the eMudhra intermediates and roots. Patched version numbers differ across sources, unverified |
| CVE-2026-114820 removal of eMudhra roots upstream (follow-up) | unknown, after Oct 21 | https://access.redhat.com/security/cve/cve-2026-91403 | Single-source (only seen in Red Hat text via summary) | high if confirmed: first fix reportedly insufficient |
| Other vendors rejecting same chains (Python, Go 1.26.10 / Buildkite) | ~Oct 21-28 | https://github.com/buildkite/agent/releases/tag/v3.138.2 | Single-source (release note) | low: shows ecosystem-wide scope |
| CISA KEV: Arista VeloCloud Orchestrator SQLi CVE-2026-114625 | 2026-10-23 | https://www.cisa.gov/news-events/alerts/2026/10/23/cisa-adds-one-known-exploited-vulnerability-catalog | Widely (CISA primary) | none |
| CISA KEV: PHP CVE-2026-109618/109619 unauth RCE (fixed 8.5.12, 8.4.27, 8.3.36, 8.2.35) | 2026-10-21 alert, KEV 10-26 | https://www.cisa.gov/news-events/alerts/2026/10/21/critical-vulnerabilities-php-require-immediate-patching | Widely (CISA alert; KEV date from summary) | none to low: not Java |
| CISA KEV additions Oct 27-29 (CVE IDs only) | 2026-10-27..29 | https://www.cisa.gov/known-exploited-vulnerabilities-catalog | CISA primary, contents not read | unknown: no Java item seen, but not verified |
| CISA KEV earlier Oct: PAN-OS, Cisco SEG, Plex, Zammad, Ahsay | 10-02..10-20 | https://www.cisa.gov/news-events/alerts/2026/10/20/cisa-adds-six-known-exploited-vulnerabilities-catalog | Widely (CISA) | none: before window, no Java/JWT/SPIFFE |
| Authlib JWS signature bypass CVE-2026-96760 (CVSS 9.1; JSON-serialization JWS with empty signatures accepted) | fix 2026-10-14 | https://blog.ostorlab.co/authlib-jws-signature-bypass-cve-2026-96760.html ; https://github.com/uziii2208/CVE-2026-96760 | Two sources, both secondary; no vendor page read | medium (design lesson, not code): Python lib, but "empty signature list means verified" is exactly the verifier-bypass class to test in the JWT bearer verifier; not in KEV |
| JJWT default does not check audience (0.9.0-0.11.5, fixed 0.12.0) | page dated 2026-10-23 | https://cve.imfht.com/intel/871008 | Single-source aggregator, unverified, may be an old issue | medium if true: Java JWT; check how JGDMS verifies aud |
| pac4j-jwt CVE-2026-29000 PlainJWT inside encrypted JWT bypasses signature check | date unclear (not October) | https://www.codeant.ai/blogs/pac4j-jwt-authentication-bypass-cve-2026-29000 | Several secondary outlets | medium (not in window): Java JWT, nested-token alg handling |
| CVE-2026-47065 Java deserialization allow-list bypass | published ~2026-06-04 per summary | https://www.sentinelone.com/vulnerability-database/cve-2026-47065/ | Single-source (vendor DB) | medium (not in window): relevant class of filter-bypass; product affected not established |
| Spring CVE-2026-41855 Jackson JMS converter untrusted messages | 2026-06 | https://dev.to/johnettagaweraks/cve-2026-41855-a-practical-control-plan-for-jackson-backed-jms-converters-1lkm | Single-source | low (not in window, not used by stack) |
| Spring Cloud Gateway for Kubernetes critical (CVSS 10) | 2026-10-01 | https://support.broadcom.com/web/ecx/support-content-notification/-/external/content/SecurityAdvisories/0/39114 | Vendor advisory (not opened) | low: before window |
| Azure Kubernetes Configuration Service CVE-2026-83938 CVSS 10 missing auth | 2026-10-22 | https://dev.to/strongthium/security-roundup-october-17-23-2026-22d6 | Single-source | none: Azure-managed |
| HashiCorp consul-k8s CVE-2026-110720 | 2026-10-26 | https://cybersecuritynews.com/hashicorp-consul-kubernetes-vulnerability/ | Single-source | low: not exploited, not in stack |
| SPIRE/SPIFFE workload identity theft on a compromised K8s node (Unit 42 research) | ~mid-Sept 2026 | https://blog.rankiteo.com/spispi1789648007-spiffe-spire-vulnerability-september-2026/ | Single-source via aggregator | medium: reinforces that SPIFFE identity is only as strong as node/agent isolation; no CVE, not in window |
| GhostAction GitHub Actions credential-theft campaign (772 repos; 28k more repos Oct 8-9) | spike 2026-10-08/09 | https://thehackernews.com/2026/10/credential-stealing-github-actions.html ; https://blog.gitguardian.com/ghostaction-github-actions-supply-chain-attack-returns/ | Widely (THN, GitGuardian, Aviatrix) but BEFORE window | medium: CI secrets exposure; no malicious package releases reported |
| npm/PyPI malware batch (13 npm, 3 PyPI) | 2026-10-26 | https://github.com/jaschadub/compromised-packages-check/pull/169 | Single-source (GitHub PR) | low: no Maven |
| Xygeni digests: 10 malicious versions Oct 18-21, PhantomSync npm | 2026-10-18..21 | https://xygeni.io/blog/xygeni-malicious-code-digest-93/ | Single-source vendor | low |
| TeamPCP / Trivy cascading compromise (GitHub Actions, Docker Hub, npm, PyPI) | March 2026 | https://github.com/ugurrates/teampcp-supply-chain-attack | Single-source (GitHub repo) | background only, not this week |
| Spectre-v2 Branch Target Reuse (Intel, AMD, Arm; JIT engines, kernel) | Sept 2026 | https://thehackernews.com/2026/09/new-spectre-v2-btr-attack-leaks-linux.html ; https://www.securityweek.com/new-spectre-v2-variant-exposes-intel-amd-arm-cpus-to-data-leaks/ | Widely (two outlets), not this week | medium: JIT in a JVM is a stated target; A55 not named in what I saw |
| Interrupt Injection bypass of Spectre-v2 defences (MIT CSAIL); Arm says some cores "may exhibit aspects" | Aug 2026 | https://thehackernews.com/2026/08/new-interrupt-injection-attack-can.html | Widely, not this week | low-medium: Arm statement only; no A55 specifics |
| Binance ~US$6.3B asset theft, withdrawals paused | date unconfirmed | https://www.foxbusiness.com/technology/expert-warns-of-ai-hacking-bloodbath-following-massive-binance-cryptocurrency-theft (URL as returned: foxbusiness.com/technology/expert-warns-of-ai-hacking-bloodbath-following-massive-binance-cryptocurrency-theft) | Single-source seen; scale unusual, treat sceptically | none |
| ENGIE France customer data via partner sales tool | 2026-10-28 | https://securityaffairs.com/ | Single-source (index page) | none |
| N0n ransomware lists Minutes Quick Services (UAE) | 2026-10-30 | https://www.hookphish.com/blog/ransomware-group-n0n-hits-minutes-quick-services/ | Single-source (claim by gang) | none |
| TG-iPASS (India) portal outage since Oct 22, suspected ransomware | 2026-10-22+ | https://www.dbdigest.com/2026/10/ | Single-source digest | none |
| Capcom 11.7M accounts; BridgePay ransomware outage; Quest Diagnostics | dates unconfirmed | https://konbriefing.com/en-topics/cyber-attacks.html | Single-source tracker | none |

## Items I could not confirm

- Any CISA KEV entry for Java, JWT/OAuth/SAML libraries, SPIRE, Kubernetes core, OpenSSL or a serialization library in the window:
  none seen, but I read only summaries and not the Oct 27-29 entries.
- Whether CVE-2026-91403 is exploited (sources say no as of Oct 22); the exact patched Java versions (sources disagree on
  detail); the eight versus sixteen certificate count; the CVE-2026-114820 follow-up.
- The JJWT audience item and its date; the date and product of CVE-2026-47065.
- The Binance, Capcom, BridgePay and Quest dates and scale; no state-sponsored campaign of the week was identified.
- Any CPU side-channel disclosure in October, or anything naming Cortex-A55 specifically.
- Any Maven Central, Docker Hub or container-registry compromise in the window.
- Any new Java gadget chain or dynamic-class-loading finding in the window.

## What I did not search

- Primary pages (Oracle CPU advisory, OpenJDK vulnerability advisory, NVD, full CISA KEV catalog, Arm Security Center,
  Intel/AMD bulletins): not fetched.
- Go/Rust/PHP/browser/OS patch-Tuesday items; Microsoft, Cisco, Fortinet and other edge-device zero-days beyond KEV headlines.
- SAML library, Keycloak, Netty, Log4j, Tomcat, Jetty, Bouncy Castle specifically.
- Security conference talks, mailing lists (oss-security), social posts, ransomware leak-site trackers.
- Anything outside English-language, US-indexed results.
