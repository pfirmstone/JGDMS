# Host 4 — Codebase Downloader Service
#
# The Codebase Downloader is the ONLY container in the SCAP pipeline with
# outbound internet access.  It is triggered by ServiceRegistrar events from
# Host 1, downloads JAR bytes, computes their SHA-256 hash, checks the
# VerdictRegistry (Host 3) for a cache hit, and dispatches AnalysisRequest
# objects to the BAE pool (Host 2) on cache miss.
#
# It has no direct connection to Host 5 (JFR Telemetry Service).

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
  --from=builder /build/services/codebase-downloader/codebase-downloader-service/target/codebase-downloader-service-${JGDMS_VERSION}.jar \
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
  --from=builder /build/jgdms-lib-dl/target/jgdms-lib-dl-${JGDMS_VERSION}.jar \
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
# BAE and VerdictRegistry client proxies needed for JERI lookup
COPY --chown=jgdms:jgdms \
  --from=builder /build/services/bytecode-analysis-engine/bytecode-analysis-engine-dl/target/bytecode-analysis-engine-dl-${JGDMS_VERSION}.jar \
  /opt/jgdms/lib/
COPY --chown=jgdms:jgdms \
  --from=builder /build/services/verdict-registry/verdict-registry-dl/target/verdict-registry-dl-${JGDMS_VERSION}.jar \
  /opt/jgdms/lib/

COPY --chown=jgdms:jgdms deploy/config/host4-downloader.config /opt/jgdms/config/
COPY --chown=jgdms:jgdms deploy/policy/host4-downloader.policy  /opt/jgdms/policy/

EXPOSE 1098

WORKDIR /opt/jgdms

ENTRYPOINT ["java", \
  "-Djava.security.manager=org.apache.river.api.security.ConcurrentPolicyFile", \
  "-Djava.security.policy=/opt/jgdms/policy/host4-downloader.policy", \
  "-Djava.rmi.server.hostname=${JGDMS_SERVICE_HOST}", \
  "-Dnet.jini.discovery.announce=0", \
  "-Dnet.jini.discovery.multicastAnnounceInterval=0", \
  "-Djdk.virtualThreadScheduler.parallelism=4", \
  "-cp", "/opt/jgdms/lib/*", \
  "org.apache.river.start.ServiceStarter", \
  "/opt/jgdms/config/host4-downloader.config"]

LABEL org.opencontainers.image.title="jgdms-host4-downloader" \
      org.opencontainers.image.description="JGDMS Host 4 — Codebase Downloader (only outbound-internet host)" \
      org.opencontainers.image.source="https://github.com/pfirmstone/JGDMS"
