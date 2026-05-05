# JGDMS SPIFFE/SPIRE Administration and Deployment Guide

## Overview

JGDMS uses [SPIFFE](https://spiffe.io/) X.509-SVID certificates as the identity layer for all JERI TLS connections in the five-host Standard Codebase Audit Pipeline (SCAP).  Each host process and each client JVM obtains a short-lived (≈1 hour) X.509 certificate — a **SVID** (SPIFFE Verifiable Identity Document) — from a SPIRE agent running on the same host.  The SVID is used directly as the TLS client/server certificate.  No JAAS `LoginModule` configuration, no Java keystores, and no long-lived certificate files are required.

---

## Architecture Summary

```
┌─────────────────────────────────────────────────────────────────┐
│ JGDMS service JVM                                               │
│                                                                 │
│  SpiffeCredentialManager                                        │
│    │ reads svid.pem / svid_key.pem (written by SPIRE agent)    │
│    │ populates Subject (CertPath + X500PrivateCredential)       │
│    │ registers Subject in SpiffeSubjectHolder (process-wide)    │
│    └─ schedules background renewal before SVID expiry           │
│                                                                 │
│  SslEndpointImpl / SslServerEndpointImpl                        │
│    └─ consults SpiffeSubjectHolder when no Subject.doAs() frame │
└─────────────────────────────────────────────────────────────────┘
        ↑ writes PEM files every ~1 h
┌───────────────────────────────┐
│ SPIRE agent (same host)       │
│  SVIDStore "disk" plugin      │
│    svid.pem                   │
│    svid_key.pem               │
│    bundle.pem                 │
└───────────────────────────────┘
        ↑ attests workload; fetches SVID
┌───────────────────────────────┐
│ SPIRE server (trust domain)   │
│  registration entries define  │
│  SPIFFE ID per workload       │
└───────────────────────────────┘
```

---

## SPIFFE ID Naming Convention

The SPIFFE ID embedded in each SVID's URI SubjectAlternativeName (SAN) identifies the workload.  JGDMS follows this scheme (trust domain is site-specific, e.g. `jgdms.example.org`):

| SPIFFE ID | SCAP host |
|---|---|
| `spiffe://<trust-domain>/host/lookup` | Host 1 — Jini Lookup Service |
| `spiffe://<trust-domain>/host/bae/engine-N` | Host 2 — BAE pool instances (N = 1, 2, …) |
| `spiffe://<trust-domain>/host/registry` | Host 3 — VerdictRegistry |
| `spiffe://<trust-domain>/host/downloader` | Host 4 — Codebase Downloader |
| `spiffe://<trust-domain>/host/telemetry` | Host 5 — JFR Telemetry |
| `spiffe://<trust-domain>/client/<name>` | Client JVMs |

The SPIFFE ID is **determined by the SPIRE server registration entry**, not by any JGDMS configuration.  JGDMS reads whatever URI SAN is embedded in the SVID certificate; it does not parse or validate the URI content beyond what Java's TLS stack requires.

---

## Prerequisites

| Component | Minimum version |
|---|---|
| SPIRE server | 1.6 |
| SPIRE agent | 1.6 (same host as JVM) |
| Java | 17 |
| JGDMS | 3.1.1 |

---

## SPIRE Server Configuration

### 1. Trust domain

Choose a trust domain name that is globally unique within your organisation.  All JGDMS hosts and clients must use the same trust domain.

```hcl
# spire-server.conf
server {
  trust_domain = "jgdms.example.org"
  ...
}
```

### 2. Registration entries

Create one SPIRE registration entry per workload using the `spire-server entry create` command (or Terraform / Helm equivalent).

**Example — Host 1 (Lookup Service), attested by Unix UID:**

```bash
spire-server entry create \
  -spiffeID spiffe://jgdms.example.org/host/lookup \
  -parentID spiffe://jgdms.example.org/spire/agent/unix/uid/1001 \
  -selector unix:uid:1001 \
  -ttl 3600
```

**Example — Host 2 BAE engine, attested by process path:**

```bash
spire-server entry create \
  -spiffeID spiffe://jgdms.example.org/host/bae/engine-1 \
  -parentID spiffe://jgdms.example.org/spire/agent/unix/uid/1002 \
  -selector unix:path:/opt/jgdms/bae/engine.jar \
  -ttl 3600
```

Repeat for Hosts 3–5 and any client JVMs, substituting the appropriate SPIFFE ID and selectors.

### 3. SVID TTL

A TTL of 3600 seconds (1 hour) is recommended.  `SpiffeCredentialManager` defaults to renewing 300 seconds (5 minutes) before expiry; adjust `DEFAULT_RENEWAL_LEAD_SECONDS` or pass a custom value to the constructor if you use a shorter TTL.

---

## SPIRE Agent Configuration

Each host running a JGDMS service must run a SPIRE agent that writes SVID material to disk using the `SVIDStore "disk"` plugin.

```hcl
# spire-agent.conf
agent {
  data_dir   = "/var/lib/spire/agent"
  trust_domain = "jgdms.example.org"
  server_address = "spire-server.internal"
  server_port    = 8081
}

plugins {
  # Workload attestation — choose the method appropriate for your platform
  WorkloadAttestor "unix" {}

  # Write SVID material to files that the JVM can read
  SVIDStore "disk" {
    plugin_data {
      svid_file_name   = "/run/spire/svid.pem"
      key_file_name    = "/run/spire/svid_key.pem"
      bundle_file_name = "/run/spire/bundle.pem"
    }
  }
}
```

**File permissions:** The PEM files must be readable by the OS user running the JGDMS JVM and must **not** be world-readable (private key exposure).  A dedicated service account is strongly recommended.

```bash
# Example: service account 'jgdms' owns the files
install -d -m 750 -o spire -g jgdms /run/spire
# SPIRE agent runs as 'spire'; JVM runs as 'jgdms' (supplementary group)
```

---

## Trust Bundle / TrustManager Configuration

`bundle.pem` contains the SPIFFE trust bundle (root CA certificates).  The JVM's `javax.net.ssl.TrustManager` must be configured to trust this CA chain so that peer SVID certificates are accepted during TLS handshakes.

### Option A — Java TrustStore

```bash
# Import the SPIFFE root CA into a JKS truststore
keytool -importcert \
  -alias spiffe-root \
  -file /run/spire/bundle.pem \
  -keystore /etc/jgdms/spiffe-trust.jks \
  -storepass changeit -noprompt
```

Pass the truststore to the JVM:

```
-Djavax.net.ssl.trustStore=/etc/jgdms/spiffe-trust.jks
-Djavax.net.ssl.trustStorePassword=changeit
```

### Option B — Custom TrustManager

Implement a `TrustManager` that reloads `bundle.pem` on rotation (useful when the SPIFFE trust bundle itself rotates).  See `FilterX509TrustManager` in the `net.jini.jeri.ssl` package for a starting point.

---

## Java Application Integration

### Minimal setup (service process)

```java
import net.jini.jeri.ssl.SpiffeCredentialManager;
import javax.security.auth.Subject;
import java.nio.file.Path;

// One Subject per JVM — reused by all JERI endpoints
Subject subject = new Subject();

// Point at the directory where SPIRE writes PEM files
Path spireDir = Path.of("/run/spire");

try (SpiffeCredentialManager mgr = new SpiffeCredentialManager(subject, spireDir)) {
    mgr.start();   // blocks until first SVID load; starts background renewal
    
    // Start JGDMS service endpoints here.
    // SslServerEndpointImpl and SslEndpointImpl will automatically use
    // the Subject registered in SpiffeSubjectHolder — no Subject.doAs() required.
    
    runServiceEventLoop();
} // mgr.close() called automatically; clears Subject credentials
```

### Custom renewal lead time

```java
// Renew 10 minutes before expiry instead of the default 5
new SpiffeCredentialManager(subject, svidSource, 600L);
```

### Custom SvidSource (e.g. SPIRE Workload API)

```java
SpiffeCredentialManager.SvidSource workloadApi = () -> {
    // Fetch SVID via gRPC from /run/spire/sockets/agent.sock
    // Return SpiffeCredentialManager.Svid(certPath, privateKey)
};
new SpiffeCredentialManager(subject, workloadApi, SpiffeCredentialManager.DEFAULT_RENEWAL_LEAD_SECONDS);
```

### Subject.doAs() (optional)

If your service wraps requests in `Subject.doAs()`, the `SpiffeSubjectHolder` fallback is bypassed and the explicitly provided Subject is used instead.  Both paths are supported simultaneously.

---

## Key File Formats

`SpiffeCredentialManager.FileSvidSource` accepts the following private key PEM formats in `svid_key.pem`:

| PEM header | Format | Notes |
|---|---|---|
| `-----BEGIN PRIVATE KEY-----` | PKCS#8 | **Preferred** — algorithm-agnostic |
| `-----BEGIN EC PRIVATE KEY-----` | SEC 1 | EC only |
| `-----BEGIN RSA PRIVATE KEY-----` | PKCS#1 | RSA only; internally re-wrapped to PKCS#8 |

Configure SPIRE's `SVIDStore "disk"` to emit PKCS#8 (`BEGIN PRIVATE KEY`) when possible to avoid format-specific code paths.

---

## Certificate Rotation

Rotation is fully automatic.  The background thread (`SpiffeCredentialManager-refresher`, daemon) fires at `(svid_expiry − renewalLeadSeconds)`.

| Event | Behaviour |
|---|---|
| Successful renewal | `Subject` credentials replaced atomically; new expiry scheduled |
| Fetch/parse failure | Warning logged; retry every 30 seconds |
| `close()` called | Scheduler shut down; Subject credentials cleared; `SpiffeSubjectHolder` cleared |

Active TLS connections are not interrupted during rotation — they complete using the credentials that were established at handshake time.  New connections pick up the new credentials immediately after the `Subject` is updated.

---

## Java Security Policy

If a Java security manager is active, the process that runs `SpiffeCredentialManager` must be granted the following permissions:

```
// Read SPIRE PEM files
permission java.io.FilePermission "/run/spire/*", "read";

// Background daemon thread creation
permission java.lang.RuntimePermission "createPlatformThread";

// Subject credential manipulation (modify the managed Subject)
permission javax.security.auth.AuthPermission "modifyPublicCredentials";
permission javax.security.auth.AuthPermission "modifyPrivateCredentials";
```

---

## Logging

`SpiffeCredentialManager` uses the `java.util.logging` (JUL) logger named:

```
net.jini.jeri.ssl.SpiffeCredentialManager
```

| Level | Events |
|---|---|
| `INFO` | Manager started (with SVID expiry time), successful refresh, manager closed |
| `WARNING` | Renewal failure (with stack trace) |
| `FINE` | Renewal scheduled (delay in seconds), intermediate key-format probe failures |

Example JUL configuration (`logging.properties`):

```properties
net.jini.jeri.ssl.SpiffeCredentialManager.level = INFO
```

---

## Operational Checklist

### Initial deployment

- [ ] SPIRE server running; trust domain configured
- [ ] Registration entry created for each workload (correct SPIFFE ID and selectors)
- [ ] SPIRE agent running on each host; `SVIDStore "disk"` writing to `/run/spire/`
- [ ] `/run/spire/` permissions: writable by SPIRE agent, readable by JVM service account
- [ ] `bundle.pem` imported into the JVM truststore (or custom TrustManager configured)
- [ ] `SpiffeCredentialManager.start()` called at JVM startup before any JERI endpoints are created
- [ ] INFO log confirms `"SpiffeCredentialManager started; SVID expires at <timestamp>"`

### Ongoing operations

- [ ] Monitor `WARNING`-level renewal failures — indicates SPIRE agent issue or file-permission problem
- [ ] Rotate trust bundle via SPIRE server bundle federation; update JVM truststore if using Option A
- [ ] When adding a new BAE engine (Host 2), create a new SPIRE registration entry with the appropriate `engine-N` SPIFFE ID and start a new SPIRE agent on that host

### Shutdown

- [ ] Call `SpiffeCredentialManager.close()` (or use try-with-resources) to cleanly remove credentials and stop the renewal thread before JVM exit

---

## Troubleshooting

| Symptom | Likely cause | Resolution |
|---|---|---|
| `UnsupportedConstraintException: Client must be logged on` at startup | `SpiffeCredentialManager.start()` not called before JERI endpoint creation, or `start()` threw an exception | Check logs for the initial SVID load error; verify PEM file paths and permissions |
| `No certificates found in svid.pem` | SPIRE agent not yet written the file, or wrong path | Confirm SPIRE agent is running and `svid_file_name` matches the path passed to `FileSvidSource` |
| `Cannot load PKCS#8 key from svid_key.pem` | Private key in unsupported format | Configure SPIRE to emit `PRIVATE KEY` (PKCS#8) format |
| TLS handshake `CertificateException: untrusted` | Peer certificate signed by SPIFFE CA not in JVM truststore | Import `bundle.pem` into truststore; ensure bundle has not rotated without truststore update |
| Renewal `WARNING` every 30 s | SPIRE agent stopped or PEM files removed | Restart SPIRE agent; check disk space on `/run/spire/` |
| SVID expiry with active connections | Renewal lead time too short, or SPIRE agent down for > `renewalLeadSeconds` | Increase `renewalLeadSeconds` or ensure SPIRE agent HA; active connections complete with expiring credentials but new connections will fail |
