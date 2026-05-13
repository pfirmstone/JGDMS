# Host 1 — Jini Lookup Service (Reggie)
#
# Multi-stage build:
#   Stage 1 (builder) — builds the JGDMS project with a standard JDK.
#   Stage 2 (runtime) — copies only the required JARs into the DirtyChai
#                       base image.
#
# The Reggie lookup service stores marshalled service items opaquely.  It
# does NOT unmarshal proxies or fetch URLs.  It has LOW trust level in the
# SCAP analysis pipeline but HIGH trust for service discovery.
#
# Required environment variables (supplied via Kubernetes ConfigMap/Secret):
#   JGDMS_VERSION         Maven artifact version (e.g. 3.1.1-SNAPSHOT)
#   JGDMS_LOOKUP_HOST     advertised hostname/IP for unicast discovery
#   JGDMS_LOOKUP_PORT     JERI listen port (default 4160)
#   JGDMS_CODEBASE_PORT   HTTP codebase server port (default 9080)
#   TRUST_DOMAIN          SPIFFE trust domain (e.g. jgdms.example.org)

ARG BASE_IMAGE=jgdms/dirtychai-base:latest
ARG BUILD_JDK=eclipse-temurin:21-jdk

# ── Stage 1: build ─────────────────────────────────────────────────────────────
FROM ${BUILD_JDK} AS builder

WORKDIR /build

# Copy Maven wrapper and POM files first for better layer caching.
COPY JGDMS/.mvn            .mvn/
COPY JGDMS/pom.xml         pom.xml

# Copy every sub-module source tree.
COPY JGDMS/ ./

# Build the full project, skipping tests.  The -Pdirtychai profile is NOT
# needed at build time when using a standard JDK — DirtyChai APIs that are
# not present on standard JDK are guarded by reflection in the JGDMS code.
RUN mvn -B -q install -DskipTests --no-transfer-progress

# ── Stage 2: runtime ───────────────────────────────────────────────────────────
FROM ${BASE_IMAGE}

ARG JGDMS_VERSION=3.1.1-SNAPSHOT

# Service JARs
COPY --chown=jgdms:jgdms \
  --from=builder /build/services/reggie/reggie-service/target/reggie-service-${JGDMS_VERSION}.jar \
  /opt/jgdms/lib/

# Shared runtime libraries (jgdms-platform, jgdms-jeri, jgdms-lib, etc.)
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
  --from=builder /build/jgdms-url-integrity/target/jgdms-url-integrity-${JGDMS_VERSION}.jar \
  /opt/jgdms/lib/
COPY --chown=jgdms:jgdms \
  --from=builder /build/jgdms-rmi-tls/target/jgdms-rmi-tls-${JGDMS_VERSION}.jar \
  /opt/jgdms/lib/

# Download JAR (served to clients discovering the lookup service proxy)
COPY --chown=jgdms:jgdms \
  --from=builder /build/services/reggie/reggie-dl/target/reggie-dl-${JGDMS_VERSION}.jar \
  /opt/jgdms/lib-dl/
COPY --chown=jgdms:jgdms \
  --from=builder /build/jgdms-lib-dl/target/jgdms-lib-dl-${JGDMS_VERSION}.jar \
  /opt/jgdms/lib-dl/

# Startup configuration template (ConfigMap will override at runtime)
COPY --chown=jgdms:jgdms deploy/config/host1-lookup.config /opt/jgdms/config/

# Security policy (ConfigMap will override at runtime)
COPY --chown=jgdms:jgdms deploy/policy/host1-lookup.policy /opt/jgdms/policy/

# JERI port (Jini unicast discovery standard port)
EXPOSE 4160
# HTTP codebase server port
EXPOSE 9080

WORKDIR /opt/jgdms

ENTRYPOINT ["java", \
  "-Djava.security.manager=org.apache.river.api.security.ConcurrentPolicyFile", \
  "-Djava.security.policy=/opt/jgdms/policy/host1-lookup.policy", \
  "-Djava.rmi.server.hostname=${JGDMS_LOOKUP_HOST}", \
  "-Dnet.jini.discovery.announce=0", \
  "-Dnet.jini.discovery.multicastAnnounceInterval=0", \
  "-Djdk.virtualThreadScheduler.parallelism=4", \
  "-cp", "/opt/jgdms/lib/*", \
  "org.apache.river.start.ServiceStarter", \
  "/opt/jgdms/config/host1-lookup.config"]

LABEL org.opencontainers.image.title="jgdms-host1-lookup" \
      org.opencontainers.image.description="JGDMS Host 1 — Jini Lookup Service (Reggie)" \
      org.opencontainers.image.source="https://github.com/pfirmstone/JGDMS"
