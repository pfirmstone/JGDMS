# JGDMS — Commercial Licensing FAQ

> **JGDMS** is open source software licensed under the
> [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
> Apache 2.0 is a permissive licence — you can use, modify, and distribute
> JGDMS in commercial products **without paying a fee**, provided you retain
> the copyright notice and licence text.
>
> So why is a commercial licence offered at all?  Read on.

---

## 1. Do I need a commercial licence to use JGDMS?

**For most uses: no.**  Apache 2.0 is deliberately permissive.  You can:

- Deploy JGDMS in production inside your company.
- Bundle JGDMS modules in a commercial product.
- Modify the source and keep your changes private.
- Sub-license JGDMS as part of a larger work.

All of the above are free under Apache 2.0, forever.

---

## 2. What does a commercial licence add?

A commercial licence agreement (available on request) provides:

| What you get | Why it matters |
|---|---|
| **Contractual SLA** — guaranteed response times for critical security bugs | Apache 2.0 provides the software "AS IS"; no warranty or SLA |
| **Indemnification clause** — supplier-side IP indemnity | Satisfies enterprise legal / procurement requirements |
| **Named-version support** — fixes back-ported to your pinned version | Community support tracks `trunk` only |
| **Priority issue triage** — your GitHub issues labelled and scheduled in next sprint | Community issues triaged on a best-effort basis |
| **Architecture review** — one annual design-review session with the author | Not available to anonymous community users |
| **Private security advisories** — CVE-equivalent disclosures before public release | Public advisories go to all users simultaneously |

---

## 3. What are the commercial licence tiers?

See [`SPONSORSHIP_TIERS.md`](./SPONSORSHIP_TIERS.md) for full pricing and tier details.

Briefly:

| Tier | Best for | Annual fee (indicative) |
|---|---|---|
| **Supporter** | Individuals / startups | USD 500 / year |
| **Professional** | SMEs (< 50 developers) | USD 5 000 / year |
| **Enterprise** | Large organisations | USD 25 000 / year |
| **Strategic Partner** | OEM / platform vendors | Custom — contact author |

---

## 4. Can I use JGDMS in a SaaS product without a commercial licence?

**Yes.**  Apache 2.0 has no SaaS / network-copyleft clause (unlike AGPL).  Running
JGDMS on a server and offering the result as a service does not trigger any licence
obligation beyond retaining the copyright notice.

However, if your SaaS product **competes directly** with a hosted JGDMS offering
(e.g. a managed SCAP pipeline service), we ask — though cannot legally require — that
you contribute improvements upstream or take a Strategic Partner licence.

---

## 5. Does the commercial licence cover DirtyChai as well?

[DirtyChai](https://github.com/pfirmstone/DirtyChai) is a separate repository and is
licensed independently.  A commercial support agreement covering JGDMS can be extended
to cover DirtyChai at no additional tier cost — ask when negotiating.

---

## 6. I am a government or defence contractor. Any specific terms?

Yes.  Government / defence deployments often require:

- A **supplier declaration of conformance** to NIST SP 800-53 or equivalent.
- **SBOM (Software Bill of Materials)** in CycloneDX or SPDX format.
- **Source escrow** arrangements.
- Custom **export control** (EAR/ITAR) representations.

All of the above can be accommodated under a custom Strategic Partner agreement.
Contact the author directly (see §10 below).

---

## 7. Is the commercial licence OSI-approved?

No.  The commercial licence is a bespoke contract layered **on top of** Apache 2.0,
not a replacement for it.  You always retain your Apache 2.0 rights; the commercial
licence adds contractual obligations and benefits on both sides.

---

## 8. What happens if I stop paying?

Your Apache 2.0 rights are irrevocable — you can continue using the version you already
have.  You lose:

- SLA coverage.
- Back-ported security fixes.
- Priority issue triage.
- Architecture review sessions.

---

## 9. Can I try before I buy?

**Yes.**  A 90-day evaluation period is available for Professional and Enterprise tiers:
you receive full SLA coverage during evaluation.  No payment is required upfront.
At the end of the evaluation you may purchase, negotiate, or walk away — with no
obligation and no change to your Apache 2.0 rights.

---

## 10. How do I get in touch?

Open a [GitHub Discussion](https://github.com/pfirmstone/JGDMS/discussions) with the
tag **`commercial`**, or email via the contact on the
[GitHub profile page](https://github.com/pfirmstone).

Please include:
- Your organisation name and size (number of developers).
- How you use / plan to use JGDMS.
- Which tier you are considering.

We aim to respond within 5 business days.
