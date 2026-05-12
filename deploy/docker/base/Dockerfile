# JGDMS DirtyChai base image
#
# Downloads the DirtyChai JDK from its rolling GitHub Release tag,
# verifies the SHA-256 checksum, installs it under /opt/dirtychai, and
# configures JAVA_HOME / PATH so every derived image automatically uses
# the DirtyChai JVM.
#
# DirtyChai is a community OpenJDK fork that retains SecurityManager /
# AccessController functionality required by JGDMS on its full-security
# deployment path.  It is distributed as a pre-built linux/x64 tarball —
# NOT as a Maven artifact.
#
# Build arguments
# ---------------
#   DIRTYCHAI_RELEASE   rolling release tag (default: dirty-chai-latest)
#                       Override with a pinned tag (e.g. v24.0.1) for
#                       reproducible production builds.
#
# Usage
# -----
#   docker build -t jgdms/dirtychai-base:latest .
#
# Registry publication is handled by .github/workflows/dirtychai-base-image.yml.

FROM ubuntu:24.04 AS downloader

ARG DIRTYCHAI_RELEASE=dirty-chai-latest

# Install minimum tooling needed for download + checksum verification.
RUN apt-get update -qq && \
    apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        gh \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /tmp/dirtychai-dl

# Download the JDK tarball and its SHA-256 checksum from GitHub Releases.
# GH_TOKEN must be passed as a build secret to avoid API rate-limiting:
#   docker build --secret id=GH_TOKEN,env=GH_TOKEN ...
# The gh CLI reads GH_TOKEN automatically from the environment when set.
RUN --mount=type=secret,id=GH_TOKEN,env=GH_TOKEN \
    gh release download "${DIRTYCHAI_RELEASE}" \
        --repo pfirmstone/DirtyChai \
        --pattern 'jdk-linux-x64.tar.gz' \
        --pattern 'jdk-linux-x64.tar.gz.sha256' \
    && sha256sum -c jdk-linux-x64.tar.gz.sha256 \
    && echo "Checksum OK"

# Extract into /opt/dirtychai; the tarball expands into a versioned
# subdirectory such as jdk-24.0.1+7.
RUN mkdir -p /opt/dirtychai && \
    tar -xzf jdk-linux-x64.tar.gz -C /opt/dirtychai

# ── Runtime image ──────────────────────────────────────────────────────────────
FROM ubuntu:24.04

# Runtime dependencies for a JVM process: glibc (already in Ubuntu),
# fontconfig is optional but avoids JVM warnings, tzdata for correct times.
RUN apt-get update -qq && \
    apt-get install -y --no-install-recommends \
        fontconfig \
        tzdata \
    && rm -rf /var/lib/apt/lists/*

# Copy DirtyChai from the downloader stage.
COPY --from=downloader /opt/dirtychai /opt/dirtychai

# Locate the versioned JAVA_HOME inside the extracted tree and create a
# stable symlink so derived images can use /opt/java regardless of version.
RUN JDK_DIR="$(dirname "$(find /opt/dirtychai -name 'javac' -type f | head -1)")"; \
    JDK_DIR="${JDK_DIR%/bin}"; \
    ln -sf "$JDK_DIR" /opt/java

ENV JAVA_HOME=/opt/java
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Run as a non-root user by default; derived images add a named service
# account for their role.
RUN groupadd --gid 1000 jgdms && \
    useradd  --uid 1000 --gid jgdms --shell /bin/false --no-create-home jgdms

# Common JGDMS directory layout adopted by all derived images.
RUN mkdir -p /opt/jgdms/lib \
             /opt/jgdms/lib-dl \
             /opt/jgdms/config \
             /opt/jgdms/policy \
             /opt/jgdms/log \
             /run/spire \
    && chown -R jgdms:jgdms /opt/jgdms /run/spire

USER jgdms

# Signal that this is a DirtyChai-based image.
LABEL org.opencontainers.image.title="jgdms-dirtychai-base" \
      org.opencontainers.image.description="DirtyChai JDK base image for JGDMS services" \
      org.opencontainers.image.source="https://github.com/pfirmstone/JGDMS" \
      org.opencontainers.image.licenses="Apache-2.0"
