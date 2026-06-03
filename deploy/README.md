# JGDMS SCAP Kubernetes Deployment

This directory contains all the artefacts required to deploy the **JGDMS Safe
Codebase Audit Pipeline (SCAP)** on Kubernetes using the **DirtyChai JDK** as
the runtime.  DirtyChai is a community OpenJDK fork that retains
`SecurityManager` / `AccessController` functionality required by JGDMS on its
full-security deployment path.

---

## Directory structure

```
deploy/
├── docker/
│   ├── base/                   # DirtyChai JDK base image
│   │   └── Dockerfile
│   ├── host1-lookup/           # Jini Lookup Service (Reggie)
│   ├── host2-bae/              # Bytecode Analysis Engine
│   ├── host3-registry/         # Verdict Registry (stateful)
│   ├── host4-downloader/       # Codebase Downloader
│   └── host5-telemetry/        # JFR Telemetry Service
├── policy/                     # Per-role SecurityManager policy files
│   ├── host1-lookup.policy
│   ├── host2-bae.policy        # Most restrictive — no network egress
│   ├── host3-registry.policy
│   ├── host4-downloader.policy # Only host with internet access
│   └── host5-telemetry.policy
├── config/                     # ServiceStarter .config files (env-parameterised)
│   ├── host1-lookup.config
│   ├── host2-bae.config
│   ├── host3-registry.config
│   ├── host4-downloader.config
│   └── host5-telemetry.config
├── k8s/                        # Raw Kubernetes manifests
│   ├── namespace.yaml
│   ├── network-policy.yaml     # Full SCAP connection-matrix enforcement
│   ├── spire/
│   │   ├── spire-server.yaml
│   │   ├── spire-agent.yaml
│   │   └── register-entries.sh # SPIRE registration entries for all five hosts
│   ├── host1-lookup/deployment.yaml
│   ├── host2-bae/
│   │   ├── deployment.yaml
│   │   ├── crash-rbac.yaml     # Role + RoleBinding for crash-guard
│   │   └── crash-guard-cm.yaml # ConfigMap with crash-guard.sh
│   ├── host3-registry/statefulset.yaml
│   ├── host4-downloader/deployment.yaml
│   └── host5-telemetry/deployment.yaml
├── helm/
│   └── jgdms-scap/             # Helm chart (packages all of the above)
│       ├── Chart.yaml
│       ├── values.yaml
│       └── templates/
│           ├── _helpers.tpl
│           ├── namespace.yaml
│           ├── host1-lookup.yaml
│           ├── host2-bae.yaml
│           ├── host3-registry.yaml
│           ├── host4-downloader.yaml
│           ├── host5-telemetry.yaml
│           ├── network-policy.yaml
│           ├── bae-crash-rbac.yaml        # crash-guard RBAC
│           ├── bae-crash-guard-cm.yaml    # crash-guard script
│           └── bae-crashloop-alert.yaml   # PrometheusRule (optional)
└── selinux/                    # SELinux type-enforcement module for BAE nodes
    ├── jgdms_bae.te
    └── jgdms_bae.fc
```

---

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Kubernetes | 1.28+ | NetworkPolicy CNI required (Calico, Cilium, Antrea…) |
| Helm | 3.14+ | For the Helm chart deployment |
| SPIRE | 1.9+ | Server StatefulSet + Agent DaemonSet in `spire` namespace |
| Docker / Buildx | 24+ | For building images |
| DirtyChai JDK | dirty-chai-latest | Auto-downloaded by the base Dockerfile |
| SELinux tooling | policycoreutils | Only required on BAE nodes |

---

## Quick start

### 1. Build the DirtyChai base image

The GitHub Actions workflow `.github/workflows/dirtychai-base-image.yml`
builds and publishes the base image automatically on every push to the
`deploy/docker/base/Dockerfile` path and on a daily schedule to pick up
upstream DirtyChai releases.

To build locally:

```bash
cd deploy/docker/base
docker build \
  --build-arg DIRTYCHAI_RELEASE=dirty-chai-latest \
  -t my-registry.example.org/jgdms-dirtychai-base:latest .
docker push my-registry.example.org/jgdms-dirtychai-base:latest
```

### 2. Build service images

Each service image uses a multi-stage build.  Run from the repository root:

