# Host 2 — Bytecode Analysis Engine (BAE) pool
#
# The BAE is the most security-hardened container in the SCAP pipeline.
# It is completely stateless, replicated N times, and operates under the
# following SELinux + Kubernetes constraints:
#
#   • No outbound network connections of any kind
#   • No exec / fork
#   • No JNI, no FFM (enforced at the DirtyChai SecurityManager layer)
#   • Read-only root filesystem (only /tmp is writable)
#   • Non-root, non-privileged, allowPrivilegeEscalation=false
#   • seccompProfile: RuntimeDefault
#
# Inbound connections accepted only from Host 4 (Codebase Downloader) and
# Host 5 (JFR Telemetry Service) via JERI/TLS.
#
# Each replica has its own engine signing key pair, provisioned as a
# Kubernetes Secret and mounted at /opt/jgdms/keys/.

ARG BASE_IMAGE=jgdms/dirtychai-base:latest
ARG BUILD_JDK=eclipse-temurin:21-jdk

# ── Stage 1: build ─────────────────────────────────────────────────────────────
FROM ${BUILD_JDK} AS builder

WORKDIR /build
COPY JGDMS/.mvn   .mvn/
COPY JGDMS/       ./
RUN mvn -B -q install -DskipTests --no-transfer-progress

# ── Stage 2: runtime ───────────────────────────────────────────────────────────
FROM ${BASE_IMAGE}

ARG JGDMS_VERSION=3.1.1-SNAPSHOT

# ASM (required by BAE at runtime)
COPY --chown=jgdms:jgdms \
  --from=builder /root/.m2/repository/org/ow2/asm/asm/9.7.1/asm-9.7.1.jar \
  /opt/jgdms/lib/

COPY --chown=jgdms:jgdms \
  --from=builder /build/services/bytecode-analysis-engine/bytecode-analysis-engine-service/target/bytecode-analysis-engine-service-${JGDMS_VERSION}.jar \
  /opt/jgdms/lib/
COPY --chown=jgdms:jgdms \
  --from=builder /build/jgdms-platform/target/jgdms-platform-${JGDMS_VERSION}.jar \
  /opt/jgdms/lib/
COPY --chown=jgdms:jgdms \
  --from=builder /build/jgdms-jeri/target/jgdms-jeri-${JGDMS_VERSION}.jar \
  /opt/jgdms/lib/
COPY --chown=jgdms:jgdms \
  --from=builder /build/jgdms-lib/target/jgdms-lib-${JGDMS_VERSION}.jar \
  /opt/jgdms/lib/
COPY --chown=jgdms:jgdms \
  --from=builder /build/service-starter/target/service-starter-${JGDMS_VERSION}.jar \
  /opt/jgdms/lib/
COPY --chown=jgdms:jgdms \
  --from=builder /build/jgdms-activation/target/jgdms-activation-${JGDMS_VERSION}.jar \
  /opt/jgdms/lib/
COPY --chown=jgdms:jgdms \
  --from=builder /build/jgdms-discovery-providers/target/jgdms-discovery-providers-${JGDMS_VERSION}.jar \
  /opt/jgdms/lib/
COPY --chown=jgdms:jgdms \
  --from=builder /build/jgdms-rmi-tls/target/jgdms-rmi-tls-${JGDMS_VERSION}.jar \
  /opt/jgdms/lib/
COPY --chown=jgdms:jgdms \
  --from=builder /build/services/jgdms-service-support/target/jgdms-service-support-${JGDMS_VERSION}.jar \
  /opt/jgdms/lib/

COPY --chown=jgdms:jgdms \
  --from=builder /build/services/bytecode-analysis-engine/bytecode-analysis-engine-dl/target/bytecode-analysis-engine-dl-${JGDMS_VERSION}.jar \
  /opt/jgdms/lib-dl/
COPY --chown=jgdms:jgdms \
  --from=builder /build/jgdms-lib-dl/target/jgdms-lib-dl-${JGDMS_VERSION}.jar \
  /opt/jgdms/lib-dl/

COPY --chown=jgdms:jgdms deploy/config/host2-bae.config  /opt/jgdms/config/
COPY --chown=jgdms:jgdms deploy/policy/host2-bae.policy   /opt/jgdms/policy/

# Engine signing-key directory (mounted from Kubernetes Secret at runtime)
RUN mkdir -p /opt/jgdms/keys && chown jgdms:jgdms /opt/jgdms/keys

# JERI inbound port
EXPOSE 1098

WORKDIR /opt/jgdms

ENTRYPOINT ["java", \
  "-Djava.security.manager=org.apache.river.api.security.ConcurrentPolicyFile", \
  "-Djava.security.policy=/opt/jgdms/policy/host2-bae.policy", \
  "-Djava.rmi.server.hostname=${JGDMS_SERVICE_HOST}", \
  "-Dnet.jini.discovery.announce=0", \
  "-Dnet.jini.discovery.multicastAnnounceInterval=0", \
  "-Djdk.virtualThreadScheduler.parallelism=4", \
  "-cp", "/opt/jgdms/lib/*", \
  "org.apache.river.start.ServiceStarter", \
  "/opt/jgdms/config/host2-bae.config"]

LABEL org.opencontainers.image.title="jgdms-host2-bae" \
      org.opencontainers.image.description="JGDMS Host 2 — Bytecode Analysis Engine (SELinux-isolated)" \
      org.opencontainers.image.source="https://github.com/pfirmstone/JGDMS"
