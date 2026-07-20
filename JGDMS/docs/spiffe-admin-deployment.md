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
│    │ registers it as the process-wide ambient identity          │
│    │   (Security.registerLocalPrincipalProvider — no doAs)       │
│    └─ schedules background renewal before SVID expiry           │
│                                                                 │
│  SslEndpointImpl / SslServerEndpointImpl                        │
│    └─ uses the ambient process identity (no Subject.doAs frame) │
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
    // SslServerEndpointImpl and SslEndpointImpl automatically use the managed
    // Subject as the process-wide *ambient* workload identity — no Subject.doAs()
    // frame is required (or wanted: the SPIFFE workload is ambient, never a doAs
    // subject). start() registers it via Security.registerLocalPrincipalProvider.
    
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

### `Subject.doAs()` is for *users*, not the workload

The SPIFFE workload identity is **ambient** — it is the process's own identity, established once
at `start()` and reached by the endpoints without any `Subject.doAs()`/`callAs()` frame.  Do **not**
attempt to carry the workload Subject through `Subject.doAs()`/`callAs()`; those APIs are reserved
for *user* subjects (authenticated humans), and on DirtyChai a worker subject is rejected by them
outright.  The two identities are deliberately distinct: the mTLS/SVID peer authenticates the
*workload*, while a human is a separate (e.g. JWT) *user* subject that authorization may check on its
own gate.  See `JGDMS-STD-003` §10.5 (two-gate authorization) and
`docs/DESIGN-spiffe-authorization-acc-transmission-2026-06-27.md`.

A request handler may still legitimately enter `Subject.callAs(userSubject, …)` to run **user** work
under a validated user identity (a `UserSubject` is bound with `callAs`, never `doAs`, which rejects
it); that is orthogonal to — and layered on top of — the ambient
workload identity, which remains in effect throughout.

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
| `close()` called | Scheduler shut down; Subject credentials cleared; ambient principal provider deregistered (`Security.registerLocalPrincipalProvider(null)`) |

Active TLS connections are not interrupted during rotation — they complete using the credentials that were established at handshake time.  New connections pick up the new credentials immediately after the `Subject` is updated.

---

## High Availability Deployment

This section describes how to eliminate the SPIRE server as a single point of failure.
A single SPIRE server (plus its agent on each host) works well for development and small
deployments, but a production JGDMS pipeline should run **two or more SPIRE servers
backed by a shared relational datastore** so that no individual server failure can prevent
SVID renewal.  JGDMS's built-in exponential-backoff renewal (`SpiffeCredentialManager`)
buys time during transient outages; an HA SPIRE deployment eliminates multi-hour outages
entirely.

---

### HA Architecture Overview

```
                     ┌────────────────────────────────────────────┐
                     │  JGDMS service JVM (each host)              │
                     │   SpiffeCredentialManager                   │
                     │     reads  /run/spire/{svid,key,bundle}.pem │
                     └────────────────────────────────────────────┘
                                   ↑ writes PEM files
                     ┌────────────────────────────────────────────┐
                     │  SPIRE agent (local to each host)           │
                     │    server_address = <LB VIP>               │
                     └────────────────────────────────────────────┘
                                   ↑ gRPC :8081
              ┌─────────────────────────────────────────────────┐
              │  TCP load balancer (HAProxy / AWS NLB / etc.)    │
              │  VIP: spire-lb.internal:8081                     │
              └────────────┬──────────────────┬─────────────────┘
                           │                  │
              ┌────────────▼──────┐  ┌────────▼──────────┐
              │  SPIRE server A   │  │  SPIRE server B    │
              │  (active)         │  │  (active)          │
              └────────────┬──────┘  └────────┬───────────┘
                           │                  │
              ┌────────────▼──────────────────▼───────────┐
              │  Shared relational datastore               │
              │  (PostgreSQL or MySQL — active/standby     │
              │   or multi-master with JGDMS workload)     │
              └────────────────────────────────────────────┘
```