```bash
docker build \
  -f deploy/docker/host1-lookup/Dockerfile \
  -t my-registry.example.org/jgdms/host1-lookup:3.1.1-SNAPSHOT \
  --build-arg BASE_IMAGE=my-registry.example.org/jgdms-dirtychai-base:latest \
  --build-arg JGDMS_VERSION=3.1.1-SNAPSHOT \
  .
```

Repeat for `host2-bae`, `host3-registry`, `host4-downloader`, and
`host5-telemetry`.

### 3. Deploy SPIRE

```bash
kubectl apply -f deploy/k8s/spire/spire-server.yaml
kubectl apply -f deploy/k8s/spire/spire-agent.yaml
# Wait for SPIRE server to be ready, then register workload identities:
kubectl exec -n spire spire-server-0 -- sh < deploy/k8s/spire/register-entries.sh
```

### 4a. Deploy with Helm (recommended)

```bash
helm install jgdms-scap deploy/helm/jgdms-scap \
  --namespace jgdms-scap \
  --create-namespace \
  --set global.trustDomain=jgdms.example.org \
  --set global.imageRegistry=my-registry.example.org
```

Override any default value in `deploy/helm/jgdms-scap/values.yaml`.

### 4b. Deploy with raw manifests

```bash
kubectl apply -f deploy/k8s/namespace.yaml
kubectl apply -f deploy/k8s/spire/
kubectl apply -f deploy/k8s/host1-lookup/
kubectl apply -f deploy/k8s/host2-bae/
kubectl apply -f deploy/k8s/host3-registry/
kubectl apply -f deploy/k8s/host4-downloader/
kubectl apply -f deploy/k8s/host5-telemetry/
kubectl apply -f deploy/k8s/network-policy.yaml
```

---

## BAE node SELinux configuration

Host 2 (BAE) pods are designed to run on SELinux-enforcing nodes.  Load the
custom policy module on each BAE node:

```bash
cd deploy/selinux
checkmodule -M -m -o jgdms_bae.mod jgdms_bae.te
semodule_package -o jgdms_bae.pp -m jgdms_bae.mod -f jgdms_bae.fc
semodule -i jgdms_bae.pp
```

Label BAE nodes so that the Deployment's `nodeSelector` targets them:

```bash
kubectl label node <bae-node-name> selinux-enforcing=true
kubectl taint node <bae-node-name> \
  node-role.kubernetes.io/selinux-enforcing=true:NoSchedule
```

If your managed Kubernetes service (GKE, EKS, AKS) does not support custom
SELinux policy modules, set `host2.requireSelinuxNodes: false` in your Helm
values.  Note that this reduces the isolation level of the BAE.

---

## BAE engine signing keys

Each BAE replica requires a unique signing key pair.  Before deploying Host 2,
generate the key pair and populate the `bae-engine-signing-key` Secret:

```bash
# Generate a new ECDSA P-256 signing key
openssl ecparam -name prime256v1 -genkey -noout -out engine-signing-key.pem
openssl req -new -x509 -key engine-signing-key.pem \
  -out engine-signing-cert.pem -days 3650 \
  -subj "/CN=jgdms-bae-engine/O=JGDMS"

# Store in the Kubernetes Secret
kubectl create secret generic bae-engine-signing-key \
  --namespace jgdms-scap \
  --from-file=engine-signing-key.pem \
  --from-file=engine-signing-cert.pem
```

Register the public key (certificate) with Host 3 (Verdict Registry) before
marking the BAE pod as ready.  See the VerdictRegistry API for the public-key
registration endpoint.

---

## Environment variables reference

All startup configuration is externalised via environment variables supplied
through Kubernetes `ConfigMap` resources.

