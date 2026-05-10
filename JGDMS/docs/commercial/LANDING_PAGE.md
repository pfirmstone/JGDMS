# JGDMS — Secure Distributed Java Services

> **Zero-trust microservice infrastructure for the JVM.**  
> Dynamic service discovery · SPIFFE/SPIRE workload identity · Supply-chain verified codebases · Fine-grained dynamic policy.

[GitHub](https://github.com/pfirmstone/JGDMS) ·
[Documentation](https://pfirmstone.github.io/JGDMS/) ·
[Commercial Licensing](./COMMERCIAL_LICENSE_FAQ.md) ·
[Sponsorship](./SPONSORSHIP_TIERS.md) ·
[Discussions](https://github.com/pfirmstone/JGDMS/discussions)

---

## What is JGDMS?

JGDMS is an open-source Java platform for building **secure, dynamically discoverable
microservices** on IPv6 networks.  It extends and modernises the Apache River / Jini
architecture with a defence-in-depth security model designed for production environments
where supply-chain integrity and workload identity matter.

It is built on top of **DirtyChai** — a security-hardened fork of OpenJDK — and is
designed to run as a cohesive platform rather than a collection of independent
libraries.

---

## Why JGDMS?

### The problem with conventional microservice security

| Conventional approach | Problem |
|---|---|
| Static policy files deployed with the service | Cannot react to runtime context; no per-proxy scoping |
| Service mesh for mTLS | Identity at the network layer only; code inside the pod is implicitly trusted |
| Manual dependency auditing | Slow, human-error-prone, not integrated into the class-loading path |
| Kerberos / username+password | Credential leakage; offline attack risk; no workload-native identity |

### The JGDMS approach

| JGDMS capability | What it does |
|---|---|
| **SPIFFE/SPIRE workload identity** | Every JVM process has a cryptographic X.509 SVID, provisioned automatically by SPIRE. No passwords. |
| **Five-host SCAP pipeline** | Every proxy JAR is bytecode-analysed by an isolated BAE pool before it is ever unmarshalled. Dangerous codebases are refused at class-load time. |
| **Dynamic policy** | Permissions are granted at proxy-preparation time, scoped to the exact (user principal ∩ workload principal ∩ codebase). Revoked automatically when the proxy is garbage-collected. |
| **Three-layer policy stack** | Bootstrap → djinn-wide → per-proxy. Each layer revocable independently. |
| **Content-hash grants** (`DigestGrant`) | Policy can condition grants on the SHA-256 hash of the JAR, not just its URL. |
| **JWT/OIDC human identity** | Per-request user identity injected into every permission check via `Subject.callAs()` and `ScopedValue`. |
| **@AtomicSerial wire protocol** | Every serialised object crossing a JERI wire is validated atomically — no partial-deserialisation gadget chains. |

---

## Architecture at a Glance

```
┌─────────────────────────────────────────────────────────┐
│                    Client JVM                           │
│  Subject.callAs(jwtUser, () -> {                        │
│    proxy.submitOrder(order);   ← dynamic grant checked  │
│  });                                                    │
└────────────────────┬────────────────────────────────────┘
                     │  JERI / TLS + SPIFFE mTLS
┌────────────────────▼────────────────────────────────────┐
│                  Service JVM                            │
│  WorkerSubject (SPIFFE SVID) — ambient in every PD      │
│  UserSubject   (JWT claims)  — scoped per request       │
│  Three-layer policy stack: SpiffePolicyFile             │
│    └── RemotePolicyProvider (InMemoryPolicyService)     │
│          └── DynamicPolicyProvider (per-proxy, GC-scoped│
└─────────────────────────────────────────────────────────┘

          Before any proxy JAR is unmarshalled:

 Host 4 (Downloader) → Host 2 (BAE Pool) → Host 3 (VerdictRegistry)
                                                    │
                              ProxyCodebaseSpi ◄────┘  (SAFE verdict required)
```

**Supply-chain pipeline hosts:**

| Host | Role | Isolation |
|---|---|---|
| 1 — Lookup Service | Service registry | Passive; stores opaque items only |
| 2 — BAE Pool | Bytecode analysis (ASM) | SELinux-isolated; no outbound internet; no direct path to Host 3 |
| 3 — Verdict Registry | Signs and stores analysis verdicts | Holds signing key; never parses bytecode |
| 4 — Codebase Downloader | Fetches JARs from the internet | Only host with outbound internet; no client-facing ports |
| 5 — JFR Telemetry | Virtual-thread pinning monitoring | Reactive only; isolated from pipeline |

---

## Key Security Properties

### Principle of Least Privilege — all three axes simultaneously

```
Effective permission  =  what the JAR declares (PERMISSIONS.LIST)
                       ∩ what the administrator allows (GrantPermission in RemotePolicyProvider)
                       ∩ what is scoped to this (user ∩ workload ∩ codebase)
```

No single party controls the outcome.  A compromised proxy JAR cannot escalate
privileges beyond what the administrator has pre-approved for that specific
SPIFFE workload identity and user principal combination.

### Automatic revocation

Dynamic grants are tied to the proxy's `ClassLoader` via weak reference.  When the
proxy is abandoned, the grant becomes void and is swept by a background daemon thread.
No manual revocation step is required.

### Supply-chain safety at class-load time

The `ProxyCodebaseSpi` gate queries the Verdict Registry **before** creating a
`ClassLoader` for an incoming proxy JAR.  A single `DANGEROUS` verdict from any BAE
instance refuses the codebase.  A quorum of `SAFE` verdicts is required to proceed.

The BAE detects:
- Blocking calls inside `<clinit>` (class-loading DoS)
- `@AtomicSerial` protocol violations (gadget-chain risk)
- Circular `<clinit>` dependencies (deadlock risk)
- Permission classes declared in `PERMISSIONS.LIST` that guard blocking sinks (privilege escalation → DoS)

---

## Who is JGDMS for?

| Sector | How JGDMS helps |
|---|---|
| **Financial services** | Zero-trust internal service mesh; auditable code supply chain; per-transaction user identity in every permission check |
| **Healthcare (HIPAA)** | Workload identity provisioned by SPIRE; no shared secrets; automatic permission revocation |
| **Government / defence** | SCAP pipeline maps to NIST SP 800-53 supply-chain controls; SBOM-ready; export-control representations available |
| **Cloud-native Java platforms** | Drop-in SPIFFE integration; dynamic service discovery over IPv6; OSGi-compatible module structure |
| **IoT / edge** | Lightweight Jini discovery over IPv6; no central broker required; services self-announce |

---

## Quick Start

> **Prerequisites:** JDK 21+ (DirtyChai recommended), Maven 3.9+, SPIRE agent running locally.

```bash
# Clone
git clone https://github.com/pfirmstone/JGDMS.git
cd JGDMS

# Build
mvn clean install -DskipTests

# Run the example lookup service
cd qa/jtreg-tests
# See JGDMS/docs/ for full configuration guide
```

Full documentation: [https://pfirmstone.github.io/JGDMS/](https://pfirmstone.github.io/JGDMS/)

---

## Open Source & Commercial

JGDMS is licensed under the **Apache License 2.0** — free to use, modify, and deploy
in commercial products.

For organisations that need contractual SLAs, back-ported security fixes, private
advisories, or architecture review sessions, **commercial support agreements** are
available.

→ [Commercial Licensing FAQ](./COMMERCIAL_LICENSE_FAQ.md)  
→ [Sponsorship & Support Tiers](./SPONSORSHIP_TIERS.md)

---

## Current Sponsors

*Be the first!*  
[Become a sponsor →](https://github.com/sponsors/pfirmstone)

---

## Contributing

Contributions are welcome.  Please read the
[contribution guidelines](../../CONTRIBUTING.md) and open an issue before submitting
a large pull request.

Security vulnerabilities should be reported privately via GitHub's
[Security Advisories](https://github.com/pfirmstone/JGDMS/security/advisories) feature.

---

## Licence

Copyright © 2016 – 2026 Peter Firmstone and contributors.  
Licensed under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
