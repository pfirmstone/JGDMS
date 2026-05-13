# Host 5 — JFR Telemetry Service
#
# Reactive service that receives jdk.VirtualThreadPinned JFR events from
# client JVMs via authenticated JERI, rate-limits and deduplicates them,
# then triggers re-analysis via the BAE proxy pool (Host 2) and submits
# updated signed verdicts to Host 3.
#
# It has NO direct connection to Host 4 (Codebase Downloader).

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
  --from=builder /build/services/jfr-telemetry/jfr-telemetry-service/target/jfr-telemetry-service-${JGDMS_VERSION}.jar \
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
# BAE and VerdictRegistry client proxies
COPY --chown=jgdms:jgdms \
  --from=builder /build/services/bytecode-analysis-engine/bytecode-analysis-engine-dl/target/bytecode-analysis-engine-dl-${JGDMS_VERSION}.jar \
  /opt/jgdms/lib/
COPY --chown=jgdms:jgdms \
  --from=builder /build/services/verdict-registry/verdict-registry-dl/target/verdict-registry-dl-${JGDMS_VERSION}.jar \
  /opt/jgdms/lib/

COPY --chown=jgdms:jgdms \
  --from=builder /build/services/jfr-telemetry/jfr-telemetry-dl/target/jfr-telemetry-dl-${JGDMS_VERSION}.jar \
  /opt/jgdms/lib-dl/
COPY --chown=jgdms:jgdms \
  --from=builder /build/jgdms-lib-dl/target/jgdms-lib-dl-${JGDMS_VERSION}.jar \
  /opt/jgdms/lib-dl/

COPY --chown=jgdms:jgdms deploy/config/host5-telemetry.config /opt/jgdms/config/
COPY --chown=jgdms:jgdms deploy/policy/host5-telemetry.policy  /opt/jgdms/policy/

EXPOSE 1098

WORKDIR /opt/jgdms

ENTRYPOINT ["java", \
  "-Djava.security.manager=org.apache.river.api.security.ConcurrentPolicyFile", \
  "-Djava.security.policy=/opt/jgdms/policy/host5-telemetry.policy", \
  "-Djava.rmi.server.hostname=${JGDMS_SERVICE_HOST}", \
  "-Dnet.jini.discovery.announce=0", \
  "-Dnet.jini.discovery.multicastAnnounceInterval=0", \
  "-Djdk.virtualThreadScheduler.parallelism=4", \
  "-cp", "/opt/jgdms/lib/*", \
  "org.apache.river.start.ServiceStarter", \
  "/opt/jgdms/config/host5-telemetry.config"]

LABEL org.opencontainers.image.title="jgdms-host5-telemetry" \
      org.opencontainers.image.description="JGDMS Host 5 — JFR Telemetry Service (reactive)" \
      org.opencontainers.image.source="https://github.com/pfirmstone/JGDMS"
