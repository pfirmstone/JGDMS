# SPIRE registration entries for JGDMS SCAP hosts
#
# Apply with:
#   kubectl exec -n spire spire-server-0 -- \
#     /opt/spire/bin/spire-server entry create -f /dev/stdin < spire-entries.yaml
#
# Or use the spire-server CLI directly:
#   spire-server entry create \
#     -spiffeID spiffe://<trust-domain>/host/lookup \
#     -selector k8s:sa:jgdms-lookup \
#     -parentID spiffe://<trust-domain>/spire/agent/k8s_psat/jgdms-cluster/<node-uid>
#
# NOTE: Replace <trust-domain> with your actual trust domain.
#
# The entries below are expressed as a shell script because the SPIRE API
# server CLI does not accept YAML directly for batch entry creation.
# Run this script inside the spire-server pod.

#!/bin/sh
set -e

TRUST_DOMAIN="${TRUST_DOMAIN:-jgdms.example.org}"
PARENT_ID="spiffe://${TRUST_DOMAIN}/spire/agent/k8s_psat/jgdms-cluster"

# Host 1 — Jini Lookup Service
spire-server entry create \
  -spiffeID "spiffe://${TRUST_DOMAIN}/host/lookup" \
  -parentID "${PARENT_ID}" \
  -selector "k8s:ns:jgdms-scap" \
  -selector "k8s:sa:jgdms-lookup"

# Host 2 — BAE pool (wildcard; each pod gets its own pod-name selector)
# Add one entry per replica (or use k8s:pod-label for the pool).
spire-server entry create \
  -spiffeID "spiffe://${TRUST_DOMAIN}/host/bae/engine" \
  -parentID "${PARENT_ID}" \
  -selector "k8s:ns:jgdms-scap" \
  -selector "k8s:sa:jgdms-bae"

# Host 3 — Verdict Registry
spire-server entry create \
  -spiffeID "spiffe://${TRUST_DOMAIN}/host/registry" \
  -parentID "${PARENT_ID}" \
  -selector "k8s:ns:jgdms-scap" \
  -selector "k8s:sa:jgdms-registry"

# Host 4 — Codebase Downloader
spire-server entry create \
  -spiffeID "spiffe://${TRUST_DOMAIN}/host/downloader" \
  -parentID "${PARENT_ID}" \
  -selector "k8s:ns:jgdms-scap" \
  -selector "k8s:sa:jgdms-downloader"

# Host 5 — JFR Telemetry Service
spire-server entry create \
  -spiffeID "spiffe://${TRUST_DOMAIN}/host/telemetry" \
  -parentID "${PARENT_ID}" \
  -selector "k8s:ns:jgdms-scap" \
  -selector "k8s:sa:jgdms-telemetry"

echo "SPIRE entries created successfully."