| Variable | Default | Description |
|----------|---------|-------------|
| `JGDMS_VERSION` | `3.1.1-SNAPSHOT` | Maven artifact version |
| `JGDMS_SERVICE_HOST` | pod IP (injected) | Advertised host for JERI endpoint |
| `JGDMS_LOOKUP_HOST` | pod IP | Host 1 only: advertised Jini lookup hostname |
| `JGDMS_LOOKUP_PORT` | `4160` | Host 1: JERI/Jini unicast port |
| `JGDMS_JERI_PORT` | `1098` | Hosts 2–5: JERI listen port |
| `JGDMS_CODEBASE_PORT` | `9080` | HTTP codebase server port |
| `JGDMS_LOOKUP_URL` | `jini://lookup.jgdms-scap.svc.cluster.local:4160` | Unicast lookup URL for service registration |
| `JGDMS_LOG_DIR` | `/opt/jgdms/log` | ReliableLog directory (Host 1, Host 3) |
| `JGDMS_VR_QUORUM` | `2` | Minimum BAE verdicts required (Host 3) |
| `JGDMS_ENGINE_ID` | pod name | Unique engine identifier (Host 2) |
| `ENGINE_SIGNING_KEY` | `/opt/jgdms/keys/engine-signing-key.pem` | Path to BAE signing private key |
| `ENGINE_SIGNING_CERT` | `/opt/jgdms/keys/engine-signing-cert.pem` | Path to BAE signing certificate |
| `TRUST_DOMAIN` | `jgdms.example.org` | SPIFFE trust domain |
| `SPIRE_DIR` | `/run/spire` | Directory containing SPIRE SVID PEM files |

---

## Security notes

- **No Phoenix activation** — all services use `NonActivatableServiceDescriptor`
  (`PID 1 = JVM`, no child-JVM spawning).
- **Multicast discovery disabled** — only unicast `LookupLocator` discovery is
  used to avoid multicast leakage between namespaces.
- **BAE isolation invariant** — NetworkPolicy and SecurityManager policy
  together prevent the BAE from connecting to the Verdict Registry (Host 3).
- **SPIFFE identity** — every inter-service call is authenticated via SPIRE
  SVID TLS; no passwords, no static API keys.
- **DirtyChai SecurityManager** — the active `ConcurrentPolicyFile` enforces
  per-role least-privilege on top of Kubernetes RBAC and NetworkPolicy.
- **Read-only root filesystem** — all containers use `readOnlyRootFilesystem:
  true`; only `/tmp` (emptyDir) and the verdict log PVC are writable.
- **Non-root** — all pods run as UID/GID 1000 (`jgdms`).

---

## BAE crash-guard

The **crash-guard** feature converts BAE pod crashes into security-actionable
verdicts in the VerdictRegistry, closing the gap where a weaponised JAR could
crash the BAE mid-analysis without generating any audit trail.

### How it works

1. **Job marker** — Before analysing a JAR, the BAE application writes the
   JAR's SHA-256 digest (the same digest used in DigestGrants) to
   `/tmp/current-job`.  After successful analysis, it deletes this file.  The
   Kubernetes `preStop` lifecycle hook configured in this chart also deletes the
   marker on graceful SIGTERM-initiated shutdown (rolling updates, scale-downs),
   preventing false verdicts.

2. **crash-guard init container** — A lightweight `bitnami/kubectl` init
   container runs before the BAE container on every pod start.  If it finds a
   residual `/tmp/current-job` from a previous crash, it:
   - Reads the last BAE container exit code and restart count from the
     Kubernetes API.
   - Classifies the crash by exit code:

     | Exit code | Signal | Verdict |
     |-----------|--------|---------|
     | 143 | SIGTERM (graceful) | No verdict — clear marker |
     | 137 | SIGKILL / OOM | `SUSPICIOUS_OOM` |
     | 134 | SIGABRT | `SUSPICIOUS_CRASH` |
     | 138 | SIGBUS | `SUSPICIOUS_CRASH` |
     | 0 with marker | Abnormal exit | `SUSPICIOUS_CRASH` |
     | other non-zero | Java exception | `ANALYSIS_ERROR` |
     | any, restarts ≥ threshold | CrashLoopBackOff | `DANGEROUS` (escalated) |

   - Patches the pod with four `jgdms.io/pending-crash-verdict-*` annotations.
   - Clears the job marker so the next BAE start sees a clean state.

3. **Downloader consumption** — The Downloader (host4) monitors BAE pod
   annotations on each Jini lookup discovery cycle.  When it finds a
   `jgdms.io/pending-crash-verdict-digest` annotation, it forwards the verdict
   to the VerdictRegistry (host3) via JERI and clears the annotation.  After
   this, any `PreferredProxyCodebaseProvider.checkVerdictForJar()` call that
   encounters that digest will see a `SUSPICIOUS_*` or `DANGEROUS` verdict and
   reject the JAR.

4. **CrashLoopBackOff Prometheus alert** — When Prometheus Operator is enabled,
   `bae-crashloop-alert.yaml` fires a `critical` alert when any BAE pod enters
   `CrashLoopBackOff` (the crash-guard init container can no longer run at this
   point).  A `warning` alert fires when restarts exceed the threshold within a
   10-minute window.