Key points:

* **SPIRE servers are active–active on read paths** (SVID issuance, bundle queries).
  Write paths (new registration entries, CA key operations) are serialised through the
  shared datastore.
* **The load balancer is a simple TCP/L4 proxy** — no TLS termination is needed because
  SPIRE uses its own mTLS bootstrap handshake.
* **SPIRE agents are not replicated** — each agent runs locally on the same host as the
  JGDMS JVM and communicates with the SPIRE server tier through the LB.

---

### HA Prerequisites

| Component | Requirement |
|---|---|
| SPIRE server | 1.8+ (active–active datastore support stable; `DataStore "sql"` plugin) |
| SPIRE agent | 1.8+ |
| Shared datastore | PostgreSQL 14+ (recommended) or MySQL 8+; accessible from all SPIRE server hosts |
| Load balancer | Any TCP/L4 load balancer supporting health checks on gRPC port 8081 |
| Shared CA key material | See §CA Options below |

---

### Shared Datastore Configuration

SPIRE server must be configured to use the `sql` datastore plugin instead of the default
SQLite.

```hcl
# spire-server.conf (shared section — identical on every server instance)
plugins {
  DataStore "sql" {
    plugin_data {
      database_type = "postgres"
      connection_string = "host=pg-primary.internal port=5432 dbname=spire user=spire password=<password> sslmode=require"
      max_open_conns    = 10
      max_idle_conns    = 2
      conn_max_lifetime = "5m"
    }
  }
}
```

Create the database and user before starting the first SPIRE server instance; SPIRE will
create the schema automatically on first boot.

```sql
-- Run as a PostgreSQL superuser
CREATE DATABASE spire;
CREATE USER spire WITH PASSWORD '<strong-random-password>';
GRANT ALL PRIVILEGES ON DATABASE spire TO spire;
```

---

### CA Options in HA

Choose one CA strategy before configuring multiple server instances — mixing strategies
on running servers corrupts the trust bundle.

#### Option 1 — Shared disk CA (simplest)

All SPIRE server instances share the same CA private key via a mounted secret store
(e.g. a Kubernetes Secret, a `tmpfs` populated by Vault Agent, or an NFS volume with
tight ACLs).  Every instance reads the same `keys.json` file.

```hcl
# spire-server.conf
plugins {
  KeyManager "disk" {
    plugin_data {
      keys_path = "/etc/spire/server/keys.json"
    }
  }
}
```

**Caution:** the `keys.json` file contains a private key.  Protect it with `chmod 600`
and restrict access to the `spire-server` service account.  Synchronise this file to all
server hosts *before* the second instance starts.

#### Option 2 — Upstream authority (recommended for production)

Delegate key material to a dedicated secrets manager.  SPIRE servers act as intermediate
CAs that obtain signing certificates from an upstream root CA (HashiCorp Vault or AWS
ACM PCA).  No private key material is stored on the SPIRE server hosts.

```hcl
# spire-server.conf
plugins {
  UpstreamAuthority "vault" {
    plugin_data {
      vault_addr    = "https://vault.internal:8200"
      pki_mount_path = "spire-pki"
      # Use Kubernetes auth or AppRole; never embed a static token here
      auth_method  = "kubernetes"
      k8s_auth_role_name = "spire-server"
    }
  }
}
```

---

### Load Balancer Configuration

