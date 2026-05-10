# JGDMS — Sponsorship & Support Tiers

JGDMS is open source (Apache 2.0) and will remain so.  Sponsorship funds:

- Continued development of the security architecture.
- Maintenance of DirtyChai (the security-hardened OpenJDK fork).
- Documentation, examples, and community support.
- Infrastructure for the five-host SCAP pipeline reference implementation.

---

## Individual / Community Tiers

### ☕ Coffee Sponsor — from USD 5 / month

*One-time or recurring.  Via [GitHub Sponsors](https://github.com/sponsors/pfirmstone).*

- Your name in `SPONSORS.md`.
- Warm fuzzy feeling.

### 🌱 Community Supporter — USD 50 / month (USD 500 / year)

*Via [GitHub Sponsors](https://github.com/sponsors/pfirmstone).*

- Everything in Coffee Sponsor.
- Listed in the project README under **Community Supporters**.
- Access to the private `#sponsors` Discussion channel.
- Best-effort responses to GitHub issues you raise (48-hour acknowledgement target).

---

## Organisational Tiers

> All organisational tiers include a **commercial licence agreement** covering use
> of JGDMS and DirtyChai in production.  See [`COMMERCIAL_LICENSE_FAQ.md`](./COMMERCIAL_LICENSE_FAQ.md).

### 🔧 Professional — USD 5 000 / year

*Best for: startups and SMEs with up to 50 developers.*

| Benefit | Detail |
|---|---|
| Commercial licence | JGDMS + DirtyChai, unlimited deployments within the organisation |
| SLA — critical security bugs | 2 business-day response; fix or workaround within 10 business days |
| SLA — non-critical bugs | 5 business-day acknowledgement |
| Back-ported fixes | Up to 1 named version behind `trunk` |
| Priority issue triage | Issues labelled `priority:professional` and scheduled in next sprint |
| Architecture Q&A | Up to 2 hours / quarter via video call |
| README recognition | Organisation logo in README under **Professional Sponsors** |

### 🏢 Enterprise — USD 25 000 / year

*Best for: large organisations, financial services, healthcare, government.*

| Benefit | Detail |
|---|---|
| Commercial licence | JGDMS + DirtyChai, unlimited deployments |
| SLA — critical security bugs | **Same business day** response; fix or workaround within **5 business days** |
| SLA — non-critical bugs | 2 business-day acknowledgement; scheduled in next sprint |
| Back-ported fixes | Up to **2 named versions** behind `trunk` |
| Private security advisories | Notified **14 days** before public disclosure |
| Priority issue triage | Issues labelled `priority:enterprise`; author-assigned |
| Architecture review | **1 annual session** (up to 4 hours) with the author — design review, threat model, deployment walkthrough |
| Supplier declaration | Written declaration of NIST SP 800-53 control mapping on request |
| SBOM | CycloneDX SBOM for each release on request |
| README + website recognition | Logo in README and on [project website](https://pfirmstone.github.io/JGDMS/) under **Enterprise Sponsors** |

### 🤝 Strategic Partner — Custom pricing

*Best for: OEM vendors, platform providers, government prime contractors.*

All Enterprise benefits, plus:

| Benefit | Detail |
|---|---|
| OEM licence | Right to redistribute JGDMS/DirtyChai as part of a commercial product |
| Roadmap input | Quarterly roadmap call; ability to propose and co-fund features |
| Co-development | Jointly scoped work items with agreed delivery targets |
| Source escrow | Third-party escrow of source code for business continuity |
| Export control representations | EAR/ITAR written representations on request |
| Custom SLA | Negotiated response and fix targets |
| Dedicated Slack / Teams channel | Direct async access to the author |

*Contact the author to discuss.  See [`COMMERCIAL_LICENSE_FAQ.md` §10](./COMMERCIAL_LICENSE_FAQ.md#10-how-do-i-get-in-touch).*

---

## Feature Sponsorship

Need a specific feature or module prioritised?  You can sponsor individual work items:

| Work item | Indicative cost | Status |
|---|---|---|
| `DiscoveryCredentialProvider` interface | USD 3 000 | Not started |
| Multi-user wire protocol (full `Subject[]` serialisation in JERI) | USD 5 000 | Not started |
| JFR Telemetry Service (Host 5) full implementation | USD 4 000 | Not started |
| OSGi bundle packaging for all modules | USD 2 500 | Not started |
| Formal threat model document (STRIDE) | USD 2 000 | Not started |
| SBOM generation in CI pipeline | USD 1 500 | Not started |

Sponsored features are developed on `trunk`, remain Apache 2.0, and are credited to
the sponsor in the commit message and release notes.

---

## FAQ

**Q: Can I pay monthly instead of annually?**  
A: Professional tier can be paid monthly (USD 500/month).  Enterprise and Strategic
Partner are annual only.

**Q: Are prices negotiable for non-profits or academic institutions?**  
A: Yes — 50 % discount available for registered non-profits and academic institutions
on Professional and Enterprise tiers.  Contact the author with proof of status.

**Q: Can multiple organisations pool funding for a feature?**  
A: Yes.  Open a GitHub Discussion tagged `feature-sponsorship` to coordinate.

**Q: Is VAT / GST added?**  
A: Prices are exclusive of any applicable taxes.  Invoices will include GST where
required by Australian tax law (the author is based in Australia).