### BAE application contract

The BAE implementation **must**:

1. Write the SHA-256 digest (64 hex characters) of the JAR being analysed to
   `/tmp/current-job` **before** beginning bytecode analysis:
   ```
   echo -n "<sha256-hex>" > /tmp/current-job
   ```

2. Delete `/tmp/current-job` **after** analysis completes successfully:
   ```
   rm -f /tmp/current-job
   ```

The `preStop` lifecycle hook handles graceful shutdowns automatically.  The BAE
application does not need to handle SIGTERM specially for this contract.

### Downloader (host4) application contract

The Downloader **must** periodically (or on pod watch events) scan all BAE pods
for the following annotations and forward them to the VerdictRegistry via JERI:

| Annotation key | Content |
|----------------|---------|
| `jgdms.io/pending-crash-verdict-digest` | SHA-256 hex digest of the in-flight JAR |
| `jgdms.io/pending-crash-verdict-verdict` | `SUSPICIOUS_OOM`, `SUSPICIOUS_CRASH`, `ANALYSIS_ERROR`, or `DANGEROUS` |
| `jgdms.io/pending-crash-verdict-exit` | BAE container exit code at time of crash |
| `jgdms.io/pending-crash-verdict-restarts` | BAE container restart count at time of crash |

After successfully forwarding a verdict, the Downloader must clear these
annotations by patching them to empty strings (or deleting them).

### Configuration reference

| Helm value | Default | Description |
|------------|---------|-------------|
| `host2.crashGuard.enabled` | `true` | Enable the crash-guard init container |
| `host2.crashGuard.image.repository` | `bitnami/kubectl` | Init container image |
| `host2.crashGuard.image.tag` | `""` (latest) | Image tag |
| `host2.crashGuard.crashLoopThreshold` | `5` | Restart count at which verdict escalates to `DANGEROUS` |
| `host2.crashGuard.tokenExpirationSeconds` | `600` | Lifetime (s) of the projected SA token for Kubernetes API access |
| `host2.crashGuard.apiServerCidr` | `0.0.0.0/0` | Kubernetes API server CIDR for NetworkPolicy egress rule |
| `prometheus.prometheusOperator.enabled` | `false` | Render the `PrometheusRule` manifest |
| `prometheus.prometheusOperator.ruleLabels` | `{}` | Extra labels for Prometheus rule selector matching |
| `prometheus.evaluationInterval` | `30s` | Rule evaluation interval |
| `prometheus.baeAlerts.crashLoopFor` | `1m` | Duration before `BAECrashLoopBackOff` alert fires |
| `prometheus.baeAlerts.frequentCrashFor` | `0m` | Duration before `BAEFrequentCrashes` alert fires |

### Production hardening

Set `host2.crashGuard.apiServerCidr` to your cluster's service CIDR to restrict
the BAE pod's Kubernetes API egress to only the API server IP range:

```bash
helm upgrade jgdms-scap deploy/helm/jgdms-scap \
  --set host2.crashGuard.apiServerCidr=10.96.0.0/12   # kubeadm default
```

To disable the crash-guard (e.g. on development clusters without RBAC):

```bash
helm upgrade jgdms-scap deploy/helm/jgdms-scap \
  --set host2.crashGuard.enabled=false
```

Note: disabling crash-guard restores the original empty egress rule on the BAE
NetworkPolicy (`host2-bae-policy`).

### Security properties preserved

- **BAE isolation invariant maintained** — the crash-guard init container calls
  the Kubernetes API only, never the VerdictRegistry directly.  The BAE →
  Registry (`DENIED`) rule in `host2-bae-policy` is preserved.
- **Main BAE container has no API token** — `automountServiceAccountToken:
  false` is set at the pod level.  The projected ServiceAccount token volume is
  mounted only by the init container and expires after
  `tokenExpirationSeconds` seconds.
- **SIGTERM false-positive prevention** — exit code 143 is explicitly detected
  and results in no verdict; the `preStop` hook further ensures the job marker
  is cleared before SIGTERM arrives on graceful shutdowns.
- **Minimal RBAC** — the `jgdms-bae-pod-annotator` Role grants only `get` and
  `patch` on `pods` in the `jgdms-scap` namespace; no cluster-level permissions
  are requested.
