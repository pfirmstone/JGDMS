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
│   ├── host2-bae/deployment.yaml
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
│           └── network-policy.yaml
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