The LB must forward raw TCP to the SPIRE server gRPC port (default 8081).  Use health
checks on port 8080 (SPIRE's built-in HTTP health endpoint) to detect and exclude failed
instances.

**HAProxy example (`haproxy.cfg` snippet):**

```
frontend spire-grpc
    bind *:8081
    mode tcp
    default_backend spire-servers

backend spire-servers
    mode tcp
    balance leastconn
    option tcp-check
    server spire-a spire-server-a.internal:8081 check port 8080
    server spire-b spire-server-b.internal:8081 check port 8080
```

**AWS NLB / GCP TCP LB:** configure two target instances on port 8081; use the SPIRE
health endpoint (`GET /health/live` on port 8080) as the health check path.

Enable the SPIRE server health endpoint in your `spire-server.conf`:

```hcl
health_checks {
  listener_enabled = true
  bind_port        = "8080"
  live_path        = "/health/live"
  ready_path       = "/health/ready"
}
```

---

### SPIRE Server Instance Configuration

Each HA server instance uses the same `spire-server.conf` — only the `bind_address`
(if you use per-host IPs) differs.  A minimal production example:

```hcl
server {
  bind_address   = "0.0.0.0"
  bind_port      = "8081"
  trust_domain   = "jgdms.example.org"
  log_level      = "INFO"
  # How long issued SVIDs are valid (must match registration-entry -ttl values)
  default_svid_ttl = "1h"
  # CA certificate TTL (how long SPIRE's own intermediate CA is valid)
  ca_ttl         = "24h"
}

plugins {
  DataStore "sql" {
    plugin_data {
      database_type = "postgres"
      connection_string = "host=pg-primary.internal dbname=spire user=spire password=<pw> sslmode=require"
    }
  }

  KeyManager "disk" {
    plugin_data {
      keys_path = "/etc/spire/server/keys.json"   # same file on all instances
    }
  }

  NodeAttestor "join_token" {}   # or x509pop/aws_iid/k8s_sat as appropriate
}

health_checks {
  listener_enabled = true
  bind_port        = "8080"
}
```

Start both instances.  The second instance will discover the existing schema in the
shared database and join automatically.  There is no explicit "primary" election step.

---

### SPIRE Agent Configuration for HA

Agents connect through the load balancer VIP.  No other agent-side change is needed.

```hcl
# spire-agent.conf  (same on every JGDMS host)
agent {
  data_dir     = "/var/lib/spire/agent"
  trust_domain = "jgdms.example.org"

  # Point at the LB VIP, not a specific server host
  server_address = "spire-lb.internal"
  server_port    = 8081

  log_level = "INFO"
}

plugins {
  WorkloadAttestor "unix" {}

  SVIDStore "disk" {
    plugin_data {
      svid_file_name   = "/run/spire/svid.pem"
      key_file_name    = "/run/spire/svid_key.pem"
      bundle_file_name = "/run/spire/bundle.pem"
    }
  }
}
```

If the load balancer supports connection draining / health checks, the agent will
transparently reconnect to the surviving SPIRE server within its gRPC reconnect window
(SPIRE default: exponential backoff, max 30 s) with no JGDMS application involvement.

---

### JGDMS Application — No Changes Required

`SpiffeCredentialManager` is unaware of the SPIRE topology.  It reads PEM files written
by the local SPIRE agent, which in turn is connected to the HA SPIRE server cluster.
The exponential-backoff renewal logic (Work Item 49) provides an additional buffer during
any brief LB failover window.

The combined survival window during a complete SPIRE-tier outage is:

```
survival_window = current_svid_remaining_ttl
               ≈ up to 1 hour from last successful renewal
```

With two HA servers and a healthy datastore, a single server host failure causes at most
a few seconds of agent reconnect latency before normal renewal resumes.

---

### Failure Mode Analysis

| Failure | Impact | Recovery |
|---|---|---|
| One SPIRE server host fails | LB health check removes it; agent reconnects to surviving server | Automatic within LB health-check interval (~10 s) |
| Both SPIRE servers fail (datastore healthy) | Agents cannot renew SVIDs; JGDMS renewal retries with exponential backoff | Restart SPIRE servers; they rejoin via shared datastore |
| Shared datastore primary fails | SPIRE servers cannot issue new SVIDs; existing agents retain their valid SVIDs | Promote datastore standby (standard PostgreSQL HA); SPIRE servers reconnect automatically |
| SPIRE agent on one JGDMS host fails | That host cannot renew its SVID after expiry | Restart SPIRE agent; it re-attests and fetches a fresh SVID |
| Full site failure | All SVIDs expire after TTL | Restore SPIRE server cluster + agents; JGDMS services restart and obtain fresh SVIDs |

---

### HA Operational Checklist

#### Infrastructure setup

- [ ] Shared PostgreSQL (or MySQL) instance accessible from all SPIRE server hosts; TLS enforced on the connection string
- [ ] `spire` database and user created; password stored in a secrets manager (not in `spire-server.conf` in plain text)
- [ ] CA key material decision made: shared `keys.json` (sync before start) or Vault `UpstreamAuthority`
- [ ] TCP load balancer configured for port 8081; health checks on port 8080 `/health/live`

#### SPIRE server deployment

- [ ] Both server instances started; `spire-server healthcheck` returns healthy on each
- [ ] `spire-server bundle show` returns the same trust bundle on both instances
- [ ] Registration entries verified: `spire-server entry show` lists all JGDMS workloads on both instances

#### SPIRE agent deployment

- [ ] `server_address` updated to load balancer VIP on all hosts
- [ ] Each agent re-attested after the address change; `spire-agent healthcheck` returns healthy

#### JGDMS services

- [ ] INFO log on each JVM confirms `"SpiffeCredentialManager started; SVID expires at <timestamp>"` after the HA switch
- [ ] Failover tested: stop one SPIRE server instance; verify SVID renewal still succeeds within 60 s on all hosts
- [ ] `isCredentialValid()` returns `true` and `secondsUntilExpiry()` > `renewalLeadSeconds` at steady state

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

### Hard prerequisite: smart-proxy-isolation admin authority (`AdminPrincipalAuthenticator`)

Unlike the grants above (permissions the trusted `SpiffeCredentialManager` code
itself needs), this is about permissions that must be **denied** to every
hosted/business protection domain — i.e. what a least-privilege policy must
*not* grant. `au.net.zeus.jgdms.loader.isolation.AdminPrincipalAuthenticator`
(the in-process gate on the subprocess policy-admin surface used by smart-proxy
isolation) authenticates the calling admin by matching a `Principal`'s
canonical name against the ambient `Subject`. That match means nothing unless
the deployed policy denies **all three** of the following to every
hosted/business protection domain (2026-07-20 board finding):

- `javax.security.auth.AuthPermission "callAs"`
- `javax.security.auth.AuthPermission "doAs"`
- `java.lang.RuntimePermission "setSecurityManager"`

Any one of these left ungranted-but-not-explicitly-denied by an otherwise
permissive/`AllPermission` policy defeats the gate: hosted code can forge a
`Subject` naming the admin principal and bind it via `Subject.callAs`/`doAs`
(the first two), or — if only those two are denied — simply install its own
permissive `SecurityManager` first (the third) and then forge the `Subject`
against that. See `AdminPrincipalAuthenticator`'s class javadoc, section
"NECESSARY, not SUFFICIENT", for the full reasoning and the adversarial
reproductions in `IsolationSecurityCriticalTest`.

**CLOSED 2026-07-20 (was: known pre-existing gap, not specific to SPIFFE/SPIRE deployment).** The 7
`qa/harness/policy/defaultspiffe*.policy` files (mahalo, outrigger, reggie, fiddler, group, mercury,
norm) that previously granted unconditional `AllPermission`/`AuthPermission "*"` to every protection
domain — which did not satisfy this prerequisite — have had those unqualified grants removed and
replaced with a narrow least-privilege baseline (commits `edb5ff072`/`5b9b31147`, merged `265d10aca`).
A static regression-guard script, `qa/harness/policy/check-spiffe-policy-no-broad-grants.sh`, was added
so this cannot silently regress. **Still do not treat those QA policy files as a production-policy
template** — they are QA bring-up configuration, narrowed to satisfy this specific prerequisite, not
independently reviewed as a minimal production baseline for any other purpose.

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
