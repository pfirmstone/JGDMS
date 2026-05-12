# Host 3 — Verdict Registry
#
# The Verdict Registry is the authoritative, high-trust store for JAR
# analysis verdicts.  It holds the signing key that clients trust.
#
# It requires PERSISTENT storage because VerdictRegistryImpl uses
# ReliableLog.  Deploy as a Kubernetes StatefulSet with a
# PersistentVolumeClaim mounted at /opt/jgdms/log.
#
# Host 2 (BAE) has NO direct connection to Host 3.  Only Hosts 4 and 5
# may submit JarAnalysisReport objects; clients may query verdicts.

ARG BASE_IMAGE=jgdms/dirtychai-base:latest
ARG BUILD_JDK=eclipse-temurin:21-jdk

# ── Stage 1: build ─────────────────────────────────────────────────────────────
FROM ${BUILD_JDK} AS builder

WORKDIR /build
COPY JGDMS/.mvn  .mvn/
COPY JGDMS/      ./
RUN mvn -B -q install -DskipTests --no-transfer-progress

# ── Stage 2: runtime ───────────────────────────────────────────────────────────
FROM ${BASE_IMAGE}

ARG JGDMS_VERSION=3.1.1-SNAPSHOT

COPY --chown=jgdms:jgdms \
  --from=builder /build/services/verdict-registry/verdict-registry-service/target/verdict-registry-service-${JGDMS_VERSION}.jar \
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
  --from=builder /build/services/verdict-registry/verdict-registry-dl/target/verdict-registry-dl-${JGDMS_VERSION}.jar \
  /opt/jgdms/lib-dl/
COPY --chown=jgdms:jgdms \
  --from=builder /build/jgdms-lib-dl/target/jgdms-lib-dl-${JGDMS_VERSION}.jar \
  /opt/jgdms/lib-dl/

COPY --chown=jgdms:jgdms deploy/config/host3-registry.config /opt/jgdms/config/
COPY --chown=jgdms:jgdms deploy/policy/host3-registry.policy  /opt/jgdms/policy/

# /opt/jgdms/log is mounted from a PersistentVolumeClaim by the StatefulSet.
VOLUME ["/opt/jgdms/log"]

EXPOSE 1098
EXPOSE 9080

WORKDIR /opt/jgdms

ENTRYPOINT ["java", \
  "-Djava.security.manager=org.apache.river.api.security.ConcurrentPolicyFile", \
  "-Djava.security.policy=/opt/jgdms/policy/host3-registry.policy", \
  "-Djava.rmi.server.hostname=${JGDMS_SERVICE_HOST}", \
  "-Dnet.jini.discovery.announce=0", \
  "-Dnet.jini.discovery.multicastAnnounceInterval=0", \
  "-Djdk.virtualThreadScheduler.parallelism=4", \
  "-cp", "/opt/jgdms/lib/*", \
  "org.apache.river.start.ServiceStarter", \
  "/opt/jgdms/config/host3-registry.config"]

LABEL org.opencontainers.image.title="jgdms-host3-registry" \
      org.opencontainers.image.description="JGDMS Host 3 — Verdict Registry (stateful, high-trust)" \
      org.opencontainers.image.source="https://github.com/pfirmstone/JGDMS"
